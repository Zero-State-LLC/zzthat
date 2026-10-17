import SnapshotTesting
import SwiftUI
import Testing
import UIKit
import ZZAPI
import ZZCore
@testable import zzThat

/// One snapshot per screen state in runtime.md, Screens, light and dark
/// (SC-13, tasks.md T085), in the Demo configuration on the iPhone 17
/// simulator. A state the table marks "Not used" has no snapshot. Loading is
/// snapshotted where the screen waits on the camera, the reader, or a call;
/// Pick, Clarify, Type, Unavailable, and Licenses wait on nothing of their own
/// (Pick and Type hand the wait to Scan and Resolve, Unavailable to Create).
@MainActor
@Suite("Screen snapshots", .serialized)
struct SnapshotTests {
    struct Case: Sendable, CustomTestStringConvertible {
        let name: String
        let view: @MainActor @Sendable () -> AnyView

        var testDescription: String { name }

        init<V: View>(_ name: String, _ view: @escaping @MainActor @Sendable () -> V) {
            self.name = name
            self.view = { AnyView(view()) }
        }
    }

    /// The iPhone 17 (402 by 874 points) with the default text size, drawn
    /// at 1x so the committed references stay small.
    static let iPhone17 = ViewImageConfig(
        safeArea: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
        size: CGSize(width: 402, height: 874),
        traits: UITraitCollection { traits in
            traits.userInterfaceIdiom = .phone
            traits.horizontalSizeClass = .compact
            traits.verticalSizeClass = .regular
            traits.preferredContentSizeCategory = .large
            traits.layoutDirection = .leftToRight
            traits.displayScale = 1
        })

    static func traits(_ style: UIUserInterfaceStyle) -> UITraitCollection {
        UITraitCollection { traits in
            traits.userInterfaceStyle = style
            traits.displayScale = 1
        }
    }

