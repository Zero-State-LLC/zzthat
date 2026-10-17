import SwiftUI
import ZZAPI
import ZZDesign

struct AccountScreenState {
    enum DeletePhase: Hashable {
        case idle
        case deleting
        case failure(CopyKey)
    }

    var signedIn: Bool
    var provider: SignInProvider?
    var supportEmail: String?
    var privacyURL: URL?
    var deletePhase: DeletePhase = .idle
    /// `account.unhide_all` ran: `account.unhide_all_done` shows (FR-035).
    var unhideAllDone = false
    /// `account.clear_saved` ran: `account.clear_saved_done` shows (FR-050).
    var clearSavedDone = false
    var loading = false
    var offline = false
    var demo = false
    /// The Terms of Use link (`account.terms`), a build setting. Nil hides it.
    var termsURL: URL?
    /// The signed-in account's id from `GET /v1/me`, shown beside `account.id`.
    var accountID: String?
}

/// Account (design/UX.md): the provider, the account id, Sign out, Delete
/// account, the early-release note, the support contact, privacy and Terms
/// links from build settings (FR-047), the community links (RM-111), and
/// `account.licenses`. No profile form (ZQ7). `account.unhide_all` shows
/// signed in or out, because hiding a code needs no account (FR-035).
struct AccountScreen<SignedOut: View>: View {
    let state: AccountScreenState
    var onSignOut: () -> Void = {}
    var onDelete: () -> Void = {}
    var onLicenses: () -> Void = {}
    var onUnhideAll: () -> Void = {}
    var onClearSaved: () -> Void = {}
    @ViewBuilder var signedOut: SignedOut

    var body: some View {
        Screen(demo: state.demo, loading: state.loading || state.deletePhase == .deleting) {
            if state.signedIn {
                if let provider = state.provider {
                    Text(verbatim: CopyKey.accountSignedInWith.text(provider.copy.text))
                        .textRole(.title)
                        .accessibilityIdentifier("account.signed_in_with")
                }
                if let accountID = state.accountID {
                    AccountIDRow(accountID: accountID)
                }
                Button(action: onSignOut) { Text(.accountSignOut) }
                    .buttonStyle(.zzSecondary)
                    .accessibilityIdentifier("account.sign_out")
                if case .failure(let key) = state.deletePhase { Message(key: key, emphasis: true) }
                Button(action: onDelete) { Text(.accountDelete) }
                    .buttonStyle(.zzDestructive)
                    .disabled(state.offline || state.deletePhase == .deleting)
                    .accessibilityIdentifier("account.delete")
                if state.offline { Text(.errorNoConnection).textRole(.caption) }
            } else {
                signedOut
            }
            if state.unhideAllDone {
                Message(key: .accountUnhideAllDone)
                    .accessibilityIdentifier("account.unhide_all_done")
            } else {
                Button(action: onUnhideAll) { Text(.accountUnhideAll) }
                    .buttonStyle(.zzSecondary)
                    .accessibilityIdentifier("account.unhide_all")
            }
            if state.clearSavedDone {
                Message(key: .accountClearSavedDone)
                    .accessibilityIdentifier("account.clear_saved_done")
            } else {
                Button(action: onClearSaved) { Text(.accountClearSaved) }
                    .buttonStyle(.zzSecondary)
                    .accessibilityIdentifier("account.clear_saved")
            }
            // RM-121, D-2026-10-11-06: a plain note, not a link or a banner.
            Text(.accountEarlyRelease)
                .textRole(.secondary)
                .accessibilityIdentifier("account.early_release")
            AccountLinks(supportEmail: state.supportEmail, privacyURL: state.privacyURL, termsURL: state.termsURL,
                         onLicenses: onLicenses)
        }
    }
}

/// `account.id` with the account's id beside it as plain, selectable text,
/// not inside the string (design/UX.md, Account; D-2026-10-13-08). It is what
/// a person quotes in an access or deletion request.
struct AccountIDRow: View {
    let accountID: String

