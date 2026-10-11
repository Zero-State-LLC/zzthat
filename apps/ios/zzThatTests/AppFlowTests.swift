import Foundation
import HTTPTypes
import Testing
import ZZAPI
import ZZCore
@testable import zzThat

/// Resolve through the FR-041 cache, with the generated client and a fake clock.
@MainActor
@Suite("Resolve and the cache")
struct ResolveFlowTests {
    nonisolated static let code = "zz-kathy-lost-cat-zz"

    static func transport(status: Int = 200, cacheControl: String = Fixtures.publicCacheControl,
                          retryAfter: String? = nil) -> AppStubTransport {
        AppStubTransport { operation, _ in
            guard operation == "resolveCode" else { return .init(status: 500, json: #"{"error":"failed"}"#) }
            switch status {
            case 200: return .init(status: 200, json: Fixtures.resolveJSON(code), headers: ["Cache-Control": cacheControl])
            case 404: return .init(status: 404, json: Fixtures.notFound)
            case 429: return .init(status: 429, json: #"{"error":"rate-limited"}"#, headers: ["Retry-After": retryAfter ?? "1"])
            default: return .init(status: status, json: #"{"error":"failed"}"#)
            }
        }
    }

    @Test func aPublicResolveIsKeptFor60SecondsOnline() async throws {
        let clock = FakeClock()
        let transport = Self.transport()
        let app = TestApp.make(service: try await TestApp.liveService(transport), clock: clock)
        let first = await ResolveLoader.load(Self.code, app: app)
        guard case .record(let view, offline: false) = first else {
            Issue.record("expected the record, got \(first)")
            return
        }
        #expect(view.title == "Lost cat")
        #expect(view.shareText == Self.code)
        clock.advance(59)
        _ = await ResolveLoader.load(Self.code, app: app)
        #expect(transport.operations == ["resolveCode"], "within 60 seconds the copy is used")
        clock.advance(1)
        _ = await ResolveLoader.load(Self.code, app: app)
        #expect(transport.operations == ["resolveCode", "resolveCode"], "after 60 seconds it fetches again")
        #expect(app.lastCanonical.value == Self.code, "the last canonical text is kept for a retry")
    }

    @Test func offlineShowsTheCopyLabeledOfflineUntil360Seconds() async throws {
        let clock = FakeClock()
        let app = TestApp.make(service: try await TestApp.liveService(Self.transport()), clock: clock)
        _ = await ResolveLoader.load(Self.code, app: app)
        app.connectivity.set(online: false)
        clock.advance(359)
        #expect(await ResolveLoader.load(Self.code, app: app) == .record(
            ResolvedView(canonical: Self.code, title: "Lost cat", body: "Answers on this page.", shareText: Self.code),
            offline: true))
        clock.advance(1)
        #expect(await ResolveLoader.load(Self.code, app: app) == .notOnDevice)
        #expect(await ResolveLoader.load("zz-copper-lantern-sky-zz", app: app) == .notOnDevice)
    }

    @Test func aLostConnectionFallsBackToTheOfflineRules() async throws {
        let clock = FakeClock()
        let failing = AtomicFlag()
        let transport = AppStubTransport { _, _ in
            if failing.value { throw URLError(.notConnectedToInternet) }
            return .init(status: 200, json: Fixtures.resolveJSON(Self.code),
                         headers: ["Cache-Control": Fixtures.publicCacheControl])
        }
        let app = TestApp.make(service: try await TestApp.liveService(transport), clock: clock)
        _ = await ResolveLoader.load(Self.code, app: app)
        failing.value = true
        clock.advance(120)
        guard case .record(_, offline: true) = await ResolveLoader.load(Self.code, app: app) else {
            Issue.record("expected the offline copy")
            return
        }
    }

    @Test func noStoreAndNotFoundAreNeverKept() async throws {
        let clock = FakeClock()
        let app = TestApp.make(service: try await TestApp.liveService(Self.transport()), clock: clock)
        _ = await ResolveLoader.load(Self.code, app: app)
        #expect(app.cache.count == 1)
        let noStore = TestApp.make(service: try await TestApp.liveService(Self.transport(cacheControl: "no-store")),
                                   clock: clock)
        _ = await ResolveLoader.load(Self.code, app: noStore)
        #expect(noStore.cache.count == 0)
        let missing = TestApp.make(service: try await TestApp.liveService(Self.transport(status: 404)), clock: clock)
        #expect(await ResolveLoader.load(Self.code, app: missing) == .notFound)
        #expect(missing.cache.count == 0)
    }

    /// FR-033: one wait message that does not say whether the code exists.
    @Test func rateLimitedWaitsForRetryAfter() async throws {
        let app = TestApp.make(service: try await TestApp.liveService(Self.transport(status: 429, retryAfter: "7")))
        #expect(await ResolveLoader.load(Self.code, app: app) == .failure(.resolveRateLimited, retryAfter: 7))
    }
}

/// Owner writes drop the cached resolve (FR-041), and offline writes are
/// refused and never queued (FR-022, T061).
@MainActor
@Suite("Owner writes and offline")
struct OwnerFlowTests {
    static func signedInDemo(online: Bool = true) async throws -> AppModel {
        let service = TestApp.demoService()
        try await service.tokens.signIn(accessToken: "demo", refreshToken: "demo")
        let app = TestApp.make(service: service, online: online)
        app.completeSignIn(provider: .apple)
        return app
    }

    static func cached(_ canonical: String, in app: AppModel) {
        let record = ResolvedRecord(view: ._public, canonical: canonical,
                                    record: .init(title: "t", body: "b", updatedAt: Date()),
                                    share: .init(text: canonical, url: nil))
        app.cache.store(ResolveResponse(record: record, cacheable: true), for: canonical)
    }

    @Test func rerollDropsTheOldWordsAndStopsAtTheCap() async throws {
        let app = try await Self.signedInDemo()
        let create = CreateModel()
        create.title = "Lost cat"
        let before = app.ownerRevision
        let minted = try #require(await create.mint(app: app))
        #expect(app.ownerRevision > before, "My codes reads the list again after a mint")
        let model = MintedModel(code: minted)
        Self.cached(minted.canonical, in: app)
        await model.reroll(app: app)
        #expect(model.code.canonical != minted.canonical)
        #expect(app.cache.lookup(minted.canonical, online: false) == .none)
        await model.reroll(app: app)
        await model.reroll(app: app)
        #expect(model.capReached)
        #expect(model.code.rerollsRemaining == 0)
    }

    @Test func revokeDropsTheEntryAndShowsRevoked() async throws {
        let app = try await Self.signedInDemo()
        let rows = try await app.service.myCodes().get().codes
        let row = try #require(rows.first { $0.status == .active })
        Self.cached(row.canonical, in: app)
        let detail = CodeDetailModel(codeID: row.id, row: row)
        await detail.load(app: app)
        await detail.revoke(app: app)
        #expect(app.cache.lookup(row.canonical, online: false) == .none)
        #expect(detail.row?.status == .revoked)
        #expect(await ResolveLoader.load(row.canonical, app: app) == .notFound, "T053")
    }

    @Test func aRecordVersionDropsTheEntry() async throws {
        let app = try await Self.signedInDemo()
        let row = try #require(try await app.service.myCodes().get().codes.first)
        Self.cached(row.canonical, in: app)
        let edit = EditRecordModel(codeID: row.id, row: nil)
        await edit.loadOnce(app: app)
        #expect(edit.phase == .editing)
        edit.title = "New title"
        #expect(await edit.save(app: app))
        #expect(app.cache.lookup(row.canonical, online: false) == .none)
    }

    @Test func aBlankTitleIsNotSaved() async throws {
        let app = try await Self.signedInDemo()
        let row = try #require(try await app.service.myCodes().get().codes.first)
        let edit = EditRecordModel(codeID: row.id, row: row)
        await edit.loadOnce(app: app)
        edit.title = "   "
        #expect(await edit.save(app: app) == false)
        #expect(edit.phase == .failure(.createTitleRequired))
    }

    @Test func deletingTheAccountClearsEveryCacheAndTheDraft() async throws {
        let app = try await Self.signedInDemo()
        Self.cached("zz-kathy-lost-cat-zz", in: app)
        Self.cached("zz-copper-lantern-sky-zz", in: app)
        app.drafts.save(title: "Lost cat", body: "")
        app.lastCanonical.value = "zz-kathy-lost-cat-zz"
        try await app.deleteAccount().get()
        #expect(app.cache.count == 0)
        #expect(app.drafts.draft == nil)
        #expect(app.lastCanonical.value == nil)
        #expect(app.isSignedIn == false)
        #expect(await app.service.tokens.hasSession == false)
    }

    @Test func offlineMintIsRefusedAndKeptAsADraft() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 500, json: #"{"error":"failed"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport), online: false)
        let create = CreateModel()
        create.title = "Lost cat"
        create.body = "Answers on this page."
        #expect(await create.mint(app: app) == nil)
        #expect(create.phase == .failure(.createOffline))
        #expect(app.drafts.draft == DraftStore.Draft(title: "Lost cat", body: "Answers on this page."))
        #expect(transport.operations.isEmpty, "nothing is queued or sent")
    }

    @Test func offlineRerollAndRevokeAreRefused() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 500, json: #"{"error":"failed"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport), online: false)
        let code = MintedCode(id: "code-1", canonical: "zz-copper-sky-river-zz", checkWord: "river", status: .active,
                              recordId: "record-1", scope: .freePublic, rerollsRemaining: 3, createdAt: Date())
        let minted = MintedModel(code: code)
        await minted.reroll(app: app)
        #expect(minted.phase == .failure(.errorNoConnection))
        let row = OwnedCode(id: "code-1", canonical: code.canonical, checkWord: "river", status: .active,
                            recordId: "record-1", scope: .freePublic, rerollsRemaining: 3, createdAt: Date(), title: "t")
        let detail = CodeDetailModel(codeID: row.id, row: row)
        await detail.revoke(app: app)
        #expect(detail.revokePhase == .failure(.errorNoConnection))
        #expect(transport.operations.isEmpty)
    }

