package com.zer0state.zzthat.grammar

// recognition-adapter.md, Runtime sequence, as pure steps the app calls in
// order: build the RecognitionResult from the reader's marker pass (steps 1
// to 3), derive the selected pair's ROI (step 4), attach the reader's text
// hypotheses for that ROI only (step 5), then rebuild the literal markers and
// hand the text to the shared scanner, grammar, classifier, check word, and
// bands (steps 6 and 7). The reader's own objects never come in here.

/** The decode of one selected or sole complete pair. */
data class PairRead(
    val pairId: String,
    /** One candidate per scanner result, hypotheses in the engine's order. Empty when no text could be decoded. */
    val candidates: List<ReadCandidate>,
    /** The reasons scoped to this pair: its ROI, its reader output, and its decode. */
    val reasonCodes: List<String>,
    /** The band when [candidates] is empty: Retry or Abstain from [reasonCodes]. */
    val emptyBand: Band,
) {
    /** Client read pipeline step 7: open at once only when the one candidate is in Accept. */
    val autoOpen: ReadCandidate? get() = candidates.singleOrNull()?.takeIf { it.band == Band.ACCEPT }
}

object CameraRead {
    /**
     * Steps 1 to 3 from the reader's pass over the upright image. [lines] are
     * normalized over that image. [unlocalizedLines] counts reader lines that
     * came without a usable box: one of them could hold a marker, so the frame
     * fails closed rather than drop it.
     */
    fun detect(
        captureId: String,
        engineId: String,
        engineVersion: String,
        image: PixelSize,
        upright: Upright,
        lines: List<RecognizedLine>,
        config: CaptureConfig,
        unlocalizedLines: Int = 0,
    ): RecognitionResult {
        val detected = MarkerDetector.detect(lines)
        val failClosedReason = when {
            unlocalizedLines > 0 || ReasonCodes.UNLOCALIZED_MARKER in detected.reasonCodes -> ReasonCodes.UNLOCALIZED_MARKER
            else -> null
        }
        val paired = if (failClosedReason != null) {
            MarkerPairing.Output(detected.markers, emptyList(), emptyList(), emptyMap(), listOf(failClosedReason), failedClosed = true)
        } else {
            MarkerPairing.pair(detected.markers, image, config)
        }
        return RecognitionResult(
            captureId = captureId,
            engineId = engineId,
            engineVersion = engineVersion,
            configVersion = config.version,
            image = image,
            upright = upright,
            runtimeOutcome = RuntimeOutcome.COMPLETED,
            fiducials = paired.markers,
            pairs = paired.pairs,
            visualCandidates = paired.visual,
            rois = emptyList(),
            candidates = emptyList(),
            reasonCodes = paired.reasonCodes.distinct(),
            failedClosed = paired.failedClosed,
        )
    }

    /** The reader threw or could not start: Abstain with typed entry, never Accept, never a network fallback. */
    fun recognizerFailed(captureId: String, engineId: String, engineVersion: String, image: PixelSize, upright: Upright, config: CaptureConfig) =
        RecognitionResult(
            captureId = captureId,
            engineId = engineId,
            engineVersion = engineVersion,
            configVersion = config.version,
            image = image,
            upright = upright,
            runtimeOutcome = RuntimeOutcome.EXCEPTION,
            fiducials = emptyList(),
            pairs = emptyList(),
            visualCandidates = emptyList(),
            rois = emptyList(),
            candidates = emptyList(),
            reasonCodes = listOf(ReasonCodes.RECOGNIZER_UNAVAILABLE),
            failedClosed = true,
        )

    /** Step 4 for one complete pair the person selected, or the sole one. */
    fun roi(result: RecognitionResult, pairId: String, config: CaptureConfig): InteriorRoi.Outcome {
        if (result.failedClosed) return InteriorRoi.Outcome.Unsafe(result.reasonCodes.firstOrNull() ?: ReasonCodes.UNSAFE_ROI)
        return InteriorRoi.derive(result, pairId, config)
    }

