import Foundation

enum LGDeveloperMode {
    private final class NoRedirect: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
            completionHandler(nil)
        }
    }

    enum Failure: Error { case keyServer }

    static func connect(address: String, approvedKey: Data?, passphrase: String) async throws -> LGSSH {
        guard let host = LGSSH.privateAddress(address) else { throw LGSSH.Failure.invalidAddress }
        let client = try await LGSSH.open(host: host, expectedKey: approvedKey)
        do {
            var key = try await downloadKey(host)
            defer { key.resetBytes(in: key.startIndex..<key.endIndex) }
            try await client.authenticate(key: key, passphrase: passphrase)
            return client
        } catch {
            client.close()
            throw error
        }
    }

    private static func downloadKey(_ host: String) async throws -> Data {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        config.connectionProxyDictionary = [:]
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 15
        let session = URLSession(configuration: config, delegate: NoRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        guard let url = URL(string: "http://\(host):9991/webos_rsa") else { throw Failure.keyServer }
        let (bytes, response) = try await session.bytes(from: url)
        let limit = 16384
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              http.expectedContentLength <= Int64(limit) else { throw Failure.keyServer }
        var key = Data()
        key.reserveCapacity(limit)
        for try await byte in bytes {
            guard key.count < limit else { throw Failure.keyServer }
            key.append(byte)
        }
        let headers = ["RSA PRIVATE KEY", "OPENSSH PRIVATE KEY", "ENCRYPTED PRIVATE KEY", "PRIVATE KEY"]
        guard headers.contains(where: { key.starts(with: "-----BEGIN \($0)-----".utf8) }) else {
            key.resetBytes(in: key.startIndex..<key.endIndex)
            throw Failure.keyServer
        }
        return key
    }
}
