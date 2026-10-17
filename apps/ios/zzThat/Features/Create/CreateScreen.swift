import PhotosUI
import SwiftUI
import ZZAPI
import ZZCore
import ZZDesign

// MARK: Create

struct CreateScreenState {
    enum Gate: Hashable {
        case loading
        case signedOut
        case unavailable
        case form
    }

    var gate: Gate
    var phase: CreateModel.Phase = .editing
    var offline = false
    var demo = false
    /// The Terms step shows above Get a code (design/UX.md, Terms).
    var termsNeeded = false
    var termsURL: URL?
    /// A 429 `Retry-After` wait is on: Get a code stays off (FR-033).
    var waiting = false
}

/// Create (design/UX.md, Create). Signed out, it asks for sign-in. While
/// `free_public` is false, or mint is not ready, it is Unavailable and shows
/// no code (FR-018). Offline, the title and body stay as a draft labeled
/// "Not a code yet." and mint is refused (FR-022, FR-024).
struct CreateScreen: View {
    @Binding var title: String
    @Binding var bodyText: String
    let state: CreateScreenState
    var ageConfirmed: Binding<Bool> = .constant(false)
    var termsAccepted: Binding<Bool> = .constant(false)
    var onSubmit: () -> Void = {}
    var onSignIn: () -> Void = {}

    /// Get a code waits for both Terms confirmations when the step shows.
    private var termsDone: Bool {
        !state.termsNeeded || (ageConfirmed.wrappedValue && termsAccepted.wrappedValue)
    }

    var body: some View {
        switch state.gate {
        case .unavailable:
            UnavailableScreen(demo: state.demo)
        case .loading:
            Screen(demo: state.demo, loading: true) { EmptyView() }
        case .signedOut:
            Screen(demo: state.demo) {
                Message(key: .createSignedOut)
                Button(action: onSignIn) { Text(.signinTitle) }
                    .buttonStyle(.zzPrimary)
                    .accessibilityIdentifier("create.sign_in")
            }
        case .form:
            Screen(demo: state.demo, loading: state.phase == .minting) {
                if state.offline {
                    DraftLabel()
                    Message(key: .createOffline)
                }
                LimitedField(label: .createTitleLabel, text: $title, limit: FieldLimits.title,
                             identifier: "create.title")
                LimitedField(label: .createBodyLabel, text: $bodyText, limit: FieldLimits.body, multiline: true,
                             identifier: "create.body")
                Text(.createPublicHint).textRole(.secondary)
                if state.termsNeeded {
                    TermsStep(ageConfirmed: ageConfirmed, termsAccepted: termsAccepted, termsURL: state.termsURL)
                }
                if case .failure(let key) = state.phase, key != .createOffline || !state.offline {
                    Message(key: key, emphasis: true)
                }
                Button(action: onSubmit) { Text(.createSubmit) }
                    .buttonStyle(.zzPrimary)
                    .disabled(state.offline || state.phase == .minting || state.waiting || !termsDone)
                    .accessibilityHint(state.offline ? Text(.errorNoConnection) : Text(verbatim: ""))
                    .accessibilityIdentifier("create.submit")
                if state.offline { Text(.errorNoConnection).textRole(.caption) }
            }
        }
    }
}

/// The Terms step (design/UX.md, Terms; D-2026-10-13-04): one step before
/// the person's first create on this device, not two screens. The person
/// confirms they are 18 or older (`terms.age_confirm`) and accepts the Terms
/// (`terms.accept`); `terms.link` opens the Terms of Use. Without a configured
/// Terms URL the link is hidden.
struct TermsStep: View {
    @Environment(\.colorScheme) private var scheme
    @Binding var ageConfirmed: Bool
    @Binding var termsAccepted: Bool
    let termsURL: URL?

    var body: some View {
        let palette = Palette(scheme)
        VStack(alignment: .leading, spacing: 0) {
            TermsCheck(key: .termsAgeConfirm, isOn: $ageConfirmed)
            Divider().overlay(palette.rule)
            TermsCheck(key: .termsAccept, isOn: $termsAccepted)
            if let termsURL {
                Divider().overlay(palette.rule)
                Link(destination: termsURL) {
                    LinkRow(text: CopyKey.termsLink.text, symbol: "arrow.up.right")
                }
                .accessibilityIdentifier("terms.link")
            }
        }
        .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
        .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip).strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
    }
}

/// One Terms confirmation: a check box row, read by VoiceOver as a toggle.
struct TermsCheck: View {
    @Environment(\.colorScheme) private var scheme
    let key: CopyKey
    @Binding var isOn: Bool

    var body: some View {
        let palette = Palette(scheme)
        Button {
            isOn.toggle()
        } label: {
            HStack {
                Text(key).textRole(.body)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .foregroundStyle(isOn ? palette.accentInk : palette.ruleStrong)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, ZZSpace.spaceSm)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? [.isToggle, .isSelected] : [.isToggle])
        .accessibilityIdentifier(key.rawValue)
    }
}

/// `create.draft_label`: what a draft is called. It has no code words and
/// is never shared or resolved (FR-024).
struct DraftLabel: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Text(.createDraftLabel)
            .zzFont(.mono, ZZTextSize.textXs, weight: .semibold)
            .foregroundStyle(Palette(scheme).ink2)
            .padding(.horizontal, ZZSpace.spaceXs)
            .padding(.vertical, ZZSpace.space3xs)
            .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip)
                .strokeBorder(Palette(scheme).ruleStrong, style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            .accessibilityIdentifier("create.draft_label")
    }
}

