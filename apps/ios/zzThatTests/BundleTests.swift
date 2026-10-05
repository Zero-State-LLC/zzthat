import Foundation
import Testing
import ZZAPI
@testable import zzThat

/// The built app bundle (tasks.md T071, T072, T075, T077; runtime.md, Build
/// configurations, Privacy, and Localization). These tests run in the Demo
/// configuration, so the bundle is the Demo app.
@MainActor
@Suite("Built bundle")
struct BundleTests {
    static var info: [String: Any] { Bundle.main.infoDictionary ?? [:] }
    static let repo: URL = {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<4 { url.deleteLastPathComponent() }
        return url
    }()

    /// T071: the camera purpose comes from `scan.camera_purpose` through the
    /// generated InfoPlist.xcstrings and the build setting the sync writes.
    @Test func infoPlistHasTheCameraPurpose() throws {
        let purpose = try #require(Self.info["NSCameraUsageDescription"] as? String)
        #expect(!purpose.isEmpty)
        let catalogURL = Self.repo.appendingPathComponent("apps/ios/zzThat/Resources/InfoPlist.xcstrings")
        let catalog = try JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any]
        let strings = catalog?["strings"] as? [String: Any]
        let entry = strings?["NSCameraUsageDescription"] as? [String: Any]
        let english = (entry?["localizations"] as? [String: Any])?["en"] as? [String: Any]
        let value = (english?["stringUnit"] as? [String: Any])?["value"] as? String
        #expect(purpose == value)
        #expect(Bundle.main.localizedString(forKey: "NSCameraUsageDescription", value: nil, table: "InfoPlist") == value)
        #expect(CopyKey.scanCameraPurpose.text == value)
    }

    /// T072, FR-012 to FR-014: no location, no microphone, and no photo
    /// library permission (the system picker needs none).
    @Test func noOtherPermission() {
        let keys = Self.info.keys
        for key in keys where key.hasSuffix("UsageDescription") {
            #expect(key == "NSCameraUsageDescription", "unexpected permission \(key)")
        }
    }

    /// runtime.md, Build configurations: the Demo id and display name, and
    /// no Google keys in Demo.
    @Test func demoIdentity() {
        #expect(Bundle.main.bundleIdentifier == "com.zer0state.zzthat.demo")
        #expect(Self.info["CFBundleDisplayName"] as? String == "zzThat Demo")
        #expect(Self.info["ZZBuild"] as? String == "demo")
        #expect(Self.info["GIDClientID"] == nil)
        #expect(Self.info["CFBundleURLTypes"] == nil)
        #expect(BuildConfig.current.kind == .demo)
        #expect(BuildConfig.current.apiOrigin == nil)
        #expect(BuildConfig.current.googleClientID == nil)
        #expect(BuildConfig.current.offersDeveloperSignIn == false)
    }

    /// FR-047: the support contact and the privacy link are build settings.
    @Test func supportAndPrivacyComeFromBuildSettings() {
        #expect(BuildConfig.current.supportEmail == "support@zer0state.com")
        #expect(BuildConfig.current.privacyURL?.host() == "zero-state-llc.github.io")
    }

    /// Release step 1: apps/VERSION is the user-visible version.
    @Test func versionIsAppsVersion() throws {
        let version = try String(contentsOf: Self.repo.appendingPathComponent("apps/VERSION"), encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        #expect(Self.info["CFBundleShortVersionString"] as? String == version)
    }

    /// The privacy manifest ships with tracking off (runtime.md, Privacy).
    @Test func privacyManifestTrackingIsOff() throws {
        let url = try #require(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
        let plist = try PropertyListSerialization.propertyList(from: Data(contentsOf: url), format: nil) as? [String: Any]
        #expect(plist?["NSPrivacyTracking"] as? Bool == false)
        #expect((plist?["NSPrivacyTrackingDomains"] as? [Any])?.isEmpty == true)
    }

    /// design.md, Sync: each bundled TTF is listed under UIAppFonts. The pin
    /// has no design/fonts yet, so both lists are empty and the system font
    /// is the fallback.
    @Test func everyBundledFontIsListed() {
        let listed = Set(Self.info["UIAppFonts"] as? [String] ?? [])
        let bundled = Set((Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            .map(\.lastPathComponent))
        #expect(bundled.isSubset(of: listed), "missing from UIAppFonts: \(bundled.subtracting(listed))")
        #expect(listed.isSubset(of: bundled), "listed but not bundled: \(listed.subtracting(bundled))")
    }

    /// The pinned wordlists are bundled when the pin has them (FR-044).
    @Test func bundledWordlistsMatchContracts() throws {
        let folder = Self.repo.appendingPathComponent("contracts/wordlists")
        let pinned = ((try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? [])
            .filter { $0.hasSuffix(".txt") && !$0.contains(".report.") }
        let lists = AppModel.loadBundledLists()
        #expect(Set(lists.map { $0.version + ".txt" }) == Set(pinned))
    }

    /// The Licenses screen shows exactly the bundled files (FR-042).
    @Test func licensesShowTheBundledFiles() {
        let folder = Self.repo.appendingPathComponent("apps/ios/zzThat/Resources/Licenses")
        let pinned = ["NOTICE", "OFL.txt"].filter {
            FileManager.default.fileExists(atPath: folder.appendingPathComponent($0).path)
        }
        #expect(LicensesScreen.bundledTexts().count == pinned.count)
    }
}

/// The generated catalog is the only source of sentences (FR-042, SC-11).
@MainActor
@Suite("Copy catalog")
struct CopyCatalogTests {
    static func catalogKeys() throws -> Set<String> {
        let url = BundleTests.repo.appendingPathComponent("apps/ios/zzThat/Resources/Localizable.xcstrings")
        let catalog = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any]
        return Set((catalog?["strings"] as? [String: Any])?.keys.map { $0 } ?? [])
    }

    /// CopyKey is the catalog's key list, so a pin move that adds or drops a
    /// key changes CopyKey in the same pull request.
    @Test func copyKeysAreTheCatalogKeys() throws {
        #expect(Set(CopyKey.allCases.map(\.rawValue)) == (try Self.catalogKeys()))
    }

    @Test(arguments: CopyKey.allCases)
    func everyKeyResolves(_ key: CopyKey) {
        #expect(key.text != key.rawValue, "\(key.rawValue) is missing from the built catalog")
        #expect(!key.text.contains("—"), "FR-030: no em dash")
    }

    /// Every error sentence the API layer names is a catalog key.
    @Test(arguments: MessageKey.allCases)
    func everyMessageKeyIsACopyKey(_ key: MessageKey) {
        #expect(CopyKey(rawValue: key.rawValue) != nil)
    }

    @Test func placeholdersAreFilled() {
        #expect(CopyKey.accountSignedInWith.text(CopyKey.providerApple.text) == "Signed in with Apple")
        #expect(CopyKey.accountSupport.text("support@zer0state.com").hasSuffix("support@zer0state.com"))
    }

    /// No sentence in app source: every Text, Button, Label, title, and
    /// accessibility label takes a CopyKey, a server value, or a code.
    @Test func appSourceWritesNoSentence() throws {
        let root = BundleTests.repo.appendingPathComponent("apps/ios/zzThat")
        let pattern = try Regex(#"(Text|Button|Label|navigationTitle|accessibilityLabel|accessibilityHint|alert)\(\s*"[^"]*[A-Za-z][^"]*""#)
        var found: [String] = []
        let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil)?
            .compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" } ?? []
        for file in files {
            for (index, line) in try String(contentsOf: file, encoding: .utf8).split(separator: "\n").enumerated()
            where line.contains(pattern) {
                found.append("\(file.lastPathComponent):\(index + 1)")
            }
        }
        #expect(found.isEmpty, "\(found)")
    }
}
