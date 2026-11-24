import Foundation
import Security

enum LGInstallCredentials {
    struct Record: Codable {
        let hostKey: Data
        var passphrase: String?
    }

    enum Failure: Error { case storage }

    static func load(_ host: String) throws -> Record? {
        var query = attributes(host)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data, data.count <= 8192 else { throw Failure.storage }
        let record = try JSONDecoder().decode(Record.self, from: data)
        guard (1...4096).contains(record.hostKey.count) else { throw Failure.storage }
        return record
    }

    static func save(_ credential: Record, for host: String) throws {
        let query = attributes(host)
        let data = [kSecValueData as String: try JSONEncoder().encode(credential)]
        var status = SecItemUpdate(query as CFDictionary, data as CFDictionary)
        if status == errSecItemNotFound {
            var entry = query.merging(data) { $1 }
            entry[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            status = SecItemAdd(entry as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw Failure.storage }
    }

    static func forget(_ host: String) throws {
        let status = SecItemDelete(attributes(host) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw Failure.storage }
    }

    private static func attributes(_ host: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "tv.miqat.ios.installation.lg",
            kSecAttrAccount as String: host,
        ]
    }
}