    /// FR-018 and SC-8: a mint answered `scope-unavailable` is Unavailable.
    @Test func scopeUnavailableMakesCreateUnavailable() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 403, json: #"{"error":"scope-unavailable"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport))
        let create = CreateModel()
        create.title = "Lost cat"
        #expect(await create.mint(app: app) == nil)
        #expect(create.mintUnavailable)
        #expect(create.title == "Lost cat", "the fields stay")
    }

    /// T030: a 400 other than contract-version keeps the fields.
    @Test func aMalformedMintSavesNothingAndKeepsTheFields() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 400, json: #"{"error":"malformed"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport))
        let create = CreateModel()
        create.title = "Lost cat"
        create.body = "Answers"
        #expect(await create.mint(app: app) == nil)
        #expect(create.phase == .failure(.errorNothingSaved))
        #expect(create.title == "Lost cat" && create.body == "Answers")
    }

    /// FR-033: after a 429, Send stays off until `Retry-After` has passed,
    /// and the one wait sentence does not say whether the code exists.
    @Test func aRateLimitedReportWaitsForRetryAfter() async throws {
        let transport = AppStubTransport { _, _ in
            .init(status: 429, json: #"{"error":"rate-limited"}"#, headers: ["Retry-After": "1"])
        }
        let app = TestApp.make(service: try await TestApp.liveService(transport, signedIn: false))
        let model = ReportModel(canonical: "zz-kathy-lost-cat-zz")
        model.state.reason = .spam
        let sending = Task { await model.send(app: app) }
        try await Task.sleep(for: .milliseconds(300))
        #expect(model.state.phase == .failure(.resolveRateLimited))
        #expect(model.state.waiting)
        #expect(!model.state.canSend)
        await sending.value
        #expect(!model.state.waiting)
        #expect(model.state.canSend)
        #expect(transport.operations == ["createReport"])
    }

    @Test func aReportIsRefusedOffline() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 202, json: #"{"id":"r-1"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport, signedIn: false), online: false)
        let model = ReportModel(canonical: "zz-kathy-lost-cat-zz")
        model.state.reason = .other
        await model.send(app: app)
        #expect(model.state.offline)
        #expect(transport.operations.isEmpty)
    }

    @Test func myCodesOfflineKeepsNoList() async throws {
        let app = try await Self.signedInDemo(online: false)
        let codes = CodesModel()
        await codes.reload(app: app)
        #expect(codes.rows.isEmpty)
        #expect(codes.phase == .failure(.errorNoConnection))
    }

    @Test func codeDetailFindsTheRowByIdWhenNotGivenOne() async throws {
        let app = try await Self.signedInDemo()
        let detail = CodeDetailModel(codeID: "demo-code-2", row: nil)
        await detail.load(app: app)
        #expect(detail.row?.canonical == "zz-river-falcon-sky-zz")
        #expect(detail.record?.title == "Parcel (demo)")
        let missing = CodeDetailModel(codeID: "no-such-code", row: nil)
        await missing.load(app: app)
        #expect(missing.phase == .notFound)
    }
}

