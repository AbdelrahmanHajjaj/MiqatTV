import Foundation

enum AndroidPersonalizationCheck {
    static func run() async throws {
        let tv = TV()
        let service = client(tv)
        let snapshot = try await service.load()
        var draft = snapshot.current
        draft.city = "alexandria"
        draft.locale = "ar"
        draft.adhan = false
        draft.remindVoice = true
        draft.favourites.reverse()
        try await service.save(draft, from: snapshot)
        let verified = try await service.load()
        precondition(verified.current == draft)
        let edits = await tv.edits
        precondition(edits.count == 1 && edits[0].contains(" && "))

        let concurrent = TV()
        let second = client(concurrent)
        let before = try await second.load()
        await concurrent.changeLocale()
        do {
            try await second.save(draft, from: before)
            preconditionFailure("A stale draft must not write.")
        } catch AndroidTVPersonalization.Failure.changed {}
        let conflictingEdits = await concurrent.edits
        precondition(conflictingEdits.isEmpty)

        let invalid = TV()
        var rejected = before.current
        rejected.favourites = ["com.example.missing"]
        do {
            try await client(invalid).save(rejected, from: before)
            preconditionFailure("A missing app must not write.")
        } catch AndroidTVPersonalization.Failure.invalid {}
        let rejectedEdits = await invalid.edits
        precondition(rejectedEdits.isEmpty)

        let ignored = TV(ignoresWrites: true)
        do {
            try await client(ignored).save(draft, from: before)
            preconditionFailure("The provider must read back the edits.")
        } catch AndroidTVPersonalization.Failure.unconfirmed {}
        print("Android personalisation fixture verified readback, conflict detection and input validation.")
    }

    private static func client(_ tv: TV) -> AndroidTVPersonalization {
        AndroidTVPersonalization(provider: .init { try await tv.execute($0) })
    }

    private actor TV {
        var edits: [String] = []
        var city = "cairo"
        var locale = "system"
        var adhan = true
        var remind = false
        var favourites = ["com.example.first", "com.example.second"]
        let ignoresWrites: Bool

        init(ignoresWrites: Bool = false) { self.ignoresWrites = ignoresWrites }

        func changeLocale() { locale = "en" }

        func execute(_ command: String) throws -> String {
            if command.hasSuffix("/info") {
                return try json(["api": 2, "favourite_slots": 9, "city": city, "locale": locale,
                                 "adhan": adhan, "remind_voice": remind])
            }
            if command.hasSuffix("/cities") {
                return try json([["id":"cairo", "en":"Cairo", "ar":"القاهرة", "countryEn":"Egypt", "countryAr":"مصر"],
                                 ["id":"alexandria", "en":"Alexandria", "ar":"الإسكندرية", "countryEn":"Egypt", "countryAr":"مصر"]])
            }
            if command.hasSuffix("/apps") {
                return try json(favourites.enumerated().map { index, app in
                    ["p": app, "en": "App", "ar": "تطبيق", "fav": index] as [String: Any]
                })
            }
            precondition(command.hasPrefix("content insert --uri content://tv.miqat.home.setup/settings "))
            edits.append(command)
            if !ignoresWrites {
                precondition(command.contains("--bind name:s:city --bind value:s:'alexandria'"))
                precondition(command.contains("--bind name:s:locale --bind value:s:'ar'"))
                precondition(command.contains("--bind name:s:adhan --bind value:s:'off'"))
                precondition(command.contains("--bind name:s:remind_voice --bind value:s:'on'"))
                precondition(command.contains("--bind name:s:favourites --bind value:s:'com.example.second,com.example.first'"))
                city = "alexandria"
                locale = "ar"
                adhan = false
                remind = true
                favourites.reverse()
            }
            return ""
        }

        private func json(_ value: Any) throws -> String {
            "Row: 0 json=" + String(decoding: try JSONSerialization.data(withJSONObject: value), as: UTF8.self)
        }
    }
}
