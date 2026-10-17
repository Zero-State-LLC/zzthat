package com.zer0state.zzthat.grammar

/**
 * The one constants file for camera capture (spec.md FR-025, tasks.md T022
 * and T102, recognition-adapter.md "Scope decisions and open gates").
 *
 * NOT QUALIFIED. Every value here is a prototype default, not a measured
 * result. ZZ-OCR-QUAL-001 freezes the detector limits, the baseline
 * tolerance, the ROI inset, the reason-code mapping, and the confidence
 * mapping in versioned qualification configuration before final evaluation.
 * Until a signed PASS receipt exists for this platform and engine, capture is
 * confirm-only (zzThis spec 004 FR-023, D-2026-10-13-05).
 */
object BandThresholds {
    /** Accept needs every word at this confidence or more (zzThis spec 004, Prototype band values, Q37). */
    const val ACCEPT: Double = 0.80

    /** A word below this sends the candidate to Retry. */
    const val RETRY_BELOW: Double = 0.50
}

/**
 * The adapter reason codes the app emits (kebab-case, as the upstream
 * adapter-result fixture writes `missing-closing-fiducial`). Each one has a
 * band in [CaptureConfig.reasonBands]. A code with no entry there fails
 * closed to Abstain.
 */
object ReasonCodes {
    /** The recognizer could not start or threw (recognition.md, Error mapping: Abstain with typed entry). */
    const val RECOGNIZER_UNAVAILABLE = "recognizer-unavailable"

    /** No terminal marker was seen at all. */
    const val NO_FIDUCIAL = "no-fiducial"

    /** An opening marker with no closing marker on its baseline. */
    const val MISSING_CLOSING_FIDUCIAL = "missing-closing-fiducial"

    /** A closing marker with no opening marker on its baseline. */
    const val MISSING_OPENING_FIDUCIAL = "missing-opening-fiducial"

    /**
     * An unpaired marker with words beside it whose side cannot be told. The
     * pinned zzThis ocr-qualification-decoder-replay.mjs reads this code, like
     * the two missing-fiducial codes, as the missing-marker capture issue.
     */
    const val PARTIAL_FIDUCIAL = "partial-fiducial"

    /** A lone `zz` with no words (zzThis decision-band-mapping `bare-mark`). */
    const val BARE_MARK = "bare-mark"

    /** More marker observations than [CaptureConfig.maxMarkers]: the whole frame fails closed. */
    const val MARKER_LIMIT_EXCEEDED = "marker-limit-exceeded"

    /** More pair hypotheses than [CaptureConfig.maxPairs]: the whole frame fails closed. */
    const val PAIR_LIMIT_EXCEEDED = "pair-limit-exceeded"

    /** A marker-like token whose own box the reader did not give, or a text line with no box. */
    const val UNLOCALIZED_MARKER = "unlocalized-marker"

    /** Skew beyond [CaptureConfig.maxSkewDegrees], or markers whose geometry does not resolve to one baseline. */
    const val UNSUPPORTED_GEOMETRY = "unsupported-geometry"

    /** The interior ROI is empty, too small, or overlaps another marker. */
    const val UNSAFE_ROI = "unsafe-roi"

    /** A payload line the reader gave without a box: the reading of the ROI is incomplete. */
    const val UNLOCALIZED_TEXT = "unlocalized-text"

    /** The ROI was valid and the reader returned no payload text. */
    const val NO_PAYLOAD_TEXT = "no-payload-text"

    /** More text hypotheses than [CaptureConfig.maxHypotheses] (decision-band-mapping `invalid-hypothesis-count-abstain`). */
    const val HYPOTHESIS_LIMIT_EXCEEDED = "hypothesis-limit-exceeded"

    /** The configuration itself is invalid (decision-band-mapping `invalid-thresholds-abstain`). */
    const val INVALID_CONFIGURATION = "invalid-configuration"

    /**
     * Evidence that does not hold together: an image with no size, a marker
     * box outside [0,1] or with no area, a repeated detection id, or pair,
     * ROI, and mark ids that do not link. The frame fails closed with no
     * candidates (zzThis specs/004-capture/qualification.md, adapter input).
     */
    const val INVALID_EVIDENCE = "invalid-evidence"
}

/**
 * Capture configuration. One instance, [DEFAULT], is the build's setting;
 * there is no runtime or remote switch (recognition.md, Configuration).
 */
