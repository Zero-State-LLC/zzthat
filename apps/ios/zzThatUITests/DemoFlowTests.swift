import XCTest

/// The Demo build end to end, with no network: the tabs, the fixture photo
/// read on the device, typed entry, demo sign-in, mint and re-roll, and the
/// owner list. It checks that every route opens the screen it names.
final class DemoFlowTests: XCTestCase {
    override func setUpWithError() throws {
        #if !ZZ_DEMO
        throw XCTSkip("The demo flow runs in the Demo configuration.")
        #else
        continueAfterFailure = false
        #endif
    }

    @MainActor
    func testScanTypeCreateAndMyCodesInDemo() throws {
        let app = XCUIApplication()
        app.launch()
        let flow = DemoFlow(app: app)
        XCTAssertTrue(app.staticTexts["demo.badge"].waitForExistence(timeout: 10))

        // Scan the fixture photo on the device, confirm the reading, and resolve it.
        flow.tap("scan.take_photo")
        flow.tap("scan.candidate.1", timeout: 30)
        flow.tap("clarify.as_written")
        XCTAssertTrue(app.staticTexts["record.title"].waitForExistence(timeout: 10))
        flow.tap("resolve.report")
        flow.tap("report.reason.spam")
        flow.tap("report.send")
        flow.tap("report.done")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // Type a demo code and open it.
        flow.tap("scan.type_instead")
        flow.tap("type.open")
        XCTAssertTrue(app.staticTexts["record.title"].waitForExistence(timeout: 10))

        // Create signs in with a demo button, mints a demo code, and re-rolls it.
        app.tabBars.buttons.element(boundBy: 1).tap()
        flow.tap("create.sign_in")
        flow.tap("signin.apple")
        let title = app.textFields["create.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        title.tap()
        title.typeText("Lost cat")
        flow.tap("create.submit")
        let code = app.staticTexts["minted.code"]
        XCTAssertTrue(code.waitForExistence(timeout: 10))
        let first = code.label
        flow.tap("minted.reroll")
        XCTAssertTrue(app.staticTexts["minted.code"].waitForExistence(timeout: 10))
        XCTAssertNotEqual(app.staticTexts["minted.code"].label, first)
        flow.tap("minted.write_check")
        flow.tap("scan.take_photo")
        XCTAssertTrue(app.staticTexts["message.writecheck.match"].waitForExistence(timeout: 30))

        // My codes lists the new code; its detail opens.
        app.tabBars.buttons.element(boundBy: 2).tap()
        let row = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'codes.row.'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        XCTAssertTrue(app.buttons["detail.share"].waitForExistence(timeout: 10))

        // Account opens from the toolbar of the tab and shows the demo account.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        flow.tap("nav.account")
        XCTAssertTrue(app.staticTexts["account.signed_in_with"].waitForExistence(timeout: 10))
        flow.tap("account.done")
    }
}

@MainActor
private struct DemoFlow {
    let app: XCUIApplication

    func tap(_ identifier: String, timeout: TimeInterval = 10) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: timeout), identifier)
        element.tap()
    }
}
