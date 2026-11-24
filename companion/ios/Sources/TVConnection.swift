import CryptoKit
import Foundation
import JavaScriptCore
import Security

enum TVConnectionError: Error { case invalid, unconfirmed, credentialStorage }

struct TVProtocol: Decodable {
    let port: Int
    let timeoutMillis: Int
    let pairWindowMillis: Int
    let maxQrBytes: Int
    let maxBodyBytes: Int
    let maxResponseBytes: Int
    let commands: [String]
    static let current: Self = PhoneData.load("lan")
}

struct TVEndpoint: Codable, Equatable {
    let url: URL
    let pin: String

    var valid: Bool {
        let config = TVProtocol.current
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              parts.scheme == "https", parts.port == config.port, let host = parts.host,
              parts.user == nil, parts.password == nil, parts.query == nil, parts.fragment == nil,
              parts.percentEncodedPath.isEmpty, url.absoluteString == "https://\(host):\(config.port)",
              pin.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else { return false }
        return Self.isPrivateIPv4(host)
    }

    static func isPrivateIPv4(_ host: String) -> Bool {
        let octets = host.split(separator: ".", omittingEmptySubsequences: false).compactMap { part -> UInt8? in
            guard let value = UInt8(part), String(value) == part else { return nil }
            return value
        }
        return octets.count == 4 && (octets[0] == 10 || (octets[0] == 172 && (16...31).contains(octets[1]))
            || (octets[0] == 192 && octets[1] == 168))
    }
}

struct TVPairQR: Decodable {
    private let type: String
    private let version: Int
    let url: URL
    let pin: String
    let token: String
    let certificate: String?

    var endpoint: TVEndpoint { .init(url: url, pin: pin) }

    static func decode(_ code: String) throws -> Self {
        guard code.utf8.count <= TVProtocol.current.maxQrBytes else { throw TVConnectionError.invalid }
        var data = Data(code.utf8)
        if code.hasPrefix("MIQ3:") {
            let js = JSContext()!
            js.evaluateScript(String(decoding: PhoneData.resource("PairingQr", "js"), as: UTF8.self))
            guard let decoded = js.objectForKeyedSubscript("PairingQr")?
                .invokeMethod("decode", withArguments: [code])?.toDictionary() else { throw TVConnectionError.invalid }
            data = try JSONSerialization.data(withJSONObject: decoded)
        }
        let pair = try JSONDecoder().decode(Self.self, from: data)
        let fields = (try JSONSerialization.jsonObject(with: data) as? [String: Any])?.keys.sorted()
        let expected = (["type", "version", "url", "pin", "token"] + (pair.version == 1 ? ["certificate"] : [])).sorted()
        guard fields == expected else { throw TVConnectionError.invalid }
        guard pair.type == "miqat-tv-pair", [1, 2, 3].contains(pair.version),
              (pair.version == 3) == code.hasPrefix("MIQ3:"),
              pair.endpoint.valid, tokenShape(pair.token) else { throw TVConnectionError.invalid }
        if pair.version == 1 {
            guard let certificate = pair.certificate, certificate.utf8.count <= 4096,
                  let der = Data(base64Encoded: certificate), der.base64EncodedString() == certificate,
                  hexHash(der) == pair.pin else { throw TVConnectionError.invalid }
        } else if pair.certificate != nil {
            throw TVConnectionError.invalid
        }
        return pair
    }

    static func tokenShape(_ token: String) -> Bool {
        token.utf8.count == 43 && token.range(of: "^[A-Za-z0-9_-]+$", options: .regularExpression) != nil
    }

    static func hexHash(_ bytes: Data) -> String {
        SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
    }
}

struct PairedTV: Codable, Equatable, Identifiable {
    let endpoint: TVEndpoint
    let clientID: String
    let secret: String
    var name: String
    var profile: String
    var pending: Bool
    var id: String { endpoint.pin }
    var credential: String { clientID + "." + secret }
}

enum TVKeychain {
    private static let attributes: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "tv.miqat.ios.companion",
        kSecAttrAccount as String: "paired-tvs",
    ]

    static func load() throws -> [PairedTV] {
        var request = attributes
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &item)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess, let bytes = item as? Data else { throw TVConnectionError.credentialStorage }
        return try JSONDecoder().decode([PairedTV].self, from: bytes)
    }

    static func save(_ value: [PairedTV]) throws {
        let data: [String: Any] = [kSecValueData as String: try JSONEncoder().encode(value)]
        var status = SecItemUpdate(attributes as CFDictionary, data as CFDictionary)
        if status == errSecItemNotFound {
            var record = attributes.merging(data) { $1 }
            record[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(record as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw TVConnectionError.credentialStorage }
    }
}

final class TVConnection: NSObject, URLSessionTaskDelegate {
    private let endpoint: TVEndpoint
    private var session: URLSession?

    @MainActor init(_ endpoint: TVEndpoint) throws {
        guard endpoint.valid else { throw TVConnectionError.invalid }
        self.endpoint = endpoint
        super.init()
        let config = URLSessionConfiguration.ephemeral
        config.tlsMinimumSupportedProtocolVersion = .TLSv12
        config.timeoutIntervalForRequest = Double(TVProtocol.current.timeoutMillis) / 1000
        config.timeoutIntervalForResource = config.timeoutIntervalForRequest
        config.urlCache = nil
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        config.connectionProxyDictionary = [:]
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }

    @MainActor func close() {
        session?.invalidateAndCancel()
        session = nil
    }

    @MainActor func request<Result: Decodable>(_ path: String, as: Result.Type, body: [String: Any]? = nil,
                                    credential: String? = nil) async throws -> Result {
        guard let session, ["pair", "pair-status", "pair-complete", "info", "remote", "command", "settings"].contains(path) else {
            throw TVConnectionError.invalid
        }
        var request = URLRequest(url: endpoint.url.appending(path: "v1", directoryHint: .isDirectory)
            .appending(path: path, directoryHint: .notDirectory))
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let credential {
            let tokens = credential.split(separator: ".")
            guard tokens.count == 2, tokens.allSatisfy({ TVPairQR.tokenShape(String($0)) }) else { throw TVConnectionError.invalid }
            request.setValue("Bearer " + credential, forHTTPHeaderField: "Authorization")
        }
        if let body {
            let bytes = try JSONSerialization.data(withJSONObject: body)
            guard bytes.count <= TVProtocol.current.maxBodyBytes else { throw TVConnectionError.invalid }
            request.httpMethod = "POST"
            request.httpBody = bytes
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              http.mimeType == "application/json", http.expectedContentLength <= Int64(TVProtocol.current.maxResponseBytes) else {
            throw TVConnectionError.unconfirmed
        }
        var result = Data()
        for try await byte in bytes {
            guard result.count < TVProtocol.current.maxResponseBytes else { throw TVConnectionError.invalid }
            result.append(byte)
        }
        return try JSONDecoder().decode(Result.self, from: result)
    }

    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust, challenge.protectionSpace.host == endpoint.url.host,
              SecTrustSetNetworkFetchAllowed(trust, false) == errSecSuccess,
              let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate], chain.count == 1,
              let leaf = chain.first, TVPairQR.hexHash(SecCertificateCopyData(leaf) as Data) == endpoint.pin,
              SecTrustSetPolicies(trust, SecPolicyCreateBasicX509()) == errSecSuccess,
              SecTrustSetAnchorCertificates(trust, [leaf] as CFArray) == errSecSuccess,
              SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess,
              SecTrustEvaluateWithError(trust, nil) else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
