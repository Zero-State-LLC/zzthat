import Foundation
import UIKit
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

    /// The Terms link is a build setting like the privacy link (design/UX.md,
    /// Terms). Demo and Local carry a zzThat placeholder, never a zzThis host
    /// (D-2026-10-11-07); Release injects its own.
    @Test func theTermsLinkComesFromBuildSettings() throws {
        let url = try #require(BuildConfig.current.termsURL)
        #expect(url.scheme == "https")
        #expect(url.host() == "zzthat.com")
        #expect(!url.absoluteString.lowercased().contains("zzthis"))
    }

    /// RM-111: the community links are the zzThis site footer's addresses.
    @Test func theCommunityLinksAreTheFooterAddresses() {
        #expect(BuildConfig.discordURL.absoluteString == "https://discord.gg/hwXvVKYzn4")
        #expect(BuildConfig.instagramURL.absoluteString == "https://www.instagram.com/zzthatcom/")
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

    /// design.md, Sync: each bundled TTF is listed under UIAppFonts, and
    /// every listed face loads, so the pinned families replace the system
    /// font fallback.
    @Test func everyBundledFontIsListedAndLoads() throws {
        let listed = Set(Self.info["UIAppFonts"] as? [String] ?? [])
        let files = Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        let bundled = Set(files.map(\.lastPathComponent))
        #expect(!bundled.isEmpty, "the pin ships the IBM Plex files")
        #expect(bundled.isSubset(of: listed), "missing from UIAppFonts: \(bundled.subtracting(listed))")
        #expect(listed.isSubset(of: bundled), "listed but not bundled: \(listed.subtracting(bundled))")
        for file in files {
            let provider = try #require(CGDataProvider(url: file as CFURL))
            let face = try #require(CGFont(provider)?.postScriptName as String?)
            #expect(UIFont(name: face, size: 17) != nil, "\(file.lastPathComponent) does not load as \(face)")
        }
        let pinned = [FontFamily.display, .body, .mono].filter(\.isPinned)
        #expect(pinned.count == 3, "the pinned families are registered, so no fallback runs")
    }

    /// The Demo, Local, and Release Info plists list the same pinned files,
    /// so a build this suite does not run cannot drift either.
    @Test(arguments: ["Info-Demo.plist", "Info-Local.plist", "Info-Release.plist"])
    func everyConfigurationListsThePinnedFonts(_ name: String) throws {
        let fonts = Self.repo.appendingPathComponent("apps/ios/zzThat/Resources/Fonts")
        let pinned = Set(try FileManager.default.contentsOfDirectory(atPath: fonts.path).filter { $0.hasSuffix(".ttf") })
        let url = Self.repo.appendingPathComponent("apps/ios/zzThat/Config").appendingPathComponent(name)
        let plist = try PropertyListSerialization.propertyList(from: Data(contentsOf: url), format: nil) as? [String: Any]
        let listed = plist?["UIAppFonts"] as? [String] ?? []
        #expect(Set(listed) == pinned)
        #expect(listed.count == pinned.count, "no file is listed twice")
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
    /// The pins in the app's lock, which Xcode keeps in Packages/ZZAPI.
    static func lockedPins() throws -> [[String: Any]] {
        let url = repo.appendingPathComponent("apps/ios/Packages/ZZAPI/Package.resolved")
        let lock = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any]
        return try #require(lock?["pins"] as? [[String: Any]])
    }

    /// T087: the Licenses screen shows the zzThis NOTICE and OFL.txt as
    /// pinned, then one block per package from the generated notices.
    @Test func licensesShowTheBundledFiles() throws {
        let folder = Self.repo.appendingPathComponent("apps/ios/zzThat/Resources/Licenses")
        let pinned = try ["NOTICE", "OFL.txt"].map {
            try String(contentsOf: folder.appendingPathComponent($0), encoding: .utf8)
        }
        let texts = LicensesScreen.bundledTexts()
        #expect(Array(texts.prefix(2)) == pinned, "NOTICE, then OFL.txt, exactly as pinned")
        #expect(texts.count == 2 + (try Self.noticeBlocks().count), "one block per listed package")
    }

    /// The package blocks of the bundled Licenses/ThirdPartyNotices.txt.
    static func noticeBlocks() throws -> [String] {
        let url = try #require(Bundle.main.resourceURL?.appendingPathComponent("Licenses/ThirdPartyNotices.txt"))
        return try String(contentsOf: url, encoding: .utf8)
            .components(separatedBy: "\n\(LicensesScreen.noticeSeparator)\n")
            .map { $0.trimmingCharacters(in: .newlines) }
    }

    /// T087 and S4: the notices list only the packages linked into the app.
    /// The build derives them from the app target's package products and
    /// fails when the linker reads a package the notices miss; this checks
    /// what it bundled. Each listed package is pinned at its listed version
    /// and has license text, and test and build-tool packages are not listed.
    @Test func thirdPartyNoticesListTheLinkedPackages() throws {
        let pins = try Self.lockedPins()
        let blocks = try Self.noticeBlocks()
        #expect(blocks.count < pins.count, "the lock pins test and build tools the app does not link")
        var listed: [String] = []
        for block in blocks {
            let lines = block.split(separator: "\n", maxSplits: 2).map(String.init)
            try #require(lines.count == 3, "a header and license text")
            let location = lines[1]
            listed.append(location)
            let pin = pins.first { ($0["location"] as? String) == location }
            let state = pin?["state"] as? [String: Any]
            let version = (state?["version"] ?? state?["revision"]) as? String
            #expect(version != nil && lines[0].hasSuffix(" \(version ?? "")"), "\(location) is pinned at its version")
            #expect(lines[2].count > 200, "\(location) has its license text")
        }
        #expect(listed.contains { $0.contains("GoogleSignIn-iOS") }, "the app's GoogleSignIn product is listed")
        #expect(!listed.contains { $0.contains("swift-snapshot-testing") }, "a test-only package is not listed")
        #expect(!listed.contains { $0.contains("swift-openapi-generator") }, "a build-tool plugin is not listed")
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
