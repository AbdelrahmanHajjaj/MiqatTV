import UserNotifications

struct AdhanNotification: Sendable {
    static let testID = "miqat.test"
    let identifier: String
    let prayer: Int
    let time: Double

    init?(_ request: UNNotificationRequest) {
        let data = request.content.userInfo
        guard let prayer = data["adhanPrayer"] as? Int, [0, 2, 3, 4, 5].contains(prayer),
              let time = data["adhanTime"] as? Double, time.isFinite, time > 0 else { return nil }
        identifier = request.identifier
        self.prayer = prayer
        self.time = time
    }

    var claimID: String { "\(identifier):\(time)" }
    var isTest: Bool { identifier == Self.testID }
}
