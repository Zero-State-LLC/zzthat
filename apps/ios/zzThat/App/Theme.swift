import SwiftUI
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

/// Type roles over the pinned families and sizes. `Font.custom` scales with
/// the system text size, and falls back to the system font while the pin
/// has no design/fonts.
enum TextRole {
    case heading
    case title
    case body
    case secondary
    case caption
    case label

    var font: Font {
        switch self {
        case .heading: ZZFont.fontDisplay(ZZTextSize.textLg, weight: .semibold)
        case .title: ZZFont.fontDisplay(ZZTextSize.textMd, weight: .semibold)
        case .body: ZZFont.fontBody(ZZTextSize.textBase)
        case .secondary: ZZFont.fontBody(ZZTextSize.textSm)
        case .caption: ZZFont.fontBody(ZZTextSize.textXs)
        case .label: ZZFont.fontBody(ZZTextSize.textSm, weight: .semibold)
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
            .font(role.font)
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
            .font(ZZFont.fontBody(ZZTextSize.textBase, weight: .semibold))
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
