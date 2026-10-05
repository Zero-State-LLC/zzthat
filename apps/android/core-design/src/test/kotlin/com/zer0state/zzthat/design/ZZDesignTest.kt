package com.zer0state.zzthat.design

import llc.zerostate.zzthis.design.ZZColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ZZDesignTest {
    private val fonts = File(System.getProperty("zz.fonts"))
    private val licenses = File(System.getProperty("zz.licenses"))

    /** design.md, Sync: res/font holds only the pinned TTF files, renamed lowercase with underscores, and each family uses them. */
    @Test
    fun everyPinnedFontIsBundledAndUsed() {
        val files = fonts.listFiles().orEmpty().map { it.name }.sorted()
        assertTrue("the pin ships the IBM Plex files", files.isNotEmpty())
        assertTrue(files.toString(), files.all { it.matches(Regex("[a-z][a-z0-9_]*\\.ttf")) })
        assertEquals(files.map { it.removeSuffix(".ttf") }.toSet(), ZZFonts.files.values.flatten().toSet())
    }

    /** design.md, Sync: NOTICE and OFL.txt are in assets/licenses, never in res/font. */
    @Test
    fun theLicensesAreBundled() {
        for (name in ZZLicenses.FILES) assertTrue(name, File(licenses, name).readText().isNotBlank())
    }

    /** Colors come from the pinned token file, light and dark. */
    @Test
    fun thePalettesAreThePinnedTokens() {
        assertEquals(ZZColor.ColorPaperLight, Palette.Light.paper)
        assertEquals(ZZColor.ColorPaperDark, Palette.Dark.paper)
        assertEquals(ZZColor.ColorAccent, Palette.Light.accent)
        assertEquals(ZZColor.ColorAccentInkDark, Palette.of(dark = true).accentInk)
    }
}
