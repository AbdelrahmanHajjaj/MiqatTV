import CryptoKit
import Foundation

@main
struct DownloadCheck {
    static func main() async throws {
        let bytes = Data(repeating: 65, count: 1024)
        let digest = SHA256.hash(data: bytes).map { String(format: "%02x", $0) }.joined()
        let stable = "v1.2.0"
        let staging = "v1.2.1-staging.2"

        func asset(_ name: String, _ tag: String, hash: String? = nil, size: Int = 1024) -> [String: Any] {
            var result: [String: Any] = ["name": name, "size": size, "state": "uploaded",
                "browser_download_url": "https://github.com/\(TVRelease.repository)/releases/download/\(tag)/\(name)"]
            if let hash { result["digest"] = "sha256:" + hash }
            return result
        }
        func listing(_ tag: String, _ assets: [[String: Any]], day: Int, prerelease: Bool = false) -> [String: Any] {
            ["tag_name": tag, "assets": assets, "published_at": "2026-11-0\(day)T00:00:00Z",
             "draft": false, "prerelease": prerelease]
        }
        func select(_ listings: [[String: Any]], platform: TVPackagePlatform = .android,
                    channel: TVPackageChannel = .production) throws -> TVRelease {
            try TVRelease.select(from: JSONSerialization.data(withJSONObject: listings), platform: platform, channel: channel)
        }
        func stream(_ data: Data) -> AsyncStream<UInt8> {
            AsyncStream { body in
                for byte in data { body.yield(byte) }
                body.finish()
            }
        }

        let stableAsset = asset("miqat.apk", stable, hash: digest.uppercased())
        let production = listing(stable, [stableAsset], day: 2)
        let beta = listing(staging, [asset("miqat.apk", staging, hash: digest)], day: 4, prerelease: true)
        let nightly = listing("latest-nightly", [asset("miqat.apk", "latest-nightly", hash: digest)], day: 5, prerelease: true)
        let release = try select([beta, production, nightly])
        precondition(release.tag == stable && release.digest == digest)
        let selectedBeta = try select([beta, production, nightly], channel: .staging)
        precondition(selectedBeta.tag == staging)

        let lg = try select([listing(stable, [asset("tv.miqat.webos_1.2.0_all.ipk", stable), asset("SHA256SUMS", stable)], day: 2)],
                            platform: .lg)
        let sums = "\(digest) *./\(lg.name)"
        let expected = try TVRelease.checksum(sums, for: lg.name)
        precondition(lg.digest == nil && lg.checksums != nil && expected == digest)
        precondition((try? TVRelease.checksum(sums + "\n" + sums, for: lg.name)) == nil)
        precondition((try? select([listing(stable, [stableAsset, stableAsset], day: 2)])) == nil)
        precondition((try? select([listing(stable, [asset("miqat.apk", stable, hash: digest, size: 1023)], day: 2)])) == nil)
        var spoofed = stableAsset
        spoofed["browser_download_url"] = "https://github.com/another/project/releases/download/\(stable)/miqat.apk"
        precondition((try? select([listing(stable, [spoofed], day: 2)])) == nil)
        precondition(TVRelease.isTrustedDownload(URL(string: "https://release-assets.githubusercontent.com/example?signature=abc")!))
        precondition(!TVRelease.isTrustedDownload(URL(string: "https://github.com.example.test/package")!))
        precondition(!TVRelease.isTrustedDownload(URL(string: "http://github.com/package")!))

        let verified = try await TVPackageDownload.stage(stream(bytes), release: release, digest: digest) { _ in }
        defer { verified.discard() }
        let onDisk = try Data(contentsOf: verified.url)
        precondition(onDisk == bytes && verified.url.lastPathComponent == "miqat.apk" && verified.version == nil)
        for invalid in [bytes.dropLast(), bytes + Data([0]), Data(repeating: 0, count: 1024)] {
            let rejected = try? await TVPackageDownload.stage(stream(invalid), release: release, digest: digest) { _ in }
            precondition(rejected == nil)
        }
        print("TV release channels, source restrictions, checksums, stream sizes and package integrity passed.")
        try await checkLocalPackage(at: CommandLine.arguments[1], platform: .lg, valid: ["valid.ipk": "0.2.28"])
        try await checkLocalPackage(at: CommandLine.arguments[2], platform: .android,
                                    valid: ["valid.apk": "42", "stored-utf16.apk": "8589934634"])
        let lgFixture = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true).appending(path: "valid.ipk")
        try await LGInstallationCheck.run(lgFixture)
        let apkFixture = URL(fileURLWithPath: CommandLine.arguments[2], isDirectory: true).appending(path: "valid.apk")
        try await AndroidInstallationCheck.run(apkFixture)
        try await AndroidRecoveryCheck.run(exportTo: URL(fileURLWithPath: CommandLine.arguments[4]))
        try await AndroidOptionsCheck.run(URL(fileURLWithPath: CommandLine.arguments[3]))
    }

    static func checkLocalPackage(at path: String, platform: TVPackagePlatform, valid: [String: String]) async throws {
        let fixture = URL(fileURLWithPath: path, isDirectory: true)
        for (name, version) in valid {
            let original = fixture.appending(path: name)
            let ready = try await TVPackageDownload.importLocal(original, platform: platform)
            defer { ready.discard() }
            let expected = try Data(contentsOf: original)
            let imported = try Data(contentsOf: ready.url)
            precondition(ready.tag == version && ready.version == version && ready.url != original && imported == expected)
        }
        for url in try FileManager.default.contentsOfDirectory(at: fixture, includingPropertiesForKeys: nil)
            where valid[url.lastPathComponent] == nil {
            let rejected = try? await TVPackageDownload.importLocal(url, platform: platform)
            precondition(rejected == nil, url.lastPathComponent)
        }
        print("Local \(platform.rawValue) package import checks passed.")
    }
}
