import PhotosUI
import SwiftUI
import ZZCore
import ZZDesign

/// What the Scan screen shows (runtime.md, Screens: Scan and Pick).
struct ScanScreenState {
    var photo: UIImage?
    var candidates: [ReadCandidate] = []
    var reading = false
    var message: CopyKey?
    var demo = false
    var cameraAvailable = false
}

/// Scan (design/UX.md, Scan with the camera): one still photo, a box on it
/// for each candidate, and a list in reading order. Typing is always one tap
/// away. Several candidates wait for a tap (FR-026); nothing is picked for
/// the person.
struct ScanScreen: View {
    let state: ScanScreenState
    @Binding var pickerItem: PhotosPickerItem?
    var onTakePhoto: () -> Void = {}
    var onTypeInstead: () -> Void = {}
    var onSelect: (ReadCandidate) -> Void = { _ in }

    var body: some View {
        Screen(demo: state.demo, loading: state.reading) {
            if let photo = state.photo {
                PhotoBoxes(image: photo, candidates: state.candidates)
                if !state.candidates.isEmpty {
                    CandidateList(candidates: state.candidates, onSelect: onSelect)
                }
                if let message = state.message { Message(key: message) }
                if !state.reading {
                    CaptureButtons(retake: true, cameraAvailable: state.cameraAvailable, demo: state.demo,
                                   pickerItem: $pickerItem, onTakePhoto: onTakePhoto)
                }
            } else {
                if let message = state.message { Message(key: message) }
                CaptureButtons(retake: false, cameraAvailable: state.cameraAvailable, demo: state.demo,
                               pickerItem: $pickerItem, onTakePhoto: onTakePhoto)
            }
            Button(action: onTypeInstead) { Text(.scanTypeInstead) }
                .buttonStyle(.zzSecondary)
                .accessibilityIdentifier("scan.type_instead")
        }
    }
}

/// `scan.take_photo` opens the camera when the phone has one. The system
/// photo picker is offered on every build (FR-012); on a phone without a
/// camera, such as the simulator, it is the main button.
struct CaptureButtons: View {
    @Environment(\.colorScheme) private var scheme
    let retake: Bool
    let cameraAvailable: Bool
    let demo: Bool
    @Binding var pickerItem: PhotosPickerItem?
    let onTakePhoto: () -> Void

    var body: some View {
        let label: CopyKey = retake ? .scanRetry : .scanTakePhoto
        let palette = Palette(scheme)
        if cameraAvailable || demo {
            HStack(spacing: ZZSpace.spaceXs) {
                Button(action: onTakePhoto) { Text(label) }
                    .buttonStyle(.zzPrimary)
                    .accessibilityIdentifier("scan.take_photo")
                PhotosPicker(selection: $pickerItem, matching: .images, photoLibrary: .shared()) {
                    Image(systemName: "photo.on.rectangle")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(palette.ink)
                        .frame(width: 44, height: 44)
                        .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
                        .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                            .strokeBorder(palette.ruleStrong, lineWidth: ZZSpace.ruleHair))
                }
                .accessibilityLabel(Text(label))
                .accessibilityIdentifier("scan.photo_picker")
            }
        } else {
            PhotosPicker(selection: $pickerItem, matching: .images, photoLibrary: .shared()) {
                Text(label)
            }
            .buttonStyle(.zzPrimary)
            .accessibilityIdentifier("scan.photo_picker")
        }
    }
}

/// Pick (runtime.md, Screens): every candidate in reading order, numbered
/// like its box. A row opens only when the person taps it.
struct CandidateList: View {
    @Environment(\.colorScheme) private var scheme
    let candidates: [ReadCandidate]
    let onSelect: (ReadCandidate) -> Void

    var body: some View {
        let palette = Palette(scheme)
        VStack(alignment: .leading, spacing: ZZSpace.spaceXs) {
            if candidates.count > 1 || candidates.first?.band != .accept {
                Text(.scanPickOne)
                    .textRole(.title)
                    .accessibilityAddTraits(.isHeader)
            }
            VStack(spacing: 0) {
                ForEach(Array(candidates.enumerated()), id: \.offset) { offset, candidate in
                    Button { onSelect(candidate) } label: {
                        CandidateRow(number: offset + 1, candidate: candidate)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("scan.candidate.\(offset + 1)")
                    if offset < candidates.count - 1 { Divider().overlay(palette.rule) }
                }
            }
            .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip).strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
        }
    }
}

struct CandidateRow: View {
    @Environment(\.colorScheme) private var scheme
    let number: Int
    let candidate: ReadCandidate

