import Foundation
import HTTPTypes
import Testing
import UIKit
import ZZAPI
import ZZCore
@testable import zzThat

/// The Scan flow on the RecognitionResult boundary, end to end in the app
/// (tasks.md T023, T024, T102; recognition-adapter.md): marks first, a pick
/// before any payload read, confirm-only for camera Accept, and no image,
/// crop, or transcript leaving the device or staying after the scan.
@MainActor
@Suite("Camera capture flow")
struct CaptureFlowTests {
    static let one = DemoPhoto.make(lines: [DemoPhoto.canonical])
    static let two = DemoPhoto.make(lines: ["zz-river-falcon-sky-zz", "zz-b2-4-zz"])

    /// The demo server reports fixture-7, so the bundled fixture-7 list
    /// classifies and checks (FR-044).
    static func demoApp() async -> AppModel {
        let app = TestApp.make(service: TestApp.demoService(), kind: .demo, bundledLists: AppModel.loadBundledLists())
        _ = await app.refreshDiscovery()
        return app
    }

    @Test func aSoleCodeIsReadThenConfirmedBeforeItOpens() async throws {
        let app = await Self.demoApp()
        let adapter = FixtureCaptureAdapter(photo: Self.one)
        let scan = ScanModel(adapter: adapter)
        let opened = await scan.read(Self.one.image, app: app)
        // Confirm-only: the reading the table would accept does not open.
        #expect(opened == nil)
        #expect(scan.visual.count == 1)
        #expect(adapter.payloadReads == 1)
        let candidate = try #require(scan.candidates.first)
        #expect(scan.candidates.count == 1)
        #expect(candidate.canonical == DemoPhoto.canonical)
        #expect(candidate.baseBand == .accept)
        #expect(candidate.band == .clarify)
        #expect(candidate.reason == "confirm-only")
        // Clarify shows the full canonical code and asks for one confirm.
        guard case .clarify(let input) = CandidateAction.of(candidate, source: .camera) else {
            Issue.record("a confirm-only reading clarifies")
            return
        }
        let clarify = ClarifyModel(input: input)
        #expect(clarify.canonical == DemoPhoto.canonical)
        #expect(clarify.step(app: app) == .confirm)
        clarify.confirm()
        #expect(clarify.step(app: app) == .done(DemoPhoto.canonical))
    }

    @Test func withAReceiptAndTheSettingOffASoleAcceptOpensAtOnce() async throws {
        var qualified = CaptureConfiguration.current
        qualified.qualification = .passReceipt(sha256: String(repeating: "c", count: 64))
        qualified.confirmOnly = false
        let app = await Self.demoApp()
        let scan = ScanModel(adapter: FixtureCaptureAdapter(photo: Self.one), configuration: qualified)
        #expect(await scan.read(Self.one.image, app: app) == DemoPhoto.canonical)
        #expect(scan.candidates.map(\.band) == [.accept])
    }

    /// Typed input is unchanged by confirm-only (spec 004 FR-023).
    @Test func typedEntryStillOpensAsTyped() async {
        let app = await Self.demoApp()
        let model = TypeModel()
        model.text = DemoPhoto.canonical
        #expect(CaptureConfiguration.current.confirmOnly)
        #expect(model.action(app: app) == .resolve(DemoPhoto.canonical))
    }

    @Test func twoCodesWaitForAPickAndReadOnlyThePick() async throws {
        let app = await Self.demoApp()
        let adapter = FixtureCaptureAdapter(photo: Self.two)
        let scan = ScanModel(adapter: adapter)
        #expect(await scan.read(Self.two.image, app: app) == nil)
        #expect(scan.visual.map { $0.pairId != nil } == [true, true])
        #expect(scan.candidates.isEmpty)
        #expect(adapter.payloadReads == 0)
        #expect(await scan.select(scan.visual[1], app: app) == nil)
        #expect(adapter.payloadReads == 1)
        #expect(scan.candidates.map(\.canonical) == ["zz-b2-4-zz"])
        #expect(scan.candidates.map(\.band) == [.clarify])
    }

    /// Over the marker limit, the whole frame fails closed: no box, no read.
    @Test func tooManyMarksFailTheFrameAndReadNothing() async throws {
        let crowded = DemoPhoto.make(lines: ["zz-river-falcon-sky-zz", "zz-b2-4-zz", "zz-copper-lantern-sky-zz",
                                             "zz-maple-harbor-sky-zz", "zz-b3-zz"])
        let app = await Self.demoApp()
        let adapter = FixtureCaptureAdapter(photo: crowded)
        let scan = ScanModel(adapter: adapter)
        #expect(await scan.read(crowded.image, app: app) == nil)
        #expect(scan.visual.isEmpty)
        #expect(scan.candidates.isEmpty)
        #expect(scan.message == .scanRetry)
        #expect(adapter.payloadReads == 0)
    }

