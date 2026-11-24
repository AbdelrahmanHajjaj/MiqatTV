struct TVSettingEdit {
    let baseline: [String: String]
    let changes: [String: String]
    var conflicts: [String] = []

    var reviewKeys: [String] { Set(changes.keys).union(conflicts).sorted() }

    func requestedValue(for key: String) -> String? { changes[key] ?? baseline[key] }

    func remaining(against values: [String: String]) -> [String: String] {
        changes.filter { values[$0.key] != $0.value }
    }
}
