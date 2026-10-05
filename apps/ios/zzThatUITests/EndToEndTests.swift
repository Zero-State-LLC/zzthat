import XCTest

/// SC-12 and runtime.md, End-to-end run: against the local zzThis Worker
/// (`npm run dev:api` at the pin), in the Local configuration, which is the
/// only build with developer sign-in. apps-e2e.yml runs it there, and a
/// missing Worker fails it. Other configurations skip it with that reason.
///
/// The flow: sign in with developer sign-in, create a code titled E2E,
/// re-roll until reroll-cap, sign out, type the code, see E2E, sign in,
/// revoke the code, type it again, see not-found, and delete the account.
final class EndToEndTests: XCTestCase {
    override func setUpWithError() throws {
        #if !ZZ_LOCAL
        throw XCTSkip("The end-to-end run needs the Local configuration and npm run dev:api.")
        #else
        continueAfterFailure = false
        #endif
    }

    @MainActor
    func testDeveloperSignInCreateRerollRevokeAndDelete() throws {
        let app = XCUIApplication()
        app.launch()
        let flow = Flow(app: app)
        flow.startSignedOut()

        // Sign in with developer sign-in from Create.
        flow.openTab(1)
        flow.tap("create.sign_in")
        flow.tap("signin.dev")

        // Create a code titled E2E.
        let title = app.textFields["create.title"]
        flow.wait(title, "the Create form after sign-in")
        title.tap()
        title.typeText("E2E")
        flow.tap("create.submit")

        // Re-roll until reroll-cap: the button goes away and the words stay.
        let reroll = app.buttons["minted.reroll"]
        flow.wait(reroll, "the minted code")
        var rerolls = 0
        while reroll.waitForExistence(timeout: 5) {
            let before = flow.mintedCode()
            reroll.tap()
            XCTAssertTrue(flow.waitForChange(of: before), "re-roll \(rerolls + 1) gives new words")
            rerolls += 1
            XCTAssertLessThanOrEqual(rerolls, 3, "the cap is 3 re-rolls (FR-017)")
        }
        XCTAssertEqual(rerolls, 3)
        flow.wait(app.staticTexts["message.minted.reroll_cap"], "minted.reroll_cap", timeout: 5)
        let code = flow.mintedCode()
        XCTAssertTrue(code.hasPrefix("zz-") && code.hasSuffix("-zz"), code)

        // Sign out, type the code, and see E2E.
        flow.backToRoot()
        flow.openAccount()
        flow.tap("account.sign_out")
        flow.wait(app.buttons["signin.dev"], "signed out", timeout: 15)
        flow.tap("account.done")
        flow.openTab(0)
        flow.typeCode(code)
        flow.wait(app.staticTexts["record.title"], "the record, signed out", timeout: 15)
        XCTAssertEqual(app.staticTexts["record.title"].label, "E2E")

        // Sign in again, revoke the code, and see not-found.
        flow.backToRoot()
        flow.openAccount()
        flow.tap("signin.dev")
        flow.wait(app.buttons["account.sign_out"], "signed in again", timeout: 15)
        flow.tap("account.done")
        flow.openTab(2)
        let row = app.buttons["codes.row.\(flow.rowID(for: code))"]
        flow.wait(row, "the code in My codes")
        row.tap()
        flow.tap("detail.revoke")
        flow.confirmAlert(Copy.text("revoke.action"))
        flow.wait(app.staticTexts["message.detail.revoked_note"], "revoked", timeout: 15)
        flow.backToRoot()
        flow.openTab(0)
        flow.backToRoot()
        flow.typeCode(code)
        flow.wait(app.staticTexts["message.resolve.not_found"], "not-found", timeout: 15)

        // Delete the account.
        flow.backToRoot()
        flow.openAccount()
        flow.tap("account.delete")
        flow.confirmAlert(Copy.text("delete.action"))
        flow.wait(app.buttons["signin.dev"], "the account is deleted", timeout: 15)
        flow.tap("account.done")
    }
}

/// English text from the generated catalog, so the test names a dialog
/// button the way the app does, without writing the sentence again.
enum Copy {
    nonisolated(unsafe) static let catalog: [String: Any] = {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<2 { url.deleteLastPathComponent() }
        url.appendPathComponent("zzThat/Resources/Localizable.xcstrings")
        guard let data = try? Data(contentsOf: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let strings = root["strings"] as? [String: Any] else { return [:] }
        return strings
    }()

    static func text(_ key: String) -> String {
        let entry = catalog[key] as? [String: Any]
        let english = (entry?["localizations"] as? [String: Any])?["en"] as? [String: Any]
        return ((english?["stringUnit"] as? [String: Any])?["value"] as? String) ?? key
    }
}

@MainActor
private struct Flow {
    let app: XCUIApplication

    func tap(_ identifier: String, timeout: TimeInterval = 15) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        wait(element, identifier, timeout: timeout)
        element.tap()
    }

    /// Waits for an element. On a miss, the log gets the screen's
    /// accessibility tree, so a failure reads from the log.
    func wait(_ element: XCUIElement, _ what: String, timeout: TimeInterval = 15) {
        if element.waitForExistence(timeout: timeout) { return }
        print("E2E missing \(what). Accessibility tree:\n\(app.debugDescription)")
        XCTFail("missing \(what)")
    }

    func openTab(_ index: Int) {
        app.tabBars.buttons.element(boundBy: index).tap()
    }

    /// Pops the current tab back to its root, where Account sits in the toolbar.
    func backToRoot() {
        for _ in 0..<6 {
            let back = app.navigationBars.buttons.element(boundBy: 0)
            guard back.exists, back.identifier != "nav.account" else { return }
            back.tap()
        }
    }

    func openAccount() {
        tap("nav.account")
    }

    /// A run that stopped early can leave a session behind in the Keychain.
    func startSignedOut() {
        openAccount()
        let signOut = app.buttons["account.sign_out"]
        if signOut.waitForExistence(timeout: 5) { signOut.tap() }
        wait(app.buttons["signin.dev"], "signed out at the start", timeout: 15)
        tap("account.done")
    }

    func mintedCode() -> String {
        app.staticTexts["minted.code"].label
    }

    func waitForChange(of code: String) -> Bool {
        let changed = NSPredicate(format: "label != %@", code)
        return XCTWaiter().wait(for: [XCTNSPredicateExpectation(predicate: changed,
                                                                object: app.staticTexts["minted.code"])],
                                timeout: 15) == .completed
    }

    /// The row id of a code in My codes, from the row whose label holds it.
    func rowID(for code: String) -> String {
        let row = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'codes.row.' AND label CONTAINS %@",
                                                   code)).firstMatch
        wait(row, "a My codes row for \(code)")
        return String(row.identifier.dropFirst("codes.row.".count))
    }

    func confirmAlert(_ action: String) {
        let button = app.alerts.buttons[action]
        wait(button, "the alert's \(action) button", timeout: 10)
        button.tap()
    }

    func typeCode(_ code: String) {
        tap("scan.type_instead")
        let field = app.textViews["type.field"].exists ? app.textViews["type.field"] : app.textFields["type.field"]
        wait(field, "the Type field", timeout: 10)
        field.tap()
        field.typeText(code)
        tap("type.open")
    }
}
