import Foundation

/// contracts/vectors.json at the pin, read in place so there is one copy.
enum Vectors {
    static let url: URL = {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<7 { url.deleteLastPathComponent() }
        return url.appendingPathComponent("contracts/vectors.json")
    }()

    nonisolated(unsafe) static let root: [String: Any] = {
        guard let data = FileManager.default.contents(atPath: url.path),
              let object = try? JSONSerialization.jsonObject(with: data),
              let root = object as? [String: Any] else {
            fatalError("cannot read \(url.path)")
        }
        return root
    }()

    static func rows(_ key: String) -> [[String: Any]] {
        root[key] as? [[String: Any]] ?? []
    }

    static func section(_ key: String) -> [String: Any] {
        root[key] as? [String: Any] ?? [:]
    }
}

/// A vector row as a test argument, labeled with its input.
struct Row: CustomTestStringConvertible, @unchecked Sendable {
    let fields: [String: Any]
    let index: Int

    var testDescription: String {
        let input = (fields["input"] as? String) ?? (fields["a"] as? String) ?? ""
        let shown = input.unicodeScalars.count > 40 ? String(input.prefix(40)) + "..." : input
        return "#\(index) \(shown.debugDescription)"
    }

    static func list(_ rows: [[String: Any]]) -> [Row] {
        rows.enumerated().map { Row(fields: $0.element, index: $0.offset) }
    }
}

import Testing
