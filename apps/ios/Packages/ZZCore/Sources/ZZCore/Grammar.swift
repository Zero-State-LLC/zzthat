// The v1 text grammar, zzThis docs/SPEC.md Section 2.2a, at the pin.
//
// This is a line-by-line port of zzThis src/lib/grammar.ts at the pin, which
// is the parser the server runs and the one contracts/vectors.json was checked
// against. It keeps that file's character sets on purpose (JavaScript trim and
// \s, and full Unicode lowercasing), so the app and the server agree on every
// input (FR-039). The two grammar_pending rows wait for zzThis spec 005 T035,
// which changes the TypeScript parser first.

public enum CodeKind: String, Sendable, Codable, Hashable {
    case plain
    case handle
    case name
    case bare
}

public enum CodeVariant: String, Sendable, Hashable {
    case dash
    case circled
}

/// The parser reasons, in the fixed order of Section 2.2a G6.
public enum ParseFailure: String, Error, Sendable, CaseIterable, Hashable {
    case empty
    case tooLong = "too-long"
    case noMarker = "no-marker"
    case noClosingMarker = "no-closing-marker"
    case noContent = "no-content"
    case markerInBody = "marker-in-body"
    case unsupportedScript = "unsupported-script"
    case reservedSymbol = "reserved-symbol"
    case misplacedAt = "misplaced-at"
    case invalidHandle = "invalid-handle"
    case invalidCharacter = "invalid-character"
}

public struct ParsedCode: Sendable, Hashable {
    public let kind: CodeKind
    /// The parts between the markers. Empty for a bare mark.
    public let words: [String]
    public let variant: CodeVariant
    public let canonical: String
    public let tag: String?
    public let handle: String?
    public let qualifiers: [String]
}

public enum Grammar {
    /// G2 step 1: the input guard, in UTF-16 code units.
    public static let maxInputUTF16 = 256

    public static func parse(_ input: String) -> Result<ParsedCode, ParseFailure> {
        let raw = Array(input.unicodeScalars)
        if Scalars.trimmed(raw).isEmpty { return .failure(.empty) }
        if input.utf16.count > maxInputUTF16 { return .failure(.tooLong) }
        let text = normalize(raw)
        if text == Scalars.of("zz") {
            return .success(ParsedCode(kind: .bare, words: [], variant: .dash, canonical: "zz",
                                       tag: nil, handle: nil, qualifiers: []))
        }
        if text == Scalars.of("(zz)") {
            return .success(ParsedCode(kind: .bare, words: [], variant: .circled, canonical: "zz",
                                       tag: nil, handle: nil, qualifiers: []))
        }
        guard let opened = opening(text) else { return .failure(.noMarker) }
        let body: [Unicode.Scalar]
        switch closing(opened.rest) {
        case .failure(let reason): return .failure(reason)
        case .success(let value): body = value
        }
        let parts = Scalars.split(body, where: { $0 == "-" || Scalars.isJSWhitespace($0) })
            .map(Scalars.string)
        if let fault = earlyFault(parts) { return .failure(fault) }
        return classify(parts, variant: opened.variant)
    }

    /// The canonical form of a list of parts.
    public static func format(_ words: [String]) -> String {
        "zz-" + words.joined(separator: "-") + "-zz"
    }

    // MARK: Normalization (grammar.ts normalize)

    static func normalize(_ input: [Unicode.Scalar]) -> [Unicode.Scalar] {
        let spaced = Scalars.trimmed(input.map { $0 == "\t" || $0 == "\r" || $0 == "\n" ? " " : $0 })
        let hyphens = spaced.map { Scalars.isDash($0) ? "-" : $0 }
        var text = Array(Scalars.string(hyphens).lowercased().unicodeScalars)
        // replace(/^zz@-?/, "zz-@")
        if Scalars.hasPrefix(text, "zz@") {
            var rest = Array(text.dropFirst(3))
            if rest.first == "-" { rest.removeFirst() }
            text = Scalars.of("zz-@") + rest
        }
        // replace(/^zz-([a-z0-9]+)@[\s-]*/, "zz-$1-@")
        if Scalars.hasPrefix(text, "zz-") {
            var index = 3
            while index < text.count, Scalars.isPlainScalar(text[index]) { index += 1 }
            if index > 3, index < text.count, text[index] == "@" {
                let group = Array(text[3..<index])
                var after = index + 1
                while after < text.count, text[after] == "-" || Scalars.isJSWhitespace(text[after]) {
                    after += 1
                }
                text = Scalars.of("zz-") + group + Scalars.of("-@") + Array(text[after...])
            }
        }
        return text
    }