    var body: some View {
        VStack(alignment: .leading, spacing: ZZSpace.space3xs) {
            Text(.accountId).textRole(.secondary)
            Text(verbatim: accountID)
                .zzFont(.mono, ZZTextSize.textSm)
                .textSelection(.enabled)
                .accessibilityIdentifier("account.id.value")
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("account.id")
    }
}

struct AccountLinks: View {
    @Environment(\.colorScheme) private var scheme
    let supportEmail: String?
    let privacyURL: URL?
    var termsURL: URL?
    let onLicenses: () -> Void

    var body: some View {
        let palette = Palette(scheme)
        VStack(alignment: .leading, spacing: 0) {
            if let supportEmail, let mail = URL(string: "mailto:\(supportEmail)") {
                Link(destination: mail) {
                    LinkRow(text: CopyKey.accountSupport.text(supportEmail), symbol: "envelope")
                }
                .accessibilityIdentifier("account.support")
                Divider().overlay(palette.rule)
            }
            if let privacyURL {
                Link(destination: privacyURL) {
                    LinkRow(text: CopyKey.accountPrivacy.text, symbol: "arrow.up.right")
                }
                .accessibilityIdentifier("account.privacy")
                Divider().overlay(palette.rule)
            }
            if let termsURL {
                Link(destination: termsURL) {
                    LinkRow(text: CopyKey.accountTerms.text, symbol: "arrow.up.right")
                }
                .accessibilityIdentifier("account.terms")
                Divider().overlay(palette.rule)
            }
            // The community links (RM-111): build constants, the same
            // addresses as the zzThis site footer.
            Link(destination: BuildConfig.discordURL) {
                LinkRow(text: CopyKey.accountDiscord.text, symbol: "arrow.up.right")
            }
            .accessibilityIdentifier("account.discord")
            Divider().overlay(palette.rule)
            Link(destination: BuildConfig.instagramURL) {
                LinkRow(text: CopyKey.accountInstagram.text, symbol: "arrow.up.right")
            }
            .accessibilityIdentifier("account.instagram")
            Divider().overlay(palette.rule)
            Button(action: onLicenses) {
                LinkRow(text: CopyKey.accountLicenses.text, symbol: "chevron.right")
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("account.licenses")
        }
        .background(palette.panel, in: RoundedRectangle(cornerRadius: ZZRadius.radiusChip))
        .overlay(RoundedRectangle(cornerRadius: ZZRadius.radiusChip).strokeBorder(palette.rule, lineWidth: ZZSpace.ruleHair))
    }
}

struct LinkRow: View {
    @Environment(\.colorScheme) private var scheme
    let text: String
    let symbol: String

    var body: some View {
        HStack {
            Text(verbatim: text)
                .textRole(.body)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: ZZSpace.spaceXs)
            Image(systemName: symbol)
                .foregroundStyle(Palette(scheme).ink3)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, ZZSpace.spaceSm)
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }
}

extension View {
    /// Delete confirm (design/UX.md): a system dialog; Cancel is the default.
    func deleteConfirm(isPresented: Binding<Bool>, onDelete: @escaping () -> Void) -> some View {
        alert(Text(.deleteTitle), isPresented: isPresented) {
            Button(role: .destructive, action: onDelete) { Text(.deleteAction) }
            Button(role: .cancel) {} label: { Text(.commonCancel) }
        } message: {
            Text(.deleteBody)
        }
    }
}

// MARK: Licenses

/// Licenses (runtime.md, Screens): the bundled `NOTICE` and `OFL.txt`, then
/// the third-party notices the build generates from the pinned packages
/// (T087), all verbatim, the one FR-042 exception.
struct LicensesScreen: View {
    let texts: [String]
    let demo: Bool

    var body: some View {
        Screen(demo: demo) {
            Text(.accountLicenses).textRole(.heading).accessibilityAddTraits(.isHeader)
            // About 200 KB of text: each license is its own lazily drawn block.
            LazyVStack(alignment: .leading, spacing: ZZSpace.spaceSm) {
                ForEach(Array(texts.enumerated()), id: \.offset) { _, text in
                    Text(verbatim: text)
                        .zzFont(.mono, ZZTextSize.textXs)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                        .panel()
                }
            }
        }
    }

