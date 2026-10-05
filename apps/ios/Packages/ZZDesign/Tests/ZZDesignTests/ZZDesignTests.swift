import Foundation
import Testing
@testable import ZZDesign

@Suite("Design")
struct ZZDesignTests {
    @Test func theFamiliesAreTheOnesTheTokensName() {
        #expect(ZZFontRegistry.families == ["IBM Plex Sans Condensed", "IBM Plex Sans", "IBM Plex Mono"])
    }

    @Test func registrationWithNoFontsReportsTheMissingFamilies() {
        let bundle = Bundle(for: Marker.self)
        let missing = ZZFontRegistry.registerBundledFonts(in: bundle)
        for family in missing { #expect(!ZZFontRegistry.isRegistered(family)) }
    }

    @Test func spacingAndRadiusAreThePinnedValues() {
        #expect(ZZSpace.spaceSm == 16)
        #expect(ZZRadius.pill == 999)
    }
}

final class Marker {}
