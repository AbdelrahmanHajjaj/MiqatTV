import Foundation

enum AndroidTVOptions {
    enum Failure: Error { case invalidCatalog }

    enum ID: String, CaseIterable, Hashable, Sendable {
        case radio, startup, wifi

        var confirmation: String {
            switch self {
            case .radio: "tv_adb_option_radio"
            case .startup: "tv_adb_option_startup"
            case .wifi: "tv_adb_option_wifi"
            }
        }
    }

    static let bundled: [Step] = {
        guard let file = Bundle.main.url(forResource: "android-setup-steps", withExtension: "txt"),
              let text = try? String(contentsOf: file, encoding: .utf8) else { return [] }
        return (try? parse(text)) ?? []
    }()

    struct Step: Identifiable, Sendable {
        let id: ID
        let en: String
        let ar: String
        fileprivate let command: String
        fileprivate let check: String

        func apply(using installer: AndroidTVInstallation) async throws -> Bool {
            if try await installer.shellResult(check).code == 0 { return true }
            try Task.checkCancellation()
            _ = try await installer.shellResult(command)
            return try await installer.shellResult(check).code == 0
        }
    }

    static func parse(_ text: String) throws -> [Step] {
        guard text.utf8.count <= 32768 else { throw Failure.invalidCatalog }
        let marker = "# option:"
        let rows = text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline)
        var steps: [Step] = []
        for index in rows.indices where rows[index].hasPrefix(marker) {
            guard let id = ID(rawValue: String(rows[index].dropFirst(marker.count))),
                  index + 2 < rows.count, !steps.contains(where: { $0.id == id }) else {
                throw Failure.invalidCatalog
            }
            let fields = rows[index + 1].split(separator: "|", maxSplits: 3, omittingEmptySubsequences: false)
            let verification = rows[index + 2]
            guard fields.count == 4, fields[0] == "0", fields.dropFirst().allSatisfy({ !$0.isEmpty }),
                  verification.hasPrefix("?|"), verification.count > 2 else { throw Failure.invalidCatalog }
            steps.append(Step(id: id, en: String(fields[1]), ar: String(fields[2]), command: String(fields[3]),
                              check: String(verification.dropFirst(2))))
        }
        guard Set(steps.map(\.id)) == Set(ID.allCases) else { throw Failure.invalidCatalog }
        return steps
    }
}