/// Typed entry, the bands, and Clarify (US2a, spec 004).
@MainActor
@Suite("Scan, Type, and Clarify")
struct ReadFlowTests {
    static let fixture7 = try! WordList(version: "fixture-7",
                                        words: ["copper", "lantern", "sky", "maple", "river", "harbor", "falcon"])

    @Test(arguments: [
        ("zz-b2-smith-1-zz", CandidateAction.resolve("zz-b2-smith-1-zz")),
        ("ZZ COPPER LANTERN SKY ZZ", CandidateAction.resolve("zz-copper-lantern-sky-zz")),
        ("zz-#tag-zz", CandidateAction.message(.typeMalformed)),
        ("copper", CandidateAction.message(.typeMalformed)),
        ("(zz)", CandidateAction.message(.scanBare)),
        ("   ", CandidateAction.message(.typeEmpty)),
    ])
    func typedEntryUsesTheGrammar(_ text: String, _ expected: CandidateAction) {
        let app = TestApp.make(service: TestApp.demoService())
        let model = TypeModel()
        model.text = text
        #expect(model.action(app: app) == expected)
    }

    /// A2 ([DANNY 2026-10-05 #43]): before discovery loads, the bundled
    /// proto-v0 list classifies and no local check word runs; once the demo
    /// server reports fixture-7, that list classifies and checks.
    @Test func theAppClassifiesWithTheServersListOrProtoV0() async {
        let lists = AppModel.loadBundledLists()
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo, bundledLists: lists)
        #expect(app.pipeline.classifyList?.version == "proto-v0")
        #expect(app.pipeline.checkList == nil)
        await app.refreshDiscovery()
        #expect(app.pipeline.classifyList?.version == "fixture-7")
        #expect(app.pipeline.checkList?.version == "fixture-7")
    }

    @Test func aCameraFieldCodeAsksForOneConfirm() {
        let pipeline = ReadPipeline(classifyList: nil, checkList: nil)
        #expect(ClarifyLogic.next(parts: ["b2", "smith", "1"], keptAsWritten: [], confirmed: false,
                                  pipeline: pipeline) == .confirm)
        #expect(ClarifyLogic.next(parts: ["b2", "smith", "1"], keptAsWritten: [], confirmed: true,
                                  pipeline: pipeline) == .done("zz-b2-smith-1-zz"))
    }

    @Test func aNearWordOffersItsCandidatesAndAsWritten() {
        let pipeline = ReadPipeline(classifyList: Self.fixture7, checkList: Self.fixture7)
        let step = ClarifyLogic.next(parts: ["coper", "lantern", "sky"], keptAsWritten: [], confirmed: false,
                                     pipeline: pipeline)
        #expect(step == .nearWord(position: 1, written: "coper", candidates: ["copper"]))
        // Picking the candidate snaps it; the check word then verifies.
        #expect(ClarifyLogic.next(parts: ["copper", "lantern", "sky"], keptAsWritten: [], confirmed: true,
                                  pipeline: pipeline) == .done("zz-copper-lantern-sky-zz"))
        // Kept as written, it is a field code and opens as written.
        #expect(ClarifyLogic.next(parts: ["coper", "lantern", "sky"], keptAsWritten: [1], confirmed: true,
                                  pipeline: pipeline) == .done("zz-coper-lantern-sky-zz"))
    }

    @Test func aCheckWordMismatchIsNeverSentOrCorrected() {
        let pipeline = ReadPipeline(classifyList: Self.fixture7, checkList: Self.fixture7)
        #expect(ClarifyLogic.next(parts: ["copper", "maple", "sky"], keptAsWritten: [], confirmed: true,
                                  pipeline: pipeline) == .checkMismatch)
    }

    @Test func bandsMapToActions() {
        let pipeline = ReadPipeline(classifyList: Self.fixture7, checkList: Self.fixture7)
        func action(_ text: String, _ confidence: Double = 0.95) -> CandidateAction? {
            let line = RecognizedLine(text: text, confidence: confidence, box: CGRect(x: 0, y: 0, width: 1, height: 0.1))
            return pipeline.read(lines: [line]).candidates.first.map { CandidateAction.of($0, source: .camera) }
        }
        #expect(action("zz-copper-lantern-sky-zz") == .resolve("zz-copper-lantern-sky-zz"))
        #expect(action("label zz-copper-lantern") == .message(.scanPartial))
        #expect(action("zz-copper-lantern-sky-zz", 0.4) == .message(.scanRetry))
        #expect(action("zz-copper-lantern-zz") == .message(.scanWrongLength))
        #expect(action("zz") == .message(.scanBare))
        #expect(action("zz-#tag-zz") == .message(.typeMalformed))
        if case .clarify = action("zz-coper-lantern-sky-zz") {} else { Issue.record("a near-word clarifies") }
    }
}

