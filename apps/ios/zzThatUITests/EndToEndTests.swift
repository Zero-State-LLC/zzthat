import XCTest

/// SC-12 and runtime.md, End-to-end run: against the local zzThis Worker
/// (`npm run dev:api` at the pin), in the Local configuration. It needs that
/// Worker, so it runs only when ZZ_E2E=1 is set for the test run, for example
/// `TEST_RUNNER_ZZ_E2E=1 xcodebuild test -configuration Local ...`.
final class EndToEndTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ZZ_E2E"] == "1",
                          "Set ZZ_E2E=1 and run the Local build against npm run dev:api.")
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testDeveloperSignInCreateRerollRevokeAndDelete() throws {
        // Sign in with developer sign-in from Create.
        app.tabBars.buttons.element(boundBy: 1).tap()
        tap("create.sign_in")
        tap("signin.dev")
        // Create a code titled E2E.
        let title = app.textFields["create.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        title.tap()
        title.typeText("E2E")
        tap("create.submit")
        // Re-roll until reroll-cap: the button goes away.
        let reroll = app.buttons["minted.reroll"]
        XCTAssertTrue(reroll.waitForExistence(timeout: 10))
        while reroll.waitForExistence(timeout: 5) { reroll.tap() }
        let code = app.staticTexts["minted.code"].label
        XCTAssertTrue(code.hasPrefix("zz-") && code.hasSuffix("-zz"))
        // Sign out, type the code, and see E2E.
        tap("nav.account")
        tap("account.sign_out")
        tap("account.done")
        app.tabBars.buttons.element(boundBy: 0).tap()
        typeCode(code)
        XCTAssertTrue(app.staticTexts["record.title"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["record.title"].label, "E2E")
        // Sign in again, revoke the code, and see not-found.
        tap("nav.account")
        tap("signin.dev")
        tap("account.done")
        app.tabBars.buttons.element(boundBy: 2).tap()
        let row = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'codes.row.'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        tap("detail.revoke")
        app.alerts.buttons.element(boundBy: 0).tap()
        app.tabBars.buttons.element(boundBy: 0).tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()
        typeCode(code)
        XCTAssertTrue(app.staticTexts["message.resolve.not_found"].waitForExistence(timeout: 10))
        // Delete the account.
        tap("nav.account")
        tap("account.delete")
        app.alerts.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.buttons["signin.dev"].waitForExistence(timeout: 10))
    }

    private func tap(_ identifier: String) {
        let element = app.descendants(matching: .any)[identifier]
        XCTAssertTrue(element.waitForExistence(timeout: 10), identifier)
        element.tap()
    }

    private func typeCode(_ code: String) {
        tap("scan.type_instead")
        let field = app.textViews["type.field"].exists ? app.textViews["type.field"] : app.textFields["type.field"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        field.typeText(code)
        tap("type.open")
    }
}
