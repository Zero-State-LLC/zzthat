import Foundation
import Observation
import UIKit
import ZZAPI
import ZZCore

/// A 429 on mint or re-roll (FR-033, T014; design/UX.md, Create: the daily
/// limit). The client waits for `Retry-After` and keeps Get a code and Get
/// different words off until then.
enum CreateWait {
    /// A `Retry-After` longer than this, in seconds, is the RM-072 daily cap,
    /// which can run to the next UTC midnight, and shows `create.daily_limit`.
    /// A shorter one is a burst limit and keeps `resolve.rate_limited`.
    /// UX.md names the daily cap by its long wait, and no burst window in
    /// zzThis spec 005 runs as long as an hour.
    static let dailyLimitSeconds = 3600

    static func message(retryAfter: Int) -> CopyKey {
        retryAfter > dailyLimitSeconds ? .createDailyLimit : .resolveRateLimited
    }

    static func until(retryAfter: Int, now: Date = Date()) -> Date {
        now.addingTimeInterval(TimeInterval(max(retryAfter, 1)))
    }
}

/// Create (US1): mint a wordlist code. The server chooses the words and the
/// check word (ZQ11), so there is no field for code words (FR-019, T034).
@MainActor
@Observable
final class CreateModel {
    enum Phase: Hashable {
        case editing
        case minting
        case failure(CopyKey)
    }

    var title = ""
    var body = ""
    var phase: Phase = .editing
    /// A mint answered `scope-unavailable` or `not-ready` (FR-018).
    private(set) var mintUnavailable = false
    /// The Terms step (design/UX.md, Terms; D-2026-10-13-04): the person
    /// confirms they are 18 or older and accepts the Terms. Both are needed
    /// before the first create on this device, and again when `terms.version`
    /// changes. Client-only: nothing about it is sent.
    var ageConfirmed = false
    var termsAccepted = false
    /// After a 429, Get a code stays off until this time (FR-033).
    private(set) var retryAt: Date?
    @ObservationIgnored private var draftLoaded = false

    var waiting: Bool { retryAt != nil }

    /// The `Retry-After` wait is over: Get a code turns back on.
    func endWait() {
        retryAt = nil
    }

    /// Whether Get a code may run: the Terms step is done, or not needed.
    func termsDone(app: AppModel) -> Bool {
        !app.needsTerms || (ageConfirmed && termsAccepted)
    }

    /// The local draft, labeled "Not a code yet." (FR-024). It holds no code words.
    func loadDraft(app: AppModel) {
        guard !draftLoaded else { return }
        draftLoaded = true
        if let draft = app.drafts.draft {
            title = draft.title
            body = draft.body
        }
    }

    func saveDraft(app: AppModel) {
        app.drafts.save(title: title, body: body)
    }

    var hasDraft: Bool { !FieldLimits.isBlank(title) || !FieldLimits.isBlank(body) }

    /// `POST /v1/codes`. Refused offline, never queued as a success (FR-022).
    func mint(app: AppModel) async -> MintedCode? {
        if let retryAt {
            guard Date() >= retryAt else { return nil }
            endWait()
        }
        guard !FieldLimits.isBlank(title) else {
            phase = .failure(.createTitleRequired)
            return nil
        }
        // Both confirmations come before Create continues (design/UX.md, Terms).
        guard termsDone(app: app) else { return nil }
        if app.needsTerms {
            app.acceptTerms()
            // Consumed: if the step shows again (a new version, or after
            // account deletion), it starts unchecked.
            ageConfirmed = false
            termsAccepted = false
        }
        guard app.isOnline else {
            saveDraft(app: app)
            phase = .failure(.createOffline)
            return nil
        }
        phase = .minting
        let trimmedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedBody = body.trimmingCharacters(in: .whitespacesAndNewlines)
        switch await app.service.mint(title: trimmedTitle, body: trimmedBody) {
        case .success(let code):
            app.ownerMinted()
            app.drafts.discard()
            title = ""
            body = ""
            phase = .editing
            return code
        case .failure(.noConnection):
            saveDraft(app: app)
            phase = .failure(.createOffline)
        case .failure(.unauthorized):
            phase = .failure(.commonSignInNeeded)
            app.needsSignIn()
        case .failure(.rateLimited(let retryAfter)):
            // The title and body stay as typed; Get a code waits (FR-033).
            retryAt = CreateWait.until(retryAfter: retryAfter)
            phase = .failure(CreateWait.message(retryAfter: retryAfter))
        case .failure(let failure):
            let key = CopyKey(failure.message(for: .mint))
            if key == .createUnavailable { mintUnavailable = true }
            // The title and body stay as typed (DEP-005).
            phase = .failure(key)
        }
        return nil
    }
}

