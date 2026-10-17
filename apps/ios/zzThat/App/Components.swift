import SwiftUI
import ZZCore
import ZZDesign

/// A screen: the paper background, the gutter, and the demo badge on every
/// Demo screen (runtime.md, Screens: Demo).
struct Screen<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    var demo = false
    var loading = false
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ZZSpace.spaceSm) {
                if demo { DemoBadge() }
                content
                if loading { LoadingMark() }
            }
            .padding(.horizontal, ZZSpace.gutter)
            .padding(.vertical, ZZSpace.spaceSm)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Palette(scheme).paper.ignoresSafeArea())
    }
}

/// `common.demo_badge`, shown only when the data is the demo data.
struct DemoBadge: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = Palette(scheme)
        Text(.commonDemoBadge)
            .zzFont(.mono, ZZTextSize.textXs, weight: .medium)
            .foregroundStyle(palette.accentInk)
            .padding(.horizontal, ZZSpace.spaceXs)
            .padding(.vertical, ZZSpace.space3xs)
            .background(palette.accentWash, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .accessibilityIdentifier("demo.badge")
    }
}

/// `common.offline_banner` above a cached record (design/UX.md, Loading and offline).
struct OfflineBanner: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Text(.commonOfflineBanner)
            .textRole(.secondary)
            .padding(ZZSpace.spaceXs)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette(scheme).panel2, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .accessibilityIdentifier("offline.banner")
    }
}

/// One sentence from the error table or an empty state.
struct Message: View {
    @Environment(\.colorScheme) private var scheme
    let key: CopyKey
    var emphasis = false

    var body: some View {
        let palette = Palette(scheme)
        Text(key)
            .textRole(.body)
            .padding(ZZSpace.spaceSm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(emphasis ? palette.accentWash : palette.panel2,
                        in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .accessibilityIdentifier("message.\(key.rawValue)")
    }
}

/// The small progress mark. The previous content stays on screen.
struct LoadingMark: View {
    var body: some View {
        HStack(spacing: ZZSpace.spaceXs) {
            ProgressView()
            Text(.commonLoading).textRole(.caption)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(.commonLoading))
    }
}

/// A canonical code in the mono font, lowercase, wrapping, never truncated.
/// The check word sits in place in the accent ink.
struct CodeText: View {
    @Environment(\.colorScheme) private var scheme
    let canonical: String
    var checkWord: String?
    var size: CGFloat = ZZTextSize.textMd

    var body: some View {
        Text(attributed)
            .zzFont(.mono, size, weight: .medium)
            .lineLimit(nil)
            .fixedSize(horizontal: false, vertical: true)
            .textSelection(.enabled)
            .accessibilityLabel(Text(verbatim: canonical))
    }

    private var attributed: AttributedString {
        let palette = Palette(scheme)
        var text = AttributedString(canonical)
        text.foregroundColor = palette.ink
        if let checkWord, let range = text.range(of: "-\(checkWord)-zz", options: .backwards) {
            let start = text.index(range.lowerBound, offsetByCharacters: 1)
            let end = text.index(start, offsetByCharacters: checkWord.count)
            text[start..<end].foregroundColor = palette.accentInk
        }
        return text
    }
}

/// A labeled single-line or multi-line field that stops input at `limit`
/// Unicode code points after trimming (runtime.md, Screens; DEP-005).
struct LimitedField: View {
    @Environment(\.colorScheme) private var scheme
    let label: CopyKey
    @Binding var text: String
    let limit: Int
    var multiline = false
    var identifier: String
    /// Title and body count after trimming (DEP-005). The report note counts
    /// as typed (the contract's maxLength).
    var countsTrimmed = true

    var body: some View {
        let palette = Palette(scheme)
        VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
            Text(label).textRole(.label)
            // A one-line field still wraps, so a long title stays readable at
            // large text sizes instead of being cut off.
            TextField("", text: $text, axis: .vertical)
                .lineLimit(multiline ? 4...12 : 1...4)
            .zzFont(.body, ZZTextSize.textBase)
            .foregroundStyle(palette.ink)
            .padding(ZZSpace.spaceXs)
            .frame(minHeight: 44)
            .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                .strokeBorder(palette.ruleStrong, lineWidth: ZZSpace.ruleHair))
            .accessibilityLabel(Text(label))
            .accessibilityIdentifier(identifier)
            .onChange(of: text) { _, new in
                // A one-line field takes no line break: Return types a space.
                let flat = multiline ? new : FieldLimits.oneLine(new)
                let limited = FieldLimits.limit(flat, to: limit, trimmed: countsTrimmed)
                if limited != new { text = limited }
            }
        }
    }
}