    /// The separator line between packages in ThirdPartyNotices.txt.
    static let noticeSeparator = String(repeating: "=", count: 64)

    /// `Licenses/NOTICE` and `Licenses/OFL.txt` from the app bundle, then one
    /// block per package from `Licenses/ThirdPartyNotices.txt`.
    static func bundledTexts(bundle: Bundle = .main) -> [String] {
        let folder = bundle.resourceURL?.appendingPathComponent("Licenses")
        func read(_ name: String) -> String? {
            guard let url = folder?.appendingPathComponent(name) else { return nil }
            return try? String(contentsOf: url, encoding: .utf8)
        }
        let pinned = ["NOTICE", "OFL.txt"].compactMap(read)
        let packages = (read("ThirdPartyNotices.txt") ?? "")
            .components(separatedBy: "\n\(noticeSeparator)\n")
            .map { $0.trimmingCharacters(in: .newlines) }
            .filter { !$0.isEmpty }
        return pinned + packages
    }
}

// MARK: Routes

enum AccountRoute: Hashable {
    case licenses
}

/// The Account sheet, opened from the toolbar of every tab.
struct AccountSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var path: [AccountRoute] = []
    @State private var deletePhase: AccountScreenState.DeletePhase = .idle
    @State private var unhideAllDone = false
    @State private var clearSavedDone = false
    @State private var confirmingDelete = false
    @State private var loading = false
    @State private var signInPhase: SignInScreenState.Phase = .idle

    var body: some View {
        NavigationStack(path: $path) {
            AccountScreen(state: AccountScreenState(signedIn: app.isSignedIn, provider: app.provider,
                                                    supportEmail: app.config.supportEmail,
                                                    privacyURL: app.config.privacyURL, deletePhase: deletePhase,
                                                    unhideAllDone: unhideAllDone, clearSavedDone: clearSavedDone,
                                                    loading: loading, offline: !app.isOnline, demo: app.isDemo,
                                                    termsURL: app.config.termsURL, accountID: app.accountID),
                          onSignOut: { Task { await app.signOut() } },
                          onDelete: { confirmingDelete = true },
                          onLicenses: { path.append(.licenses) },
                          onUnhideAll: {
                              app.unhideAll()
                              unhideAllDone = true
                          },
                          onClearSaved: {
                              app.clearSaved()
                              clearSavedDone = true
                          }) {
                SignInBody(phase: $signInPhase, embedded: true)
            }
            .navigationTitle(Text(.accountTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button { dismiss() } label: { Text(.commonDone) }
                        .accessibilityIdentifier("account.done")
                }
            }
            .deleteConfirm(isPresented: $confirmingDelete, onDelete: delete)
            .navigationDestination(for: AccountRoute.self) { _ in
                LicensesScreen(texts: LicensesScreen.bundledTexts(), demo: app.isDemo)
                    .navigationTitle(Text(.accountLicenses))
                    .navigationBarTitleDisplayMode(.inline)
            }
            .task(id: app.isSignedIn) { await loadAccount() }
        }
    }

    /// `GET /v1/me` names the provider. Offline, the cached provider shows.
    private func loadAccount() async {
        guard app.isSignedIn, app.isOnline else { return }
        loading = true
        if case .success(let account) = await app.service.me() { app.noteAccount(account) }
        loading = false
    }

    private func delete() {
        guard app.isOnline else {
            deletePhase = .failure(.errorNoConnection)
            return
        }
        deletePhase = .deleting
        Task {
            switch await app.deleteAccount() {
            case .success: deletePhase = .idle
            case .failure(.noConnection): deletePhase = .failure(.errorNoConnection)
            case .failure(let failure): deletePhase = .failure(CopyKey(failure.message(for: .deleteAccount)))
            }
        }
    }
}

/// The toolbar item on every tab that opens Account (runtime.md, Navigation).
struct AccountToolbarItem: ToolbarContent {
    @Environment(Sheets.self) private var sheets

    var body: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button { sheets.account = true } label: {
                Image(systemName: "person.crop.circle")
                    .frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel(Text(.navAccount))
            .accessibilityIdentifier("nav.account")
        }
    }
}
