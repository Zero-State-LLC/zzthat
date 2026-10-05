// The zzThis spec 004 scanner (Client read pipeline, Scanner rules 1 to 8).
// It finds every marker pair, partial code, and bare mark in running text and
// never picks one (FR-013). Rules 1, 3, 6, and the closing-only partial in 7
// follow D-2026-10-04-06; rules 4, 5, and the zz@ partial follow
// D-2026-10-04-03; the empty pair and rule 8 follow D-2026-10-04-11.

public enum ScanCandidateKind: Sendable, Hashable {
    case code(ParsedCode)
    case bare
    case partial(text: String)
    case invalid(reason: ParseFailure, text: String)
}

public struct ScanCandidate: Sendable, Hashable {
    public let kind: ScanCandidateKind
    /// Scalar offsets in the scanned text, from the first character to the
    /// last one the candidate covers.
    public let range: Range<Int>
    /// Scalar ranges of the candidate's words: every part between the markers,
    /// the check word included, and never a marker. A bare mark lists itself.
    public let wordRanges: [Range<Int>]
}

public enum Scanner {
    public static func scan(_ text: String) -> [ScanCandidate] {
        let scalars = Array(text.unicodeScalars).map(rule1)
        let tokens = tokenize(scalars)
        return candidates(tokens, scalars)
    }

    // MARK: Rule 1

    static func rule1(_ scalar: Unicode.Scalar) -> Unicode.Scalar {
        switch scalar.value {
        case 0x09...0x0D, 0x85, 0x2028, 0x2029: return " "
        default: break
        }
        return scalar.properties.generalCategory == .spaceSeparator ? " " : scalar
    }

    static func isHyphen(_ scalar: Unicode.Scalar) -> Bool {
        scalar == "-" || Scalars.isDash(scalar)
    }

    // MARK: Rules 2 and 3

    struct Token {
        /// The whole token, set-aside characters included.
        var start: Int
        var end: Int
        /// The token after rule 3 set its punctuation aside.
        var coreStart: Int
        var coreEnd: Int
        var endsClause = false
        var isMarker = false
        /// A token that starts with zz@ (rule 4). It opens a pair and never closes one.
        var isOpener = false
        var isCircled = false

        var isEmpty: Bool { coreStart == coreEnd }
        var isWord: Bool { !isEmpty && !isMarker }
        /// An opener with text after its @ (rule 4: that text is content).
        var hasContentAfterAt: Bool { isOpener && coreEnd - coreStart > 3 }
    }

    static let leading: Set<Unicode.Scalar> = ["(", "[", "\"", "'", "\u{201C}", "\u{2018}"]
    static let trailing: Set<Unicode.Scalar> = [")", "]", ".", ",", ";", "!", "?", "\"", "'", "\u{201D}", "\u{2019}"]

    static func tokenize(_ s: [Unicode.Scalar]) -> [Token] {
        var tokens: [Token] = []
        var index = 0
        while index < s.count {
            if s[index] == " " || isHyphen(s[index]) {
                index += 1
                continue
            }
            var end = index
            while end < s.count, s[end] != " ", !isHyphen(s[end]) { end += 1 }
            tokens.append(contentsOf: splitCircled(s, index, end))
            index = end
        }
        return tokens.map { setAside($0, s) }
    }

    /// Rule 2: a circled marker (zz) is its own token wherever it appears.
    static func splitCircled(_ s: [Unicode.Scalar], _ start: Int, _ end: Int) -> [Token] {
        var pieces: [Token] = []
        var pieceStart = start
        var index = start
        while index + 4 <= end {
            if s[index] == "(", lower(s[index + 1]) == "z", lower(s[index + 2]) == "z", s[index + 3] == ")" {
                if pieceStart < index {
                    pieces.append(Token(start: pieceStart, end: index, coreStart: pieceStart, coreEnd: index))
                }
                var circled = Token(start: index, end: index + 4, coreStart: index, coreEnd: index + 4)
                circled.isCircled = true
                pieces.append(circled)
                index += 4
                pieceStart = index
            } else {
                index += 1
            }
        }
        if pieceStart < end {
            pieces.append(Token(start: pieceStart, end: end, coreStart: pieceStart, coreEnd: end))
        }
        return pieces
    }

    static func setAside(_ token: Token, _ s: [Unicode.Scalar]) -> Token {
        var token = token
        if token.isCircled {
            token.isMarker = true
            return token
        }
        while token.coreStart < token.coreEnd, leading.contains(s[token.coreStart]) { token.coreStart += 1 }
        while token.coreStart < token.coreEnd {
            let last = s[token.coreEnd - 1]
            if trailing.contains(last) {
                token.coreEnd -= 1
                token.endsClause = true
            } else if last == ":", lowerText(s, token.coreStart, token.coreEnd - 1) == "zz" {
                token.coreEnd -= 1
                token.endsClause = true
            } else {
                break
            }
        }
        let core = lowerText(s, token.coreStart, token.coreEnd)
        if core == "zz" || core == "(zz)" {
            token.isMarker = true
        } else if core.hasPrefix("zz@") {
            token.isMarker = true
            token.isOpener = true
        }
        return token
    }

    static func lower(_ scalar: Unicode.Scalar) -> Unicode.Scalar {
        ("A"..."Z").contains(scalar) ? Unicode.Scalar(scalar.value + 32)! : scalar
    }