    private struct Opened {
        let variant: CodeVariant
        let rest: [Unicode.Scalar]
    }

    private static func opening(_ text: [Unicode.Scalar]) -> Opened? {
        if Scalars.hasPrefix(text, "(zz)") {
            return Opened(variant: .circled, rest: Array(text.dropFirst(4)))
        }
        if text.count >= 3, text[0] == "z", text[1] == "z",
           text[2] == "-" || Scalars.isJSWhitespace(text[2]) {
            var rest = Array(text.dropFirst(2))
            while let first = rest.first, first == "-" || Scalars.isJSWhitespace(first) {
                rest.removeFirst()
            }
            return Opened(variant: .dash, rest: rest)
        }
        return nil
    }

    private static func closing(_ rest: [Unicode.Scalar]) -> Result<[Unicode.Scalar], ParseFailure> {
        if rest == Scalars.of("zz") || rest == Scalars.of("(zz)") { return .failure(.noContent) }
        if Scalars.hasSuffix(rest, "(zz)") { return .success(Array(rest.dropLast(4))) }
        let count = rest.count
        if count >= 3, rest[count - 1] == "z", rest[count - 2] == "z",
           rest[count - 3] == "-" || Scalars.isJSWhitespace(rest[count - 3]) {
            var body = Array(rest.dropLast(2))
            while let last = body.last, last == "-" || Scalars.isJSWhitespace(last) {
                body.removeLast()
            }
            return .success(body)
        }
        return .failure(.noClosingMarker)
    }

    // MARK: Parts (grammar.ts earlyFault, atProblem, classify)

    private enum At {
        case none
        case misplaced
        case found(index: Int, part: String)
    }

    private static func soleAt(_ parts: [String]) -> At {
        var found: At = .none
        for (index, part) in parts.enumerated() where part.unicodeScalars.contains("@") {
            if case .found = found { return .misplaced }
            found = .found(index: index, part: part)
        }
        return found
    }

    private static func oneLeadingAt(_ part: String) -> Bool {
        let scalars = Array(part.unicodeScalars)
        return scalars.first == "@" && !scalars.dropFirst().contains("@")
    }

    private static func tagPosition(_ parts: [String], _ index: Int) -> Bool {
        if index == 0 { return true }
        if index != 1 { return false }
        return isPlain(parts[0])
    }

    private static func validHandle(_ part: String) -> Bool {
        let body = Array(part.unicodeScalars.dropFirst())
        guard (1...32).contains(body.count),
              body.allSatisfy({ Scalars.isPlainScalar($0) || $0 == "." || $0 == "_" }) else { return false }
        guard body.contains(where: Scalars.isPlainScalar) else { return false }
        if body.first == "." || body.last == "." { return false }
        for index in body.indices.dropFirst() where body[index] == "." && body[index - 1] == "." {
            return false
        }
        return true
    }

    private static func atProblem(_ parts: [String]) -> ParseFailure? {
        switch soleAt(parts) {
        case .none: return nil
        case .misplaced: return .misplacedAt
        case .found(let index, let part):
            if !oneLeadingAt(part) { return .misplacedAt }
            if !tagPosition(parts, index) { return .misplacedAt }
            if !validHandle(part) { return .invalidHandle }
            return nil
        }
    }

    private static func earlyFault(_ parts: [String]) -> ParseFailure? {
        if parts.contains("zz") { return .markerInBody }
        if parts.contains(where: hasNonASCIILetter) { return .unsupportedScript }
        if parts.contains(where: { $0.unicodeScalars.contains(where: Scalars.isReserved) }) {
            return .reservedSymbol
        }
        return atProblem(parts)
    }

    private static func qualifiersPlain(_ parts: [String], skip: Int) -> Bool {
        parts.enumerated().allSatisfy { index, part in index == skip || isPlain(part) }
    }

    private static func classify(_ parts: [String], variant: CodeVariant) -> Result<ParsedCode, ParseFailure> {
        guard case .found(let index, let part) = soleAt(parts) else {
            return classifyPlain(parts, variant: variant)
        }
        if !qualifiersPlain(parts, skip: index) { return .failure(.invalidCharacter) }
        return .success(ParsedCode(kind: .handle, words: parts, variant: variant, canonical: format(parts),
                                   tag: index == 1 ? parts[0] : nil, handle: part,
                                   qualifiers: Array(parts[(index + 1)...])))
    }

