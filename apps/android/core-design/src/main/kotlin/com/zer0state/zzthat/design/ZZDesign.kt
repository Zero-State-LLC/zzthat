package com.zer0state.zzthat.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import llc.zerostate.zzthis.design.ZZColor
import llc.zerostate.zzthis.design.ZZType

/**
 * The pinned token colors for one color scheme (runtime.md, Accessibility:
 * colors come from the pinned token files, light and dark). Every value is a
 * Tokens.kt constant; none is authored here.
 */
data class Palette(
    val paper: Color,
    val panel: Color,
    val panel2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val ruleStrong: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val accentWash: Color,
    val focus: Color,
) {
    companion object {
        val Light = Palette(
            paper = ZZColor.ColorPaperLight,
            panel = ZZColor.ColorPanelLight,
            panel2 = ZZColor.ColorPanel2Light,
            ink = ZZColor.ColorInkLight,
            ink2 = ZZColor.ColorInk2Light,
            ink3 = ZZColor.ColorInk3Light,
            rule = ZZColor.ColorRuleLight,
            ruleStrong = ZZColor.ColorRuleStrongLight,
            accent = ZZColor.ColorAccent,
            onAccent = ZZColor.ColorOnAccent,
            accentInk = ZZColor.ColorAccentInkLight,
            accentWash = ZZColor.ColorAccentWashLight,
            focus = ZZColor.ColorFocusLight,
        )

        val Dark = Palette(
            paper = ZZColor.ColorPaperDark,
            panel = ZZColor.ColorPanelDark,
            panel2 = ZZColor.ColorPanel2Dark,
            ink = ZZColor.ColorInkDark,
            ink2 = ZZColor.ColorInk2Dark,
            ink3 = ZZColor.ColorInk3Dark,
            rule = ZZColor.ColorRuleDark,
            ruleStrong = ZZColor.ColorRuleStrongDark,
            accent = ZZColor.ColorAccent,
            onAccent = ZZColor.ColorOnAccent,
            accentInk = ZZColor.ColorAccentInkDark,
            accentWash = ZZColor.ColorAccentWashDark,
            focus = ZZColor.ColorFocusDark,
        )

        fun of(dark: Boolean): Palette = if (dark) Dark else Light
    }
}

/**
 * The pinned IBM Plex families (design.md, Sync; FR-043), from the TTF files
 * in res/font, under the family names in ZZType.
 */
object ZZFonts {
    /** ZZType.FontDisplayFamily, IBM Plex Sans Condensed. */
    val display: FontFamily = FontFamily(
        Font(R.font.ibmplexsanscondensed_regular, ZZType.WeightRegular),
        Font(R.font.ibmplexsanscondensed_medium, ZZType.WeightMedium),
        Font(R.font.ibmplexsanscondensed_semibold, ZZType.WeightSemibold),
        Font(R.font.ibmplexsanscondensed_bold, ZZType.WeightBold),
    )

    /** ZZType.FontBodyFamily, IBM Plex Sans. */
    val body: FontFamily = FontFamily(
        Font(R.font.ibmplexsans_regular, ZZType.WeightRegular),
        Font(R.font.ibmplexsans_medium, ZZType.WeightMedium),
        Font(R.font.ibmplexsans_semibold, ZZType.WeightSemibold),
        Font(R.font.ibmplexsans_bold, ZZType.WeightBold),
    )

    /** ZZType.FontMonoFamily, IBM Plex Mono. Codes are always in this family. */
    val mono: FontFamily = FontFamily(
        Font(R.font.ibmplexmono_regular, ZZType.WeightRegular),
        Font(R.font.ibmplexmono_medium, ZZType.WeightMedium),
        Font(R.font.ibmplexmono_semibold, ZZType.WeightSemibold),
        Font(R.font.ibmplexmono_bold, ZZType.WeightBold),
    )

    /** The pinned files each family uses, by resource name. */
    val files: Map<String, List<String>> = mapOf(
        ZZType.FontDisplayFamily to weights("ibmplexsanscondensed"),
        ZZType.FontBodyFamily to weights("ibmplexsans"),
        ZZType.FontMonoFamily to weights("ibmplexmono"),
    )

    private fun weights(prefix: String) = listOf("regular", "medium", "semibold", "bold").map { "${prefix}_$it" }
}

/** The licenses bundled in assets/licenses, shown verbatim on the Licenses screen (FR-042). */
object ZZLicenses {
    const val FOLDER = "licenses"
    val FILES = listOf("NOTICE", "OFL.txt")
}

/** A weight the type roles use, kept with the tokens. */
val SemiboldWeight: FontWeight = ZZType.WeightSemibold
