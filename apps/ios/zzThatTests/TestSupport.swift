import Foundation
import UIKit
import HTTPTypes
import OpenAPIRuntime
import ZZAPI
import ZZCore
@testable import zzThat

/// A clock the FR-041 tests move by hand.
final class FakeClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(_ start: Date = Date(timeIntervalSince1970: 1_791_072_000)) {
        current = start
    }

    func now() -> Date { lock.withLock { current } }

    func advance(_ seconds: TimeInterval) {
        lock.withLock { current = current.addingTimeInterval(seconds) }
    }
}

/// A transport that answers each operation from a handler, so app tests can
/// drive the generated client with real headers (the Cache-Control a
/// resolve carries decides FR-041).
final class AppStubTransport: ClientTransport, @unchecked Sendable {
    struct Reply: Sendable {
        var status: Int
        var json: String?
        var headers: [String: String] = [:]
    }

    private let lock = NSLock()
    private var log: [String] = []
    private let handler: @Sendable (String, HTTPRequest) throws -> Reply

    init(_ handler: @escaping @Sendable (String, HTTPRequest) throws -> Reply) {
        self.handler = handler
    }

    var operations: [String] { lock.withLock { log } }

    func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL,
              operationID: String) async throws -> (HTTPResponse, HTTPBody?) {
        lock.withLock { log.append(operationID) }
        let reply = try handler(operationID, request)
        var fields = HTTPFields()
        fields[HTTPField.Name("X-ZZ-Contract")!] = "1"
        fields[.cacheControl] = "no-store"
        if reply.json != nil { fields[.contentType] = "application/json" }
        for (name, value) in reply.headers { fields[HTTPField.Name(name)!] = value }
        return (HTTPResponse(status: .init(code: reply.status), headerFields: fields), reply.json.map { HTTPBody($0) })
    }
}

enum Fixtures {
    static let publicCacheControl = "public, max-age=60, stale-while-revalidate=300"

    static func resolveJSON(_ canonical: String, title: String = "Lost cat", body: String = "Answers on this page.",
                            creator: String? = nil) -> String {
        let creatorField = creator.map { #","creator":"\#($0)""# } ?? ""
        return #"{"view":"public","canonical":"\#(canonical)","record":{"title":"\#(title)","body":"\#(body)","updated_at":"2026-10-04T00:00:00.123Z"},"share":{"text":"\#(canonical)","url":null}\#(creatorField)}"#
    }

    static let notFound = #"{"error":"not-found"}"#
}

@MainActor
enum TestApp {
    nonisolated static func temporaryURL(_ name: String) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("zzthat-tests-\(UUID().uuidString)")
            .appendingPathComponent(name)
    }

