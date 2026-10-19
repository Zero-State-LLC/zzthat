import CoreGraphics

/// One recognized line (runtime.md, Read pipeline step 2). Boxes are
/// normalized to the image, with the origin at the top left.
public struct RecognizedLine: Sendable, Hashable {
    public struct Piece: Sendable, Hashable {
        /// Scalar offsets of a run of non-space text in `text`.
        public let range: Range<Int>
        public let box: CGRect

        public init(range: Range<Int>, box: CGRect) {
            self.range = range
            self.box = box
        }
    }

    public let text: String
    /// The top candidate's confidence, from 0 to 1.
    public let confidence: Double
    public let box: CGRect
    /// Boxes for the line's words, when the reader can give them.
    public let pieces: [Piece]

    public init(text: String, confidence: Double, box: CGRect, pieces: [Piece] = []) {
        self.text = text
        self.confidence = confidence
        self.box = box
        self.pieces = pieces
    }
}

/// A candidate after the pipeline: the scanner result, its class, the local
/// check word when it ran, the band, and where it sits on the photo.
public struct ReadCandidate: Sendable, Hashable, Identifiable {
    public let id: Int
    public let kind: ScanCandidateKind
    public let codeClass: CodeClass?
    public let check: CheckResult?
    /// The band the app acts on: after confirm-only mode for a camera read.
    public let band: Band
    public let rows: [BandRow]
    public let boxes: [CGRect]
    /// The spec 004 table's band before confirm-only mode, and its rule.
    public var baseBand: Band = .abstain
    public var reason: String = ""
    /// For a camera read: the pair and the text hypothesis it came from.
    public var pairId: String?
    public var textCandidateId: String?
    /// True when either endpoint of this camera pair was a tolerant glyph.
    public var tolerantPair: Bool = false

    public var canonical: String? {
        if case .code(let code) = kind { return code.canonical }
        return nil
    }
}

public struct ReadResult: Sendable, Hashable {
    public let text: String
    public let candidates: [ReadCandidate]

    /// Client read pipeline step 6: open at once only when there is one
    /// candidate and it is in Accept. Otherwise the person picks.
    public var autoOpen: ReadCandidate? {
        candidates.count == 1 && candidates[0].band == .accept ? candidates[0] : nil
    }
}

public struct ReadPipeline: Sendable {
    /// The list to classify against (the bundled list).
    public let classifyList: WordList?
    /// The list for a local check word: only the bundled list whose version
    /// equals the server's `wordlist_version` (FR-044).
    public let checkList: WordList?

    public init(classifyList: WordList?, checkList: WordList?) {
        self.classifyList = classifyList
        self.checkList = checkList
    }

    /// The bundled lists and the server's list version pick both lists.
    /// Classification uses only the list whose version matches the server's
    /// `wordlist_version`. When none matches, or discovery has not loaded,
    /// it uses proto-v0, never the fixture-7 test list (A2, [DANNY
    /// 2026-10-05 #43]). The local check word still needs the match (FR-044).
    public init(bundled: [WordList], serverVersion: String?) {
        let check = WordListLoader.matching(bundled, serverVersion: serverVersion)
        let fallback = bundled.first { $0.version == WordListLoader.defaultClassifyVersion }
        self.init(classifyList: check ?? fallback, checkList: check)
    }

    /// Steps 3 to 5 on recognized lines.
    public func read(lines: [RecognizedLine]) -> ReadResult {
        let ordered = Self.readingOrder(lines)
        var text: [Unicode.Scalar] = []
        var lineRanges: [Range<Int>] = []
        for (index, line) in ordered.enumerated() {
            if index > 0 { text.append(" ") }
            let start = text.count
            text.append(contentsOf: line.text.unicodeScalars)
            lineRanges.append(start..<text.count)
        }
        let joined = Scalars.string(text)
        let found = Scanner.scan(joined)
        let candidates = found.enumerated().map { offset, candidate in
            let rows = candidate.wordRanges.map { range in
                let line = lineRanges.firstIndex { $0.contains(range.lowerBound) } ?? 0
                return BandRow(token: Scalars.string(text[range]), lineIndex: line,
                               lineConfidence: ordered.isEmpty ? 0 : ordered[line].confidence)
            }
            let boxes = Self.boxes(for: candidate.range, lines: ordered, lineRanges: lineRanges)
            return evaluate(candidate.kind, id: offset, rows: rows, boxes: boxes, source: .camera)
        }
        return ReadResult(text: joined, candidates: candidates)
    }

