package com.zer0state.zzthat.grammar

// The engine-neutral camera evidence (recognition-adapter.md, recognition.md
// Boundary, tasks.md T023). Field names follow the pinned zzThis
// ZZ-OCR-QUAL-001 adapter-result.schema.json one for one, in Kotlin case:
// `fiducials` (fiducialDetection), `rois` (roiDetection), `candidates`
// (textCandidate), and `reasonCodes` (reason_codes). Fields the schema does
// not have are marked "app only". Vendor recognizer objects never reach this
// file: an adapter maps them to RecognizedLine first.
//
// Coordinates are normalized to [0,1] over the upright source image, origin
// at the top left. Ids are scoped to one image: a new photo is a new
// RecognitionResult with a new captureId, and nothing carries across.

/** A point in normalized upright image coordinates (schema `point`). */
data class NormPoint(val x: Double, val y: Double)

/** The source image's size in pixels after upright normalization (app only; geometry needs the aspect ratio). */
data class PixelSize(val width: Int, val height: Int)

/** A pixel rectangle over the upright source image, right and bottom exclusive (app only). */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Step 1, upright normalization, applied once to the captured still (app
 * only provenance). [rotationDegrees] is the EXIF rotation applied, one of
 * 0, 90, 180, 270; [mirrored] is an EXIF flip.
 */
data class Upright(val rotationDegrees: Int, val mirrored: Boolean) {
    companion object {
        val NONE = Upright(0, mirrored = false)
    }
}

/** Schema `fiducialDetection.role`. */
enum class MarkerRole(val wire: String) {
    OPENING("opening"),
    CLOSING("closing"),
    UNKNOWN("unknown"),
}

/** Schema `runtime_outcome`. A failed runtime row carries no recognition output. */
enum class RuntimeOutcome(val wire: String) {
    COMPLETED("completed"),
    EXCEPTION("exception"),
}

/**
 * Schema `confidence_status` and `confidence` together: a meaningful engine
 * score, or explicitly none. Never synthesized.
 */
sealed interface Confidence {
    data class Available(val value: Double) : Confidence {
        init {
            require(value.isFinite() && value in 0.0..1.0) { "confidence is outside 0 to 1" }
        }
    }

    data object NotAvailable : Confidence

    val status: String get() = if (this is Available) "available" else "not-available"

    val valueOrNull: Double? get() = (this as? Available)?.value

    companion object {
        /** The minimum across parts; one part without a meaningful score makes the whole not available. */
        fun minimum(values: List<Double?>): Confidence =
            if (values.isEmpty() || values.any { it == null }) NotAvailable else Available(values.minOf { it!! })
    }
}

/** Schema `fiducialDetection`: one terminal `zz` index marker. */
data class MarkerObservation(
    val detectionId: String,
    /** Null when the marker shares no baseline with another marker. */
    val baselineId: String?,
    val role: MarkerRole,
    /** The one pair this marker belongs to; null when it is in none, or in more than one (ambiguous). */
    val pairId: String?,
    /** A marker detection score. ML Kit gives none that is meaningful, so it stays null; it is never payload confidence. */
    val score: Double?,
    val box: NormRect,
    /** App only: the marker as written, `zz` or `(zz)`, in any case. Shown on a partial or bare row. */
    val literal: String,
    /**
     * App only: where the payload interior ends, on the marker's left, when
     * the marker shares its reader token with the text before it: halfway
     * between the joining hyphen and the glyph before that hyphen, or
     * halfway to a glyph glued on. Null when nothing is glued. Glyph boxes
     * only, never their text.
     */
    val edgeBefore: Double? = null,
    /** App only: the same, on the marker's right: where the payload interior starts. */
    val edgeAfter: Double? = null,
    /** App only: the reader's text angle for this marker's line, in degrees, or null when the reader gives none. */
    val angleDegrees: Double?,
    /** App only: the marker is the only token on its reader line (a bare mark rather than a partial code when unpaired). */
    val aloneOnLine: Boolean,
)

/** App only: an opening and a closing marker on one baseline, adjacent in reading order. */
data class PairHypothesis(
    val pairId: String,
    val openingDetectionId: String,
    val closingDetectionId: String,
    val baselineId: String,
    /** The two markers' union: the box the Scan screen draws. */
    val box: NormRect,
)

/** Complete pairs, partial codes, and bare marks stay separate, visible candidates (step 2). */
sealed interface VisualCandidate {
    val box: NormRect

    data class Complete(val pair: PairHypothesis) : VisualCandidate {
        override val box get() = pair.box
    }

    /** One endpoint only. Selecting it never runs payload OCR. */
    data class Partial(val marker: MarkerObservation) : VisualCandidate {
        override val box get() = marker.box
    }

    /** A lone mark. Selecting it never runs payload OCR. */
    data class Bare(val marker: MarkerObservation) : VisualCandidate {
        override val box get() = marker.box
    }
}

/** Schema `roiDetection`: the validated interior ROI of one complete pair. */
data class PayloadRoi(
    val pairId: String,
    val openingDetectionId: String,
    val closingDetectionId: String,
    val baselineId: String,
    val polygon: List<NormPoint>,
    /** The in-plane rotation applied to the crop. V1 applies none past upright normalization, so this is 0. */
    val orientationDegrees: Double,
    /** Perspective and projective transforms are prohibited in v1, so this is always null. */
    val rectificationTransform: List<Double>?,
    /** True means the safe crop succeeded; it never means a perspective rectification was applied. */
    val rectificationSucceeded: Boolean,
    /** App only: the source pixel rectangle that was cropped. */
    val crop: PixelRect,
) {
    init {
        require(rectificationTransform == null) { "v1 applies no perspective or projective rectification" }
    }
}

