import SwiftUI
import ZZDesign

/// Saved (FR-050, zzThis RM-115, design/UX.md Saved): the codes the person
/// saved on this device, newest first. A row opens Resolve. Nothing here
/// calls the server, and no photo is kept.
struct SavedScreen: View {
    let codes: [String]
    let demo: Bool
    var onOpen: (String) -> Void = { _ in }

    var body: some View {
        Screen(demo: demo) {
            Text(.savedTitle).textRole(.heading).accessibilityAddTraits(.isHeader)
            if codes.isEmpty {
                Message(key: .savedEmpty)
            } else {
                LazyVStack(spacing: ZZSpace.spaceXs) {
                    ForEach(codes, id: \.self) { canonical in
                        Button { onOpen(canonical) } label: {
                            CodeText(canonical: canonical, size: ZZTextSize.textBase)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .panel()
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("saved.row.\(canonical)")
                    }
                }
            }
        }
    }
}

/// The entry to Saved at the top of My codes.
struct SavedEntry: View {
    @Environment(\.colorScheme) private var scheme
    let onOpen: () -> Void

    var body: some View {
        let palette = Palette(scheme)
        Button(action: onOpen) {
            LinkRow(text: CopyKey.savedTitle.text, symbol: "chevron.right")
        }
        .buttonStyle(.plain)
        .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
        .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip).strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
        .accessibilityIdentifier("codes.saved")
    }
}
