// Generated from design/tokens.json by scripts/build-design-tokens.mjs.
// Do not edit. Register the IBM Plex families before using ZZFont.
import SwiftUI

public enum ZZColor {
    public static let colorPaperLight = Color(.sRGB, red: 0.956863, green: 0.937255, blue: 0.894118, opacity: 1) // #f4efe4 from oklch(95.3% 0.016 86)
    public static let colorPaperDark = Color(.sRGB, red: 0.070588, green: 0.062745, blue: 0.054902, opacity: 1) // #12100e from oklch(17.5% 0.006 75)
    public static func colorPaper(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorPaperDark : colorPaperLight }
    public static let colorPanelLight = Color(.sRGB, red: 0.980392, green: 0.968627, blue: 0.941176, opacity: 1) // #faf7f0 from oklch(97.6% 0.009 86)
    public static let colorPanelDark = Color(.sRGB, red: 0.105882, green: 0.098039, blue: 0.086275, opacity: 1) // #1b1916 from oklch(21.5% 0.007 75)
    public static func colorPanel(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorPanelDark : colorPanelLight }
    public static let colorPanel2Light = Color(.sRGB, red: 0.917647, green: 0.890196, blue: 0.831373, opacity: 1) // #eae3d4 from oklch(91.7% 0.021 86)
    public static let colorPanel2Dark = Color(.sRGB, red: 0.145098, green: 0.133333, blue: 0.121569, opacity: 1) // #25221f from oklch(25.5% 0.008 75)
    public static func colorPanel2(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorPanel2Dark : colorPanel2Light }
    public static let colorInkLight = Color(.sRGB, red: 0.109804, green: 0.105882, blue: 0.098039, opacity: 1) // #1c1b19 from oklch(22.2% 0.004 85)
    public static let colorInkDark = Color(.sRGB, red: 0.937255, green: 0.921569, blue: 0.882353, opacity: 1) // #efebe1 from oklch(94% 0.014 86)
    public static func colorInk(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorInkDark : colorInkLight }
    public static let colorInk2Light = Color(.sRGB, red: 0.290196, green: 0.274510, blue: 0.247059, opacity: 1) // #4a463f from oklch(39.6% 0.013 82)
    public static let colorInk2Dark = Color(.sRGB, red: 0.741176, green: 0.717647, blue: 0.670588, opacity: 1) // #bdb7ab from oklch(78% 0.018 85)
    public static func colorInk2(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorInk2Dark : colorInk2Light }
    public static let colorInk3Light = Color(.sRGB, red: 0.356863, green: 0.341176, blue: 0.317647, opacity: 1) // #5b5751 from oklch(46% 0.012 82)
    public static let colorInk3Dark = Color(.sRGB, red: 0.635294, green: 0.619608, blue: 0.584314, opacity: 1) // #a29e95 from oklch(70% 0.014 85)
    public static func colorInk3(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorInk3Dark : colorInk3Light }
    public static let colorRuleLight = Color(.sRGB, red: 0.811765, green: 0.776471, blue: 0.705882, opacity: 1) // #cfc6b4 from oklch(82.9% 0.027 85)
    public static let colorRuleDark = Color(.sRGB, red: 0.207843, green: 0.196078, blue: 0.180392, opacity: 1) // #35322e from oklch(32% 0.009 80)
    public static func colorRule(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorRuleDark : colorRuleLight }
    public static let colorRuleStrongLight = Color(.sRGB, red: 0.525490, green: 0.501961, blue: 0.450980, opacity: 1) // #868073 from oklch(60% 0.02 85)
    public static let colorRuleStrongDark = Color(.sRGB, red: 0.435294, green: 0.419608, blue: 0.392157, opacity: 1) // #6f6b64 from oklch(53% 0.012 80)
    public static func colorRuleStrong(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorRuleStrongDark : colorRuleStrongLight }
    public static let colorGridLight = Color(.sRGB, red: 0.894118, green: 0.866667, blue: 0.811765, opacity: 1) // #e4ddcf from oklch(90% 0.02 86)
    public static let colorGridDark = Color(.sRGB, red: 0.101961, green: 0.094118, blue: 0.082353, opacity: 1) // #1a1815 from oklch(21% 0.007 75)
    public static func colorGrid(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorGridDark : colorGridLight }
    public static let colorAccent = Color(.sRGB, red: 0.972549, green: 0.313725, blue: 0.007843, opacity: 1) // #f85002 from oklch(65.9% 0.215 38)
    public static let colorOnAccent = Color(.sRGB, red: 0.109804, green: 0.105882, blue: 0.098039, opacity: 1) // #1c1b19 from oklch(22.2% 0.004 85)
    public static let colorAccentInkLight = Color(.sRGB, red: 0.607843, green: 0.129412, blue: 0.000000, opacity: 1) // #9b2100 from oklch(45% 0.165 38)
    public static let colorAccentInkDark = Color(.sRGB, red: 0.992157, green: 0.494118, blue: 0.286275, opacity: 1) // #fd7e49 from oklch(73% 0.17 42)
    public static func colorAccentInk(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorAccentInkDark : colorAccentInkLight }
    public static let colorAccentWashLight = Color(.sRGB, red: 1.000000, green: 0.862745, blue: 0.784314, opacity: 1) // #ffdcc8 from oklch(92% 0.05 50)
    public static let colorAccentWashDark = Color(.sRGB, red: 0.231373, green: 0.113725, blue: 0.074510, opacity: 1) // #3b1d13 from oklch(27% 0.05 40)
    public static func colorAccentWash(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorAccentWashDark : colorAccentWashLight }
    public static let colorFocusLight = Color(.sRGB, red: 0.741176, green: 0.168627, blue: 0.000000, opacity: 1) // #bd2b00 from oklch(52% 0.19 38)
    public static let colorFocusDark = Color(.sRGB, red: 1.000000, green: 0.549020, blue: 0.337255, opacity: 1) // #ff8c56 from oklch(76% 0.16 44)
    public static func colorFocus(_ scheme: ColorScheme) -> Color { scheme == .dark ? colorFocusDark : colorFocusLight }
    public static let maskSolid = Color(.sRGB, red: 0.000000, green: 0.000000, blue: 0.000000, opacity: 1) // #000000 from oklch(0% 0 0)
    public static let colorDitherDark = Color(.sRGB, red: 0.070588, green: 0.062745, blue: 0.054902, opacity: 1) // #12100e from oklch(17.5% 0.006 75)
    public static let colorDitherLight = Color(.sRGB, red: 0.976471, green: 0.658824, blue: 0.439216, opacity: 1) // #f9a870 from oklch(80% 0.12 55)
    public static let shadowLight = Color(.sRGB, red: 0.109804, green: 0.105882, blue: 0.098039, opacity: 0.12) // #1c1b19 from rgb(28 27 25 / 0.12)
    public static let shadowDark = Color(.sRGB, red: 0.000000, green: 0.000000, blue: 0.000000, opacity: 0.35) // #000000 from rgb(0 0 0 / 0.35)
    public static func shadow(_ scheme: ColorScheme) -> Color { scheme == .dark ? shadowDark : shadowLight }
}

