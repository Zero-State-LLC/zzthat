import CryptoKit
import Foundation
import Testing
@testable import ZZCore

@Suite("P-1 to P-7")
struct ReadPathDecisionTests {
    @Test func typedInputDoesNotTreatALookalikeAsAMarker() {
        let tokens = Scanner.tokenize(Array("22 copper zz".unicodeScalars))
        #expect(tokens.filter(\.isMarker).count == 1)
        #expect(!MarkerConfusion.isTolerantWholeToken("(22)"))
        #expect(!MarkerConfusion.isTolerantWholeToken("ll"))
        #expect(!MarkerConfusion.isTolerantWholeToken("2z@"))
        #expect(!MarkerConfusion.isTolerantWholeToken("2022"))
        #expect(MarkerConfusion.isTolerantWholeToken("22"))
    }

    @Test func anExactPairDropsAMiddleTolerantToken() {
        let markers = [
            FixtureAdapter.mark(100, 250, after: true),
            FixtureAdapter.mark(450, 250, before: true, after: true, glyph: .tolerant, rawToken: "22"),
            FixtureAdapter.mark(800, 250, before: true),
        ]
        let frame = MarkerPairing.frame(imageId: "t", markers: markers, imageWidth: 1000, imageHeight: 500,
                                         engineId: "fixture", engineVersion: "0")
        #expect(frame.fiducials.count == 2)
        #expect(frame.fiducials.allSatisfy { $0.markerGlyph == .exact })
    }

    @Test func aTolerantOuterEndPairsAndCannotAccept() async throws {
        let markers = [
            FixtureAdapter.mark(100, 250, after: true, glyph: .tolerant, rawToken: "22"),
            FixtureAdapter.mark(800, 250, before: true),
        ]
        let adapter = FixtureAdapter(markers: markers, hypotheses: CapturePipelineTests.code())
        let configuration = CaptureConfiguration(
            version: "test-pass",
            qualification: .passReceipt(sha256: "abc"),
            confirmOnly: false,
            thresholds: CaptureConfiguration.current.thresholds,
            limits: CaptureConfiguration.current.limits,
            geometry: CaptureConfiguration.current.geometry,
            reasonCodes: CaptureConfiguration.current.reasonCodes,
            confidenceMapping: CaptureConfiguration.current.confidenceMapping)
        let pipeline = try CapturePipelineTests.pipeline(adapter, configuration)
        let session = await pipeline.detect(imageData: Data(), imageId: "img")
        let chosen = try #require(session.automaticSelection)
        let outcome = await pipeline.process(session, candidateId: chosen.id)
        let decoded = try #require(outcome.decoded.first)
        #expect(decoded.canonical == "zz-copper-lantern-sky-zz")
        #expect(decoded.band == .clarify)
        #expect(decoded.reason == "tolerant-marker")
        #expect(decoded.tolerantPair)
        #expect(CreationCheck.verdict(read: decoded.canonical, minted: decoded.canonical ?? "",
                                      tolerantPair: true) == .rewriteMarks)
    }

    @Test func theDecoderIsOffAndDoesNotFillFromTheCheckEquation() throws {
        let list = try WordList(version: "tiny", words: ["copper", "lantern", "sky", "river"])
        let none = JointDecoder.offers(hypotheses: [["copper", "lantern", "sky"]], list: list,
                                        allowlist: [list.identity], enabled: false)
        #expect(none.isEmpty)
        let short = JointDecoder.offers(hypotheses: [["copper", "lantern", "xxxx"]], list: list,
                                         allowlist: [list.identity], enabled: true)
        #expect(short.isEmpty)
        let wrongHash = ListIdentity(version: list.version, sha256: "nope")
        let refused = JointDecoder.offers(hypotheses: [["copper", "lantern", "sky"]], list: list,
                                           allowlist: [wrongHash], enabled: true)
        #expect(refused.isEmpty)
    }

    @Test func aLargeAllowlistedListCanOfferAndNeverReplaces() throws {
        var words = (0..<1000).map { index -> String in
            var chars = Array(repeating: Character("z"), count: 20)
            var value = index + 1
            for position in stride(from: 0, to: 20, by: 5) {
                chars[position] = Character(UnicodeScalar(98 + (value % 24))!)
                value /= 24
            }
            return String(chars)
        }
        words[0] = String(repeating: "a", count: 20)
        words[1] = String(repeating: "b", count: 20)
        words[2] = String(repeating: "m", count: 20)
        let list = try WordList(version: "n1000", words: words)
        let check = list.words[CheckWord.checkIndex(0, 1, listSize: list.words.count)]
        let offers = JointDecoder.offers(hypotheses: [[list.words[0], list.words[1], check]], list: list,
                                          allowlist: [list.identity], enabled: true)
        #expect(offers.count == 1)
        #expect(offers.first?.band == .clarify)
        #expect(offers.first?.canonical == "zz-" + [list.words[0], list.words[1], check].joined(separator: "-") + "-zz")
        let missing = JointDecoder.offers(hypotheses: [[list.words[0], list.words[1], "x"]], list: list,
                                           allowlist: [list.identity], enabled: true)
        #expect(missing.isEmpty)
    }
}

extension FixtureAdapter {
    static func mark(_ x: Double, _ y: Double, size: Double = 40, degrees: Double = 0, before: Bool = false,
                     after: Bool = false, glyph: MarkerGlyph = .exact, rawToken: String = "zz") -> MarkerObservation {
        let angle = degrees * .pi / 180
        let c = cos(angle), s = sin(angle)
        let w = size * 1.2 / 2, h = size / 2
        let corners = [(-w, -h), (w, -h), (w, h), (-w, h)].map { dx, dy in
            NormalizedPoint(x: (x + c * dx - s * dy) / Double(width), y: (y + s * dx + c * dy) / Double(height))
        }
        return MarkerObservation(quad: corners, baselineAngleDegrees: degrees, inkBefore: before, inkAfter: after,
                                 glyph: glyph, rawToken: rawToken)
    }
}