/// Sign-in leaves no half session (runtime.md, Screens: Sign in).
@MainActor
@Suite("Sign-in")
struct SignInFlowTests {
    static func failure(_ result: Result<Void, SignIn.Failure>) -> SignIn.Failure? {
        if case .failure(let failure) = result { return failure }
        return nil
    }

    @Test func demoButtonsSignInWithoutAProvider() async {
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo)
        #expect(Self.failure(await SignIn.run(.apple, app: app)) == nil)
        #expect(app.isSignedIn)
        #expect(app.provider == .apple)
    }

    @Test func aRefusedExchangeLeavesNoSession() async throws {
        let transport = AppStubTransport { operation, _ in
            operation == "createNonce"
                ? .init(status: 200, json: #"{"nonce":"n-1","expires_in":600}"#)
                : .init(status: 401, json: #"{"error":"unauthorized"}"#)
        }
        let app = TestApp.make(service: try await TestApp.liveService(transport, signedIn: false))
        #expect(Self.failure(await SignIn.run(.dev, app: app)) == .failed(.signinFailed))
        #expect(app.isSignedIn == false)
        #expect(await app.service.tokens.hasSession == false)
    }

    @Test func offlineSignInIsRefused() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 500, json: nil) }
        let app = TestApp.make(service: try await TestApp.liveService(transport, signedIn: false), online: false)
        #expect(Self.failure(await SignIn.run(.apple, app: app)) == .failed(.errorNoConnection))
        #expect(transport.operations.isEmpty)
    }

    /// Auth step 2: the Apple request carries the hex SHA-256 of the nonce.
    @Test func appleNonceIsTheHexSHA256() {
        #expect(SignIn.sha256Hex("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test func providersFollowDiscoveryAndTheBuild() async {
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo)
        await app.refreshDiscovery()
        #expect(SignInProviders.shown(app: app) == [.apple, .google])
        let release = TestApp.make(service: TestApp.demoService(), kind: .release)
        await release.refreshDiscovery()
        #expect(SignInProviders.shown(app: release) == [.apple], "Google waits for its client id; no dev")
    }
}

