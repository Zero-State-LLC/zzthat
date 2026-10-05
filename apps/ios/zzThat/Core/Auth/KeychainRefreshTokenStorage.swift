import Foundation
import Security
import ZZAPI

/// The refresh token in the Keychain, readable after the first unlock and
/// never moved to another device (runtime.md, Auth step 6). The access token
/// stays in memory in `TokenStore`.
struct KeychainRefreshTokenStorage: RefreshTokenStorage {
    enum KeychainError: Error {
        case status(OSStatus)
    }

    let service: String
    let account = "refresh-token"

    init(service: String = "com.zer0state.zzthat.session") {
        self.service = service
    }

    private var query: [CFString: Any] {
        [kSecClass: kSecClassGenericPassword, kSecAttrService: service, kSecAttrAccount: account]
    }

    func load() -> String? {
        var query = self.query
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func save(_ token: String) throws {
        delete()
        var attributes = query
        attributes[kSecValueData] = Data(token.utf8)
        attributes[kSecAttrAccessible] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainError.status(status) }
    }

    func delete() {
        SecItemDelete(query as CFDictionary)
    }
}
