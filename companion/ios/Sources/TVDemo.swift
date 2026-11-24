import Foundation
import MiqatKit

struct TVDemo {
    let endpoint: DemoTv
    private let storageKey: String
    let controls: [TVControl]
    var remote: TVRemoteState

    init(profile: String, controls: [TVControl]) {
        storageKey = "demo-tv-settings-" + profile
        self.controls = controls
        let stored = UserDefaults.standard.dictionary(forKey: storageKey) ?? [:]
        var specs: [SettingSpec] = []
        var values: [String: String] = [:]
        for control in controls where control.available && control.section != "tests" {
            guard control.kind != "action", let defaultValue = control.default else { continue }
            let numeric = control.kind == "number"
            let rules = UiContract.shared
            let spec = SettingSpec(id: control.id, kind: control.kind,
                group: control.section == "appearance" ? "appearance" : "",
                options: control.options.map(\.value),
                minimum: numeric ? rules.minimum(id: control.id) : 0,
                maximum: numeric ? rules.maximum(id: control.id) : Int32(clamping: control.maximum ?? 0),
                step: numeric ? rules.numberStep(id: control.id) : 1)
            guard spec.accepts(value: defaultValue) else { continue }
            let saved = stored[control.id] as? String
            values[control.id] = saved.flatMap { spec.accepts(value: $0) ? $0 : nil } ?? defaultValue
            specs.append(spec)
        }
        let cities = Dictionary(uniqueKeysWithValues: PhoneData.cities.map { city in
            (city.id, ["prayer.method": city.method, "prayer.asr": city.hanafi ? "hanafi" : "shafi"])
        })
        endpoint = DemoTv(specs: specs, initial: values, cityDefaults: cities)
        remote = TVRemoteState(commands: TVProtocol.current.commands,
            inputs: (1...2).map { .init(value: "HDMI_\($0)", label: "HDMI \($0)") }, media: nil)
    }

    func read() -> TVSettingsState {
        TVSettingsState(controls: controls, values: endpoint.read().values,
                        systemLanguage: TVProfileCatalog.data.demoSystemLanguage)
    }

    func apply(expected: [String: String], changes: [String: String]) -> TVSettingsState? {
        guard endpoint.online, !changes.isEmpty, changes.allSatisfy({ id, value in
            endpoint.specs.contains { $0.id == id && $0.accepts(value: value) }
        }) else { return nil }
        let result = endpoint.apply(expected: expected, changes: changes)
        if result.status == "saved" { UserDefaults.standard.set(result.snapshot.values, forKey: storageKey) }
        return TVSettingsState(controls: controls, values: result.snapshot.values, status: result.status,
                               systemLanguage: TVProfileCatalog.data.demoSystemLanguage, conflicts: result.conflicts)
    }

    mutating func send(_ command: String, value: String) {
        switch command {
        case "device.inputs":
            if remote.inputs.contains(where: { $0.value == value }) { remote.input = value }
        case "device.home", "device.exit-input":
            remote.input = nil
        case "radio.play": remote.media = .init(kind: "radio", state: "playing")
        case "tests.adhan-short": remote.media = .init(kind: "adhan", state: "playing")
        case "radio.stop":
            if remote.media?.kind == "radio" { remote.media = nil }
        case "tests.cancel":
            if remote.media?.kind == "adhan" { remote.media = nil }
        default:
            break
        }
    }
}
