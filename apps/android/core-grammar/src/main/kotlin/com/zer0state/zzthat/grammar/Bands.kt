package com.zer0state.zzthat.grammar

// The spec 004 decision bands (Decision bands and Prototype band values).

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
 * step 1). The band function takes these rows, not recognizer objects.
 */
data class BandRow(
    val token: String,
    val lineIndex: Int,
    val lineConfidence: Double,
)

object Bands {
    /** A candidate takes the lowest confidence among the lines its words came from. */
    fun candidateConfidence(rows: List<BandRow>): Double? {
        val byLine = HashMap<Int, Double>()
        for (row in rows) {
            byLine[row.lineIndex] = minOf(byLine[row.lineIndex] ?: row.lineConfidence, row.lineConfidence)
        }
        return byLine.values.minOrNull()
    }

    /**
     * @param candidate the scanner candidate, or null when the photo held none.
     * @param codeClass the classifier result for a code candidate.
     * @param check the local check word result, or null when step 4 did not
     *   run (no matching list version, FR-044).
     * @param rows the candidate's words with their line confidences. Typed
     *   input passes none.
     */
    fun band(candidate: ScanKind?, codeClass: CodeClass?, check: CheckResult?, rows: List<BandRow>, source: ReadSource): Band {
        if (candidate == null) return if (source == ReadSource.CAMERA) Band.RETRY else Band.ABSTAIN
        when (candidate) {
            is ScanKind.Invalid, ScanKind.Bare -> return Band.ABSTAIN
            is ScanKind.Partial -> return if (source == ReadSource.CAMERA) Band.RETRY else Band.ABSTAIN
            is ScanKind.Code -> Unit
        }
        if (check == CheckResult.WrongLength) return Band.ABSTAIN
        val resolvedClass = codeClass ?: CodeClass.Field
        return when (source) {
            ReadSource.TYPED -> when {
                resolvedClass is CodeClass.Confirm -> Band.CLARIFY
                check == CheckResult.CheckMismatch -> Band.CLARIFY
                else -> Band.ACCEPT
            }
            ReadSource.CAMERA -> {
                val confidence = candidateConfidence(rows) ?: 0.0
                when {
                    confidence < BandThresholds.RETRY_BELOW -> Band.RETRY
                    resolvedClass is CodeClass.Confirm -> Band.CLARIFY
                    check == CheckResult.CheckMismatch -> Band.CLARIFY
                    resolvedClass != CodeClass.Word -> Band.CLARIFY
                    confidence < BandThresholds.ACCEPT -> Band.CLARIFY
                    else -> Band.ACCEPT
                }
            }
        }
    }
}
