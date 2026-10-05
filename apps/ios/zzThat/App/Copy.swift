import Foundation
import SwiftUI
import ZZAPI

/// Every sentence on screen is a catalog lookup (FR-042). Text is shown
/// verbatim, so a catalog string is never read as Markdown.
extension CopyKey {
    var text: String {
        Bundle.main.localizedString(forKey: rawValue, value: nil, table: "Localizable")
    }

    /// `{provider}` and `{email}` became `%@` in the catalog (runtime.md, Localization).
    func text(_ argument: String) -> String {
        String(format: text, locale: Locale.current, argument)
    }

    init(_ message: MessageKey) {
        // Every MessageKey is a copy.json key; CopyKeyTests checks the list.
        self = CopyKey(rawValue: message.rawValue) ?? .errorUnavailable
    }
}

extension Text {
    init(_ key: CopyKey) {
        self.init(verbatim: key.text)
    }
}

extension SignInProvider {
    /// `provider.apple`, `provider.google`, or `provider.dev` (runtime.md, Localization).
    var copy: CopyKey {
        switch self {
        case .apple: .providerApple
        case .google: .providerGoogle
        case .dev: .providerDev
        }
    }
}

extension OwnedCode.StatusPayload {
    var copy: CopyKey {
        switch self {
        case .active: .codesStatusActive
        case .used: .codesStatusUsed
        case .expired: .codesStatusExpired
        case .revoked: .codesStatusRevoked
        }
    }
}

extension ReportReason {
    var copy: CopyKey {
        switch self {
        case .spam: .reportReasonSpam
        case .harassment: .reportReasonHarassment
        case .personalData: .reportReasonPersonalData
        case .other: .reportReasonOther
        }
    }
}
