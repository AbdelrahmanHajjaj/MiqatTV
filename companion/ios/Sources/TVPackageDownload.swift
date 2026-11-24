import CryptoKit
import Foundation

enum TVPackageDownload {
    enum Progress: Sendable {
        case lookup, receiving(Double), verifying
    }

    struct Package: Sendable {
        let url: URL
        let tag: String
        let version: String?

        init(url: URL, tag: String, version: String? = nil) {
            self.url = url
            self.tag = tag
            self.version = version
        }

        func discard() { try? FileManager.default.removeItem(at: url.deletingLastPathComponent()) }
    }

    static func download(platform: TVPackagePlatform, channel: TVPackageChannel,
                         report: @Sendable (Progress) async -> Void) async throws -> Package {
        await report(.lookup)
        let list = try await metadata(TVRelease.list, limit: 2 * 1024 * 1024)
        let release = try TVRelease.select(from: list, platform: platform, channel: channel)
        let digest: String
        if let expected = release.digest {
            digest = expected
        } else if let url = release.checksums {
            let sums = try await metadata(url, limit: 256 * 1024)
            guard let text = String(data: sums, encoding: .utf8) else { throw TVReleaseError.invalidMetadata }
            digest = try TVRelease.checksum(text, for: release.name)
        } else {
            throw TVReleaseError.invalidMetadata
        }

        let client = session()
        defer { client.invalidateAndCancel() }
        let bytes = try await source(release.url, session: client, limit: release.size)
        let ready = try await stage(bytes, release: release, digest: digest, report: report)
        do {
            if platform == .lg {
                return Package(url: ready.url, tag: ready.tag, version: try LgPackage.version(of: ready.url))
            }
            return ready
        } catch {
            ready.discard()
            throw error
        }
    }

    static func stage<Bytes: AsyncSequence>(_ bytes: Bytes, release: TVRelease, digest: String,
                                            report: @Sendable (Progress) async -> Void) async throws -> Package
        where Bytes.Element == UInt8 {
        let output = try workspace(name: release.name)
        let result = Package(url: output, tag: release.tag)
        do {
            try await save(bytes, release: release, digest: digest, output: output, report: report)
            try Task.checkCancellation()
            return result
        } catch {
            result.discard()
            throw error
        }
    }

    static func importLocal(_ source: URL, platform: TVPackagePlatform) async throws -> Package {
        let granted = source.startAccessingSecurityScopedResource()
        defer { if granted { source.stopAccessingSecurityScopedResource() } }
        let extensionName = platform == .lg ? "ipk" : "apk"
        guard source.pathExtension.lowercased() == extensionName,
              try source.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true else {
            throw TVReleaseError.invalidDownload
        }
        let output = try workspace(name: platform == .lg ? "miqat-webos.ipk" : "miqat.apk")
        let staged = Package(url: output, tag: "")
        do {
            let limit = platform == .lg ? LgPackage.maximumSize : AndroidTVPackage.maximumSize
            try copyLocal(source, to: output, limit: limit)
            let version: String
            if platform == .lg {
                version = try LgPackage.version(of: output)
            } else {
                version = try AndroidTVPackage.version(of: output)
            }
            try Task.checkCancellation()
            return Package(url: output, tag: version, version: version)
        } catch {
            staged.discard()
            throw error
        }
    }

    private static func workspace(name: String) throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appending(path: "miqat-" + UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false)
        return folder.appending(path: name, directoryHint: .notDirectory)
    }

    private static func copyLocal(_ source: URL, to output: URL, limit: Int) throws {
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        guard try input.seekToEnd() <= UInt64(limit),
              FileManager.default.createFile(atPath: output.path, contents: nil) else { throw TVReleaseError.invalidDownload }
        try input.seek(toOffset: 0)
        let file = try FileHandle(forWritingTo: output)
        defer { try? file.close() }
        var received = 0
        while let chunk = try input.read(upToCount: 65536), !chunk.isEmpty {
            try Task.checkCancellation()
            received += chunk.count
            guard received <= limit else { throw TVReleaseError.invalidDownload }
            try file.write(contentsOf: chunk)
        }
        try file.synchronize()
    }

    private static func save<Bytes: AsyncSequence>(_ bytes: Bytes, release: TVRelease, digest: String, output: URL,
                                                    report: @Sendable (Progress) async -> Void) async throws
        where Bytes.Element == UInt8 {
        guard FileManager.default.createFile(atPath: output.path, contents: nil) else { throw TVReleaseError.invalidDownload }
        let file = try FileHandle(forWritingTo: output)
        defer { try? file.close() }
        var hash = SHA256()
        var chunk = Data()
        chunk.reserveCapacity(65536)
        var total = 0
        var lastPercent = -1
        await report(.receiving(0))
        for try await byte in bytes {
            total += 1
            guard total <= release.size else { throw TVReleaseError.invalidDownload }
            chunk.append(byte)
            if chunk.count == 65536 {
                try Task.checkCancellation()
                hash.update(data: chunk)
                try file.write(contentsOf: chunk)
                chunk.removeAll(keepingCapacity: true)
                let percent = total * 100 / release.size
                if percent != lastPercent {
                    await report(.receiving(Double(total) / Double(release.size)))
                    lastPercent = percent
                }
            }
        }
        await report(.verifying)
        guard total == release.size else { throw TVReleaseError.invalidDownload }
        hash.update(data: chunk)
        let actual = hash.finalize().map { String(format: "%02x", $0) }.joined()
        guard actual == digest else { throw TVReleaseError.invalidDownload }
        try file.write(contentsOf: chunk)
        try file.synchronize()
    }

    private static func metadata(_ url: URL, limit: Int) async throws -> Data {
        let client = session()
        defer { client.invalidateAndCancel() }
        let bytes = try await source(url, session: client, limit: limit)
        var data = Data()
        for try await byte in bytes {
            guard data.count < limit else { throw TVReleaseError.invalidMetadata }
            data.append(byte)
        }
        try Task.checkCancellation()
        return data
    }

    private static func source(_ url: URL, session: URLSession, limit: Int) async throws -> URLSession.AsyncBytes {
        guard TVRelease.isTrustedDownload(url) else { throw TVReleaseError.untrustedSource }
        var request = URLRequest(url: url)
        request.setValue("Miqat-installer", forHTTPHeaderField: "User-Agent")
        if url.host == "api.github.com" {
            request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        }
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200,
              http.expectedContentLength <= Int64(limit) else { throw TVReleaseError.invalidDownload }
        return bytes
    }

    private static func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 300
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        return URLSession(configuration: config, delegate: Redirects(), delegateQueue: nil)
    }

    private final class Redirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
        private var count = 0

        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
            count += 1
            guard count <= 5, let url = request.url, TVRelease.isTrustedDownload(url) else {
                completionHandler(nil)
                return
            }
            completionHandler(request)
        }
    }
}
