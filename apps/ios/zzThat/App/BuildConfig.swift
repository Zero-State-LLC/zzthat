import Foundation

/// The three builds (runtime.md, Build configurations). The values come from
/// the xcconfig files through Info.plist keys, so nothing here is a secret
/// and Release values are injected at build time.
enum BuildKind: String, Sendable {
    case demo
    case local
    case release
}

struct BuildConfig: Sendable {
    let kind: BuildKind
    /// Nil in Demo, which makes no network call.
    let apiOrigin: URL?
    let supportEmail: String?
    let privacyURL: URL?
    /// The Terms of Use (design/UX.md, Terms), a build setting like the
    /// privacy link. Release injects it. Nil when no URL is configured: then
    /// `terms.link` and `account.terms` are hidden, and the Terms step still
    /// asks for both confirmations.
    let termsURL: URL?
    /// Nil while the Google client id is still a placeholder: GoogleSignIn
    /// raises an exception without a real `GIDClientID` and URL scheme, so
    /// the Google button stays hidden (runtime.md, iOS Google setup).
    let googleClientID: String?

    var isDemo: Bool { kind == .demo }

    /// FR-048: only Local offers developer sign-in. Release compiles it out.
    var offersDeveloperSignIn: Bool {
        #if ZZ_LOCAL
        kind == .local
        #else
        false
        #endif
    }

    static let current = BuildConfig(bundle: .main)

    /// The zzThat community links on Account (design/UX.md, Account: early
    /// release and community links; RM-111). Build constants, not copy: the
    /// same addresses as the zzThis site footer, `src/content/navigation.ts`.
    static let discordURL = URL(string: "https://discord.gg/hwXvVKYzn4")!
    static let instagramURL = URL(string: "https://www.instagram.com/zzthatcom/")!

    init(kind: BuildKind, apiOrigin: URL?, supportEmail: String?, privacyURL: URL?, termsURL: URL? = nil,
         googleClientID: String?) {
        self.kind = kind
        self.apiOrigin = apiOrigin
        self.supportEmail = supportEmail
        self.privacyURL = privacyURL
        self.termsURL = termsURL
        self.googleClientID = googleClientID
    }

    init(bundle: Bundle) {
        func value(_ key: String) -> String? {
            guard let text = bundle.object(forInfoDictionaryKey: key) as? String else { return nil }
            let trimmed = text.trimmingCharacters(in: .whitespaces)
            return trimmed.isEmpty || trimmed.hasPrefix("$(") ? nil : trimmed
        }
        kind = value("ZZBuild").flatMap(BuildKind.init(rawValue:)) ?? .demo
        apiOrigin = kind == .demo ? nil : value("ZZAPIOrigin").flatMap(URL.init(string:))
        supportEmail = value("ZZSupportEmail")
        privacyURL = value("ZZPrivacyURL").flatMap(URL.init(string:))
        termsURL = value("ZZTermsURL").flatMap(URL.init(string:))
        let client = value("GIDClientID")
        let scheme = (bundle.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]])?
            .compactMap { $0["CFBundleURLSchemes"] as? [String] }.joined().first
        if kind != .demo, let client, client != "unset", let scheme, scheme != "unset", !scheme.hasPrefix("$(") {
            googleClientID = client
        } else {
            googleClientID = nil
        }
    }
}
