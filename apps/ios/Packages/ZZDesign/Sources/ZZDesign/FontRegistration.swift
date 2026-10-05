import CoreText
import Foundation
import os

/// Registers the pinned IBM Plex files (design.md, Sync: design/fonts/*.ttf)
/// that the app bundle holds. The app lists each file under `UIAppFonts`, so
/// iOS normally registers them at launch; this covers a file that is present
/// but not listed. A family that is missing falls back to the system font,
/// which `Font.custom` does on its own, and a debug build logs that once.
public enum ZZFontRegistry {
    public static let families = [ZZFont.fontDisplayName, ZZFont.fontBodyName, ZZFont.fontMonoName]

    private static let logger = Logger(subsystem: "com.zer0state.zzthat", category: "fonts")
    private static let state = OSAllocatedUnfairLock(initialState: false)

    /// The TTF files in a bundle, by file name.
    public static func bundledFontFiles(in bundle: Bundle) -> [URL] {
        (bundle.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    /// Registers each bundled TTF that is not registered yet. Returns the
    /// families that are still missing afterwards. Runs once per process.
    @discardableResult
    public static func registerBundledFonts(in bundle: Bundle = .main) -> [String] {
        let firstRun = state.withLock { done -> Bool in
            defer { done = true }
            return !done
        }
        if firstRun {
            for url in bundledFontFiles(in: bundle) {
                var error: Unmanaged<CFError>?
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error)
                error?.release()
            }
        }
        let missing = families.filter { !isRegistered($0) }
        #if DEBUG
        if firstRun, !missing.isEmpty {
            logger.debug("Pinned font families not bundled at this pin, using the system font: \(missing.joined(separator: ", "), privacy: .public)")
        }
        #endif
        return missing
    }

    public static func isRegistered(_ family: String) -> Bool {
        let descriptor = CTFontDescriptorCreateWithAttributes([kCTFontFamilyNameAttribute: family] as CFDictionary)
        let matches = CTFontDescriptorCreateMatchingFontDescriptors(descriptor, nil) as? [CTFontDescriptor] ?? []
        return !matches.isEmpty
    }
}
