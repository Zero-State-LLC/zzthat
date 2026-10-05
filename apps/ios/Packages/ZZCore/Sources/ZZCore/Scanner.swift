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

    /// Rules 4 to 8, in the order of zzThis packages/zz-core/src/scanner.ts
    /// at the pin, so every platform finds the same candidates. Positions are
    /// kept alongside, for the boxes and the band rows.
    static func candidates(_ tokens: [Token], _ s: [Unicode.Scalar]) -> [ScanCandidate] {
        /// The separators written before a token. Pieces of one token split
        /// around a circled marker have none between them.
        func before(_ index: Int) -> ArraySlice<Unicode.Scalar> {
            index > 0 ? s[tokens[index - 1].end..<tokens[index].start] : []
        }
        func hyphenRun(_ index: Int) -> Bool {
            let gap = before(index)
            return !gap.isEmpty && gap.allSatisfy(isHyphen)
        }
        /// A marker joined by a hyphen to the word just before it.
        func joinedToWordBefore(_ index: Int) -> Bool {
            index > 0 && index < tokens.count && hyphenRun(index) && tokens[index - 1].isWord
        }
        /// A marker joined by a hyphen to the word just after it.
        func joinedToWordAfter(_ index: Int) -> Bool {
            index + 1 < tokens.count && hyphenRun(index + 1) && tokens[index + 1].isWord
        }
        /// Rule 5: two markers in a row form a pair unless an exception holds.
        func formsPair(_ a: Int, _ b: Int) -> Bool {
            if tokens[b].isOpener || tokens[a].endsClause { return false }
            let between = (a + 1)..<b
            if between.contains(where: { tokens[$0].endsClause }) { return false }
            let outerBefore = joinedToWordBefore(a)
            let outerAfter = joinedToWordAfter(b)
            if outerBefore, outerAfter { return false }
            let empty = between.isEmpty && !tokens[a].hasContentAfterAt
            return !(empty && (outerBefore || outerAfter))
        }
        /// Rule 6: the grammar input drops the set-aside characters and keeps
        /// the separators as written. `to` is exclusive.
        func spanText(_ from: Int, _ to: Int) -> String {
            var out: [Unicode.Scalar] = []
            for index in from..<to {
                if index > from { out.append(contentsOf: before(index)) }
                out.append(contentsOf: s[tokens[index].coreStart..<tokens[index].coreEnd])
            }
            return Scalars.string(out)
        }
        func span(_ from: Int, _ to: Int) -> Range<Int> {
            tokens[from].coreStart..<max(tokens[from].coreStart, tokens[to - 1].coreEnd)
        }
        /// Every part between the markers, the check word included; an
        /// opener's text after its @ counts.
        func words(_ from: Int, _ to: Int) -> [Range<Int>] {
            (from..<to).compactMap { index in
                let token = tokens[index]
                if token.hasContentAfterAt { return (token.coreStart + 3)..<token.coreEnd }
                return token.isWord ? token.coreStart..<token.coreEnd : nil
            }
        }
        /// Rule 7, forward: to the clause end, the next marker, or the end.
        func forwardEnd(_ start: Int) -> Int {
            for index in start..<tokens.count {
                if index > start, tokens[index].isMarker { return index }
                if tokens[index].endsClause { return index + 1 }
            }
            return tokens.count
        }
        /// Rule 7, backward: to the clause start, the previous marker, or the start.
        func backwardStart(_ end: Int) -> Int {
            var start = end
            while start > 0, !tokens[start - 1].isMarker, !tokens[start - 1].endsClause { start -= 1 }
            return start
        }

        struct Found {
            let start: Int
            let candidate: ScanCandidate
        }
        func partial(_ from: Int, _ to: Int) -> Found {
            Found(start: from, candidate: ScanCandidate(kind: .partial(text: spanText(from, to)),
                                                        range: span(from, to), wordRanges: words(from, to)))
        }

        let markers = tokens.indices.filter { tokens[$0].isMarker }
        var found: [Found] = []
        var paired = Set<Int>()
        for (a, b) in zip(markers, markers.dropFirst()) where formsPair(a, b) {
            let input = spanText(a, b + 1)
            let kind: ScanCandidateKind
            switch Grammar.parse(input) {
            case .success(let code) where code.kind != .bare: kind = .code(code)
            case .success: kind = .invalid(reason: .noContent, text: input)
            case .failure(let reason): kind = .invalid(reason: reason, text: input)
            }
            found.append(Found(start: a, candidate: ScanCandidate(kind: kind, range: span(a, b + 1),
                                                                  wordRanges: words(a, b + 1))))
            paired.insert(a)
            paired.insert(b)
        }
        for m in markers where !paired.contains(m) {
            let token = tokens[m]
            // A zz or (zz) joined by a hyphen to a word before it reads back only.
            if !token.isOpener, joinedToWordBefore(m) {
                found.append(partial(backwardStart(m), m + 1))
                continue
            }
            // A zz@ token holds its own word, which comes before any clause end.
            let wordFollows = !token.endsClause && m + 1 < tokens.count && tokens[m + 1].isWord
            if token.hasContentAfterAt || wordFollows {
                found.append(partial(m, forwardEnd(m)))
                continue
            }
            found.append(Found(start: m, candidate: ScanCandidate(kind: .bare, range: token.coreStart..<token.coreEnd,
                                                                  wordRanges: [token.coreStart..<token.coreEnd])))
        }

        // Rule 8: reading order, each canonical code once. Bare marks,
        // partials, and invalid candidates each keep their own entry.
        var seen = Set<String>()
        return found.sorted { $0.start < $1.start }.compactMap { item in
            if case .code(let code) = item.candidate.kind, !seen.insert(code.canonical).inserted { return nil }
            return item.candidate
        }
    }
}