    private static func classifyPlain(_ parts: [String], variant: CodeVariant) -> Result<ParsedCode, ParseFailure> {
        guard let first = parts.first else { return .failure(.noContent) }
        if isName(first) {
            if !qualifiersPlain(parts, skip: 0) { return .failure(.invalidCharacter) }
            return .success(ParsedCode(kind: .name, words: parts, variant: variant, canonical: format(parts),
                                       tag: nil, handle: nil, qualifiers: Array(parts.dropFirst())))
        }
        if !parts.allSatisfy(isPlain) { return .failure(.invalidCharacter) }
        return .success(ParsedCode(kind: .plain, words: parts, variant: variant, canonical: format(parts),
                                   tag: nil, handle: nil, qualifiers: []))
    }

    // MARK: Part shapes

    /// PLAIN: one or more of a to z and 0 to 9.
    static func isPlain(_ part: String) -> Bool {
        !part.isEmpty && part.unicodeScalars.allSatisfy(Scalars.isPlainScalar)
    }

    /// NAME: labels of a to z and 0 to 9 joined by single dots, ending in .eth.
    static func isName(_ part: String) -> Bool {
        let labels = part.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.count >= 2, labels.last == "eth" else { return false }
        return labels.allSatisfy { label in
            !label.isEmpty && label.unicodeScalars.allSatisfy(Scalars.isPlainScalar)
        }
    }

    /// NON_ASCII_LETTER: a Unicode letter that is not a to z.
    private static func hasNonASCIILetter(_ part: String) -> Bool {
        part.unicodeScalars.contains { scalar in
            if ("a"..."z").contains(scalar) { return false }
            switch scalar.properties.generalCategory {
            case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter:
                return true
            default:
                return false
            }
        }
    }
}

/// Scalar helpers that match the JavaScript character classes grammar.ts uses.
enum Scalars {
    static func of(_ text: String) -> [Unicode.Scalar] { Array(text.unicodeScalars) }

    static func string(_ scalars: [Unicode.Scalar]) -> String {
        var view = String.UnicodeScalarView()
        view.append(contentsOf: scalars)
        return String(view)
    }

    static func string(_ scalars: ArraySlice<Unicode.Scalar>) -> String { string(Array(scalars)) }

    /// JavaScript \s and String.prototype.trim: WhiteSpace and LineTerminator.
    static func isJSWhitespace(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.value {
        case 0x09...0x0D, 0x20, 0xA0, 0x1680, 0x2000...0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000, 0xFEFF:
            return true
        default:
            return false
        }
    }

    /// G2 step 3: U+2010 to U+2015 and U+2212 read as a hyphen.
    static func isDash(_ scalar: Unicode.Scalar) -> Bool {
        (0x2010...0x2015).contains(scalar.value) || scalar.value == 0x2212
    }

    static func isPlainScalar(_ scalar: Unicode.Scalar) -> Bool {
        ("a"..."z").contains(scalar) || ("0"..."9").contains(scalar)
    }

    static func isReserved(_ scalar: Unicode.Scalar) -> Bool {
        scalar == "#" || scalar == "$" || scalar == "/" || scalar == ":"
    }

    static func trimmed(_ scalars: [Unicode.Scalar]) -> [Unicode.Scalar] {
        guard let start = scalars.firstIndex(where: { !isJSWhitespace($0) }),
              let end = scalars.lastIndex(where: { !isJSWhitespace($0) }) else { return [] }
        return Array(scalars[start...end])
    }

    static func hasPrefix(_ scalars: [Unicode.Scalar], _ prefix: String) -> Bool {
        let p = of(prefix)
        return scalars.count >= p.count && Array(scalars[0..<p.count]) == p
    }

    static func hasSuffix(_ scalars: [Unicode.Scalar], _ suffix: String) -> Bool {
        let s = of(suffix)
        return scalars.count >= s.count && Array(scalars[(scalars.count - s.count)...]) == s
    }

    /// Split on runs of separators and drop empty pieces, like
    /// `text.split(/[-\s]+/).filter((part) => part !== "")`.
    static func split(_ scalars: [Unicode.Scalar], where isSeparator: (Unicode.Scalar) -> Bool) -> [[Unicode.Scalar]] {
        var pieces: [[Unicode.Scalar]] = []
        var current: [Unicode.Scalar] = []
        for scalar in scalars {
            if isSeparator(scalar) {
                if !current.isEmpty { pieces.append(current) }
                current = []
            } else {
                current.append(scalar)
            }
        }
        if !current.isEmpty { pieces.append(current) }
        return pieces
    }
}
