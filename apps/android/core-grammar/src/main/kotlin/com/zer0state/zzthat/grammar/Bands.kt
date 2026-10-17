package com.zer0state.zzthat.grammar

// The spec 004 decision bands (Decision bands and Prototype band values), in
// the precedence of the pinned ZZ-OCR-QUAL-001 decision-band-mapping.json, and
// confirm-only mode (spec 004 FR-023). The band reads normalized evidence
// only: scanner, classifier, and check-word results, per-word line
// confidence, and the adapter's reason codes and hypothesis count. It never
// sees a recognizer object.

enum class Band(val wire: String) {
    ACCEPT("accept"),
    CLARIFY("clarify"),
    RETRY("retry"),
    ABSTAIN("abstain"),
}

enum class ReadSource {
    CAMERA,
    TYPED,
}

/**
 * One word of a candidate with the line it came from (Client read pipeline
 * step 2). The band function takes these rows, not recognizer objects.
 */
data class BandRow(
    val token: String,
    val lineIndex: Int,
    /** The line's meaningful engine confidence, or null when the engine gives none. Never synthesized. */
    val lineConfidence: Double?,
)

/** Camera evidence outside the decoded text: the adapter's reason codes and how many text hypotheses the ROI gave. */
data class CaptureEvidence(val reasonCodes: List<String> = emptyList(), val hypothesisCount: Int = 1) {
    companion object {
        val NONE = CaptureEvidence()
    }
}

/** The band, and whether confirm-only turned an Accept into this Clarify. */
data class BandDecision(val band: Band, val confirmOnly: Boolean = false)

object Bands {
    /**
     * A candidate takes the lowest confidence among the lines its words came
     * from. One word without a meaningful line confidence makes the
     * candidate's confidence not available.
     */
    fun candidateConfidence(rows: List<BandRow>): Confidence = Confidence.minimum(rows.map { it.lineConfidence })

    fun band(
        candidate: ScanKind?,
        codeClass: CodeClass?,
        check: CheckResult?,
        rows: List<BandRow>,
        source: ReadSource,
        evidence: CaptureEvidence = CaptureEvidence.NONE,
        config: CaptureConfig = CaptureConfig.DEFAULT,
    ): Band = decide(candidate, codeClass, check, rows, source, evidence, config).band

    /**
     * @param candidate the scanner candidate, or null when there was none.
     * @param codeClass the classifier result for a code candidate.
     * @param check the local check word result, or null when step 5 did not
     *   run (no matching list version, FR-044).
     * @param rows the candidate's words with their line confidences. Typed
     *   input passes none.
     */
    fun decide(
        candidate: ScanKind?,
        codeClass: CodeClass?,
        check: CheckResult?,
        rows: List<BandRow>,
        source: ReadSource,
        evidence: CaptureEvidence = CaptureEvidence.NONE,
        config: CaptureConfig = CaptureConfig.DEFAULT,
    ): BandDecision = when (source) {
        ReadSource.TYPED -> BandDecision(typed(candidate, codeClass, check))
        ReadSource.CAMERA -> {
            val band = camera(candidate, codeClass, check, rows, evidence, config)
            // FR-023: only the camera column changes, and only Accept.
            if (band == Band.ACCEPT && config.confirmOnly) BandDecision(Band.CLARIFY, confirmOnly = true) else BandDecision(band)
        }
    }

    /** spec 004 Prototype band values, Typed trigger column. */
    private fun typed(candidate: ScanKind?, codeClass: CodeClass?, check: CheckResult?): Band {
        val code = candidate as? ScanKind.Code ?: return Band.ABSTAIN
        if (code.code.kind == CodeKind.BARE) return Band.ABSTAIN
        if (check == CheckResult.WrongLength) return Band.ABSTAIN
        return when {
            (codeClass ?: CodeClass.Field) is CodeClass.Confirm -> Band.CLARIFY
            check == CheckResult.CheckMismatch -> Band.CLARIFY
            else -> Band.ACCEPT
        }
    }

    /** decision-band-mapping.json `precedence`, first match wins. */
    private fun camera(
        candidate: ScanKind?,
        codeClass: CodeClass?,
        check: CheckResult?,
        rows: List<BandRow>,
        evidence: CaptureEvidence,
        config: CaptureConfig,
    ): Band {
        val reasonBands = evidence.reasonCodes.map(config::bandFor)
        // bare-mark-abstain, and any reason code that maps to Abstain (an
        // unmapped code does: fail closed).
        if (candidate == ScanKind.Bare || Band.ABSTAIN in reasonBands) return Band.ABSTAIN
        // explicit-unreadable-capture-issue-retry; a partial candidate is a missing marker.
        if (Band.RETRY in reasonBands || candidate is ScanKind.Partial) return Band.RETRY
        // no-candidate-retry
        if (candidate == null) return Band.RETRY
        // invalid-thresholds-abstain
        if (!config.isValid) return Band.ABSTAIN
        // invalid-hypothesis-count-abstain, and more than the configured limit
        if (evidence.hypothesisCount < 1 || evidence.hypothesisCount > config.maxHypotheses) return Band.ABSTAIN
        // multiple-hypotheses-clarify: never pick one by confidence or validity.
        if (evidence.hypothesisCount > 1) return Band.CLARIFY
        // malformed-payload-grammar-abstain
        val code = (candidate as? ScanKind.Code)?.code ?: return Band.ABSTAIN
        if (code.kind == CodeKind.BARE) return Band.ABSTAIN
        // handle-name-field-or-near-word-clarify (FR-010, FR-015)
        val resolvedClass = codeClass ?: CodeClass.Field
        if (resolvedClass != CodeClass.Word) return Band.CLARIFY
        // wrong-check-word-length-abstain
        if (check == CheckResult.WrongLength) return Band.ABSTAIN
        // check-word-mismatch-or-unknown-word-clarify
        if (check == CheckResult.CheckMismatch || check is CheckResult.UnknownWord) return Band.CLARIFY
        // missing-confidence-clarify
        val confidence = candidateConfidence(rows).valueOrNull ?: return Band.CLARIFY
        return when {
            confidence < config.retryBelowConfidence -> Band.RETRY
            confidence < config.acceptMinConfidence -> Band.CLARIFY
            else -> Band.ACCEPT
        }
    }
}