    @Test func aPhotoWithNoMarksSaysNoCode() async throws {
        let app = await Self.demoApp()
        let none = DemoPhoto.make(lines: ["the buzz word"])
        let scan = ScanModel(adapter: FixtureCaptureAdapter(photo: none))
        #expect(await scan.read(none.image, app: app) == nil)
        #expect(scan.message == .scanNoCode)
    }

    /// The existing copy for each way a box or a frame ends without a reading.
    @Test(arguments: [
        (BandDecision(.retry, "missing-marker"), CopyKey.scanPartial),
        (BandDecision(.retry, "marker-limit-exceeded"), .scanRetry),
        (BandDecision(.retry, "unsupported-geometry"), .scanRetry),
        (BandDecision(.retry, "no-payload-text"), .scanRetry),
        (BandDecision(.abstain, "bare-mark"), .scanBare),
        (BandDecision(.abstain, "recognizer-unavailable"), .scanNoCode),
        (BandDecision(.abstain, "invalid-configuration"), .scanNoCode),
    ])
    func dispositionsUseTheExistingCopy(_ decision: BandDecision, _ key: CopyKey) {
        #expect(ScanModel.message(for: decision) == key)
    }

    /// FR-004, FR-005, SC-4: scanning sends nothing and keeps nothing. Only
    /// the canonical code the person confirmed goes out, to resolve.
    @Test func scanningSendsNothingAndOnlyTheConfirmedCodeResolves() async throws {
        let paths = PathLog()
        let transport = AppStubTransport { operation, request in
            paths.append(request.path ?? "")
            guard operation == "resolveCode" else { return .init(status: 500, json: #"{"error":"failed"}"#) }
            return .init(status: 200, json: Fixtures.resolveJSON("zz-b2-4-zz"),
                         headers: ["Cache-Control": Fixtures.publicCacheControl])
        }
        let app = TestApp.make(service: try await TestApp.liveService(transport), bundledLists: AppModel.loadBundledLists())
        let scan = ScanModel(adapter: FixtureCaptureAdapter(photo: Self.two))
        _ = await scan.read(Self.two.image, app: app)
        let scratch = try #require(app.photos.currentURL)
        #expect(FileManager.default.fileExists(atPath: scratch.path))
        _ = await scan.select(scan.visual[1], app: app)
        #expect(transport.operations.isEmpty, "reading a photo makes no request")
        #expect(app.lastCanonical.value == nil, "nothing is kept before the person opens a code")
        let candidate = try #require(scan.candidates.first)
        guard case .clarify(let input) = CandidateAction.of(candidate, source: .camera) else {
            Issue.record("a camera field code clarifies")
            return
        }
        let clarify = ClarifyModel(input: input)
        clarify.confirm()
        guard case .done(let canonical) = clarify.step(app: app) else {
            Issue.record("one confirm opens the code")
            return
        }
        scan.end(app: app)
        #expect(!FileManager.default.fileExists(atPath: scratch.path))
        #expect(app.photos.storedFiles.isEmpty)
        #expect(!scan.holdsEvidence)
        #expect(scan.visual.isEmpty && scan.candidates.isEmpty)
        _ = await ResolveLoader.load(canonical, app: app)
        #expect(transport.operations == ["resolveCode"])
        #expect(paths.all.count == 1)
        #expect(paths.all.first?.hasSuffix("/" + canonical) == true, "the request names only the code: \(paths.all)")
    }

    /// The Demo photo through the real Vision adapter on the simulator: the
    /// marks pair, the ROI reads, and confirm-only keeps it in Clarify.
    @Test func theDemoPhotoReadsWithVisionOnTheDevice() async throws {
        let app = await Self.demoApp()
        let scan = ScanModel()
        #expect(await scan.readDemoPhoto(app: app) == nil)
        #expect(scan.visual.count == 1)
        #expect(scan.candidates.map(\.canonical) == [DemoPhoto.canonical])
        #expect(scan.candidates.map(\.band) == [.clarify])
        scan.end(app: app)
        #expect(app.photos.storedFiles.isEmpty)
    }
}

/// Request paths a stub transport saw.
final class PathLog: @unchecked Sendable {
    private let lock = NSLock()
    private var paths: [String] = []

    func append(_ path: String) { lock.withLock { paths.append(path) } }
    var all: [String] { lock.withLock { paths } }
}
