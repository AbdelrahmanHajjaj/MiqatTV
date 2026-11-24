import Foundation
import MiqatKit
import Security

struct AndroidADBIdentity {
    enum Failure: Error { case keychain, signature, publicKey }

    private static let tag = Data("tv.miqat.ios.installation.android.adb".utf8)
    private static let storage = NSLock()
    private let key: SecKey

    static func load() throws -> Self {
        storage.lock()
        defer { storage.unlock() }
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass as String: kSecAttrKeyClassPrivate,
            kSecAttrApplicationTag as String: tag,
            kSecReturnRef as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(attributes as CFDictionary, &item)
        if status == errSecSuccess, let item, CFGetTypeID(item) == SecKeyGetTypeID() {
            return Self(key: item as! SecKey)
        }
        guard status == errSecItemNotFound else { throw Failure.keychain }
        let config: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeySizeInBits as String: 2048,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tag,
                kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            ],
        ]
        guard let key = SecKeyCreateRandomKey(config as CFDictionary, nil) else { throw Failure.keychain }
        return Self(key: key)
    }

    func sign(_ token: Data) throws -> Data {
        guard token.count == 20,
              let signature = SecKeyCreateSignature(key, .rsaSignatureDigestPKCS1v15SHA1, token as CFData, nil),
              CFDataGetLength(signature) == 256 else { throw Failure.signature }
        return signature as Data
    }

    func offer() throws -> Data {
        guard let publicKey = SecKeyCopyPublicKey(key),
              let der = SecKeyCopyExternalRepresentation(publicKey, nil) as Data?,
              let encoded = AdbRsaPublicKey.shared.encode(pkcs1: der.adbBytes) else { throw Failure.publicKey }
        return Data((Data(adbBytes: encoded).base64EncodedString() + " Miqat-iPhone\u{0}").utf8)
    }
}

extension Data {
    var adbBytes: KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        return bytes
    }

    init(adbBytes bytes: KotlinByteArray) {
        self.init(count: Int(bytes.size))
        withUnsafeMutableBytes { (buffer: UnsafeMutableRawBufferPointer) in
            for index in buffer.indices { buffer[index] = UInt8(bitPattern: bytes.get(index: Int32(index))) }
        }
    }
}