    static func lowerText(_ s: [Unicode.Scalar], _ start: Int, _ end: Int) -> String {
        guard start < end else { return "" }
        return Scalars.string(s[start..<end].map(lower))
    }

    // MARK: Rules 4 to 8

    static func candidates(_ tokens: [Token], _ s: [Unicode.Scalar]) -> [ScanCandidate] {
        func joinedByHyphen(_ left: Int, _ right: Int) -> Bool {
            guard left >= 0, right < tokens.count else { return false }
            let gap = s[tokens[left].end..<tokens[right].start]
            return !gap.isEmpty && gap.allSatisfy(isHyphen)
        }
        func joinedBackToWord(_ index: Int) -> Bool {
            index > 0 && joinedByHyphen(index - 1, index) && tokens[index - 1].isWord
        }
        func joinedForwardToWord(_ index: Int) -> Bool {
            index + 1 < tokens.count && joinedByHyphen(index, index + 1) && tokens[index + 1].isWord
        }
        /// The text of tokens first...last: their cores, with the original
        /// separators between them kept (rule 6).
        func text(_ first: Int, _ last: Int) -> String {
            var out: [Unicode.Scalar] = []
            for index in first...last {
                out.append(contentsOf: s[tokens[index].coreStart..<tokens[index].coreEnd])
                if index < last { out.append(contentsOf: s[tokens[index].end..<tokens[index + 1].start]) }
            }
            return Scalars.string(out)
        }
        func words(_ first: Int, _ last: Int) -> [Range<Int>] {
            guard first <= last else { return [] }
            var ranges: [Range<Int>] = []
            for index in first...last {
                let token = tokens[index]
                if token.hasContentAfterAt {
                    ranges.append((token.coreStart + 3)..<token.coreEnd)
                } else if token.isWord {
                    ranges.append(token.coreStart..<token.coreEnd)
                }
            }
            return ranges
        }

        let markers = tokens.indices.filter { tokens[$0].isMarker }
        var found: [ScanCandidate] = []
        var paired = Set<Int>()

        // Rule 5: two markers in a row form a pair unless an exception holds.
        for (a, b) in zip(markers, markers.dropFirst()) {
            if tokens[b].isOpener || tokens[a].endsClause { continue }
            let between = (a + 1)..<b
            if between.contains(where: { tokens[$0].endsClause }) { continue }
            let nothingBetween = between.allSatisfy { tokens[$0].isEmpty } && !tokens[a].hasContentAfterAt
            if nothingBetween, joinedBackToWord(a) || joinedForwardToWord(b) { continue }
            if joinedBackToWord(a), joinedForwardToWord(b) { continue }
            paired.insert(a)
            paired.insert(b)
            let input = text(a, b)
            let range = tokens[a].coreStart..<tokens[b].coreEnd
            let kind: ScanCandidateKind
            switch Grammar.parse(input) {
            case .success(let code) where code.kind != .bare: kind = .code(code)
            case .success: kind = .invalid(reason: .noContent, text: input)
            case .failure(let reason): kind = .invalid(reason: reason, text: input)
            }
            found.append(ScanCandidate(kind: kind, range: range, wordRanges: words(a, b)))
        }

        // Rule 7: a marker in no pair is partial or bare.
        for (position, m) in markers.enumerated() where !paired.contains(m) {
            let token = tokens[m]
            if !token.isOpener, joinedBackToWord(m) {
                // Backward reading only: back to the clause start, the
                // previous marker, or the start of the text.
                var first = m - 1
                while first > 0, !tokens[first - 1].isMarker, !tokens[first - 1].endsClause { first -= 1 }
                while first < m - 1, tokens[first].isEmpty { first += 1 }
                found.append(ScanCandidate(kind: .partial(text: text(first, m)),
                                           range: tokens[first].coreStart..<token.coreEnd,
                                           wordRanges: words(first, m - 1)))
                continue
            }
            let nextMarker = position + 1 < markers.count ? markers[position + 1] : tokens.count
            let wordFollows = token.hasContentAfterAt
                || ((m + 1)..<nextMarker).first(where: { !tokens[$0].isEmpty || tokens[$0].endsClause })
                    .map { tokens[$0].isWord } ?? false
            if !token.endsClause, wordFollows {
                // Forward reading: to the clause end, the next marker, or the end.
                var last = m
                while last + 1 < nextMarker {
                    last += 1
                    if tokens[last].endsClause { break }
                }
                while last > m, tokens[last].isEmpty { last -= 1 }
                found.append(ScanCandidate(kind: .partial(text: text(m, last)),
                                           range: token.coreStart..<tokens[last].coreEnd,
                                           wordRanges: words(m, last)))
                continue
            }
            found.append(ScanCandidate(kind: .bare, range: token.coreStart..<token.coreEnd,
                                       wordRanges: [token.coreStart..<token.coreEnd]))
        }

        // Rule 8: reading order, each canonical code once.
        found.sort { ($0.range.lowerBound, $0.range.upperBound) < ($1.range.lowerBound, $1.range.upperBound) }
        var seen = Set<String>()
        return found.filter { candidate in
            guard case .code(let code) = candidate.kind else { return true }
            return seen.insert(code.canonical).inserted
        }
    }
}