    var body: some View {
        let palette = Palette(scheme)
        HStack(alignment: .center, spacing: ZZSpace.spaceXs) {
            Text(verbatim: "\(number)")
                .font(ZZFont.fontMono(ZZTextSize.textXs, weight: .semibold))
                .foregroundStyle(palette.onAccent)
                .padding(.horizontal, ZZSpace.space2xs)
                .background(palette.accent, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
            VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
                switch candidate.kind {
                case .code(let code):
                    CodeText(canonical: code.canonical, size: ZZTextSize.textBase)
                case .partial(let text):
                    MonoText(text: text)
                    Text(.scanPartial).textRole(.caption)
                case .bare:
                    MonoText(text: "zz")
                    Text(.scanBare).textRole(.caption)
                case .invalid(_, let text):
                    MonoText(text: text)
                    Text(.typeMalformed).textRole(.caption)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .foregroundStyle(palette.ink3)
                .accessibilityHidden(true)
        }
        .padding(ZZSpace.spaceSm)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// Text read from a photo or typed, shown as written in the mono font.
struct MonoText: View {
    @Environment(\.colorScheme) private var scheme
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(ZZFont.fontMono(ZZTextSize.textBase, weight: .medium))
            .foregroundStyle(Palette(scheme).ink)
            .fixedSize(horizontal: false, vertical: true)
    }
}

// MARK: Type

struct TypeScreenState {
    var message: CopyKey?
    var loading = false
    var demo = false
}

/// Type (US2a): the same parser and the same resolve as Scan.
struct TypeScreen: View {
    @Environment(\.colorScheme) private var scheme
    @Binding var text: String
    let state: TypeScreenState
    var onOpen: () -> Void = {}

    var body: some View {
        let palette = Palette(scheme)
        Screen(demo: state.demo, loading: state.loading) {
            VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
                Text(.typeFieldLabel).textRole(.label)
                TextField("", text: $text, axis: .vertical)
                    .lineLimit(1...4)
                    .font(ZZFont.fontMono(ZZTextSize.textBase))
                    .foregroundStyle(palette.ink)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.asciiCapable)
                    .submitLabel(.go)
                    .onSubmit(onOpen)
                    .padding(ZZSpace.spaceXs)
                    .frame(minHeight: 44)
                    .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
                    .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                        .strokeBorder(palette.ruleStrong, lineWidth: ZZSpace.ruleHair))
                    .accessibilityLabel(Text(.typeFieldLabel))
                    .accessibilityIdentifier("type.field")
            }
            if let message = state.message {
                Message(key: message)
            } else if FieldLimits.isBlank(text) {
                Text(.typeEmpty).textRole(.secondary)
            }
            Button(action: onOpen) { Text(.typeOpen) }
                .buttonStyle(.zzPrimary)
                .disabled(state.loading)
                .accessibilityIdentifier("type.open")
        }
    }
}

// MARK: Clarify

struct ClarifyScreenState {
    var parts: [String]
    var step: ClarifyStep
    var demo = false
}

/// Clarify (spec 004 FR-015 and FR-016): the uncertain word with its list
/// candidates and the part as written, or a check-word mismatch with the
/// words as read. Nothing is corrected for the person.
struct ClarifyScreen: View {
    let state: ClarifyScreenState
    var onPick: (String, Int) -> Void = { _, _ in }
    var onKeep: (Int) -> Void = { _ in }
    var onConfirm: () -> Void = {}
    var onTypeInstead: () -> Void = {}
    var onRetake: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo) {
            switch state.step {
            case .nearWord(let position, let written, let candidates):
                Text(.scanClarifyTitle).textRole(.heading).accessibilityAddTraits(.isHeader)
                PartsText(parts: state.parts, highlight: position)
                ForEach(candidates, id: \.self) { word in
                    Button { onPick(word, position) } label: {
                        Text(verbatim: word).font(ZZFont.fontMono(ZZTextSize.textBase, weight: .semibold))
                    }
                    .buttonStyle(.zzSecondary)
                    .accessibilityIdentifier("clarify.candidate.\(word)")
                }
                VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
                    MonoText(text: written)
                    Button { onKeep(position) } label: { Text(.scanClarifyAsWritten) }
                        .buttonStyle(.zzSecondary)
                        .accessibilityIdentifier("clarify.as_written")
                }
            case .confirm:
                Text(.scanClarifyTitle).textRole(.heading).accessibilityAddTraits(.isHeader)
                PartsText(parts: state.parts, highlight: nil)
                Button(action: onConfirm) { Text(.scanClarifyAsWritten) }
                    .buttonStyle(.zzPrimary)
                    .accessibilityIdentifier("clarify.as_written")
            case .checkMismatch:
                PartsText(parts: state.parts, highlight: nil)
                Message(key: .scanCheckMismatch)
            case .wrongLength:
                PartsText(parts: state.parts, highlight: nil)
                Message(key: .scanWrongLength)
            case .done(let canonical):
                CodeText(canonical: canonical)
            }
            Button(action: onTypeInstead) { Text(.scanTypeInstead) }
                .buttonStyle(.zzSecondary)
                .accessibilityIdentifier("scan.type_instead")
            Button(action: onRetake) { Text(.scanRetry) }
                .buttonStyle(.zzSecondary)
                .accessibilityIdentifier("scan.retry")
        }
    }
}

/// A code as read, with one part marked for the person to check.
struct PartsText: View {
    @Environment(\.colorScheme) private var scheme
    let parts: [String]
    let highlight: Int?

    var body: some View {
        let palette = Palette(scheme)
        Text(attributed(palette))
            .font(ZZFont.fontMono(ZZTextSize.textMd, weight: .medium))
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityLabel(Text(verbatim: Grammar.format(parts)))
    }

    private func attributed(_ palette: Palette) -> AttributedString {
        var text = AttributedString("zz-")
        text.foregroundColor = palette.ink
        for (index, part) in parts.enumerated() {
            var piece = AttributedString(part)
            if highlight == index + 1 {
                piece.foregroundColor = palette.accentInk
                piece.backgroundColor = palette.accentWash
                piece.underlineStyle = .single
            } else {
                piece.foregroundColor = palette.ink
            }
            text += piece
            var separator = AttributedString("-")
            separator.foregroundColor = palette.ink
            text += separator
        }
        var close = AttributedString("zz")
        close.foregroundColor = palette.ink
        text += close
        return text
    }
}
