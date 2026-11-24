import XCTest

final class FirstUseTests: XCTestCase {
    @MainActor
    func testManualCityWithLocationDenied() {
        let app = launch()
        tap(app.buttons["Get started"])
        XCTAssertTrue(app.staticTexts["Location access is off. Allow it in app settings or choose your city."]
            .waitForExistence(timeout: 10))
        tap(app.descendants(matching: .any)["miqat.location.city"].firstMatch)

        let search = app.searchFields.firstMatch
        if !search.isHittable { app.swipeDown() }
        tap(search)
        search.typeText("London")
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
    private func launch() -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        return app
    }

    @MainActor
    private func finishSetup(in app: XCUIApplication) {
        XCTAssertTrue(app.navigationBars["Your style"].waitForExistence(timeout: 10))
        let finish = app.buttons["Start using Miqat"]
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