final class AtomicFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var stored = false
    var value: Bool {
        get { lock.withLock { stored } }
        set { lock.withLock { stored = newValue } }
    }
}

/// DEP-005 limits as Create and Edit record enforce them (runtime.md, Screens).
@MainActor
@Suite("Field limits")
struct FieldLimitTests {
    @Test func titleStopsAt120CodePointsAfterTrimming() {
        let long = String(repeating: "a", count: 130)
        #expect(FieldLimits.limit(long, to: FieldLimits.title).unicodeScalars.count == 120)
        let padded = "  " + String(repeating: "b", count: 120) + "  "
        #expect(FieldLimits.limit(padded, to: FieldLimits.title) == padded, "edge spaces do not count")
        let accented = String(repeating: "e\u{301}", count: 70)
        #expect(FieldLimits.count(FieldLimits.limit(accented, to: FieldLimits.title)) <= 120,
                "code points, not characters, are counted")
    }

    @Test func theReportNoteCountsAsTyped() {
        let note = " " + String(repeating: "n", count: 500)
        #expect(FieldLimits.limit(note, to: FieldLimits.reportNote, trimmed: false).unicodeScalars.count == 500)
    }

    @Test func aOneLineFieldTakesNoLineBreak() {
        #expect(FieldLimits.oneLine("Lost\ncat\r\nflyer") == "Lost cat flyer")
    }
}

