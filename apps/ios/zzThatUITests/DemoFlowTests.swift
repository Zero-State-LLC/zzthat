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

        // A1: the photo picker has its own name, not the camera button's.
        let picker = app.buttons["scan.photo_picker"]
        XCTAssertTrue(picker.waitForExistence(timeout: 10))
        XCTAssertEqual(picker.label, Copy.text("scan.choose_photo"))

        // Scan the fixture photo on the device. Its check word verifies against
        // the bundled fixture-7 list, so the one candidate in Accept opens at once.
        flow.tap("scan.take_photo")
        // The first Vision request on a fresh simulator loads its model, which
        // can take well over 30 seconds.
        if !app.staticTexts["record.title"].waitForExistence(timeout: 120) {
            print("DEMOFLOW tree after the fixture photo:\n\(app.debugDescription)")
            XCTFail("the fixture photo did not open its record")
        }
        flow.tap("resolve.report")
        flow.tap("report.reason.spam")
        flow.tap("report.send")
        flow.tap("report.done")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        // A near-word asks the person: zz-coper-lantern-sky-zz offers copper.
        flow.tap("scan.type_instead")
        let field = app.textViews["type.field"].exists ? app.textViews["type.field"] : app.textFields["type.field"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        // Put the caret after the prefilled demo code, then clear it.
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 40))
        field.typeText("zz-coper-lantern-sky-zz")
        flow.tap("type.open")
        flow.tap("clarify.candidate.copper")
        XCTAssertTrue(app.staticTexts["record.title"].waitForExistence(timeout: 10))
        app.navigationBars.buttons.element(boundBy: 0).tap()
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
        flow.confirmTermsIfAsked()
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

    /// The Terms step before the first create on this device (design/UX.md,
    /// Terms): confirm 18 or older and accept the Terms, when it shows.
    func confirmTermsIfAsked() {
        let age = app.descendants(matching: .any)["terms.age_confirm"].firstMatch
        guard age.waitForExistence(timeout: 3) else { return }
        age.tap()
        app.descendants(matching: .any)["terms.accept"].firstMatch.tap()
    }

    func tap(_ identifier: String, timeout: TimeInterval = 10) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: timeout), identifier)
        element.tap()
    }
}
