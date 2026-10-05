// The one constants file for the capture bands (spec.md FR-025, tasks.md T022).
// Values are zzThis spec 004, Prototype band values (Q37). They are parameters,
// not measured results.

public enum BandThresholds {
    /// Accept needs every word at this confidence or more.
    public static let accept: Double = 0.80
    /// A word below this sends the candidate to Retry.
    public static let retryBelow: Double = 0.50
}