/** Schema `textCandidate`: one raw text hypothesis for one ROI, in the engine's order. */
data class TextHypothesis(
    val candidateId: String,
    /** Never null: an ROI and its recognition output link to exactly one pair. */
    val pairId: String,
    /** Payload text between the markers, as the engine read it; the markers are not in it. */
    val rawText: String,
    /** The minimum meaningful line confidence, or explicitly not available. */
    val confidence: Confidence,
    /** Line boxes over the source image. */
    val regions: List<NormRect>,
    /** App only: line provenance, in reading order, in source coordinates. */
    val lines: List<RecognizedLine>,
)

/**
 * The adapter's output for one captured image: marker evidence after
 * pairing, then, once one complete pair is the sole or selected candidate,
 * its ROI and text hypotheses. Memory only, for the current attempt.
 */
data class RecognitionResult(
    /** The stable image-scoped id; every other id here is scoped to it. */
    val captureId: String,
    val engineId: String,
    val engineVersion: String,
    /** App only: [CaptureConfig.version]. */
    val configVersion: String,
    val image: PixelSize,
    val upright: Upright,
    val runtimeOutcome: RuntimeOutcome,
    val fiducials: List<MarkerObservation>,
    /** App only. */
    val pairs: List<PairHypothesis>,
    /** App only: what the Scan screen boxes and lists, in reading order. Empty when the frame failed closed. */
    val visualCandidates: List<VisualCandidate>,
    val rois: List<PayloadRoi>,
    val candidates: List<TextHypothesis>,
    /** Unique, in the order they were raised. */
    val reasonCodes: List<String>,
    /** App only: the whole frame failed closed (a limit, unsupported geometry, or an unlocalized marker). No payload OCR runs. */
    val failedClosed: Boolean,
) {
    init {
        require(engineId.isNotEmpty() && engineVersion.isNotEmpty()) { "engine id and version are required" }
        require(reasonCodes.distinct().size == reasonCodes.size) { "reason codes are unique" }
        require(!failedClosed || visualCandidates.isEmpty()) { "a frame that failed closed offers no candidate" }
        require(pairs.all { it.pairId.isNotEmpty() }) { "every complete pair has a pair id" }
        val pairIds = pairs.map { it.pairId }.toSet()
        require(rois.all { it.pairId in pairIds }) { "every ROI links to one pair" }
        require(candidates.all { candidate -> rois.any { it.pairId == candidate.pairId } }) { "every hypothesis links to an ROI" }
        require(runtimeOutcome == RuntimeOutcome.COMPLETED || candidates.isEmpty()) { "a failed runtime row has no recognition output" }
    }

    fun pair(pairId: String): PairHypothesis? = pairs.firstOrNull { it.pairId == pairId }

    fun marker(detectionId: String): MarkerObservation? = fiducials.firstOrNull { it.detectionId == detectionId }

    /** The hypotheses for one pair, in the engine's order. */
    fun hypotheses(pairId: String): List<TextHypothesis> = candidates.filter { it.pairId == pairId }

    /**
     * Step 3: proceed without a tap only when one complete pair is the only
     * selectable visual candidate.
     */
    val soleCompletePair: PairHypothesis?
        get() = (visualCandidates.singleOrNull() as? VisualCandidate.Complete)?.pair

    fun withReason(code: String): RecognitionResult = if (code in reasonCodes) this else copy(reasonCodes = reasonCodes + code)

    /**
     * The schema-shaped `fiducials`, `rois`, `candidates`, and `reason_codes`
     * of one adapter-result `sampleResult`, with the schema's own keys. App
     * only fields are left out. Used to prove the shape matches the pinned
     * schema; nothing writes it anywhere.
     */
    fun schemaFields(): Map<String, Any?> = mapOf(
        "runtime_outcome" to runtimeOutcome.wire,
        "fiducials" to fiducials.map { marker ->
            buildMap {
                put("detection_id", marker.detectionId)
                put("baseline_id", marker.baselineId)
                put("role", marker.role.wire)
                put("pair_id", marker.pairId)
                marker.score?.let { put("score", it) }
                put("box", marker.box.schema())
            }
        },
        "rois" to rois.map { roi ->
            buildMap {
                put("pair_id", roi.pairId)
                put("opening_detection_id", roi.openingDetectionId)
                put("closing_detection_id", roi.closingDetectionId)
                put("baseline_id", roi.baselineId)
                put("polygon", roi.polygon.map { mapOf("x" to it.x, "y" to it.y) })
                put("orientation_degrees", roi.orientationDegrees)
                roi.rectificationTransform?.let { put("rectification_transform", it) }
                put("rectification_succeeded", roi.rectificationSucceeded)
            }
        },
        "candidates" to candidates.map { candidate ->
            mapOf(
                "candidate_id" to candidate.candidateId,
                "pair_id" to candidate.pairId,
                "raw_text" to candidate.rawText,
                "confidence_status" to candidate.confidence.status,
                "confidence" to candidate.confidence.valueOrNull,
                "regions" to candidate.regions.map { it.schema() },
            )
        },
        "reason_codes" to reasonCodes,
    )

    private fun NormRect.schema() = mapOf("x" to x, "y" to y, "width" to width, "height" to height)
}
