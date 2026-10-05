import AuthenticationServices
import CryptoKit
import Foundation
import GoogleSignIn
import UIKit
import ZZAPI

/// Sign-in (runtime.md, Auth; zzThis spec 005 FR-020): a server nonce from
/// `POST /v1/auth/nonce`, the platform's own provider sign-in with that
/// nonce, then the provider ID token to `POST /v1/auth/token`. A failure
/// leaves no half session: tokens are kept only after the exchange succeeds.
@MainActor
enum SignIn {
    enum Failure: Error, Hashable {
        /// The person closed the provider sheet. Nothing is shown.
        case cancelled
        /// `signin.failed`, or `error.no_connection` offline.
        case failed(CopyKey)
    }

    static func run(_ provider: SignInProvider, app: AppModel) async -> Result<Void, Failure> {
        if app.isDemo {
            // Demo buttons do not call a provider. The demo API keeps the session in memory.
            return await exchange(provider, idToken: "demo", nonce: "demo-nonce", code: nil, app: app)
        }
        guard app.isOnline else { return .failure(.failed(.errorNoConnection)) }
        let nonce: String
        switch await app.service.nonce() {
        case .success(let value): nonce = value
        case .failure(.noConnection): return .failure(.failed(.errorNoConnection))
        case .failure: return .failure(.failed(.signinFailed))
        }
        switch provider {
        case .apple:
            switch await AppleSignIn.run(nonce: nonce) {
            case .success(let credential):
                return await exchange(.apple, idToken: credential.idToken, nonce: nonce,
                                      code: credential.authorizationCode, app: app)
            case .failure(let failure): return .failure(failure)
            }
        case .google:
            guard app.config.googleClientID != nil, let presenter = UIApplication.shared.topViewController else {
                return .failure(.failed(.signinFailed))
            }
            switch await GoogleSignInFlow.run(nonce: nonce, presenting: presenter) {
            case .success(let idToken): return await exchange(.google, idToken: idToken, nonce: nonce, code: nil, app: app)
            case .failure(let failure): return .failure(failure)
            }
        case .dev:
            #if ZZ_LOCAL
            // Developer sign-in (FR-048, zzThis spec 005 FR-022): the same nonce
            // flow with a fixed ID token and no authorization code.
            return await exchange(.dev, idToken: "dev:local-tester", nonce: nonce, code: nil, app: app)
            #else
            return .failure(.failed(.signinFailed))
            #endif
        }
    }

    private static func exchange(_ provider: SignInProvider, idToken: String, nonce: String, code: String?,
                                 app: AppModel) async -> Result<Void, Failure> {
        switch await app.service.exchange(provider: provider, idToken: idToken, nonce: nonce, authorizationCode: code) {
        case .success:
            app.completeSignIn(provider: provider)
            return .success(())
        case .failure(.noConnection):
            return .failure(.failed(.errorNoConnection))
        case .failure:
            return .failure(.failed(.signinFailed))
        }
    }

    /// The Apple request carries the hex SHA-256 of the server nonce (Auth step 2).
    static func sha256Hex(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Sign in with Apple, with no requested scopes: the app asks for no name
/// and no email (ZQ7). It returns the identity token and authorization code.
@MainActor
final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate,
    ASAuthorizationControllerPresentationContextProviding {
    struct Credential: Sendable {
        let idToken: String
        let authorizationCode: String?
    }

    private var continuation: CheckedContinuation<Result<Credential, SignIn.Failure>, Never>?
    private static var current: AppleSignIn?

    static func run(nonce: String) async -> Result<Credential, SignIn.Failure> {
        let flow = AppleSignIn()
        current = flow
        defer { current = nil }
        return await withCheckedContinuation { continuation in
            flow.continuation = continuation
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = []
            request.nonce = SignIn.sha256Hex(nonce)
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = flow
            controller.presentationContextProvider = flow
            controller.performRequests()
        }
    }

    private func finish(_ result: Result<Credential, SignIn.Failure>) {
        continuation?.resume(returning: result)
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8) else {
            finish(.failure(.failed(.signinFailed)))
            return
        }
        let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        finish(.success(Credential(idToken: token, authorizationCode: code)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: any Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        finish(.failure(cancelled ? .cancelled : .failed(.signinFailed)))
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.foregroundKeyWindow ?? ASPresentationAnchor()
    }
}

/// Google Sign-In with the raw server nonce, no hint, and no extra scopes
/// (Auth step 3). Only the ID token is kept; the Google session is dropped
/// at once, because the app keeps its own session.
@MainActor
enum GoogleSignInFlow {
    static func run(nonce: String, presenting: UIViewController) async -> Result<String, SignIn.Failure> {
        let outcome: (token: String?, cancelled: Bool) = await withCheckedContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presenting, hint: nil, additionalScopes: nil,
                                            nonce: nonce) { result, error in
                let cancelled = (error as? NSError)?.code == GIDSignInError.canceled.rawValue
                continuation.resume(returning: (result?.user.idToken?.tokenString, cancelled))
            }
        }
        GIDSignIn.sharedInstance.signOut()
        if let token = outcome.token { return .success(token) }
        return .failure(outcome.cancelled ? .cancelled : .failed(.signinFailed))
    }
}

extension UIApplication {
    var foregroundKeyWindow: UIWindow? {
        connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)
    }

    var topViewController: UIViewController? {
        var top = foregroundKeyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