/// Unavailable (FR-018): Create stays on screen and invents no code.
struct UnavailableScreen: View {
    let demo: Bool

    var body: some View {
        Screen(demo: demo) {
            Message(key: .createUnavailable)
        }
    }
}

// MARK: Minted code

struct MintedScreenState {
    var canonical: String
    var checkWord: String?
    var capReached: Bool
    /// The person kept these words (RM-113): the picker closes, locally.
    var kept = false
    var phase: MintedModel.Phase = .idle
    var offline = false
    var demo = false
    /// A 429 `Retry-After` wait is on: Get different words stays off (FR-033).
    var waiting = false
}

/// Minted code (design/UX.md): the code is the largest text, in the mono
/// font, never truncated. The check word sits in place and has its own label
/// and accessibility label. Re-roll hides at the cap and offline.
struct MintedScreen: View {
    let state: MintedScreenState
    var onReroll: () -> Void = {}
    var onKeep: () -> Void = {}
    var onWriteCheck: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .rerolling) {
            // The code is the largest text on the screen (design/UX.md, Minted code).
            Text(.mintedHeading).textRole(.title).accessibilityAddTraits(.isHeader)
            CodeText(canonical: state.canonical, checkWord: state.checkWord, size: ZZTextSize.textLg)
                .accessibilityIdentifier("minted.code")
            if let checkWord = state.checkWord {
                CheckWordLabel(word: checkWord)
                Text(.mintedCheckWordHelp).textRole(.secondary)
            }
            if case .failure(let key) = state.phase { Message(key: key) }
            if state.capReached || state.kept {
                if state.phase != .failure(.mintedRerollCap) { Message(key: .mintedRerollCap) }
            } else if !state.offline {
                // The suggested-word picker (RM-113): the server chose these
                // words; keep them, or get different ones. No code string is sent.
                Text(.mintedPickHelp).textRole(.secondary)
                Button(action: onReroll) { Text(.mintedReroll) }
                    .buttonStyle(.zzSecondary)
                    .disabled(state.phase == .rerolling || state.waiting)
                    .accessibilityIdentifier("minted.reroll")
                Button(action: onKeep) { Text(.mintedKeep) }
                    .buttonStyle(.zzSecondary)
                    .disabled(state.phase == .rerolling)
                    .accessibilityIdentifier("minted.keep")
            }
            Button(action: onWriteCheck) { Text(.mintedWriteCheck) }
                .buttonStyle(.zzPrimary)
                .accessibilityIdentifier("minted.write_check")
        }
    }
}

/// The check word as its own label (runtime.md, Accessibility).
struct CheckWordLabel: View {
    @Environment(\.colorScheme) private var scheme
    let word: String

    var body: some View {
        HStack(spacing: ZZSpace.spaceXs) {
            Text(.mintedCheckWordLabel).textRole(.label)
            Text(verbatim: word)
                .zzFont(.mono, ZZTextSize.textBase, weight: .semibold)
                .foregroundStyle(Palette(scheme).accentInk)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(.mintedCheckWordLabel))
        .accessibilityValue(Text(verbatim: word))
        .accessibilityIdentifier("minted.check_word")
    }
}

// MARK: Write check

struct WriteCheckScreenState {
    var canonical: String
    var photo: UIImage?
    /// The boxes to pick from before any payload is read (FR-052).
    var visual: [VisualCandidate] = []
    var candidates: [ReadCandidate] = []
    var reading = false
    var verdict: WriteCheckModel.Verdict?
    var message: CopyKey?
    var demo = false
    var cameraAvailable = false
}

/// Write check (design/UX.md): the photo of the handwriting is read on the
/// phone and compared with the minted code. `writecheck.on_device` is always
/// visible. Nothing is uploaded.
struct WriteCheckScreen: View {
    let state: WriteCheckScreenState
    @Binding var pickerItem: PhotosPickerItem?
    var onTakePhoto: () -> Void = {}
    var onSelectBox: (VisualCandidate) -> Void = { _ in }
    var onSelect: (ReadCandidate) -> Void = { _ in }

    var body: some View {
        Screen(demo: state.demo, loading: state.reading) {
            CodeText(canonical: state.canonical)
            Text(.writecheckOnDevice).textRole(.secondary)
            if let photo = state.photo {
                if state.candidates.isEmpty {
                    PhotoBoxes(image: photo, groups: state.visual.map { [$0.outline] })
                } else {
                    PhotoBoxes(image: photo, candidates: state.candidates)
                }
                if state.verdict == nil, state.candidates.count > 1 {
                    CandidateList(candidates: state.candidates, onSelect: onSelect)
                } else if state.verdict == nil, state.candidates.isEmpty, !state.visual.isEmpty, !state.reading,
                          state.message == nil {
                    BoxList(photo: photo, candidates: state.visual, onSelect: onSelectBox)
                }
            }
            switch state.verdict {
            case .match: Message(key: .writecheckMatch)
            case .mismatch: Message(key: .writecheckMismatch, emphasis: true)
            case nil: if let message = state.message { Message(key: message) }
            }
            if !state.reading {
                CaptureButtons(retake: state.photo != nil, cameraAvailable: state.cameraAvailable, demo: state.demo,
                               pickerItem: $pickerItem, onTakePhoto: onTakePhoto)
            }
        }
    }
}
