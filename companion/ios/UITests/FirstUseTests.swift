import XCTest

final class FirstUseTests: XCTestCase {
    @MainActor
    func testManualCityWithLocationDenied() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        tap(app.buttons["Get started"])
        XCTAssertTrue(app.staticTexts["Location access is off. Allow it in app settings or choose your city."]
            .waitForExistence(timeout: 10))
        tap(app.descendants(matching: .any)["miqat.location.city"].firstMatch)

        let search = app.searchFields.firstMatch
        if !search.isHittable { app.swipeDown() }
        tap(search)
        search.typeText("London")
        tap(app.buttons["London"])
        tap(app.buttons["Confirm location"])

        let alerts = app.switches["Prayer alerts"]
        reveal(alerts, in: app)
        XCTAssertEqual(alerts.value as? String, "1")
        tap(alerts)
        XCTAssertEqual(alerts.value as? String, "0")
        tap(app.buttons["Skip"])

        app.terminate()
        app.launch()
        tap(app.buttons["Finish setting up Miqat"])
        reveal(alerts, in: app)
        XCTAssertEqual(alerts.value as? String, "0")
        tap(app.buttons["Continue"])
        XCTAssertTrue(app.navigationBars["Your style"].waitForExistence(timeout: 10))
        let finish = app.buttons["Start using Miqat"]
        reveal(finish, in: app)
        tap(finish)

        XCTAssertTrue(app.buttons["London"].waitForExistence(timeout: 10))
        reveal(app.buttons["miqat.prayer.0"], in: app)
        reveal(app.staticTexts["Sunrise"], in: app)
        for index in [2, 3, 4, 5] {
            reveal(app.buttons["miqat.prayer.\(index)"], in: app)
        }
        XCTAssertFalse(app.staticTexts["Prayer times unavailable for this location and date."].exists)
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
