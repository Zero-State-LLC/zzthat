import Foundation
import Observation
import UIKit
import ZZCore

/// What tapping a candidate does (zzThis spec 004, Client read pipeline
/// step 6, and the bands). The app never opens a candidate the person did
/// not pick, except one candidate in Accept.
enum CandidateAction: Hashable {
    case resolve(String)
    case clarify(ClarifyInput)
    case message(CopyKey)

    static func of(_ candidate: ReadCandidate, source: ReadSource) -> CandidateAction {
        switch candidate.band {
        case .accept:
            guard let canonical = candidate.canonical else { return .message(.typeMalformed) }
            return .resolve(canonical)
        case .clarify:
            guard case .code(let code) = candidate.kind else { return .message(.typeMalformed) }
            return .clarify(ClarifyInput(parts: code.words, kind: code.kind, source: source))
        case .retry:
            // A partial code, or a word below 0.50 (spec 004 Prototype band values).
            if case .partial = candidate.kind { return .message(.scanPartial) }
            return .message(.scanRetry)
        case .abstain:
            switch candidate.kind {
            case .bare: return .message(.scanBare)
            case .code where candidate.check == .wrongLength: return .message(.scanWrongLength)
            default: return .message(.typeMalformed)
            }
        }
    }
}

/// The code a Clarify step starts from.
struct ClarifyInput: Hashable {
    let parts: [String]
    let kind: CodeKind
    let source: ReadSource
}

/// One scan: the photo, the on-device read, and the candidates (runtime.md,
/// Read pipeline). The photo stays on the device, in the caches folder only
/// while the scan runs (FR-004).
@MainActor
@Observable
final class ScanModel {
    private(set) var photo: UIImage?
    private(set) var candidates: [ReadCandidate] = []
    private(set) var reading = false
    /// The one sentence under the photo: an empty read, a band reason, the
    /// camera being off, a 429, or the offline wait.
    var message: CopyKey?
    private(set) var hasRead = false
    private let recognize: @Sendable (Data) async throws -> [RecognizedLine]
    private var recognitionTask: Task<[RecognizedLine], Error>?
    private var readGeneration = 0

    init(recognize: @escaping @Sendable (Data) async throws -> [RecognizedLine] = { data in
        try await Task.detached(priority: .userInitiated) {
            try VisionReader.recognize(imageData: data)
        }.value
    }) {
        self.recognize = recognize
    }

    var isEmpty: Bool { photo == nil && !reading }

    /// Reads one still photo. Returns the canonical code to open at once
    /// when there is one candidate and it is in Accept.
    func read(_ image: UIImage, app: AppModel) async -> String? {
        guard let data = image.jpegData(compressionQuality: 0.9) else {
            message = .scanNoCode
            return nil
        }
        end(app: app)
        let generation = readGeneration
        _ = try? app.photos.begin(data)
        photo = image
        reading = true
        message = nil
        candidates = []
        let task = Task.detached(priority: .userInitiated) { try await recognize(data) }
        recognitionTask = task
        defer {
            if generation == readGeneration {
                reading = false
                recognitionTask = nil
            }
        }
        let lines = (try? await task.value) ?? []
        guard generation == readGeneration, !Task.isCancelled else {
            if generation == readGeneration { end(app: app) }
            return nil
        }
        let result = app.pipeline.read(lines: lines)
        hasRead = true
        candidates = result.candidates
        if result.candidates.isEmpty { message = .scanNoCode }
        return result.autoOpen?.canonical
    }

    /// Demo builds read the fixture photo on the device instead of the camera.
    func readDemoPhoto(app: AppModel) async -> String? {
        await read(DemoPhoto.make(lines: [DemoPhoto.canonical]).image, app: app)
    }

    func cameraIsOff() {
        message = .scanCameraOff
    }

    /// The scan ends: the photo is deleted (FR-004, T063).
    func end(app: AppModel) {
        readGeneration += 1
        recognitionTask?.cancel()
        recognitionTask = nil
        app.photos.end()
        photo = nil
        candidates = []
        message = nil
        hasRead = false
        reading = false
    }
}

/// Typed entry (US2a): the same grammar and the same resolve call. The whole
/// input must be one code or one bare mark (Section 2.2a G8).
@MainActor
@Observable
final class TypeModel {
    var text = ""
    var message: CopyKey?

    func action(app: AppModel) -> CandidateAction {
        switch app.pipeline.readTyped(text) {
        case .failure(.empty): return .message(.typeEmpty)
        case .failure: return .message(.typeMalformed)
        case .success(let candidate): return .of(candidate, source: .typed)
        }
    }
}

/// Clarify (design/UX.md, Scan with the camera, step 4): one uncertain word
/// at a time with its list candidates and "as written", a check-word
/// mismatch with the words as read, or a field code, handle, or name that a
/// person confirms before it opens (spec 004 FR-010).
enum ClarifyStep: Hashable {
    /// `position` is 1-based, as the classifier reports it.
    case nearWord(position: Int, written: String, candidates: [String])
    case checkMismatch
    case wrongLength
    case confirm
    case done(String)
}

enum ClarifyLogic {
    /// The next step for these parts. Parts the person kept as written are
    /// never offered again, and a picked candidate is classified again. A
    /// camera reading opens only after the person has looked at it once.
    static func next(parts: [String], keptAsWritten: Set<Int>, confirmed: Bool,
                     pipeline: ReadPipeline) -> ClarifyStep {
        guard case .success(let code) = Grammar.parse(Grammar.format(parts)) else { return .checkMismatch }
        let codeClass = Classifier.classify(code, wordlist: pipeline.classifyList?.words ?? [])
        if let position = codeClass.nearWords.keys.sorted().first(where: { !keptAsWritten.contains($0) }),
           let candidates = codeClass.nearWords[position] {
            return .nearWord(position: position, written: parts[position - 1], candidates: candidates)
        }
        if codeClass == .word, let list = pipeline.checkList,
           code.words.allSatisfy({ list.index(of: $0) != nil }) {
            switch CheckWord.verify(code, list: list) {
            case .checkMismatch: return .checkMismatch
            case .wrongLength: return .wrongLength
            case .ok, .unknownWord: break
            }
        }
        if !confirmed { return .confirm }
        return .done(code.canonical)
    }
}

@MainActor
@Observable
final class ClarifyModel {
    let input: ClarifyInput
    private(set) var parts: [String]
    private(set) var keptAsWritten: Set<Int> = []
    private(set) var confirmed = false

    init(input: ClarifyInput) {
        self.input = input
        parts = input.parts
        // Typed field codes, handles, and names open as typed (spec 004
        // bands); only the camera asks a person to confirm them.
        confirmed = input.source == .typed
    }

    func step(app: AppModel) -> ClarifyStep {
        ClarifyLogic.next(parts: parts, keptAsWritten: keptAsWritten, confirmed: confirmed, pipeline: app.pipeline)
    }

    var canonical: String { Grammar.format(parts) }

    /// Picking a candidate snaps that part (Section 2.2a G1). The person has
    /// now looked at the reading.
    func pick(_ word: String, at position: Int) {
        parts[position - 1] = word
        confirmed = true
    }

    func keepAsWritten(_ position: Int) {
        keptAsWritten.insert(position)
        confirmed = true
    }

    /// The person read the code back and keeps it as written.
    func confirm() {
        confirmed = true
    }
}