    @Test(arguments: cases)
    func screen(_ testCase: Case) {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let scheme: ColorScheme = style == .dark ? .dark : .light
            let view = NavigationStack { testCase.view() }
                .environment(\.colorScheme, scheme)
            assertSnapshot(of: view,
                           as: .image(precision: 1, perceptualPrecision: 0.98, layout: .device(config: Self.iPhone17),
                                      traits: Self.traits(style)),
                           named: "\(testCase.name)-\(style == .dark ? "dark" : "light")")
        }
    }

    /// runtime.md, Accessibility (T076): the main screens at the largest
    /// accessibility text size, light only, on a tall canvas so nothing
    /// scrolls out of view. The text grows, and the canonical code wraps
    /// rather than truncates.
    static let largeText = ViewImageConfig(
        safeArea: UIEdgeInsets(top: 62, left: 0, bottom: 34, right: 0),
        size: CGSize(width: 402, height: 2400),
        traits: UITraitCollection { traits in
            traits.userInterfaceIdiom = .phone
            traits.horizontalSizeClass = .compact
            traits.verticalSizeClass = .regular
            traits.preferredContentSizeCategory = .accessibilityExtraExtraExtraLarge
            traits.layoutDirection = .leftToRight
            traits.displayScale = 1
        })

    nonisolated static let largeTextNames: Set<String> = ["scan-demo", "pick-demo", "type-demo", "resolve-demo", "create-demo",
                                              "terms-demo", "minted-demo", "writecheck-demo", "codes-demo",
                                              "detail-demo", "signin-demo", "account-demo"]

    @Test(arguments: cases.filter { largeTextNames.contains($0.name) })
    func largeTextScreen(_ testCase: Case) {
        let view = NavigationStack { testCase.view() }
            .environment(\.colorScheme, .light)
            .environment(\.dynamicTypeSize, .accessibility5)
        assertSnapshot(of: view,
                       as: .image(precision: 1, perceptualPrecision: 0.98, layout: .device(config: Self.largeText),
                                  traits: Self.traits(.light)),
                       named: "\(testCase.name)-ax5")
    }

    /// The two confirm dialogs are system alerts (design/UX.md, Revoke
    /// confirm and Delete confirm): the title, the body, the destructive
    /// action, and Cancel, which is the default. A system alert renders only
    /// on screen, so it is presented in a window over its Demo screen and the
    /// window is captured.
    @Test(arguments: ["revoke-confirm-demo", "delete-confirm-demo"])
    func confirmDialog(_ name: String) async throws {
        let revoke = name.hasPrefix("revoke")
        for style in [UIUserInterfaceStyle.light, .dark] {
            let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
            let window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: Self.iPhone17.size ?? .zero)
            window.overrideUserInterfaceStyle = style
            let background = revoke
                ? AnyView(Self.detail(CodeDetailScreenState(row: Self.demoRows[0], record: Self.demoOwnerRecord,
                                                            phase: .loaded, demo: true)))
                : AnyView(Self.account({ var state = Self.signedInAccount; state.demo = true; return state }()))
            let host = UIHostingController(rootView: NavigationStack { background })
            window.rootViewController = host
            window.makeKeyAndVisible()
            let alert = UIAlertController(title: (revoke ? CopyKey.revokeTitle : .deleteTitle).text,
                                          message: (revoke ? CopyKey.revokeBody : .deleteBody).text,
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: (revoke ? CopyKey.revokeAction : .deleteAction).text,
                                          style: .destructive))
            alert.addAction(UIAlertAction(title: CopyKey.commonCancel.text, style: .cancel))
            host.present(alert, animated: false)
            try await Task.sleep(for: .seconds(1))
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let image = UIGraphicsImageRenderer(bounds: window.bounds, format: format).image { _ in
                _ = window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            alert.dismiss(animated: false)
            window.isHidden = true
            assertSnapshot(of: image, as: .image(precision: 0.99, perceptualPrecision: 0.97),
                           named: "\(name)-\(style == .dark ? "dark" : "light")")
        }
    }

    // MARK: Fixtures

    static let fixtureLines = DemoPhoto.make(lines: [DemoPhoto.canonical])
    static let twoCodeLines = DemoPhoto.make(lines: ["zz-river-falcon-sky-zz", "zz-b2-4-zz"])
    static let noCodeLines = DemoPhoto.make(lines: ["the buzz word"])

    /// The pipeline as the Demo build runs it: the bundled lists, with the
    /// demo server's `wordlist_version` (fixture-7) picking the local check
    /// word (FR-044).
    static func candidates(_ photo: (image: UIImage, lines: [(text: String, box: CGRect)])) -> [ReadCandidate] {
        let lines = photo.lines.map { RecognizedLine(text: $0.text, confidence: 0.97, box: $0.box) }
        let pipeline = ReadPipeline(bundled: AppModel.loadBundledLists(),
                                    serverVersion: DemoAPI.discovery.wordlistVersion)
        return pipeline.read(lines: lines).candidates
    }

    /// The Scan screen after a read, as the Demo build reaches it: the boxes
    /// from the zz marks and, when one complete pair is the only box, its
    /// reading. Confirm-only (T102) keeps that reading in Clarify.
    static func scanState(_ photo: (image: UIImage, lines: [(text: String, box: CGRect)]), message: CopyKey? = nil,
                          demo: Bool = false) -> ScanScreenState {
        let pipeline = ReadPipeline(bundled: AppModel.loadBundledLists(),
                                    serverVersion: DemoAPI.discovery.wordlistVersion)
        let frame = CaptureFixture.frame(photo)
        let decoded = frame.visualCandidates.count == 1 ? CaptureFixture.read(photo, pipeline: pipeline).1.decoded : []
        return ScanScreenState(photo: photo.image, visual: frame.visualCandidates, candidates: decoded,
                               message: message, demo: demo)
    }

    static let demoRecord = DemoAPI.records["zz-kathy-lost-cat-zz"]!
    static let demoView = ResolvedView(canonical: "zz-kathy-lost-cat-zz", title: demoRecord.title,
                                       body: demoRecord.body, shareText: "zz-kathy-lost-cat-zz")
    static let creatorView = ResolvedView(canonical: "zz-kathy-lost-cat-zz", title: demoRecord.title,
                                          body: demoRecord.body, shareText: "zz-kathy-lost-cat-zz",
                                          creator: DemoAPI.demoCreator)
    static let demoMint = DemoAPI.mintSequence[0]
    static let demoRows: [OwnedCode] = [
        OwnedCode(id: "demo-code-1", canonical: "zz-copper-lantern-sky-zz", checkWord: "sky", status: .active,
                  recordId: "demo-record-1", scope: .freePublic, rerollsRemaining: 0, createdAt: DemoAPI.fixedDate,
                  title: DemoAPI.records["zz-copper-lantern-sky-zz"]!.title),
        OwnedCode(id: "demo-code-2", canonical: "zz-river-falcon-sky-zz", checkWord: "sky", status: .active,
                  recordId: "demo-record-2", scope: .freePublic, rerollsRemaining: 0, createdAt: DemoAPI.fixedDate,
                  title: DemoAPI.records["zz-river-falcon-sky-zz"]!.title),
        OwnedCode(id: "demo-code-3", canonical: "zz-b2-4-zz", checkWord: nil, status: .revoked,
                  recordId: "demo-record-3", scope: .freePublic, rerollsRemaining: 0, createdAt: DemoAPI.fixedDate,
                  title: DemoAPI.records["zz-b2-4-zz"]!.title),
        OwnedCode(id: "demo-code-4", canonical: "zz-harbor-maple-river-zz", checkWord: "river", status: .expired,
                  recordId: "demo-record-4", scope: .freePublic, rerollsRemaining: 0, createdAt: DemoAPI.fixedDate,
                  title: DemoAPI.expiredRecord.title),
    ]
    static let demoOwnerRecord = OwnerRecord(id: "demo-record-1", version: 1,
                                             title: DemoAPI.records["zz-copper-lantern-sky-zz"]!.title,
                                             body: DemoAPI.records["zz-copper-lantern-sky-zz"]!.body,
                                             updatedAt: DemoAPI.fixedDate)

    static func scan(_ state: ScanScreenState) -> some View {
        ScanScreen(state: state, pickerItem: .constant(nil))
    }

    static func type(_ text: String, _ state: TypeScreenState) -> some View {
        TypeScreen(text: .constant(text), state: state)
    }

    static func create(_ state: CreateScreenState, title: String = "", body: String = "",
                       confirmed: Bool = false) -> some View {
        CreateScreen(title: .constant(title), bodyText: .constant(body), state: state,
                     ageConfirmed: .constant(confirmed), termsAccepted: .constant(confirmed))
    }

    /// The Local and Demo placeholder Terms link (Shared.xcconfig).
    static let termsURL = URL(string: "https://zzthat.com/terms/")

    static func minted(_ state: MintedScreenState) -> some View { MintedScreen(state: state) }

    static func writeCheck(_ state: WriteCheckScreenState) -> some View {
        WriteCheckScreen(state: state, pickerItem: .constant(nil))
    }

    static func detail(_ state: CodeDetailScreenState) -> some View { CodeDetailScreen(state: state) }

    static func edit(_ state: EditRecordScreenState, title: String, body: String) -> some View {
        EditRecordScreen(title: .constant(title), bodyText: .constant(body), state: state)
    }

    static func signIn(_ state: SignInScreenState) -> some View { SignInScreen(state: state) }

    static func account(_ state: AccountScreenState) -> some View {
        AccountScreen(state: state) {
            SignInContent(state: SignInScreenState(providers: [.apple, .google], offline: state.offline))
        }
    }

    static func report(_ state: ReportState) -> some View { ReportScreen(state: .constant(state)) }

    static let signedInAccount = AccountScreenState(signedIn: true, provider: .apple,
                                                    supportEmail: "support@zer0state.com",
                                                    privacyURL: URL(string: "https://zero-state-llc.github.io/zzthis/privacy/"),
                                                    termsURL: termsURL, accountID: DemoAPI.account.id)

    // MARK: Cases

    nonisolated static let cases: [Case] = scanCases + typeCases + resolveCases + createCases + codesCases + accountCases

    nonisolated static let scanCases: [Case] = [
        Case("scan-loading") { scan(ScanScreenState(photo: fixtureLines.image, reading: true)) },
        Case("scan-empty") { scan(ScanScreenState(photo: noCodeLines.image, message: .scanNoCode)) },
        Case("scan-error") { scan(ScanScreenState(message: .scanCameraOff)) },
        Case("scan-offline") { scan(scanState(fixtureLines, message: .resolveNotOnDevice)) },
        Case("scan-demo") { scan(scanState(fixtureLines, demo: true)) },
        Case("pick-offline") { scan(scanState(twoCodeLines, message: .resolveNotOnDevice)) },
        Case("pick-demo") { scan(scanState(twoCodeLines, demo: true)) },
        Case("clarify-error") {
            ClarifyScreen(state: ClarifyScreenState(parts: ["copper", "maple", "sky"], step: .checkMismatch))
        },
        Case("clarify-offline") {
            ClarifyScreen(state: ClarifyScreenState(parts: ["b2", "smith", "1"], step: .confirm))
        },
        Case("clarify-demo") {
            ClarifyScreen(state: ClarifyScreenState(
                parts: ["coper", "lantern", "sky"],
                step: .nearWord(position: 1, written: "coper", candidates: ["copper"]), demo: true))
        },
    ]

    nonisolated static let typeCases: [Case] = [
        Case("type-empty") { type("", TypeScreenState()) },
        Case("type-error") { type("zz-copper-lantern", TypeScreenState(message: .typeMalformed)) },
        Case("type-offline") { type(DemoPhoto.canonical, TypeScreenState(message: .resolveNotOnDevice)) },
        Case("type-demo") { type(DemoPhoto.canonical, TypeScreenState(demo: true)) },
    ]

    nonisolated static let resolveCases: [Case] = [
        Case("resolve-loading") { ResolveScreen(canonical: demoView.canonical, phase: .loading(previous: nil), demo: false) },
        Case("resolve-error") { ResolveScreen(canonical: demoView.canonical, phase: .notFound, demo: false) },
        Case("resolve-offline") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(demoView, offline: true), demo: false)
        },
        Case("resolve-offline-not-on-device") {
            ResolveScreen(canonical: demoView.canonical, phase: .notOnDevice, demo: false)
        },
        Case("resolve-demo") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(demoView, offline: false), demo: true)
        },
        Case("resolve-hidden") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(demoView, offline: false), demo: false,
                          hidden: true)
        },
        Case("resolve-creator") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(creatorView, offline: false), demo: false)
        },
        Case("resolve-saved") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(creatorView, offline: false), demo: false,
                          saved: true)
        },
        Case("resolve-blocked") {
            ResolveScreen(canonical: demoView.canonical, phase: .record(creatorView, offline: false), demo: false,
                          blocked: true)
        },
        Case("report-sent") { report(ReportState(reason: .spam, phase: .sent)) },
        Case("report-sent-block") { report(ReportState(reason: .spam, phase: .sent, canBlock: true)) },
        Case("report-sent-blocked") {
            report(ReportState(reason: .spam, phase: .sent, hidden: true, canBlock: true, blocked: true))
        },
        Case("report-sent-hidden") { report(ReportState(reason: .spam, phase: .sent, hidden: true)) },
        Case("report-loading") { report(ReportState(reason: .spam, phase: .sending)) },
        Case("report-empty") { report(ReportState()) },
        Case("report-error") { report(ReportState(reason: .spam, phase: .failure(.resolveRateLimited))) },
        Case("report-offline") { report(ReportState(reason: .spam, offline: true)) },
        Case("report-demo") { report(ReportState(reason: .personalData, demo: true)) },
    ]

    nonisolated static let createCases: [Case] = [
        Case("create-loading") {
            create(CreateScreenState(gate: .form, phase: .minting), title: demoRecord.title, body: demoRecord.body)
        },
        Case("create-empty") { create(CreateScreenState(gate: .signedOut)) },
        Case("create-error") {
            create(CreateScreenState(gate: .form, phase: .failure(.createContentRefused)),
                   title: demoRecord.title, body: demoRecord.body)
        },
        Case("create-offline") {
            create(CreateScreenState(gate: .form, offline: true), title: demoRecord.title, body: demoRecord.body)
        },
        Case("create-demo") {
            create(CreateScreenState(gate: .form, demo: true), title: demoRecord.title, body: demoRecord.body)
        },
        // The Terms step before the first create (design/UX.md, Terms).
        Case("terms-empty") {
            create(CreateScreenState(gate: .form, termsNeeded: true, termsURL: termsURL),
                   title: demoRecord.title, body: demoRecord.body)
        },
        Case("terms-confirmed") {
            create(CreateScreenState(gate: .form, termsNeeded: true, termsURL: termsURL),
                   title: demoRecord.title, body: demoRecord.body, confirmed: true)
        },
        Case("terms-no-link") {
            create(CreateScreenState(gate: .form, termsNeeded: true), title: demoRecord.title, body: demoRecord.body)
        },
        Case("terms-demo") {
            create(CreateScreenState(gate: .form, demo: true, termsNeeded: true, termsURL: termsURL),
                   title: demoRecord.title, body: demoRecord.body)
        },
        // A 429 during the daily cap (design/UX.md, Create: the daily limit).
        Case("create-daily-limit") {
            create(CreateScreenState(gate: .form, phase: .failure(.createDailyLimit), waiting: true),
                   title: demoRecord.title, body: demoRecord.body)
        },
        Case("unavailable-empty") { create(CreateScreenState(gate: .unavailable)) },
        Case("unavailable-offline") { create(CreateScreenState(gate: .unavailable, offline: true)) },
        Case("unavailable-demo") { create(CreateScreenState(gate: .unavailable, demo: true)) },
        Case("minted-loading") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: false,
                                     phase: .rerolling))
        },
        Case("minted-error") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: true,
                                     phase: .failure(.mintedRerollCap)))
        },
        Case("minted-offline") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: false,
                                     offline: true))
        },
        Case("minted-daily-limit") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: false,
                                     phase: .failure(.createDailyLimit), waiting: true))
        },
        Case("minted-kept") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: false,
                                     kept: true))
        },
        Case("minted-demo") {
            minted(MintedScreenState(canonical: demoMint.canonical, checkWord: demoMint.checkWord, capReached: false,
                                     demo: true))
        },
        Case("writecheck-loading") {
            writeCheck(WriteCheckScreenState(canonical: DemoPhoto.canonical, photo: fixtureLines.image, reading: true))
        },
        Case("writecheck-empty") { writeCheck(WriteCheckScreenState(canonical: DemoPhoto.canonical)) },
        Case("writecheck-error") {
            writeCheck(WriteCheckScreenState(canonical: demoMint.canonical, photo: fixtureLines.image,
                                             candidates: candidates(fixtureLines), verdict: .mismatch))
        },
        Case("writecheck-offline") {
            writeCheck(WriteCheckScreenState(canonical: DemoPhoto.canonical, photo: fixtureLines.image,
                                             candidates: candidates(fixtureLines), verdict: .match))
        },
        Case("writecheck-demo") {
            writeCheck(WriteCheckScreenState(canonical: DemoPhoto.canonical, photo: fixtureLines.image,
                                             candidates: candidates(fixtureLines), verdict: .match, demo: true))
        },
    ]

    nonisolated static let codesCases: [Case] = [
        Case("codes-loading") { CodesScreen(state: CodesScreenState(signedIn: true, rows: demoRows, phase: .loading)) },
        Case("codes-empty") { CodesScreen(state: CodesScreenState(signedIn: true, phase: .loaded)) },
        Case("codes-empty-signed-out") { CodesScreen(state: CodesScreenState(signedIn: false)) },
        Case("codes-error") {
            CodesScreen(state: CodesScreenState(signedIn: true, phase: .failure(.errorForbidden)))
        },
        Case("codes-offline") {
            CodesScreen(state: CodesScreenState(signedIn: true, phase: .failure(.errorNoConnection)))
        },
        Case("saved-empty") { SavedScreen(codes: [], demo: false) },
        Case("saved-demo") {
            SavedScreen(codes: ["zz-kathy-lost-cat-zz", "zz-copper-lantern-sky-zz"], demo: true)
        },
        Case("codes-demo") {
            CodesScreen(state: CodesScreenState(signedIn: true, rows: demoRows, phase: .loaded, demo: true))
        },
        Case("detail-expired") {
            detail(CodeDetailScreenState(row: demoRows[3], phase: .loaded))
        },
        Case("detail-loading") { detail(CodeDetailScreenState(row: demoRows[0], phase: .loading)) },
        Case("detail-error") { detail(CodeDetailScreenState(phase: .notFound)) },
        Case("detail-offline") { detail(CodeDetailScreenState(phase: .failure(.errorNoConnection))) },
        Case("detail-demo") {
            detail(CodeDetailScreenState(row: demoRows[0], record: demoOwnerRecord, phase: .loaded, demo: true))
        },
        Case("edit-loading") { edit(EditRecordScreenState(phase: .loading), title: "", body: "") },
        Case("edit-empty") {
            edit(EditRecordScreenState(phase: .editing), title: "", body: demoOwnerRecord.body)
        },
        Case("edit-error") {
            edit(EditRecordScreenState(phase: .failure(.createContentRefused)), title: demoOwnerRecord.title,
                 body: demoOwnerRecord.body)
        },
        Case("edit-offline") {
            edit(EditRecordScreenState(phase: .editing, offline: true), title: demoOwnerRecord.title,
                 body: demoOwnerRecord.body)
        },
        Case("edit-demo") {
            edit(EditRecordScreenState(phase: .editing, demo: true), title: demoOwnerRecord.title,
                 body: demoOwnerRecord.body)
        },
        Case("revoke-confirm-loading") {
            detail(CodeDetailScreenState(row: demoRows[0], record: demoOwnerRecord, phase: .loaded,
                                         revokePhase: .revoking))
        },
        Case("revoke-confirm-error") {
            detail(CodeDetailScreenState(row: demoRows[0], record: demoOwnerRecord, phase: .loaded,
                                         revokePhase: .failure(.errorNothingSaved)))
        },
        Case("revoke-confirm-offline") {
            detail(CodeDetailScreenState(row: demoRows[0], record: demoOwnerRecord, phase: .loaded,
                                         revokePhase: .failure(.errorNoConnection)))
        },
    ]

    nonisolated static let accountCases: [Case] = [
        Case("signin-loading") { signIn(SignInScreenState(providers: [.apple, .google], phase: .signingIn)) },
        Case("signin-empty") { signIn(SignInScreenState(providers: [.apple, .google])) },
        Case("signin-error") { signIn(SignInScreenState(providers: [.apple, .google], phase: .failure(.signinFailed))) },
        Case("signin-offline") { signIn(SignInScreenState(providers: [.apple, .google], offline: true)) },
        Case("signin-demo") { signIn(SignInScreenState(providers: [.apple, .google], demo: true)) },
        Case("account-loading") {
            var state = signedInAccount
            state.loading = true
            return account(state)
        },
        Case("account-empty") {
            var state = signedInAccount
            state.signedIn = false
            return account(state)
        },
        Case("account-error") {
            var state = signedInAccount
            state.deletePhase = .failure(.deleteFailed)
            return account(state)
        },
        Case("account-offline") {
            var state = signedInAccount
            state.offline = true
            return account(state)
        },
        Case("account-demo") {
            var state = signedInAccount
            state.demo = true
            return account(state)
        },
        Case("account-unhide-all-done") {
            var state = signedInAccount
            state.signedIn = false
            state.unhideAllDone = true
            return account(state)
        },
        Case("account-clear-saved-done") {
            var state = signedInAccount
            state.clearSavedDone = true
            return account(state)
        },
        Case("delete-confirm-loading") {
            var state = signedInAccount
            state.deletePhase = .deleting
            return account(state)
        },
        Case("delete-confirm-error") {
            var state = signedInAccount
            state.deletePhase = .failure(.deleteFailed)
            return account(state)
        },
        Case("delete-confirm-offline") {
            var state = signedInAccount
            state.offline = true
            state.deletePhase = .failure(.errorNoConnection)
            return account(state)
        },
        Case("licenses-offline") { LicensesScreen(texts: LicensesScreen.bundledTexts(), demo: false) },
        Case("licenses-demo") { LicensesScreen(texts: LicensesScreen.bundledTexts(), demo: true) },
    ]
}
