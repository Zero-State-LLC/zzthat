import Foundation
import OpenAPIRuntime

/// Server times are RFC 3339 UTC, with or without fractional seconds (`.123Z`
/// or `Z`) (runtime.md, API client step 9). Decoding tries fractional seconds
/// first, then whole seconds. Encoding writes fractional seconds, the form the
/// server writes.
public struct LenientISO8601Transcoder: DateTranscoder, @unchecked Sendable {
    private let lock = NSLock()
    private let fractional: ISO8601DateFormatter
    private let whole: ISO8601DateFormatter

    public init() {
        fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        fractional.timeZone = TimeZone(identifier: "UTC")
        whole = ISO8601DateFormatter()
        whole.formatOptions = [.withInternetDateTime]
        whole.timeZone = TimeZone(identifier: "UTC")
    }

    public func encode(_ date: Date) throws -> String {
        lock.lock()
        defer { lock.unlock() }
        return fractional.string(from: date)
    }

    public func decode(_ string: String) throws -> Date {
        lock.lock()
        defer { lock.unlock() }
        if let date = fractional.date(from: string) ?? whole.date(from: string) { return date }
        throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "Expected an RFC 3339 date-time"))
    }
}
