package com.zer0state.zzthat.grammar

/**
 * The one constants file for the capture bands (spec.md FR-025, tasks.md T022).
 *
 * Values are zzThis spec 004, Prototype band values (Q37). They are
 * parameters, not measured results.
 */
object BandThresholds {
    /** Accept needs every word at this confidence or more. */
    const val ACCEPT: Double = 0.80

    /** A word below this sends the candidate to Retry. */
    const val RETRY_BELOW: Double = 0.50
}
