import Foundation

struct TVRemoteState: Decodable {
    struct Input: Decodable { let value: String; let label: String }
    struct Media: Decodable { let kind: String?; let state: String }
    struct Result: Decodable {
        enum Status: String, Decodable { case accepted, busy, unavailable, uncertain }
        let status: Status
    }

    static let unavailable = Self(commands: [], inputs: [], media: nil)
    let commands: [String]
    let inputs: [Input]
    var media: Media?
    var input: String?

    func valid(allowed: [String]) -> Bool {
        Set(commands).count == commands.count && commands.allSatisfy(allowed.contains)
            && inputs.count <= 128 && Set(inputs.map(\.value)).count == inputs.count
            && inputs.allSatisfy { (1...2048).contains($0.value.utf16.count) && (1...255).contains($0.label.utf16.count) }
    }

    func permits(_ command: String, value: String = "", fresh: Bool, allowed: [String]) -> Bool {
        guard allowed.contains(command), commands.contains(command), value.utf16.count <= 2048 else { return false }
        if !value.isEmpty {
            guard command == "device.inputs", inputs.contains(where: { $0.value == value }) else { return false }
        }
        return fresh || command == "radio.stop" || command == "tests.cancel"
    }

    func statusKey(for kind: String) -> String? {
        guard let media, media.kind == kind else { return nil }
        switch media.state {
        case "loading": return "loading_controls"
        case "playing": return "phone_playing"
        case "paused": return kind == "radio" ? "phone_radio_paused" : "phone_paused"
        default: return nil
        }
    }
}
