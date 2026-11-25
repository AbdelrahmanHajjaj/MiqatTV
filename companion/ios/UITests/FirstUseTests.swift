import XCTest

final class FirstUseTests: XCTestCase {
    @MainActor
    func testManualCityWithLocationDenied() {
        let app = launch()
        tap(app.buttons["Get started"])
        XCTAssertTrue(app.staticTexts["Location access is off. Allow it in app settings or choose your city."]
            .waitForExistence(timeout: 10))
        tap(app.descendants(matching: .any)["miqat.location.city"].firstMatch)

        search("London", in: app)
        tap(app.buttons["London"])
        tap(app.buttons["miqat.location.confirm"])
        let continueSetup = app.buttons["Continue"]
        XCTAssertTrue(continueSetup.isEnabled)

        let alerts = app.switches["Prayer alerts"]
        reveal(alerts, in: app)
        XCTAssertEqual(alerts.value as? String, "1")
        alerts.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
        let alertsOff = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == '0'"), object: alerts)
        XCTAssertEqual(XCTWaiter.wait(for: [alertsOff], timeout: 5), .completed)
        tap(app.buttons["Skip"])

        app.terminate()
        app.launch()
        tap(app.buttons["Finish setting up Miqat"])
        reveal(alerts, in: app)
        XCTAssertEqual(alerts.value as? String, "0")
        tap(continueSetup)
        finishSetup(in: app)

        XCTAssertTrue(app.buttons["London"].waitForExistence(timeout: 10))
        reveal(app.buttons["miqat.prayer.0"], in: app)
        reveal(app.staticTexts["Sunrise"], in: app)
        for index in [2, 3, 4, 5] {
            reveal(app.buttons["miqat.prayer.\(index)"], in: app)
        }
        XCTAssertFalse(app.staticTexts["Prayer times unavailable for this location and date."].exists)
    }

    @MainActor
    func testAllowNotificationsDuringSetup() {
        checkNotificationChoice(allow: true)
    }

    @MainActor
    func testDenyNotificationsDuringSetup() {
        checkNotificationChoice(allow: false)
    }

    @MainActor
    func testDeferLocationInArabic() {
        let app = launch(language: "ar", region: "EG")
        XCTAssertTrue(app.staticTexts["مرحبًا بك في ميقات"].waitForExistence(timeout: 10))
        tap(app.buttons["ابدأ الآن"])
        let deferLocation = app.buttons["الإعداد لاحقاً"]
        XCTAssertTrue(deferLocation.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["متابعة"].isEnabled)
        tap(deferLocation)
        finishSetup(in: app, arabic: true)

        app.terminate()
        app.launch()
        let confirmLocation = app.buttons["تأكيد الموقع"]
        XCTAssertTrue(confirmLocation.waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["أكمل إعداد ميقات"].exists)
        let prayer = app.tabBars.buttons["الصلاة"]
        let settings = app.tabBars.buttons["الإعدادات"]
        XCTAssertTrue(prayer.exists && settings.exists)
        XCTAssertGreaterThan(prayer.frame.midX, settings.frame.midX)

        tap(settings)
        tap(app.buttons["تنبيهات الصلاة"])
        let alerts = app.switches["تنبيهات الصلاة"]
        XCTAssertTrue(alerts.waitForExistence(timeout: 10))
        XCTAssertEqual(alerts.value as? String, "0")

        tap(prayer)
        tap(confirmLocation)
        let confirm = app.buttons["miqat.location.confirm"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 10))

