import SwiftUI
import Testing
import UIKit
@testable import zzThat

/// runtime.md, Accessibility (T076): every text color the app draws on each
/// background it draws it on meets WCAG AA for small text (4.5:1), light
/// and dark, computed from the pinned token values. Xcode's contrast audit
/// samples rendered pixels and flags tinted chips and anti-aliased text that
/// pass, so this check is the contrast oracle (AccessibilityAuditTests).
@Suite("Contrast")
struct ContrastTests {
    struct Pair: CustomTestStringConvertible, Sendable {
        let text: String
        let background: String
        let pick: @Sendable (Palette) -> (Color, Color)

        var testDescription: String { "\(text) on \(background)" }
    }

    static let pairs: [Pair] = [
        Pair(text: "ink", background: "paper") { ($0.ink, $0.paper) },
        Pair(text: "ink", background: "panel") { ($0.ink, $0.panel) },
        Pair(text: "ink", background: "panel2") { ($0.ink, $0.panel2) },
        Pair(text: "ink2", background: "paper") { ($0.ink2, $0.paper) },
        Pair(text: "ink2", background: "panel") { ($0.ink2, $0.panel) },
        Pair(text: "ink2", background: "panel2") { ($0.ink2, $0.panel2) },
        Pair(text: "ink3", background: "paper") { ($0.ink3, $0.paper) },
        Pair(text: "ink3", background: "panel") { ($0.ink3, $0.panel) },
        Pair(text: "accentInk", background: "paper") { ($0.accentInk, $0.paper) },
        Pair(text: "accentInk", background: "panel") { ($0.accentInk, $0.panel) },
        Pair(text: "accentInk", background: "accentWash") { ($0.accentInk, $0.accentWash) },
        Pair(text: "onAccent", background: "accent") { ($0.onAccent, $0.accent) },
    ]

    @Test(arguments: pairs)
    func meetsAA(_ pair: Pair) {
        for scheme in [ColorScheme.light, .dark] {
            let (text, background) = pair.pick(Palette(scheme))
            let ratio = Self.ratio(text, background)
            #expect(ratio >= 4.5, "\(pair.testDescription), \(scheme): \(ratio)")
        }
    }

    static func ratio(_ a: Color, _ b: Color) -> Double {
        let la = luminance(a)
        let lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    static func luminance(_ color: Color) -> Double {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        func linear(_ c: CGFloat) -> Double {
            let c = Double(c)
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }
}
