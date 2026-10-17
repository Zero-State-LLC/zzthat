import Foundation
import Testing
import ZZAPI
@testable import zzThat

/// The Terms step before the first create on a device (design/UX.md, Terms;
/// zzThis D-2026-10-13-04; tasks.md T104). Client-only: the step sends
/// nothing, and the accepted `terms.version` stays on the device.
@MainActor
@Suite("Terms step")
struct TermsStepTests {
    static func signedIn(acceptedTermsVersion: String?) async throws -> AppModel {
        let service = TestApp.demoService()
        try await service.tokens.signIn(accessToken: "demo", refreshToken: "demo")
        let app = TestApp.make(service: service, acceptedTermsVersion: acceptedTermsVersion)
        app.completeSignIn(provider: .apple)
        return app
    }

    static func create(_ title: String = "Lost cat") -> CreateModel {
        let model = CreateModel()
        model.title = title
        return model
    }

    @Test func theCurrentVersionIsTheCatalogValue() {
        #expect(!CopyKey.termsVersion.text.isEmpty)
        #expect(CopyKey.termsVersion.text != CopyKey.termsVersion.rawValue, "terms.version resolves in the catalog")
    }

    @Test func theStepShowsBeforeTheFirstCreateAndSendsNothing() async throws {
        let transport = AppStubTransport { _, _ in .init(status: 500, json: #"{"error":"failed"}"#) }
        let app = TestApp.make(service: try await TestApp.liveService(transport), acceptedTermsVersion: nil)
        #expect(app.needsTerms)
        let create = Self.create()
        #expect(!create.termsDone(app: app))
        #expect(await create.mint(app: app) == nil)
        #expect(transport.operations.isEmpty, "Create does not continue before the step")
        #expect(app.termsStore.acceptedVersion == nil)
    }

    @Test func createWaitsForBothConfirmations() async throws {
        let app = try await Self.signedIn(acceptedTermsVersion: nil)
        let create = Self.create()
        create.ageConfirmed = true
        #expect(!create.termsDone(app: app))
        #expect(await create.mint(app: app) == nil)
        create.ageConfirmed = false
        create.termsAccepted = true
        #expect(!create.termsDone(app: app))
        #expect(await create.mint(app: app) == nil)
        #expect(app.needsTerms, "nothing is stored before both are confirmed")
        create.ageConfirmed = true
        #expect(create.termsDone(app: app))
        #expect(await create.mint(app: app) != nil)
        #expect(!app.needsTerms)
        #expect(app.termsStore.acceptedVersion == CopyKey.termsVersion.text)
    }

    @Test func theStepDoesNotShowAgainAfterAcceptance() async throws {
        let app = try await Self.signedIn(acceptedTermsVersion: nil)
        let first = Self.create()
        first.ageConfirmed = true
        first.termsAccepted = true
        #expect(await first.mint(app: app) != nil)
        let second = Self.create("Parcel")
        #expect(second.termsDone(app: app))
        #expect(await second.mint(app: app) != nil, "the next create needs no confirmation")
    }

    @Test func theAcceptedVersionOutlivesTheApp() {
        let defaults = TestApp.defaults()
        TermsStore(defaults: defaults).acceptedVersion = CopyKey.termsVersion.text
        #expect(TermsStore(defaults: defaults).acceptedVersion == CopyKey.termsVersion.text)
    }

    @Test func aNewTermsVersionShowsTheStepAgain() async throws {
        let app = try await Self.signedIn(acceptedTermsVersion: "draft-2026-01-01")
        #expect(app.needsTerms)
        let create = Self.create()
        #expect(await create.mint(app: app) == nil)
        create.ageConfirmed = true
        create.termsAccepted = true
        #expect(await create.mint(app: app) != nil)
        #expect(app.termsStore.acceptedVersion == CopyKey.termsVersion.text)
        #expect(!create.ageConfirmed && !create.termsAccepted, "the step starts unchecked if it shows again")
    }

    @Test func accountDeletionClearsTheAcceptedVersion() async throws {
        let app = try await Self.signedIn(acceptedTermsVersion: CopyKey.termsVersion.text)
        #expect(!app.needsTerms)
        try await app.deleteAccount().get()
        #expect(app.needsTerms)
        #expect(app.termsStore.acceptedVersion == nil)
    }

    @Test func signingOutKeepsTheAcceptedVersion() async throws {
        let app = try await Self.signedIn(acceptedTermsVersion: CopyKey.termsVersion.text)
        await app.signOut()
        #expect(!app.needsTerms)
    }

    @Test func scanningAndTypingNeverAskForTheStep() async throws {
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo, acceptedTermsVersion: nil)
        // Scan and Type both end in this resolve (US2, US2a).
        guard case .record = await ResolveLoader.load("zz-kathy-lost-cat-zz", app: app) else {
            Issue.record("a signed-out typed code resolves without the Terms step")
            return
        }
        #expect(app.needsTerms, "the step is still pending, and resolve did not need it")
        #expect(app.termsStore.acceptedVersion == nil, "resolving neither asks nor stores")
    }

    @Test func theTermsLinkIsABuildSettingAndHiddenWithoutOne() {
        let url = URL(string: "https://zzthat.com/terms/")!
        #expect(TestApp.make(service: TestApp.demoService(), termsURL: url).config.termsURL == url)
        #expect(TestApp.make(service: TestApp.demoService()).config.termsURL == nil)
    }
}

/// A 429 on mint or re-roll (FR-033, T014; design/UX.md, Create: the daily
/// limit): Create waits for `Retry-After` and keeps the title and body.
@MainActor
@Suite("Create rate limits")
struct CreateRateLimitTests {
    static func limited(_ operation: String, retryAfter: String) -> AppStubTransport {
        AppStubTransport { op, _ in
            if op == operation {
                return .init(status: 429, json: #"{"error":"rate-limited"}"#, headers: ["Retry-After": retryAfter])
            }
            return .init(status: 500, json: #"{"error":"failed"}"#)
        }
    }

    @Test func theDailyCapIsAWaitLongerThanAnHour() {
        #expect(CreateWait.message(retryAfter: 1) == .resolveRateLimited)
        #expect(CreateWait.message(retryAfter: CreateWait.dailyLimitSeconds) == .resolveRateLimited)
        #expect(CreateWait.message(retryAfter: CreateWait.dailyLimitSeconds + 1) == .createDailyLimit)
        #expect(CreateWait.message(retryAfter: 20 * 3600) == .createDailyLimit)
    }

    @Test func aDailyCapOnMintWaitsAndKeepsTheFields() async throws {
        let transport = Self.limited("mintCode", retryAfter: "36000")
        let app = TestApp.make(service: try await TestApp.liveService(transport))
        let create = CreateModel()
        create.title = "Lost cat"
        create.body = "Answers"
        #expect(await create.mint(app: app) == nil)
        #expect(create.phase == .failure(.createDailyLimit))
        #expect(create.waiting)
        #expect(create.title == "Lost cat" && create.body == "Answers")
        #expect(await create.mint(app: app) == nil)
        #expect(transport.operations == ["mintCode"], "Get a code stays off until Retry-After ends")
        create.endWait()
        #expect(!create.waiting)
        _ = await create.mint(app: app)
        #expect(transport.operations == ["mintCode", "mintCode"])
    }

    @Test func aShortRateLimitOnMintShowsTheWaitSentence() async throws {
        let app = TestApp.make(service: try await TestApp.liveService(Self.limited("mintCode", retryAfter: "30")))
        let create = CreateModel()
        create.title = "Lost cat"
        #expect(await create.mint(app: app) == nil)
        #expect(create.phase == .failure(.resolveRateLimited))
        #expect(create.waiting)
    }

    @Test func aRateLimitedRerollWaitsAndKeepsTheWords() async throws {
        let transport = Self.limited("rerollCode", retryAfter: "36000")
        let app = TestApp.make(service: try await TestApp.liveService(transport))
        let code = MintedCode(id: "code-1", canonical: "zz-copper-sky-river-zz", checkWord: "river", status: .active,
                              recordId: "record-1", scope: .freePublic, rerollsRemaining: 5, createdAt: Date())
        let minted = MintedModel(code: code)
        await minted.reroll(app: app)
        #expect(minted.phase == .failure(.createDailyLimit))
        #expect(minted.waiting)
        #expect(minted.code.canonical == code.canonical)
        #expect(!minted.capReached)
        await minted.reroll(app: app)
        #expect(transport.operations == ["rerollCode"], "Get different words stays off until Retry-After ends")
        minted.endWait()
        #expect(!minted.waiting)
    }

    @Test func aWaitThatHasPassedLetsCreateContinue() async throws {
        let transport = Self.limited("mintCode", retryAfter: "1")
        let app = TestApp.make(service: try await TestApp.liveService(transport))
        let create = CreateModel()
        create.title = "Lost cat"
        _ = await create.mint(app: app)
        #expect(create.waiting)
        try await Task.sleep(for: .milliseconds(1200))
        _ = await create.mint(app: app)
        #expect(transport.operations == ["mintCode", "mintCode"])
    }
}

/// Idle-expired codes in My codes (tasks.md T103; zzThis D-2026-10-11-15),
/// and the account id on Account (D-2026-10-13-08).
@MainActor
@Suite("Expired codes and the account id")
struct ExpiredAndAccountTests {
    @Test func theDemoListHasAnExpiredRowWithNoRevoke() async throws {
        let service = TestApp.demoService()
        try await service.tokens.signIn(accessToken: "demo", refreshToken: "demo")
        let rows = try await service.myCodes().get().codes
        let expired = try #require(rows.first { $0.status == .expired })
        #expect(expired.status.copy == .codesStatusExpired)
        let detail = CodeDetailScreenState(row: expired, phase: .loaded)
        #expect(!detail.showsRevoke)
        let active = try #require(rows.first { $0.status == .active })
        #expect(CodeDetailScreenState(row: active, phase: .loaded).showsRevoke)
        let resolved = await service.resolve(expired.canonical)
        guard case .failure(.notFound) = resolved else {
            Issue.record("an expired code is not public (FR-010)")
            return
        }
    }

    @Test func accountShowsTheIdFromGetMeWhileSignedIn() async throws {
        let service = TestApp.demoService()
        try await service.tokens.signIn(accessToken: "demo", refreshToken: "demo")
        let app = TestApp.make(service: service, kind: .demo)
        app.completeSignIn(provider: .apple)
        #expect(app.accountID == nil)
        app.noteAccount(try await service.me().get())
        #expect(app.accountID == DemoAPI.account.id)
        await app.signOut()
        #expect(app.accountID == nil, "signed out, no id shows")
    }
}