    /// Typed entry (US2a): the whole input must be one code or one bare mark
    /// (Section 2.2a G8). Returns the parser reason on failure.
    public func readTyped(_ input: String) -> Result<ReadCandidate, ParseFailure> {
        switch Grammar.parse(input) {
        case .failure(let reason): return .failure(reason)
        case .success(let code):
            let kind: ScanCandidateKind = code.kind == .bare ? .bare : .code(code)
            return .success(evaluate(kind, id: 0, rows: [], boxes: [], source: .typed))
        }
    }

    /// The classifier and the local check word for a candidate (steps 4
    /// and 5). The check runs only with the list whose version matches the
    /// server's (FR-044).
    func classifyAndCheck(_ kind: ScanCandidateKind) -> (CodeClass?, CheckResult?) {
        guard case .code(let code) = kind else { return (nil, nil) }
        let codeClass = Classifier.classify(code, wordlist: classifyList?.words ?? [])
        var check: CheckResult?
        if codeClass == .word, let checkList, code.words.allSatisfy({ checkList.index(of: $0) != nil }) {
            check = CheckWord.verify(code, list: checkList)
        }
        return (codeClass, check)
    }

    /// Bands a typed candidate, or a candidate of the whole-photo text path
    /// (`read(lines:)`), with the spec 004 table and no confirm-only mode.
    /// Scan reads the camera through `CapturePipeline` instead; the text path
    /// stays for write check, which compares a reading with the minted code
    /// and resolves nothing.
    func evaluate(_ kind: ScanCandidateKind, id: Int, rows: [BandRow], boxes: [CGRect],
                  source: ReadSource) -> ReadCandidate {
        let (codeClass, check) = classifyAndCheck(kind)
        let confidence = Bands.candidateConfidence(rows).map(ConfidenceEvidence.available) ?? .notAvailable
        let decision = Bands.decide(BandEvidence(source: source, kind: kind, codeClass: codeClass, check: check,
                                                 confidence: confidence))
        var candidate = ReadCandidate(id: id, kind: kind, codeClass: codeClass, check: check, band: decision.band,
                                      rows: rows, boxes: boxes)
        candidate.baseBand = decision.band
        candidate.reason = decision.reason
        return candidate
    }

    /// Step 3: top to bottom, then left to right. Lines whose vertical
    /// centers fall inside one another's height share a row.
    public static func readingOrder(_ lines: [RecognizedLine]) -> [RecognizedLine] {
        readingOrder(boxes: lines.map(\.box)).map { lines[$0] }
    }

    /// The same order as indices into `boxes`.
    static func readingOrder(boxes: [CGRect]) -> [Int] {
        let byTop = boxes.indices.sorted { (boxes[$0].minY, boxes[$0].minX) < (boxes[$1].minY, boxes[$1].minX) }
        var rows: [[Int]] = []
        for index in byTop {
            let box = boxes[index]
            if let last = rows.last?.first.map({ boxes[$0] }),
               abs(box.midY - last.midY) < max(min(box.height, last.height) / 2, 0.0001) {
                rows[rows.count - 1].append(index)
            } else {
                rows.append([index])
            }
        }
        return rows.flatMap { $0.sorted { boxes[$0].minX < boxes[$1].minX } }
    }

    static func boxes(for range: Range<Int>, lines: [RecognizedLine], lineRanges: [Range<Int>]) -> [CGRect] {
        var boxes: [CGRect] = []
        for (index, lineRange) in lineRanges.enumerated() {
            // A candidate on another line has no overlap with this one.
            let lower = max(range.lowerBound, lineRange.lowerBound)
            let upper = min(range.upperBound, lineRange.upperBound)
            guard lower < upper else { continue }
            let local = (lower - lineRange.lowerBound)..<(upper - lineRange.lowerBound)
            let line = lines[index]
            let pieces = line.pieces.filter { $0.range.overlaps(local) }
            if pieces.isEmpty {
                boxes.append(line.box)
            } else {
                boxes.append(pieces.dropFirst().reduce(pieces[0].box) { $0.union($1.box) })
            }
        }
        return boxes
    }
}