public struct ZZCubic: Sendable {
    public let p1x: Double
    public let p1y: Double
    public let p2x: Double
    public let p2y: Double
}

public enum ZZFont {
    public static let fontDisplayName = "IBM Plex Sans Condensed"
    public static func fontDisplay(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom(fontDisplayName, size: size).weight(weight)
    }
    public static let fontBodyName = "IBM Plex Sans"
    public static func fontBody(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom(fontBodyName, size: size).weight(weight)
    }
    public static let fontMonoName = "IBM Plex Mono"
    public static func fontMono(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        Font.custom(fontMonoName, size: size).weight(weight)
    }
}

public enum ZZTextSize {
    /// 1rem is 16px. The site does not set a root font size.
    public static let text2xs: CGFloat = 12
    public static let textXs: CGFloat = 13
    public static let textSm: CGFloat = 15
    public static let textBase: CGFloat = 17
    public static let textMd: CGFloat = 20
    public static let textLg: CGFloat = 25
    public static let textXlCss = "clamp(1.75rem, 1.6vw + 1.1rem, 2.4rem)"
    public static let textDisplaySCss = "clamp(2rem, 2.4vw + 1.1rem, 3.4rem)"
    public static let textCodeCss = "clamp(1rem, 2.75vw, 2.3rem)"
}

public enum ZZSpace {
    public static let space3xs: CGFloat = 4
    public static let space2xs: CGFloat = 6
    public static let spaceXs: CGFloat = 10
    public static let spaceSm: CGFloat = 16
    public static let spaceMd: CGFloat = 26
    public static let spaceLg: CGFloat = 42
    public static let spaceXl: CGFloat = 68
    public static let space2xl: CGFloat = 110
    public static let gutter: CGFloat = 16
    public static let max: CGFloat = 1280
    public static let ruleHair: CGFloat = 1
    public static let gridCell: CGFloat = 24
    public static let navH: CGFloat = 64
    public static let cut: CGFloat = 16
}

public enum ZZRadius {
    public static let radiusNone: CGFloat = 0
    public static let radiusChip: CGFloat = 3
    public static let pill: CGFloat = 999
}

public enum ZZShadow {
    public static let mountTop: CGFloat = 8
    public static let mountBottom: CGFloat = -8
    public static let mountBlur: CGFloat = 10
    /// Horizontal inset of the mount shadow. The site CSS uses 4%.
    public static let mountInset: Double = 0.04
}

public enum ZZMotion {
    public static let easeOut = ZZCubic(p1x: 0.16, p1y: 1, p2x: 0.3, p2y: 1)
    public static let easeIn = ZZCubic(p1x: 0.7, p1y: 0, p2x: 0.84, p2y: 0)
    public static let easeInOut = ZZCubic(p1x: 0.65, p1y: 0, p2x: 0.35, p2y: 1)
    public static let durMicro: TimeInterval = 0.12
    public static let durShort: TimeInterval = 0.22
    public static let durLong: TimeInterval = 0.42
}

