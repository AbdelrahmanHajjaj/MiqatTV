import Foundation

enum AndroidTVCleanup {
    struct Candidate: Identifiable, Sendable {
        let id: String
        let kind: String
        let suggested: Bool
    }

    struct Review: Sendable {
        let candidates: [Candidate]
        let disabledEarlier: [String]
    }

    static let bundled: [Candidate] = {
        guard let file = Bundle.main.url(forResource: "android-clean-catalog", withExtension: "tsv"),
              let text = try? String(contentsOf: file, encoding: .utf8) else { return [] }
        return catalog(text)
    }()

    static func catalog(_ text: String) -> [Candidate] {
        let kinds = Set(["tracking", "ads", "demo", "launcher", "apps", "background"])
        var names = Set<String>()
        return text.split(whereSeparator: \.isNewline).compactMap { line in
            guard !line.hasPrefix("#") else { return nil }
            let parts = line.split(separator: "\t", maxSplits: 4, omittingEmptySubsequences: false)
            guard parts.count >= 4, let count = Int(parts[1]), let kept = Int(parts[2]), count >= 0,
                  kept >= 0, kinds.contains(String(parts[3])) else { return nil }
            let name = String(parts[0])
            guard safePackage(name), names.insert(name).inserted else { return nil }
            let kind = String(parts[3])
            let suggested = count >= 2 && kept == 0 && ["tracking", "ads", "demo"].contains(kind)
            return Candidate(id: name, kind: kind, suggested: suggested)
        }
    }

    static func safePackage(_ name: String) -> Bool {
        guard name.utf8.count <= 255,
              name.range(of: #"\A[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+\z"#, options: .regularExpression) != nil,
              name != AndroidTVPackage.packageID else { return false }
        let protected = #"^(?:com\.android\.(?:systemui|shell|phone|settings|tv\.settings|vending|externalstorage|providers\..*|inputmethod\..*|networkstack.*)|com\.(?:google\.)?android\.(?:permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata|overlay\.modules.*|gms|gsf|inputmethod\..*|tv\.remote\.service))$"#
        let lower = name.lowercased()
        return name.range(of: protected, options: .regularExpression) == nil && !lower.hasPrefix("com.cvte.")
            && !["bluetooth", "inputmethod", "networkstack"].contains(where: { lower.contains($0) })
    }
}
