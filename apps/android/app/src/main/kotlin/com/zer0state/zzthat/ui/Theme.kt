package com.zer0state.zzthat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.zer0state.zzthat.design.Palette
import com.zer0state.zzthat.design.ZZFonts
import llc.zerostate.zzthis.design.ZZType

val LocalPalette = staticCompositionLocalOf { Palette.Light }

/** The pinned colors and type, light and dark, following the system setting (runtime.md, Accessibility). */
@Composable
fun ZZTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = Palette.of(dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.accentInk,
        onSecondary = palette.paper,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.paper,
        onSurface = palette.ink,
        surfaceVariant = palette.panel2,
        onSurfaceVariant = palette.ink2,
        surfaceContainerLowest = palette.paper,
        surfaceContainerLow = palette.panel,
        surfaceContainer = palette.panel,
        surfaceContainerHigh = palette.panel,
        surfaceContainerHighest = palette.panel2,
        outline = palette.ruleStrong,
        outlineVariant = palette.rule,
        error = palette.accentInk,
        onError = palette.paper,
    )
    val body = TextStyle(fontFamily = ZZFonts.body, color = palette.ink)
    val typography = Typography(
        bodyLarge = body.copy(fontSize = ZZType.TextBase),
        bodyMedium = body.copy(fontSize = ZZType.TextSm),
        bodySmall = body.copy(fontSize = ZZType.TextXs),
        labelLarge = body.copy(fontSize = ZZType.TextBase, fontWeight = ZZType.WeightSemibold),
        titleLarge = TextStyle(fontFamily = ZZFonts.display, fontSize = ZZType.TextLg, fontWeight = ZZType.WeightSemibold),
        headlineSmall = TextStyle(fontFamily = ZZFonts.display, fontSize = ZZType.TextLg, fontWeight = ZZType.WeightSemibold),
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

/** Type roles over the pinned families and sizes. */
enum class TextRole {
    HEADING,
    TITLE,
    BODY,
    SECONDARY,
    CAPTION,
    LABEL,
}

@Composable
@ReadOnlyComposable
fun TextRole.style(): TextStyle {
    val palette = LocalPalette.current
    val family = if (this == TextRole.HEADING || this == TextRole.TITLE) ZZFonts.display else ZZFonts.body
    val size: TextUnit = when (this) {
        TextRole.HEADING -> ZZType.TextLg
        TextRole.TITLE -> ZZType.TextMd
        TextRole.BODY -> ZZType.TextBase
        TextRole.SECONDARY, TextRole.LABEL -> ZZType.TextSm
        TextRole.CAPTION -> ZZType.TextXs
    }
    val weight: FontWeight = when (this) {
        TextRole.HEADING, TextRole.TITLE, TextRole.LABEL -> ZZType.WeightSemibold
        else -> ZZType.WeightRegular
    }
    val color = when (this) {
        TextRole.SECONDARY -> palette.ink2
        TextRole.CAPTION -> palette.ink3
        else -> palette.ink
    }
    return TextStyle(fontFamily = family, fontSize = size, fontWeight = weight, color = color)
}