    /**
     * Step 5: the reader's hypotheses for [roi], each a list of lines
     * normalized over the crop, in the engine's order. They are mapped back
     * to source coordinates; the order is kept as given.
     */
    fun withPayload(result: RecognitionResult, roi: PayloadRoi, readings: List<List<RecognizedLine>>): RecognitionResult {
        val hypotheses = readings.map { lines -> lines.filterNot { Unicode.isBlank(it.text) }.map { toSource(it, roi.crop, result.image) } }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, lines ->
                val ordered = ReadPipeline.readingOrder(lines)
                TextHypothesis(
                    candidateId = "${roi.pairId}/h${index + 1}",
                    pairId = roi.pairId,
                    rawText = ordered.joinToString(" ") { it.text },
                    confidence = Confidence.minimum(ordered.map { it.confidence }),
                    regions = ordered.map { it.box },
                    lines = ordered,
                )
            }
        return result.copy(
            rois = result.rois.filterNot { it.pairId == roi.pairId } + roi,
            candidates = result.candidates.filterNot { it.pairId == roi.pairId } + hypotheses,
        )
    }

    /**
     * Steps 6 and 7 for one pair. Each hypothesis, in the engine's order,
     * becomes the literal code `zz-<payload>-zz`, as the pinned zzThis
     * zz-core decideCaptureBand does, and goes through the shared grammar,
     * classifier, check word, and bands. Nothing is reordered or dropped by
     * confidence, validity, or wordlist distance.
     */
    fun decode(result: RecognitionResult, pairId: String, pipeline: ReadPipeline, roiReason: String? = null): PairRead {
        val config = pipeline.capture
        val reasons = LinkedHashSet<String>()
        roiReason?.let(reasons::add)
        val pair = result.pair(pairId)
        val hypotheses = result.hypotheses(pairId)
        if (pair == null || result.failedClosed) reasons += result.reasonCodes.ifEmpty { listOf(ReasonCodes.UNSAFE_ROI) }
        if (reasons.isEmpty() && hypotheses.isEmpty()) reasons += ReasonCodes.NO_PAYLOAD_TEXT
        if (reasons.isEmpty() && hypotheses.size > config.maxHypotheses) reasons += ReasonCodes.HYPOTHESIS_LIMIT_EXCEEDED
        val evidence = CaptureEvidence(reasons.toList(), hypotheses.size)
        val emptyBand = Bands.band(null, null, null, emptyList(), ReadSource.CAMERA, evidence, config)
        if (reasons.isNotEmpty()) return PairRead(pairId, emptyList(), reasons.toList(), emptyBand)
        val candidates = hypotheses.mapIndexed { index, hypothesis ->
            val literal = literal(hypothesis.rawText)
            val kind = when (val parsed = Grammar.parse(literal)) {
                is ParseResult.Success -> if (parsed.code.kind == CodeKind.BARE) ScanKind.Invalid(ParseFailure.NO_CONTENT, literal) else ScanKind.Code(parsed.code)
                is ParseResult.Failure -> ScanKind.Invalid(parsed.reason, literal)
            }
            pipeline.evaluate(kind, index, rows(hypothesis), hypothesis.regions, ReadSource.CAMERA, evidence)
        }
        return PairRead(pairId, candidates, reasons.toList(), emptyBand)
    }

    /** Step 6: the literal opening and closing markers around the payload as read. */
    fun literal(payload: String): String = "zz-$payload-zz"

    /**
     * Every payload part keeps its own line's confidence (spec 004 Client
     * read pipeline step 2): the line's text split at spaces and hyphens.
     */
    private fun rows(hypothesis: TextHypothesis): List<BandRow> = hypothesis.lines.flatMapIndexed { index, line ->
        Unicode.plainSpaces(line.text).split(' ', '-').filter { part -> part.isNotEmpty() && part.none { Unicode.isDash(it.code) } }
            .map { BandRow(it, index, line.confidence) }
    }

    private fun toSource(line: RecognizedLine, crop: PixelRect, image: PixelSize): RecognizedLine {
        fun map(box: NormRect) = NormRect(
            x = (crop.left + box.x * crop.width) / image.width,
            y = (crop.top + box.y * crop.height) / image.height,
            width = box.width * crop.width / image.width,
            height = box.height * crop.height / image.height,
        )
        return line.copy(
            box = map(line.box),
            pieces = line.pieces.map { piece -> piece.copy(box = map(piece.box), glyphs = piece.glyphs.map(::map)) },
        )
    }
}
