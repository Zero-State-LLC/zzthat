package com.zer0state.zzthat.grammar

/**
 * The one constants file for capture (spec.md FR-025, tasks.md T022).
 *
 * These are the zzThis spec 004 prototype band values and the Section 2.2a G1
 * near-word limit. They are parameters (zzThis Q37), not measured results.
 */
object Parameters {
    /** Accept needs every word at this line confidence or more. */
    const val ACCEPT_AT: Double = 0.80

    /** A word below this line confidence sends a candidate to Retry. */
    const val RETRY_BELOW: Double = 0.50

    /** A letters-only part within this Levenshtein distance of a list word is a near-word. */
    const val NEAR_WORD_MAX_DISTANCE: Int = 2
}
