import AuthenticationServices
import GoogleSignIn
import SwiftUI
import ZZAPI
import ZZDesign

struct SignInScreenState {
    enum Phase: Hashable {
        case idle
        case signingIn
        case failure(CopyKey)
    }

    var providers: [SignInProvider]
    var phase: Phase = .idle
    var offline = false
    var demo = false
    var loadingProviders = false
}

/// Sign in (design/UX.md): `signin.why`, then the provider buttons the
/// platform draws: Sign in with Apple and Google (FR-040). Local builds add
/// `signin.dev`. Demo buttons do not call a provider.
struct SignInScreen: View {
    @Environment(\.colorScheme) private var scheme
    let state: SignInScreenState
    var onSignIn: (SignInProvider) -> Void = { _ in }

    var body: some View {
        Screen(demo: state.demo, loading: state.phase == .signingIn || state.loadingProviders) {
            Text(.signinTitle).textRole(.heading).accessibilityAddTraits(.isHeader)
            Text(.signinWhy).textRole(.body)
            if case .failure(let key) = state.phase { Message(key: key, emphasis: true) }
            if state.offline { Message(key: .errorNoConnection) }
            VStack(spacing: ZZSpace.spaceXs) {
                ForEach(state.providers, id: \.self) { provider in
                    switch provider {
                    case .apple:
                        AppleButton { onSignIn(.apple) }
                            .id(scheme)
                            .frame(height: 48)
                            .accessibilityIdentifier("signin.apple")
                    case .google:
                        GoogleButton { onSignIn(.google) }
                            .frame(height: 48)
                            .accessibilityIdentifier("signin.google")
                    case .dev:
                        Button { onSignIn(.dev) } label: { Text(.signinDev) }
                            .buttonStyle(.zzSecondary)
                            .accessibilityIdentifier("signin.dev")
                    }
                }
            }
            .disabled(state.offline || state.phase == .signingIn)
        }
    }
}

/// Apple's own button (Human Interface Guidelines, Sign in with Apple). Its
/// tap starts the nonce flow; the request is built after the nonce arrives.
struct AppleButton: UIViewRepresentable {
    @Environment(\.colorScheme) private var scheme
    let action: () -> Void

    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(authorizationButtonType: .signIn,
                                                  authorizationButtonStyle: scheme == .dark ? .white : .black)
        button.addTarget(context.coordinator, action: #selector(Coordinator.tap), for: .touchUpInside)
        return button
    }

    func updateUIView(_ button: ASAuthorizationAppleIDButton, context: Context) {
        context.coordinator.action = action
    }

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc func tap() { action() }
    }
}

/// Google's own button from the GoogleSignIn product. It only draws; the
/// tap starts the nonce flow, and only then does GoogleSignIn run.
struct GoogleButton: UIViewRepresentable {
    @Environment(\.colorScheme) private var scheme
    let action: () -> Void

    func makeUIView(context: Context) -> GIDSignInButton {
        let button = GIDSignInButton()
        button.style = .wide
        button.addTarget(context.coordinator, action: #selector(Coordinator.tap), for: .touchUpInside)
        return button
    }

    func updateUIView(_ button: GIDSignInButton, context: Context) {
        button.colorScheme = scheme == .dark ? .dark : .light
        context.coordinator.action = action
    }

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) {
            self.action = action
        }

        @objc func tap() { action() }
    }
}

/// The live Sign in sheet: the providers discovery lists, the ones this
/// build can run, then the flow. A success closes the sheet.
struct SignInRoute: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var phase: SignInScreenState.Phase = .idle
    @State private var loadingProviders = false

    var body: some View {
        NavigationStack {
            SignInBody(phase: $phase, loadingProviders: loadingProviders, onDone: { dismiss() })
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button { dismiss() } label: { Text(.commonCancel) }
                    }
                }
        }
        .task {
            if app.discovery == nil, app.isOnline {
                loadingProviders = true
                await app.refreshDiscovery()
                loadingProviders = false
            }
        }
    }
}

/// The sign-in content, shared by the Sign in sheet and a signed-out Account.
struct SignInBody: View {
    @Environment(AppModel.self) private var app
    @Binding var phase: SignInScreenState.Phase
    var loadingProviders = false
    var onDone: () -> Void = {}

    var body: some View {
        SignInScreen(state: SignInScreenState(providers: SignInProviders.shown(app: app), phase: phase,
                                              offline: !app.isOnline, demo: app.isDemo,
                                              loadingProviders: loadingProviders),
                     onSignIn: signIn)
    }

    private func signIn(_ provider: SignInProvider) {
        phase = .signingIn
        Task {
            switch await SignIn.run(provider, app: app) {
            case .success:
                phase = .idle
                onDone()
            case .failure(.cancelled):
                phase = .idle
            case .failure(.failed(let key)):
                phase = .failure(key)
            }
        }
    }
}

enum SignInProviders {
    /// Discovery decides the buttons (FR-040). iOS draws Apple and Google;
    /// Google only once its client id is set; `dev` only in Local (FR-048).
    @MainActor
    static func shown(app: AppModel) -> [SignInProvider] {
        var shown = app.authProviders.filter { provider in
            switch provider {
            case .apple: true
            case .google: app.isDemo || app.config.googleClientID != nil
            case .dev: false
            }
        }
        if app.config.offersDeveloperSignIn { shown.append(.dev) }
        return shown
    }
}
