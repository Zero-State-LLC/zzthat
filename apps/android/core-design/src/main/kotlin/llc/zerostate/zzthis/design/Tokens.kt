// Generated from design/tokens.json by scripts/build-design-tokens.mjs.
// Do not edit. Bundle IBM Plex under the family names in ZZType, then pass that FontFamily in.
package llc.zerostate.zzthis.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing

object ZZColor {
    val ColorPaperLight = Color(0xFFF4EFE4) // #f4efe4 from oklch(95.3% 0.016 86)
    val ColorPaperDark = Color(0xFF12100E) // #12100e from oklch(17.5% 0.006 75)
    val ColorPanelLight = Color(0xFFFAF7F0) // #faf7f0 from oklch(97.6% 0.009 86)
    val ColorPanelDark = Color(0xFF1B1916) // #1b1916 from oklch(21.5% 0.007 75)
    val ColorPanel2Light = Color(0xFFEAE3D4) // #eae3d4 from oklch(91.7% 0.021 86)
    val ColorPanel2Dark = Color(0xFF25221F) // #25221f from oklch(25.5% 0.008 75)
    val ColorInkLight = Color(0xFF1C1B19) // #1c1b19 from oklch(22.2% 0.004 85)
    val ColorInkDark = Color(0xFFEFEBE1) // #efebe1 from oklch(94% 0.014 86)
    val ColorInk2Light = Color(0xFF4A463F) // #4a463f from oklch(39.6% 0.013 82)
    val ColorInk2Dark = Color(0xFFBDB7AB) // #bdb7ab from oklch(78% 0.018 85)
    val ColorInk3Light = Color(0xFF5B5751) // #5b5751 from oklch(46% 0.012 82)
    val ColorInk3Dark = Color(0xFFA29E95) // #a29e95 from oklch(70% 0.014 85)
    val ColorRuleLight = Color(0xFFCFC6B4) // #cfc6b4 from oklch(82.9% 0.027 85)
    val ColorRuleDark = Color(0xFF35322E) // #35322e from oklch(32% 0.009 80)
    val ColorRuleStrongLight = Color(0xFF868073) // #868073 from oklch(60% 0.02 85)
    val ColorRuleStrongDark = Color(0xFF6F6B64) // #6f6b64 from oklch(53% 0.012 80)
    val ColorGridLight = Color(0xFFE4DDCF) // #e4ddcf from oklch(90% 0.02 86)
    val ColorGridDark = Color(0xFF1A1815) // #1a1815 from oklch(21% 0.007 75)
    val ColorAccent = Color(0xFFF85002) // #f85002 from oklch(65.9% 0.215 38)
    val ColorOnAccent = Color(0xFF1C1B19) // #1c1b19 from oklch(22.2% 0.004 85)
    val ColorAccentInkLight = Color(0xFF9B2100) // #9b2100 from oklch(45% 0.165 38)
    val ColorAccentInkDark = Color(0xFFFD7E49) // #fd7e49 from oklch(73% 0.17 42)
    val ColorAccentWashLight = Color(0xFFFFDCC8) // #ffdcc8 from oklch(92% 0.05 50)
    val ColorAccentWashDark = Color(0xFF3B1D13) // #3b1d13 from oklch(27% 0.05 40)
    val ColorFocusLight = Color(0xFFBD2B00) // #bd2b00 from oklch(52% 0.19 38)
    val ColorFocusDark = Color(0xFFFF8C56) // #ff8c56 from oklch(76% 0.16 44)
    val MaskSolid = Color(0xFF000000) // #000000 from oklch(0% 0 0)
    val ColorDitherDark = Color(0xFF12100E) // #12100e from oklch(17.5% 0.006 75)
    val ColorDitherLight = Color(0xFFF9A870) // #f9a870 from oklch(80% 0.12 55)
    val ShadowLight = Color(red = 0.1098f, green = 0.1059f, blue = 0.0980f, alpha = 0.12f) // #1c1b19 from rgb(28 27 25 / 0.12)
    val ShadowDark = Color(red = 0.0000f, green = 0.0000f, blue = 0.0000f, alpha = 0.35f) // #000000 from rgb(0 0 0 / 0.35)
}

object ZZType {
    const val FontDisplayFamily = "IBM Plex Sans Condensed"
    const val FontDisplayStack = "\"IBM Plex Sans Condensed\", \"Arial Narrow\", sans-serif"
    const val FontBodyFamily = "IBM Plex Sans"
    const val FontBodyStack = "\"IBM Plex Sans\", system-ui, sans-serif"
    const val FontMonoFamily = "IBM Plex Mono"
    const val FontMonoStack = "\"IBM Plex Mono\", \"IBM Plex Sans KR\", \"IBM Plex Sans JP\", \"IBM Plex Sans Hebrew\", ui-monospace, monospace"
    val WeightRegular = FontWeight.W400
    val WeightMedium = FontWeight.W500
    val WeightSemibold = FontWeight.W600
    val WeightBold = FontWeight.W700
    const val LineHeight = 1.6f
    val Text2xs = 12.sp
    val TextXs = 13.sp
    val TextSm = 15.sp
    val TextBase = 17.sp
    val TextMd = 20.sp
    val TextLg = 25.sp
    const val TextXlCss = "clamp(1.75rem, 1.6vw + 1.1rem, 2.4rem)"
    const val TextDisplaySCss = "clamp(2rem, 2.4vw + 1.1rem, 3.4rem)"
    const val TextCodeCss = "clamp(1rem, 2.75vw, 2.3rem)"
    fun body(family: FontFamily, size: TextUnit, weight: FontWeight = WeightRegular) =
        TextStyle(fontFamily = family, fontSize = size, fontWeight = weight)
}

object ZZSpace {
    val Space3xs = 4.dp
    val Space2xs = 6.dp
    val SpaceXs = 10.dp
    val SpaceSm = 16.dp
    val SpaceMd = 26.dp
    val SpaceLg = 42.dp
    val SpaceXl = 68.dp
    val Space2xl = 110.dp
    val Gutter = 16.dp
    val Max = 1280.dp
    val RuleHair = 1.dp
    val GridCell = 24.dp
    val NavH = 64.dp
    val Cut = 16.dp
}

object ZZRadius {
    val RadiusNone = 0.dp
    val RadiusChip = 3.dp
    val Pill = 999.dp
}

object ZZShadow {
    val MountTop = 8.dp
    val MountBottom = -8.dp
    val MountBlur = 10.dp
    const val MountInset = 0.04f // 4% in the site CSS
}

object ZZMotion {
    val EaseOut = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    val EaseIn = CubicBezierEasing(0.7f, 0.0f, 0.84f, 0.0f)
    val EaseInOut = CubicBezierEasing(0.65f, 0.0f, 0.35f, 1.0f)
    const val DurMicroMs = 120
    const val DurShortMs = 220
    const val DurLongMs = 420
}

