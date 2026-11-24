import Foundation

enum TVPackagePlatform: String, CaseIterable {
    case android, lg

    fileprivate func accepts(_ name: String) -> Bool {
        switch self {
        case .android: name == "miqat.apk"
        case .lg: name == "miqat-webos.ipk" || name.range(of: "^tv\\.miqat\\.webos_[0-9]+(?:\\.[0-9]+)*_all\\.ipk$", options: .regularExpression) != nil
        }
    }
}

enum TVPackageChannel: String, CaseIterable {
    case production, staging
}

enum TVReleaseError: Error {
    case unavailable, invalidMetadata, untrustedSource, invalidDownload
}

struct TVRelease {
    static let repository = "AbdelrahmanHajjaj/MiqatTV"
    static let list = URL(string: "https://api.github.com/repos/" + repository + "/releases?per_page=30")!
    static let maximumSize = 100 * 1024 * 1024

    let name: String
    let tag: String
    let size: Int
    let url: URL
    let digest: String?
    let checksums: URL?

    private struct Listing: Decodable {
        let tagName: String
        let draft: Bool
        let prerelease: Bool
        let publishedAt: String?
        let assets: [Asset]
    }

    private struct Asset: Decodable {
        let name: String
        let browserDownloadUrl: URL
        let size: Int
        let state: String
        let digest: String?
    }

    static func select(from data: Data, platform: TVPackagePlatform, channel: TVPackageChannel) throws -> Self {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let listings = try decoder.decode([Listing].self, from: data)
        let formatter = ISO8601DateFormatter()
        let release = listings.compactMap { listing -> (Listing, Date)? in
            guard !listing.draft, listing.prerelease == (channel == .staging),
                  let published = listing.publishedAt, let date = formatter.date(from: published),
                  listing.assets.contains(where: { platform.accepts($0.name) }) else { return nil }
            if channel == .staging && listing.tagName.range(of: "^v[0-9]+\\.[0-9]+\\.[0-9]+-staging\\.[1-9][0-9]*$", options: .regularExpression) == nil {
                return nil
            }
            return (listing, date)
        }.max { $0.1 < $1.1 }?.0
        guard let release else { throw TVReleaseError.unavailable }
        let packages = release.assets.filter { platform.accepts($0.name) }
        let sums = release.assets.filter { $0.name == "SHA256SUMS" }
        guard packages.count == 1, let package = packages.first, sums.count <= 1,
              package.state == "uploaded", (1024...maximumSize).contains(package.size),
              release.tagName.range(of: "^[A-Za-z0-9_.-]+$", options: .regularExpression) != nil,
              release.tagName != ".", release.tagName != ".." else { throw TVReleaseError.invalidMetadata }
        let url = try assetURL(package, tag: release.tagName)
        let checksums = try sums.first.map { try assetURL($0, tag: release.tagName) }
        let digest = package.digest.flatMap { $0.isEmpty ? nil : $0 }
        if let digest {
            guard digest.hasPrefix("sha256:"), validDigest(String(digest.dropFirst(7))) else {
                throw TVReleaseError.invalidMetadata
            }
        }
        guard digest != nil || checksums != nil else { throw TVReleaseError.invalidMetadata }
        return .init(name: package.name, tag: release.tagName, size: package.size, url: url,
                     digest: digest.map { String($0.dropFirst(7)).lowercased() }, checksums: checksums)
    }

    private static func assetURL(_ asset: Asset, tag: String) throws -> URL {
        let url = asset.browserDownloadUrl
        let expected = "https://github.com/\(repository)/releases/download/\(tag)/\(asset.name)"
        guard url.absoluteString == expected else { throw TVReleaseError.untrustedSource }
        return url
    }

    static func checksum(_ text: String, for name: String) throws -> String {
        let matching = text.split(whereSeparator: \.isNewline).compactMap { line -> String? in
            let digest = String(line.prefix(64))
            guard validDigest(digest) else { return nil }
            let suffix = line.dropFirst(64)
            guard suffix.hasPrefix("  ") || suffix.hasPrefix(" *") else { return nil }
            let path = suffix.dropFirst(2)
            let filename = path.hasPrefix("./") ? path.dropFirst(2) : path
            return filename == name ? digest.lowercased() : nil
        }
        guard matching.count == 1, let digest = matching.first else { throw TVReleaseError.invalidMetadata }
        return digest
    }

    static func isTrustedDownload(_ url: URL) -> Bool {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false),
              parts.scheme == "https", parts.port == nil, parts.user == nil, parts.password == nil,
              let host = parts.host else { return false }
        return ["api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"].contains(host)
    }

    private static func validDigest(_ value: String) -> Bool {
        value.utf8.count == 64 && value.range(of: "^[A-Fa-f0-9]{64}$", options: .regularExpression) != nil
    }
}