    nonisolated static func defaults() -> UserDefaults {
        let name = "zzthat-tests-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    /// An app on the given service. `kind` is not Demo unless asked, so the
    /// connectivity flag decides online and offline.
    /// `acceptedTermsVersion` is what the device stored at the Terms step: by
    /// default the current `terms.version`, as after the step. The Terms tests
    /// pass nil for a device that never saw it, or an older version.
    static func make(service: ZZService, kind: BuildKind = .local, online: Bool = true,
                     clock: FakeClock = FakeClock(), bundledLists: [WordList] = [],
                     acceptedTermsVersion: String? = CopyKey.termsVersion.text, termsURL: URL? = nil) -> AppModel {
        let cacheURL = temporaryURL("resolve-cache.json")
        try? FileManager.default.createDirectory(at: cacheURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        let defaults = defaults()
        let terms = TermsStore(defaults: defaults)
        terms.acceptedVersion = acceptedTermsVersion
        return AppModel(config: BuildConfig(kind: kind, apiOrigin: URL(string: "http://localhost:8787"),
                                            supportEmail: "support@zer0state.com", privacyURL: nil,
                                            termsURL: termsURL, googleClientID: nil),
                        service: service, cache: ResolveCache(fileURL: cacheURL, clock: clock),
                        drafts: DraftStore(defaults: defaults), lastCanonical: LastCanonicalStore(defaults: defaults),
                        providerStore: ProviderStore(defaults: defaults), hiddenStore: HiddenStore(defaults: defaults),
                        savedStore: SavedStore(defaults: defaults), termsStore: terms,
                        photos: PhotoScratch(directory: temporaryURL("photos")),
                        connectivity: Connectivity(isOnline: online), bundledLists: bundledLists)
    }

    static func demoService() -> ZZService {
        ZZService(api: DemoAPI(), tokens: TokenStore(storage: MemoryRefreshTokenStorage()))
    }

    static func liveService(_ transport: AppStubTransport, signedIn: Bool = true) async throws -> ZZService {
        let storage = MemoryRefreshTokenStorage(token: signedIn ? "refresh-0" : nil)
        let service = ZZService.live(origin: URL(string: "http://localhost:8787")!, storage: storage,
                                     transport: transport)
        if signedIn { try await service.tokens.signIn(accessToken: "access-0", refreshToken: "refresh-0") }
        return service
    }
}

/// Camera evidence for a photo drawn by `DemoPhoto`, shaped the way the
/// Vision adapter reports it: a zz mark at each end of each line that has
/// one, and the payload between them as one hypothesis. No Vision call, so
/// snapshots and flow tests are deterministic.
enum CaptureFixture {
    typealias Photo = (image: UIImage, lines: [(text: String, box: CGRect)])

    static let width = 1200
    static func height(_ photo: Photo) -> Int { Int(photo.image.size.height) }

    static func markers(_ photo: Photo) -> [MarkerObservation] {
        photo.lines.flatMap { line -> [MarkerObservation] in
            let text = line.text
            guard hasMarks(text) else { return [] }
            let mark = line.box.width * 2 / Double(text.count)
            func quad(_ x: Double) -> [NormalizedPoint] {
                [NormalizedPoint(x: x, y: line.box.minY), NormalizedPoint(x: x + mark, y: line.box.minY),
                 NormalizedPoint(x: x + mark, y: line.box.maxY), NormalizedPoint(x: x, y: line.box.maxY)]
            }
            return [MarkerObservation(quad: quad(line.box.minX), baselineAngleDegrees: 0, inkBefore: false, inkAfter: true),
                    MarkerObservation(quad: quad(line.box.maxX - mark), baselineAngleDegrees: 0, inkBefore: true,
                                      inkAfter: false)]
        }
    }

    static func frame(_ photo: Photo, configuration: CaptureConfiguration = .current) -> RecognitionResult {
        MarkerPairing.frame(imageId: "fixture", markers: markers(photo), imageWidth: width, imageHeight: height(photo),
                            engineId: "fixture", engineVersion: "0", configuration: configuration)
    }

    /// The payload between the marks of the line the pair is on.
    static func hypotheses(_ photo: Photo, pairIndex: Int, confidence: Double? = 0.97) -> [PayloadHypothesis] {
        let text = photo.lines.filter { hasMarks($0.text) }[pairIndex].text
        let payload = String(text.dropFirst(2).dropLast(2))
        return [PayloadHypothesis(lines: [PayloadLine(text: payload, confidence: confidence,
                                                      cropBox: CGRect(x: 0.05, y: 0.2, width: 0.9, height: 0.6))])]
    }

    /// A line written as one code: zz at both ends.
    static func hasMarks(_ text: String) -> Bool {
        let lower = text.lowercased()
        return lower.count > 4 && lower.hasPrefix("zz") && lower.hasSuffix("zz")
    }

    /// The frame, then the reading of one pair, through the app's pipeline.
    static func read(_ photo: Photo, pipeline: ReadPipeline, pairIndex: Int = 0,
                     configuration: CaptureConfiguration = .current) -> (RecognitionResult, CaptureOutcome) {
        let frame = frame(photo, configuration: configuration)
        let capture = CapturePipeline(decoder: pipeline, configuration: configuration, adapter: FixtureCaptureAdapter())
        let candidate = frame.visualCandidates.filter { $0.pairId != nil }[pairIndex]
        return (frame, capture.process(frame, candidateId: candidate.id,
                                       hypotheses: hypotheses(photo, pairIndex: pairIndex)))
    }
}

/// The fixture evidence as an adapter, for the Scan flow tests. It counts
/// payload reads and records nothing else.
final class FixtureCaptureAdapter: RecognitionAdapter, @unchecked Sendable {
    let photo: CaptureFixture.Photo?
    private let lock = NSLock()
    private var reads = 0

    init(photo: CaptureFixture.Photo? = nil) {
        self.photo = photo
    }

    let engineId = "fixture"
    let engineVersion = "0"
    var payloadReads: Int { lock.withLock { reads } }

    func prepare(imageData: Data) async throws -> CaptureImage {
        CaptureImage(pixelWidth: CaptureFixture.width, pixelHeight: photo.map(CaptureFixture.height) ?? 100,
                     backing: nil)
    }

    func detectMarkers(in image: CaptureImage) async throws -> [MarkerObservation] {
        photo.map(CaptureFixture.markers) ?? []
    }

    func readPayload(in image: CaptureImage, roi: ROIGeometry) async throws -> [PayloadHypothesis] {
        guard let photo else { return [] }
        lock.withLock { reads += 1 }
        // The pair whose ROI center falls on a line is that line's pair.
        let lines = photo.lines.filter { CaptureFixture.hasMarks($0.text) }
        let y = roi.centerY / Double(image.pixelHeight)
        let index = lines.firstIndex { $0.box.minY <= y && y <= $0.box.maxY } ?? 0
        return CaptureFixture.hypotheses(photo, pairIndex: index)
    }
}
