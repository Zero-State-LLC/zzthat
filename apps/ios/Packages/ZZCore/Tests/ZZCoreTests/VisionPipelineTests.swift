#if canImport(Vision)
import CoreGraphics
import CoreText
import Foundation
import Testing
@testable import ZZCore

/// runtime.md, Testing: the read pipeline on a rendered image of
/// zz-copper-lantern-sky-zz yields that one candidate (tasks.md T023, Phase 2).
@Suite("Vision read pipeline")
struct VisionPipelineTests {
    /// Black text on white, one line per string, in a plain sans-serif face.
    static func render(_ lines: [String], width: Int = 1800, lineHeight: Int = 220) throws -> CGImage {
        let height = lineHeight * lines.count + 80
        let context = try #require(CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        let font = CTFontCreateWithName("Helvetica" as CFString, 110, nil)
        for (index, text) in lines.enumerated() {
            let attributes: [NSAttributedString.Key: Any] = [
                NSAttributedString.Key(kCTFontAttributeName as String): font,
                NSAttributedString.Key(kCTForegroundColorAttributeName as String): CGColor(red: 0, green: 0, blue: 0, alpha: 1),
            ]
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
            context.textPosition = CGPoint(x: 60, y: height - 40 - lineHeight * (index + 1) + 60)
            CTLineDraw(line, context)
        }
        return try #require(context.makeImage())
    }

    static func fixture7() throws -> WordList {
        let words = try #require(Vectors.section("check_word")["wordlist"] as? [String])
        return try WordList(version: "fixture-7", words: words)
    }

    @Test func renderedCodeYieldsThatOneCandidate() throws {
        let image = try Self.render(["zz-copper-lantern-sky-zz"])
        let lines = try VisionReader.recognize(image)
        let list = try Self.fixture7()
        let result = ReadPipeline(classifyList: list, checkList: list).read(lines: lines)
        #expect(result.candidates.map(\.canonical) == ["zz-copper-lantern-sky-zz"], "read: \(result.text)")
        let candidate = try #require(result.candidates.first)
        #expect(candidate.codeClass == .word)
        #expect(candidate.check == .ok)
        #expect(!candidate.boxes.isEmpty)
        #expect(candidate.boxes.allSatisfy { CGRect(x: 0, y: 0, width: 1, height: 1).contains($0.insetBy(dx: 0.001, dy: 0.001)) })
    }

    @Test func aCodeAcrossTwoLinesIsOneCode() throws {
        let image = try Self.render(["zz-copper-lantern", "sky-zz"])
        let lines = try VisionReader.recognize(image)
        let result = ReadPipeline(classifyList: nil, checkList: nil).read(lines: lines)
        #expect(result.candidates.map(\.canonical) == ["zz-copper-lantern-sky-zz"], "read: \(result.text)")
        #expect(result.candidates.first?.rows.map(\.lineIndex) == [0, 0, 1])
    }
}
#endif
