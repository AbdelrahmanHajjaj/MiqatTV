struct TVRedirects {
    private(set) var replacements: [String: String]
    private let apps: Set<String>
    private let maximum: Int

    init?(_ value: String, apps: Set<String>, maximum: Int) {
        guard value.utf16.count <= maximum else { return nil }
        self.apps = apps.filter { !$0.isEmpty && !$0.contains(",") && !$0.contains("=") }
        self.maximum = maximum
        replacements = [:]
        if value.isEmpty { return }
        for entry in value.split(separator: ",", omittingEmptySubsequences: false) {
            let fields = entry.split(separator: "=", omittingEmptySubsequences: false).map(String.init)
            guard fields.count == 2, self.apps.contains(fields[0]), self.apps.contains(fields[1]),
                  replacements[fields[0]] == nil else { return nil }
            replacements[fields[0]] = fields[1]
        }
        guard valid else { return nil }
    }

    var value: String {
        replacements.keys.sorted().compactMap { source in
            replacements[source].map { source + "=" + $0 }
        }.joined(separator: ",")
    }

    private var valid: Bool {
        for source in replacements.keys {
            var visited = Set<String>()
            var next = source
            while let target = replacements[next] {
                if !visited.insert(next).inserted { return false }
                next = target
            }
        }
        return true
    }

    mutating func add(from source: String, to target: String) {
        guard apps.contains(source), apps.contains(target), replacements[source] == nil else { return }
        replacements[source] = target
        if !valid || value.utf16.count > maximum { replacements.removeValue(forKey: source) }
    }

    mutating func remove(_ source: String) {
        replacements.removeValue(forKey: source)
    }
}