enum FieldLimits {
    static let title = 120
    static let body = 4000
    static let reportNote = 500

    /// Code points after trimming, as the server counts them (FR-007).
    static func count(_ text: String, trimmed: Bool = true) -> Int {
        (trimmed ? text.trimmingCharacters(in: .whitespacesAndNewlines) : text).unicodeScalars.count
    }

    /// Drops whole characters from the end until the text fits.
    static func limit(_ text: String, to max: Int, trimmed: Bool = true) -> String {
        var text = text
        while count(text, trimmed: trimmed) > max, !text.isEmpty { text.removeLast() }
        return text
    }

    /// Line breaks become spaces, for a one-line field such as the title.
    static func oneLine(_ text: String) -> String {
        String(text.map { $0.isNewline ? " " : $0 })
    }

    static func isBlank(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// The photo with a numbered box around each candidate (design/UX.md, Scan
/// with the camera, step 2). Boxes are normalized, origin at the top left.
struct PhotoBoxes: View {
    @Environment(\.colorScheme) private var scheme
    let image: UIImage
    /// One list of normalized boxes per candidate, numbered in order.
    let groups: [[CGRect]]

    init(image: UIImage, groups: [[CGRect]]) {
        self.image = image
        self.groups = groups
    }

    init(image: UIImage, candidates: [ReadCandidate]) {
        self.init(image: image, groups: candidates.map(\.boxes))
    }

    var body: some View {
        let palette = Palette(scheme)
        Image(uiImage: image)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .overlay {
                GeometryReader { proxy in
                    ForEach(Array(groups.enumerated()), id: \.offset) { offset, boxes in
                        ForEach(Array(boxes.enumerated()), id: \.offset) { boxIndex, box in
                            let rect = CGRect(x: box.minX * proxy.size.width, y: box.minY * proxy.size.height,
                                              width: box.width * proxy.size.width, height: box.height * proxy.size.height)
                            Rectangle()
                                .strokeBorder(palette.accent, lineWidth: 2)
                                .frame(width: rect.width + 6, height: rect.height + 6)
                                .position(x: rect.midX, y: rect.midY)
                            if boxIndex == 0 {
                                Text(verbatim: "\(offset + 1)")
                                    .zzFont(.mono, ZZTextSize.textXs, weight: .semibold)
                                    .foregroundStyle(palette.onAccent)
                                    .padding(.horizontal, 4)
                                    .background(palette.accent)
                                    .position(x: max(rect.minX, 8), y: max(rect.minY - 10, 8))
                            }
                        }
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                .strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
            .accessibilityHidden(true)
    }
}

/// One part of a photo, in memory, scaled to a row's height: how a box is
/// shown before its payload is read. Nothing is cropped or saved; the view
/// draws the same photo through a window.
struct PhotoRegion: View {
    let image: UIImage
    /// Normalized over the photo, origin at the top left.
    let region: CGRect
    var height: CGFloat = 44

    var body: some View {
        let padded = region.intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        let size = image.size
        let scale = padded.height > 0 ? height / (padded.height * size.height) : 1
        let width = min(padded.width * size.width * scale, 240)
        Color.clear
            .frame(width: width, height: height)
            .overlay(alignment: .topLeading) {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: size.width * scale, height: size.height * scale)
                    .offset(x: -padded.minX * size.width * scale, y: -padded.minY * size.height * scale)
            }
            .clipped()
            .accessibilityHidden(true)
    }
}
