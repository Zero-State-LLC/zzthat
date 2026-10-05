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
}

/// Create (design/UX.md, Create). Signed out, it asks for sign-in. While
/// `free_public` is false, or mint is not ready, it is Unavailable and shows
/// no code (FR-018). Offline, the title and body stay as a draft labeled
/// "Not a code yet." and mint is refused (FR-022, FR-024).
struct CreateScreen: View {
    @Binding var title: String
    @Binding var bodyText: String
    let state: CreateScreenState
    var onSubmit: () -> Void = {}
    var onSignIn: () -> Void = {}

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
                if case .failure(let key) = state.phase, key != .createOffline || !state.offline {
                    Message(key: key, emphasis: true)
                }
                Button(action: onSubmit) { Text(.createSubmit) }
                    .buttonStyle(.zzPrimary)
                    .disabled(state.offline || state.phase == .minting)
                    .accessibilityHint(state.offline ? Text(.errorNoConnection) : Text(verbatim: ""))
                    .accessibilityIdentifier("create.submit")
                if state.offline { Text(.errorNoConnection).textRole(.caption) }
            }
        }
    }
}

/// `create.draft_label`: what a draft is called. It has no code words and
/// is never shared or resolved (FR-024).
struct DraftLabel: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Text(.createDraftLabel)
            .font(ZZFont.fontMono(ZZTextSize.textXs, weight: .semibold))
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
    var phase: MintedModel.Phase = .idle
    var offline = false
    var demo = false
}

/// Minted code (design/UX.md): the code is the largest text, in the mono
/// font, never truncated. The check word sits in place and has its own label
/// and accessibility label. Re-roll hides at the cap and offline.
struct MintedScreen: View {
    let state: MintedScreenState
    var onReroll: () -> Void = {}
    var onWriteCheck: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .rerolling) {
            Text(.mintedHeading).textRole(.heading).accessibilityAddTraits(.isHeader)
            CodeText(canonical: state.canonical, checkWord: state.checkWord, size: ZZTextSize.textLg)
                .accessibilityIdentifier("minted.code")
            if let checkWord = state.checkWord {
                CheckWordLabel(word: checkWord)
                Text(.mintedCheckWordHelp).textRole(.secondary)
            }
            if case .failure(let key) = state.phase { Message(key: key) }
            if state.capReached {
                if state.phase != .failure(.mintedRerollCap) { Message(key: .mintedRerollCap) }
            } else if !state.offline {
                Button(action: onReroll) { Text(.mintedReroll) }
                    .buttonStyle(.zzSecondary)
                    .disabled(state.phase == .rerolling)
                    .accessibilityIdentifier("minted.reroll")
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
                .font(ZZFont.fontMono(ZZTextSize.textBase, weight: .semibold))
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
    var onSelect: (ReadCandidate) -> Void = { _ in }

    var body: some View {
        Screen(demo: state.demo, loading: state.reading) {
            CodeText(canonical: state.canonical)
            Text(.writecheckOnDevice).textRole(.secondary)
            if let photo = state.photo {
                PhotoBoxes(image: photo, candidates: state.candidates)
                if state.verdict == nil, state.candidates.count > 1 {
                    CandidateList(candidates: state.candidates, onSelect: onSelect)
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