/// Minted code (design/UX.md, Minted code): the canonical form, the check
/// word, and the re-rolls `rerolls_remaining` allows, 5 for a new code (FR-017, RM-125).
@MainActor
@Observable
final class MintedModel {
    enum Phase: Hashable {
        case idle
        case rerolling
        case failure(CopyKey)
    }

    private(set) var code: MintedCode
    private(set) var capReached: Bool
    private(set) var phase: Phase = .idle
    /// After a 429, Get different words stays off until this time (FR-033).
    private(set) var retryAt: Date?

    var waiting: Bool { retryAt != nil }

    /// The `Retry-After` wait is over: Get different words turns back on.
    func endWait() {
        retryAt = nil
    }

    init(code: MintedCode) {
        self.code = code
        capReached = code.rerollsRemaining <= 0
    }

    /// `POST /v1/codes/{id}/reroll`. Each tap replaces the current code; at
    /// the cap, or once the code was resolved, the words stay.
    func reroll(app: AppModel) async {
        if let retryAt {
            guard Date() >= retryAt else { return }
            endWait()
        }
        guard app.isOnline else {
            phase = .failure(.errorNoConnection)
            return
        }
        phase = .rerolling
        let previous = code
        switch await app.service.reroll(codeID: code.id) {
        case .success(let next):
            app.ownerChanged(canonical: previous.canonical)
            code = next
            capReached = next.rerollsRemaining <= 0
            phase = .idle
        case .failure(.noConnection):
            phase = .failure(.errorNoConnection)
        case .failure(.unauthorized):
            phase = .failure(.commonSignInNeeded)
            app.needsSignIn()
        case .failure(.rateLimited(let retryAfter)):
            // The words stay; Get different words waits (FR-033).
            retryAt = CreateWait.until(retryAfter: retryAfter)
            phase = .failure(CreateWait.message(retryAfter: retryAfter))
        case .failure(let failure):
            let key = CopyKey(failure.message(for: .reroll))
            if key == .mintedRerollCap { capReached = true }
            phase = .failure(key)
        }
    }
}

/// Write check (FR-037): the person's photo goes through the same
/// RecognitionResult boundary as Scan (recognition-adapter.md, T023): marks
/// first, a pick before any payload is read, and payload OCR only in the
/// picked pair's ROI. The reading is compared with the minted code on the
/// device. Nothing is uploaded.
@MainActor
@Observable
final class WriteCheckModel {
    enum Verdict: Hashable {
        case match
        case mismatch
    }

    let canonical: String
    private let scan: ScanModel
    private(set) var verdict: Verdict?
    private(set) var message: CopyKey?

    var photo: UIImage? { scan.photo }
    /// Every box the person can pick before any payload is read (FR-052).
    var visual: [VisualCandidate] { scan.visual }
    /// The read pair's readings, in the engine's order.
    var candidates: [ReadCandidate] { scan.candidates }
    var reading: Bool { scan.reading }

    init(canonical: String, adapter: any RecognitionAdapter = VisionRecognitionAdapter(),
         configuration: CaptureConfiguration = .current) {
        self.canonical = canonical
        scan = ScanModel(adapter: adapter, configuration: configuration)
    }

    func read(_ image: UIImage, app: AppModel) async {
        verdict = nil
        message = nil
        _ = await scan.read(image, app: app)
        settle()
    }

    /// The person picked a box (FR-026, FR-052): only a complete pair is
    /// read. A partial code or a bare mark is not the minted code.
    func pick(_ box: VisualCandidate, app: AppModel) async {
        guard verdict == nil else { return }
        _ = await scan.select(box, app: app)
        if case .complete = box.kind {
            settle(picked: true)
        } else {
            verdict = .mismatch
            message = nil
        }
    }

    /// One reading decides; several wait for a tap; none is a message,
    /// unless the boxes still wait for a pick.
    private func settle(picked: Bool = false) {
        guard !scan.reading else { return }
        switch scan.candidates.count {
        case 0: if picked || scan.visual.isEmpty || scan.message != nil { message = scan.message ?? .scanNoCode }
        case 1: pick(scan.candidates[0])
        default: break
        }
    }

    func cameraIsOff() {
        message = .scanCameraOff
    }

    /// The person picked the candidate their photo shows (spec 004 FR-017).
    func pick(_ candidate: ReadCandidate) {
        verdict = candidate.canonical == canonical ? .match : .mismatch
        message = nil
    }

    /// The check ends: its photo is deleted (FR-004) and the evidence dropped.
    func end(app: AppModel) {
        scan.end(app: app)
        verdict = nil
        message = nil
    }

    /// Whether any evidence of the attempt is still held.
    var holdsEvidence: Bool { scan.holdsEvidence }
}
