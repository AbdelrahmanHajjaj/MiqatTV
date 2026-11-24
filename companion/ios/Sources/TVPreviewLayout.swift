struct TVPreviewLayout {
    enum Countdown: Equatable {
        case hidden, mihrabLeft, mihrabRight, stripTop, stripBottom
    }

    let arabic: Bool
    let easternDigits: Bool
    let twelveHourClock: Bool
    let countdown: Countdown
    let prayerIndices: [Int]
    let favouriteRows: Int
    let opacity: Double

    init(values: [String: String], systemLanguage: String?) {
        let language = values["display.language"] ?? "system"
        arabic = language == "ar" || (language == "system" && systemLanguage == "ar")
        let digits = values["display.digits"] ?? "western"
        easternDigits = digits == "eastern" || (digits == "auto" && arabic)
        twelveHourClock = values["display.clock"] == "12"
        switch values["appearance.countdown"] ?? "mihrab" {
        case "hidden":
            countdown = .hidden
        case "compact":
            countdown = values["appearance.compact-position"] == "bottom" ? .stripBottom : .stripTop
        default:
            let side = values["appearance.position"] ?? "auto"
            countdown = side == "left" || (side == "auto" && !arabic) ? .mihrabLeft : .mihrabRight
        }
        switch values["appearance.prayers"] ?? "all" {
        case "none": prayerIndices = []
        case "current": prayerIndices = [2]
        case "next": prayerIndices = [3]
        case "both": prayerIndices = [2, 3]
        default: prayerIndices = [0, 2, 3, 4, 5]
        }
        favouriteRows = values["appearance.favourites"] == "classic" ? 1 : 2
        opacity = values["appearance.mihrab-opacity"].flatMap { Double($0) } ?? 0
    }
}
