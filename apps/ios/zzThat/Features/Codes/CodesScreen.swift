import SwiftUI
import ZZAPI
import ZZDesign

// MARK: My codes

struct CodesScreenState {
    var signedIn: Bool
    var rows: [OwnedCode] = []
    var phase: CodesModel.Phase = .idle
    var demo = false
}

/// My codes (design/UX.md): rows with the code in mono, the record title,
/// and a status word when the code is not active.
struct CodesScreen: View {
    let state: CodesScreenState
    var onSignIn: () -> Void = {}
    var onSelect: (OwnedCode) -> Void = { _ in }
    var onLastRow: () -> Void = {}
    var onRetry: () -> Void = {}
    var onSaved: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .loading) {
            // Saved is on the device, so it shows signed in or out (FR-050).
            SavedEntry(onOpen: onSaved)
            if !state.signedIn {
                Message(key: .codesSignedOut)
                Button(action: onSignIn) { Text(.signinTitle) }
                    .buttonStyle(.zzPrimary)
                    .accessibilityIdentifier("codes.sign_in")
            } else if case .failure(let key) = state.phase {
                Message(key: key)
                RetryButton(waiting: false, action: onRetry)
            } else if state.rows.isEmpty, state.phase == .loaded {
                Message(key: .codesEmpty)
            } else {
                LazyVStack(spacing: ZZSpace.spaceXs) {
                    ForEach(state.rows, id: \.id) { row in
                        Button { onSelect(row) } label: { OwnedCodeRow(row: row) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("codes.row.\(row.id)")
                            .onAppear { if row.id == state.rows.last?.id { onLastRow() } }
                    }
                }
            }
        }
    }
}

struct OwnedCodeRow: View {
    @Environment(\.colorScheme) private var scheme
    let row: OwnedCode

    var body: some View {
        HStack(alignment: .center, spacing: ZZSpace.spaceXs) {
            VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
                CodeText(canonical: row.canonical, checkWord: row.checkWord, size: ZZTextSize.textBase)
                Text(verbatim: row.title).textRole(.body)
                if row.status != .active {
                    Text(row.status.copy).textRole(.caption)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .foregroundStyle(Palette(scheme).ink3)
                .accessibilityHidden(true)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        .panel()
        .accessibilityElement(children: .combine)
    }
}

// MARK: Code detail

struct CodeDetailScreenState {
    var row: OwnedCode?
    var record: OwnerRecord?
    var phase: CodeDetailModel.Phase = .loading
    var revokePhase: CodeDetailModel.RevokePhase = .idle
    var saved = false
    var demo = false
}

/// Code detail (design/UX.md): the code, the title, the body as plain text,
/// and Share, Edit, and Revoke. Revoke shows only while the code is active.
/// A revoked code shows `detail.revoked_note` and no Edit.
struct CodeDetailScreen: View {
    let state: CodeDetailScreenState
    var onEdit: () -> Void = {}
    var onRevoke: () -> Void = {}
    var onRetry: () -> Void = {}

    var body: some View {
        let loading = state.phase == .loading || state.revokePhase == .revoking
        Screen(demo: state.demo, loading: loading) {
            switch state.phase {
            case .notFound:
                Message(key: .resolveNotFound)
            case .failure(let key):
                Message(key: key)
                RetryButton(waiting: false, action: onRetry)
            case .loading, .loaded:
                if let row = state.row {
                    VStack(alignment: .leading, spacing: ZZSpace.spaceXs) {
                        CodeText(canonical: row.canonical, checkWord: row.checkWord)
                        Text(verbatim: state.record?.title ?? row.title)
                            .textRole(.heading)
                            .accessibilityAddTraits(.isHeader)
                        if let body = state.record?.body, !body.isEmpty {
                            Text(verbatim: body).textRole(.body).textSelection(.enabled)
                        }
                        if row.status != .active { Text(row.status.copy).textRole(.caption) }
                    }
                    .panel()
                    if state.saved { Message(key: .editSaved) }
                    if row.status == .revoked { Message(key: .detailRevokedNote) }
                    if case .failure(let key) = state.revokePhase { Message(key: key, emphasis: true) }
                    if state.phase == .loaded {
                        ShareLink(item: row.canonical) { Text(.resolveShare) }
                            .buttonStyle(.zzPrimary)
                            .accessibilityIdentifier("detail.share")
                        if row.status != .revoked {
                            Button(action: onEdit) { Text(.detailEdit) }
                                .buttonStyle(.zzSecondary)
                                .accessibilityIdentifier("detail.edit")
                        }
                        if row.status == .active {
                            Button(action: onRevoke) { Text(.detailRevoke) }
                                .buttonStyle(.zzDestructive)
                                .disabled(state.revokePhase == .revoking)
                                .accessibilityIdentifier("detail.revoke")
                        }
                    }
                }
            }
        }
    }
}

extension View {
    /// Revoke confirm (design/UX.md): a system dialog with the title, the
    /// body, a destructive action, and Cancel, which is the default.
    func revokeConfirm(isPresented: Binding<Bool>, onRevoke: @escaping () -> Void) -> some View {
        alert(Text(.revokeTitle), isPresented: isPresented) {
            Button(role: .destructive, action: onRevoke) { Text(.revokeAction) }
            Button(role: .cancel) {} label: { Text(.commonCancel) }
        } message: {
            Text(.revokeBody)
        }
    }
}

// MARK: Edit record

struct EditRecordScreenState {
    var phase: EditRecordModel.Phase = .loading
    var demo = false
    var offline = false
}

/// Edit record (design/UX.md): the title is required; one Save; a failure
/// keeps the text in the fields.
struct EditRecordScreen: View {
    @Binding var title: String
    @Binding var bodyText: String
    let state: EditRecordScreenState
    var onSave: () -> Void = {}

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .loading || state.phase == .saving) {
            switch state.phase {
            case .notFound:
                Message(key: .resolveNotFound)
            case .loadFailure(let key):
                Message(key: key)
            default:
                LimitedField(label: .createTitleLabel, text: $title, limit: FieldLimits.title,
                             identifier: "edit.title")
                LimitedField(label: .createBodyLabel, text: $bodyText, limit: FieldLimits.body, multiline: true,
                             identifier: "edit.body")
                Text(.createPublicHint).textRole(.secondary)
                if case .failure(let key) = state.phase {
                    Message(key: key, emphasis: true)
                } else if FieldLimits.isBlank(title), state.phase == .editing {
                    Message(key: .createTitleRequired)
                }
                Button(action: onSave) { Text(.editSave) }
                    .buttonStyle(.zzPrimary)
                    .disabled(state.offline || state.phase == .saving || state.phase == .loading)
                    .accessibilityIdentifier("edit.save")
                if state.offline { Text(.errorNoConnection).textRole(.caption) }
            }
        }
    }
}