/// Hide this code (FR-035, zzThis D-2026-10-10-25, RM-101): a list on the
/// device only.
@MainActor
@Suite("Hidden codes")
struct HiddenCodeTests {
    static let code = ResolveFlowTests.code

    @Test func aHiddenCodeStillResolvesAndShowsOnlyTheHiddenState() async throws {
        let transport = ResolveFlowTests.transport(cacheControl: "no-store")
        let app = TestApp.make(service: try await TestApp.liveService(transport, signedIn: false))
        app.hide(Self.code)
        #expect(transport.operations.isEmpty, "hiding calls no server")
        // Scanning and typing both open Resolve with the canonical code.
        let model = ResolveModel(canonical: Self.code)
        await model.load(app: app)
        guard case .record(let view, offline: false) = model.phase else {
            Issue.record("expected the record, got \(model.phase)")
            return
        }
        #expect(view.title == "Lost cat")
        #expect(app.isHidden(Self.code), "Resolve shows the hidden state, not the title and body")
        #expect(transport.operations == ["resolveCode"])
        app.unhide(Self.code)
        #expect(!app.isHidden(Self.code))
        #expect(transport.operations == ["resolveCode"], "unhiding calls no server")
    }

    @Test func theListOutlivesTheAppAndStaysOnTheDevice() {
        let defaults = TestApp.defaults()
        let store = HiddenStore(defaults: defaults)
        store.codes = [Self.code]
        #expect(HiddenStore(defaults: defaults).codes == [Self.code])
        store.codes = []
        #expect(defaults.object(forKey: "hidden.codes") == nil)
    }

    @Test func unhideAllAndAccountDeletionEmptyTheList() async throws {
        let app = try await OwnerFlowTests.signedInDemo()
        app.hide(Self.code)
        app.hide("zz-copper-lantern-sky-zz")
        app.unhideAll()
        #expect(app.hiddenCodes.isEmpty)
        #expect(app.hiddenStore.codes.isEmpty)
        app.hide(Self.code)
        try await app.deleteAccount().get()
        #expect(app.hiddenCodes.isEmpty)
        #expect(app.hiddenStore.codes.isEmpty)
    }
}
