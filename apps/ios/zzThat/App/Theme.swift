import SwiftUI
import UIKit
import ZZDesign

/// The pinned token colors for one color scheme (runtime.md, Accessibility:
/// colors come from the pinned token files, light and dark).
struct Palette {
    let paper: Color
    let panel: Color
    let panel2: Color
    let ink: Color
    let ink2: Color
    let ink3: Color
    let rule: Color
    let ruleStrong: Color
    let accent: Color
    let onAccent: Color
    let accentInk: Color
    let accentWash: Color
    let focus: Color

    init(_ scheme: ColorScheme) {
        paper = ZZColor.colorPaper(scheme)
        panel = ZZColor.colorPanel(scheme)
        panel2 = ZZColor.colorPanel2(scheme)
        ink = ZZColor.colorInk(scheme)
        ink2 = ZZColor.colorInk2(scheme)
        ink3 = ZZColor.colorInk3(scheme)
        rule = ZZColor.colorRule(scheme)
        ruleStrong = ZZColor.colorRuleStrong(scheme)
        accent = ZZColor.colorAccent
        onAccent = ZZColor.colorOnAccent
        accentInk = ZZColor.colorAccentInk(scheme)
        accentWash = ZZColor.colorAccentWash(scheme)
        focus = ZZColor.colorFocus(scheme)
    }
}

/// The pinned families (design.md, Sync; FR-043). While the pin has no
/// design/fonts, each falls back to the system font of the same size and
/// weight, monospaced for the mono family, so codes stay in a mono face.
/// Both follow the system text size (runtime.md, Accessibility).
enum FontFamily {
    case display
    case body
    case mono

    var pinnedName: String {
        switch self {
        case .display: ZZFont.fontDisplayName
        case .body: ZZFont.fontBodyName
        case .mono: ZZFont.fontMonoName
        }
    }

    /// Whether the pinned family is registered, checked once per process.
    var isPinned: Bool { Self.registered.contains(pinnedName) }

    private static let registered: Set<String> = Set(ZZFontRegistry.families.filter(ZZFontRegistry.isRegistered))

    func font(_ size: CGFloat, weight: Font.Weight, dynamicTypeSize: DynamicTypeSize) -> Font {
        if isPinned {
            // Font.custom scales with the text size on its own.
            switch self {
            case .display: return ZZFont.fontDisplay(size, weight: weight)
            case .body: return ZZFont.fontBody(size, weight: weight)
            case .mono: return ZZFont.fontMono(size, weight: weight)
            }
        }
        let base = self == .mono
            ? UIFont.monospacedSystemFont(ofSize: size, weight: weight.uiFontWeight)
            : UIFont.systemFont(ofSize: size, weight: weight.uiFontWeight)
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        return Font(UIFontMetrics(forTextStyle: .body).scaledFont(for: base, compatibleWith: traits))
    }
}

extension Font.Weight {
    var uiFontWeight: UIFont.Weight {
        switch self {
        case .ultraLight: .ultraLight
        case .thin: .thin
        case .light: .light
        case .medium: .medium
        case .semibold: .semibold
        case .bold: .bold
        case .heavy: .heavy
        case .black: .black
        default: .regular
        }
    }
}

struct FamilyFont: ViewModifier {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let family: FontFamily
    let size: CGFloat
    let weight: Font.Weight

    func body(content: Content) -> some View {
        content.font(family.font(size, weight: weight, dynamicTypeSize: dynamicTypeSize))
    }
}

extension View {
    func zzFont(_ family: FontFamily, _ size: CGFloat, weight: Font.Weight = .regular) -> some View {
        modifier(FamilyFont(family: family, size: size, weight: weight))
    }
}

/// Type roles over the pinned families and sizes.
enum TextRole {
    case heading
    case title
    case body
    case secondary
    case caption
    case label

    var family: FontFamily {
        switch self {
        case .heading, .title: .display
        case .body, .secondary, .caption, .label: .body
        }
    }

    var size: CGFloat {
        switch self {
        case .heading: ZZTextSize.textLg
        case .title: ZZTextSize.textMd
        case .body: ZZTextSize.textBase
        case .secondary, .label: ZZTextSize.textSm
        case .caption: ZZTextSize.textXs
        }
    }

    var weight: Font.Weight {
        switch self {
        case .heading, .title, .label: .semibold
        case .body, .secondary, .caption: .regular
        }
    }

    func color(_ palette: Palette) -> Color {
        switch self {
        case .heading, .title, .body, .label: palette.ink
        case .secondary: palette.ink2
        case .caption: palette.ink3
        }
    }
}

struct RoleText: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    let role: TextRole

    func body(content: Content) -> some View {
        content
            .zzFont(role.family, role.size, weight: role.weight)
            .foregroundStyle(role.color(Palette(scheme)))
            .fixedSize(horizontal: false, vertical: true)
    }
}

extension View {
    func textRole(_ role: TextRole) -> some View {
        modifier(RoleText(role: role))
    }
}

/// Buttons: at least 44 by 44 points (runtime.md, Accessibility).
struct ZZButtonStyle: ButtonStyle {
    enum Kind {
        case primary
        case secondary
        case destructive
    }

    @Environment(\.colorScheme) private var scheme
    @Environment(\.isEnabled) private var isEnabled
    let kind: Kind

    func makeBody(configuration: Configuration) -> some View {
        let palette = Palette(scheme)
        configuration.label
            .zzFont(.body, ZZTextSize.textBase, weight: .semibold)
            .multilineTextAlignment(.center)
            .padding(.horizontal, ZZSpace.spaceSm)
            .padding(.vertical, ZZSpace.spaceXs)
            .frame(maxWidth: .infinity, minHeight: 44)
            .foregroundStyle(foreground(palette))
            .background(background(palette), in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .overlay {
                if kind != .primary {
                    RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                        .strokeBorder(kind == .destructive ? palette.accentInk : palette.ruleStrong,
                                      lineWidth: ZZSpace.ruleHair)
                }
            }
            .opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.45)
            .contentShape(Rectangle())
    }

    private func foreground(_ palette: Palette) -> Color {
        switch kind {
        case .primary: palette.onAccent
        case .secondary: palette.ink
        case .destructive: palette.accentInk
        }
    }

    private func background(_ palette: Palette) -> Color {
        switch kind {
        case .primary: palette.accent
        case .secondary, .destructive: palette.panel
        }
    }
}

extension ButtonStyle where Self == ZZButtonStyle {
    static var zzPrimary: ZZButtonStyle { ZZButtonStyle(kind: .primary) }
    static var zzSecondary: ZZButtonStyle { ZZButtonStyle(kind: .secondary) }
    static var zzDestructive: ZZButtonStyle { ZZButtonStyle(kind: .destructive) }
}

/// A hairline panel, the site's card.
struct PanelModifier: ViewModifier {
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        let palette = Palette(scheme)
        content
            .padding(ZZSpace.spaceSm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                .strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
    }
}

extension View {
    func panel() -> some View { modifier(PanelModifier()) }
}