        let custom = app.buttons["إحداثيات مخصّصة"]
        let latitude = app.textFields["خط العرض"]
        let longitude = app.textFields["خط الطول"]
        let apply = app.buttons["تطبيق"]
        tap(custom)
        replace(latitude, with: "٣٠٫٠٥٠١")
        replace(longitude, with: "٣١٫٢٤٠١")
        XCTAssertEqual(latitude.value as? String, "٣٠٫٠٥٠١")
        XCTAssertEqual(longitude.value as? String, "٣١٫٢٤٠١")
        XCTAssertTrue(apply.isEnabled)
        tap(apply)
        tap(confirm)
        tap(custom)
        XCTAssertTrue(latitude.waitForExistence(timeout: 10))
        XCTAssertEqual(latitude.value as? String, "30.0501")
        XCTAssertEqual(longitude.value as? String, "31.2401")
    }

    @MainActor
    func testCustomCoordinatesAndTimeZone() {
        let app = launch()
        tap(app.buttons["Get started"])
        let custom = app.buttons["Custom coordinates"]
        let latitude = app.textFields["Latitude"]
        let longitude = app.textFields["Longitude"]
        let apply = app.buttons["Apply"]
        let zonePicker = app.descendants(matching: .any)["miqat.location.timezone"].firstMatch
        let savedZone = app.descendants(matching: .any)["miqat.location.saved-timezone"].firstMatch

        tap(custom)
        XCTAssertTrue(latitude.waitForExistence(timeout: 10))
        let originalLatitude = latitude.value as? String
        let originalZoneLabel = zonePicker.label
        let originalZoneValue = zonePicker.value as? String
        replace(latitude, with: "30.0501")
        tap(zonePicker)
        search("Africa/Cairo", in: app)
        tap(app.buttons["Africa/Cairo"])
        tap(app.buttons["Cancel"])

        tap(custom)
        XCTAssertTrue(latitude.waitForExistence(timeout: 10))
        XCTAssertEqual(latitude.value as? String, originalLatitude)
        XCTAssertEqual(zonePicker.label, originalZoneLabel)
        XCTAssertEqual(zonePicker.value as? String, originalZoneValue)

        replace(latitude, with: "999")
        XCTAssertFalse(apply.isEnabled)
        replace(latitude, with: "30.0501")
        replace(longitude, with: "31.2401")
        XCTAssertTrue(apply.isEnabled)
        tap(zonePicker)
        search("Africa/Cairo", in: app)
        tap(app.buttons["Africa/Cairo"])
        let cairoSelected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", "Africa/Cairo"),
                                                     object: zonePicker)
        XCTAssertEqual(XCTWaiter.wait(for: [cairoSelected], timeout: 10), .completed)
        XCTAssertEqual(latitude.value as? String, "30.0501")
        XCTAssertEqual(longitude.value as? String, "31.2401")
        tap(apply)
        XCTAssertTrue(savedZone.waitForExistence(timeout: 10))
        XCTAssertEqual(savedZone.value as? String, "Africa/Cairo")
        tap(app.buttons["miqat.location.confirm"])
        XCTAssertTrue(app.buttons["Continue"].isEnabled)
        tap(app.buttons["Skip"])

        app.terminate()
        app.launch()
        tap(app.buttons["Finish setting up Miqat"])
        XCTAssertTrue(savedZone.waitForExistence(timeout: 10))
        XCTAssertEqual(savedZone.value as? String, "Africa/Cairo")
        XCTAssertTrue(app.buttons["Continue"].isEnabled)
        tap(custom)
        XCTAssertTrue(latitude.waitForExistence(timeout: 10))
        XCTAssertEqual(latitude.value as? String, "30.0501")
        XCTAssertEqual(longitude.value as? String, "31.2401")
    }

    @MainActor
    private func search(_ text: String, in app: XCUIApplication) {
        let field = app.searchFields.firstMatch
        if !field.isHittable { app.swipeDown() }
        tap(field)
        field.typeText(text)
    }

    @MainActor
    private func replace(_ field: XCUIElement, with text: String) {
        tap(field)
        guard let old = field.value as? String else {
            XCTFail("Missing coordinate field value")
            return
        }
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count) + text)
    }

    @MainActor
    private func checkNotificationChoice(allow: Bool) {
        let app = launch()
        tap(app.buttons["Get started"])
        tap(app.buttons["miqat.location.confirm"])
        tap(app.buttons["Continue"])

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let permission = springboard.alerts.containing(.button, identifier: "Allow").firstMatch
        XCTAssertTrue(permission.waitForExistence(timeout: 15))
        if allow {
            tap(permission.buttons["Allow"])
        } else {
            let deny = NSPredicate(format: "label IN %@", ["Don't Allow", "Don’t Allow"])
            tap(permission.buttons.matching(deny).firstMatch)
        }
        finishSetup(in: app)

        app.terminate()
        app.launch()
        tap(app.tabBars.buttons["Settings"])
        tap(app.buttons["Prayer alerts"])
        let alerts = app.switches["Prayer alerts"]
        XCTAssertTrue(alerts.waitForExistence(timeout: 10))
        XCTAssertEqual(alerts.value as? String, allow ? "1" : "0")
    }

    @MainActor
    private func launch(language: String = "en", region: String = "US") -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(\(language))", "-AppleLocale", "\(language)_\(region)"]
        app.launch()
        return app
    }

    @MainActor
    private func finishSetup(in app: XCUIApplication, arabic: Bool = false) {
        XCTAssertTrue(app.navigationBars[arabic ? "مظهرك" : "Your style"].waitForExistence(timeout: 10))
        let finish = app.buttons[arabic ? "ابدأ استخدام ميقات" : "Start using Miqat"]
        reveal(finish, in: app)
        tap(finish)
    }

    @MainActor
    private func tap(_ element: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(element.waitForExistence(timeout: 10), file: file, line: line)
        element.tap()
    }

    @MainActor
    private func reveal(_ element: XCUIElement, in app: XCUIApplication,
                        file: StaticString = #filePath, line: UInt = #line) {
        for _ in 0..<6 {
            if element.isHittable { return }
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable, file: file, line: line)
    }
}