data class CaptureConfig(
    /** A version string recorded with every RecognitionResult. */
    val version: String,
    /** False until ZZ-OCR-QUAL-001 records a PASS receipt for this exact configuration. */
    val qualified: Boolean,
    /**
     * zzThis spec 004 FR-023: a camera candidate that would be Accept becomes
     * Clarify with the full canonical code and a confirm step. Typed input is
     * unchanged. On until a signed PASS receipt exists.
     */
    val confirmOnly: Boolean,
    val acceptMinConfidence: Double,
    val retryBelowConfidence: Double,
    /** Hard limit on terminal marker observations in one frame. Over it, the whole frame fails closed. */
    val maxMarkers: Int,
    /** Hard limit on complete pair hypotheses in one frame. Over it, the whole frame fails closed. */
    val maxPairs: Int,
    /** Hard limit on text hypotheses for one ROI. Over it, Abstain. */
    val maxHypotheses: Int,
    /** Two markers share a baseline when their bottom edges differ by at most this fraction of the taller marker's height. */
    val baselineTolerance: Double,
    /** Two paired markers may differ in height by at most this ratio. */
    val maxMarkerHeightRatio: Double,
    /** Text skewed more than this, in degrees, is unsupported geometry in v1 (no rectification). */
    val maxSkewDegrees: Double,
    /**
     * When no glyph sits beside a marker in its reader token, the interior ROI
     * starts this fraction of the marker height past the marker's inner edge.
     * With a glyph beside it, the edge is halfway between the two boxes.
     */
    val roiInset: Double,
    /** The ROI extends this fraction of the marker height above and below the markers, for ascenders and descenders. */
    val roiVerticalExtent: Double,
    /** The ROI crop must be at least this many pixels on each side (ML Kit's input minimum is 32). */
    val minRoiPixels: Int,
    /** Reason code to band. A reason code not listed here is Abstain. */
    val reasonBands: Map<String, Band>,
) {
    /**
     * decision-band-mapping `invalid-thresholds-abstain`, with zz-core's
     * validThresholds rule (finite, in 0 to 1, retry strictly below accept),
     * and missing or non-positive limits fail closed.
     */
    val isValid: Boolean
        get() = retryBelowConfidence.isFinite() && acceptMinConfidence.isFinite() &&
            retryBelowConfidence in 0.0..1.0 && acceptMinConfidence in 0.0..1.0 &&
            retryBelowConfidence < acceptMinConfidence &&
            maxMarkers > 0 && maxPairs > 0 && maxHypotheses > 0 &&
            baselineTolerance.isFinite() && baselineTolerance > 0 &&
            maxMarkerHeightRatio.isFinite() && maxMarkerHeightRatio >= 1 &&
            maxSkewDegrees.isFinite() && maxSkewDegrees >= 0 &&
            roiInset.isFinite() && roiInset >= 0 &&
            roiVerticalExtent.isFinite() && roiVerticalExtent >= 0 &&
            minRoiPixels > 0

    /** The band for an adapter reason code. Unknown codes fail closed. */
    fun bandFor(reason: String): Band = reasonBands[reason] ?: Band.ABSTAIN

    companion object {
        /**
         * The upstream decision-band-mapping.json `capture_issues` rows, then
         * the app's own codes mapped by recognition.md, Error mapping.
         */
        val DEFAULT_REASON_BANDS: Map<String, Band> = mapOf(
            ReasonCodes.PARTIAL_FIDUCIAL to Band.RETRY,
            "cutoff-marker" to Band.RETRY,
            "blur" to Band.RETRY,
            "glare" to Band.RETRY,
            ReasonCodes.BARE_MARK to Band.ABSTAIN,
            ReasonCodes.RECOGNIZER_UNAVAILABLE to Band.ABSTAIN,
            ReasonCodes.NO_FIDUCIAL to Band.RETRY,
            ReasonCodes.MISSING_CLOSING_FIDUCIAL to Band.RETRY,
            ReasonCodes.MISSING_OPENING_FIDUCIAL to Band.RETRY,
            ReasonCodes.MARKER_LIMIT_EXCEEDED to Band.RETRY,
            ReasonCodes.PAIR_LIMIT_EXCEEDED to Band.RETRY,
            ReasonCodes.UNLOCALIZED_MARKER to Band.RETRY,
            ReasonCodes.UNSUPPORTED_GEOMETRY to Band.RETRY,
            ReasonCodes.UNSAFE_ROI to Band.RETRY,
            ReasonCodes.UNLOCALIZED_TEXT to Band.RETRY,
            ReasonCodes.NO_PAYLOAD_TEXT to Band.RETRY,
            ReasonCodes.HYPOTHESIS_LIMIT_EXCEEDED to Band.ABSTAIN,
            ReasonCodes.INVALID_CONFIGURATION to Band.ABSTAIN,
            ReasonCodes.INVALID_EVIDENCE to Band.ABSTAIN,
        )

        /** The build's capture configuration. NOT QUALIFIED: see the file comment. */
        val DEFAULT = CaptureConfig(
            version = "android-capture-0-unqualified",
            qualified = false,
            confirmOnly = true,
            acceptMinConfidence = BandThresholds.ACCEPT,
            retryBelowConfidence = BandThresholds.RETRY_BELOW,
            maxMarkers = 8,
            maxPairs = 4,
            maxHypotheses = 4,
            baselineTolerance = 0.35,
            maxMarkerHeightRatio = 2.0,
            maxSkewDegrees = 10.0,
            roiInset = 0.25,
            roiVerticalExtent = 0.6,
            minRoiPixels = 32,
            reasonBands = DEFAULT_REASON_BANDS,
        )
    }
}
