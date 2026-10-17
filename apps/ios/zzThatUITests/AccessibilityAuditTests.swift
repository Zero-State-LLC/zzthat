import XCTest

/// runtime.md, Accessibility (tasks.md T076): Xcode's accessibility audit
/// on each main screen of the Demo build: names, text size, touch targets,
/// contrast, and clipped text.
final class AccessibilityAuditTests: XCTestCase {
    override func setUpWithError() throws {
        #if !ZZ_DEMO
        throw XCTSkip("The audit runs in the Demo configuration.")
        #else
        continueAfterFailure = true
        #endif
    }

    @MainActor
    func testMainScreensPassTheAudit() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["demo.badge"].waitForExistence(timeout: 10))
        try audit(app, "scan")

        tap(app, "scan.type_instead")
        try audit(app, "type")
        tap(app, "type.open")
        XCTAssertTrue(app.staticTexts["record.title"].waitForExistence(timeout: 10))
        try audit(app, "resolve")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()

        app.tabBars.buttons.element(boundBy: 1).tap()
        try audit(app, "create signed out")
        tap(app, "create.sign_in")
        try audit(app, "sign in")
        tap(app, "signin.apple")
        let title = app.textFields["create.title"]
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        try audit(app, "create")
        title.tap()
        title.typeText("Lost cat")
        let age = app.descendants(matching: .any)["terms.age_confirm"].firstMatch
        if age.waitForExistence(timeout: 3) {
            try audit(app, "terms")
            age.tap()
            app.descendants(matching: .any)["terms.accept"].firstMatch.tap()
        }
        tap(app, "create.submit")
        XCTAssertTrue(app.staticTexts["minted.code"].waitForExistence(timeout: 10))
        try audit(app, "minted")

        app.tabBars.buttons.element(boundBy: 2).tap()
        let row = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'codes.row.'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        try audit(app, "my codes", textSizeInPlace: false)
        row.tap()
        XCTAssertTrue(app.buttons["detail.share"].waitForExistence(timeout: 10))
        try audit(app, "detail")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        tap(app, "nav.account")
        XCTAssertTrue(app.staticTexts["account.signed_in_with"].waitForExistence(timeout: 10))
        try audit(app, "account")
    }

    /// Runs the audit and names the screen in every issue it records.
    ///
    /// - Contrast is not audited here: Xcode samples rendered pixels and
    ///   flagged pairs that measure 6:1 to 9.5:1 from the pinned tokens, on
    ///   some runs and not others. `ContrastTests` computes every text and
    ///   background pair from the tokens instead.
    /// - Dynamic Type on a navigation bar button is a system bar item: the
    ///   system caps its size and a long press shows the Large Content Viewer.
    /// - On a scrolling list (`textSizeInPlace: false`), the audit grows the
    ///   text in place and loses the rows it pushes off screen. The `-ax5`
    ///   snapshots in SnapshotTests show those screens at the largest size.
    @MainActor
    private func audit(_ app: XCUIApplication, _ screen: String, textSizeInPlace: Bool = true) throws {
        var types = XCUIAccessibilityAuditType.all
        types.remove(.contrast)
        if !textSizeInPlace { types.remove(.dynamicType) }
        let barButtons = Set(app.navigationBars.buttons.allElementsBoundByIndex.flatMap { [$0.identifier, $0.label] })
        try app.performAccessibilityAudit(for: types) { issue in
            let element = issue.element
            let name = element.map { $0.identifier.isEmpty ? $0.label : $0.identifier } ?? "?"
            if issue.auditType == .dynamicType, let element,
               barButtons.contains(element.identifier) || barButtons.contains(element.label) {
                return true
            }
            print("A11Y \(screen) | \(issue.auditType) | \(name) | \(issue.compactDescription)")
            return false
        }
    }

    @MainActor
    private func tap(_ app: XCUIApplication, _ identifier: String) {
        let element = app.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 10), identifier)
        element.tap()
    }
}
