import Foundation

enum AndroidOptionsCheck {
    static func run(_ file: URL) async throws {
        let text = try String(contentsOf: file, encoding: .utf8)
        let steps = try AndroidTVOptions.parse(text)
        precondition(steps.map(\.id) == [.radio, .startup, .wifi])
        let missingCheck = text.split(whereSeparator: \.isNewline).dropLast().joined(separator: "\n")
        precondition((try? AndroidTVOptions.parse(missingCheck)) == nil)
        let radio = steps[0]
        let configured = TV(codes: [0])
        let alreadyReady = try await radio.apply(using: installer(configured))
        let configuredCalls = await configured.calls
        precondition(alreadyReady && configuredCalls.count == 1 && configuredCalls[0].contains("dumpsys"))

        let supported = TV(codes: [1, 0, 0])
        let applied = try await radio.apply(using: installer(supported))
        let appliedCalls = await supported.calls
        precondition(applied && appliedCalls.count == 3)
        precondition(appliedCalls[1].contains("android.permission.RECORD_AUDIO"))
        precondition(appliedCalls[1].contains("android.permission.WRITE_SECURE_SETTINGS"))

        let unsupported = TV(codes: [1, 0, 1])
        let startup = try await steps[1].apply(using: installer(unsupported))
        let startupCalls = await unsupported.calls
        precondition(!startup && startupCalls[1].contains("APP_AUTO_START"))
        print("Android optional setup fixture verified the bundled steps, idempotence and unsupported actions.")
    }

    private static func installer(_ tv: TV) -> AndroidTVInstallation {
        AndroidTVInstallation { command, input, _ in
            precondition(input == nil)
            return await tv.execute(command)
        }
    }

    private actor TV {
        var codes: [Int]
        var calls: [String] = []

        init(codes: [Int]) { self.codes = codes }

        func execute(_ command: String) -> Data {
            calls.append(command)
            precondition(!codes.isEmpty)
            return Data("MIQAT_RC=\(codes.removeFirst())\r\n".utf8)
        }
    }
}
