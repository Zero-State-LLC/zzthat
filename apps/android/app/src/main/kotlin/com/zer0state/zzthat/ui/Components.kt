package com.zer0state.zzthat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zer0state.zzthat.R
import com.zer0state.zzthat.design.ZZFonts
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.ReadCandidate
import llc.zerostate.zzthis.design.ZZRadius
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType
import java.text.BreakIterator

/** The minimum touch target on Android (runtime.md, Accessibility). */
val MinTouch: Dp = 48.dp

val ChipShape = RoundedCornerShape(ZZRadius.RadiusChip)

/** The copy key behind a string resource, for test tags: `message.` plus the key with underscores. */
@Composable
fun copyName(@StringRes id: Int): String = LocalContext.current.resources.getResourceEntryName(id)

/**
 * A screen: the paper background, the gutter, and the demo badge on every
 * Demo screen (runtime.md, Screens: Demo). Loading keeps the previous content
 * on screen and adds a small progress mark (design/UX.md, Loading and offline).
 */
@Composable
fun ZZScreen(
    demo: Boolean = false,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZZSpace.Gutter, vertical = ZZSpace.SpaceSm),
        verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceSm),
    ) {
        if (demo) DemoBadge()
        content()
        if (loading) LoadingMark()
    }
}

/** `common.demo_badge`, shown only when the data is the demo data. */
@Composable
fun DemoBadge() {
    val palette = LocalPalette.current
    Text(
        text = stringResource(R.string.common_demo_badge),
        style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextXs, fontWeight = ZZType.WeightMedium, color = palette.accentInk),
        modifier = Modifier
            .testTag("demo.badge")
            .background(palette.accentWash, ChipShape)
            .padding(horizontal = ZZSpace.SpaceXs, vertical = ZZSpace.Space3xs),
    )
}

/** `common.offline_banner` above a cached record (design/UX.md, Loading and offline). */
@Composable
fun OfflineBanner() {
    Text(
        text = stringResource(R.string.common_offline_banner),
        style = TextRole.SECONDARY.style(),
        modifier = Modifier
            .testTag("offline.banner")
            .fillMaxWidth()
            .background(LocalPalette.current.panel2, ChipShape)
            .padding(ZZSpace.SpaceXs),
    )
}

/** One sentence from the error table or an empty state. */
@Composable
fun Message(@StringRes key: Int, emphasis: Boolean = false) {
    val palette = LocalPalette.current
    Text(
        text = stringResource(key),
        style = TextRole.BODY.style(),
        modifier = Modifier
            .testTag("message." + copyName(key))
            .fillMaxWidth()
            .background(if (emphasis) palette.accentWash else palette.panel2, ChipShape)
            .padding(ZZSpace.SpaceSm),
    )
}

/** The small progress mark. The previous content stays on screen. */
@Composable
fun LoadingMark() {
    val label = stringResource(R.string.common_loading)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = LocalPalette.current.ink2, strokeWidth = 2.dp)
        Text(label, style = TextRole.CAPTION.style())
    }
}

/** A role-styled line of catalog text. */
@Composable
fun RoleText(text: String, role: TextRole, modifier: Modifier = Modifier) {
    Text(text = text, style = role.style(), modifier = modifier)
}

/**
 * A canonical code in the mono font, lowercase, wrapping, never truncated.
 * The check word sits in place in the accent ink.
 */
@Composable
fun CodeText(canonical: String, checkWord: String? = null, size: TextUnit = ZZType.TextMd, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val text = buildAnnotatedString {
        append(canonical)
        if (checkWord != null) {
            val start = canonical.lastIndexOf("-$checkWord-zz")
            if (start >= 0) addStyle(SpanStyle(color = palette.accentInk), start + 1, start + 1 + checkWord.length)
        }
    }
    SelectionContainer {
        Text(
            text = text,
            style = TextStyle(fontFamily = ZZFonts.mono, fontSize = size, fontWeight = ZZType.WeightMedium, color = palette.ink),
            modifier = modifier.semantics { contentDescription = canonical },
        )
    }
}

/** Text read from a photo or typed, shown as written in the mono font. */
@Composable
fun MonoText(text: String) {
    Text(
        text = text,
        style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextBase, fontWeight = ZZType.WeightMedium, color = LocalPalette.current.ink),
    )
}

enum class ButtonKind {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE,
}

/** A full-width button with a 48 dp target (runtime.md, Accessibility). */
@Composable
fun ZZButton(
    text: String,
    tag: String,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val background = if (kind == ButtonKind.PRIMARY) palette.accent else palette.panel
    val foreground = when (kind) {
        ButtonKind.PRIMARY -> palette.onAccent
        ButtonKind.SECONDARY -> palette.ink
        ButtonKind.DESTRUCTIVE -> palette.accentInk
    }
    var modifier = Modifier
        .testTag(tag)
        .fillMaxWidth()
        .defaultMinSize(minHeight = MinTouch)
        .clip(ChipShape)
        .background(background.copy(alpha = if (enabled) 1f else 0.45f), ChipShape)
    if (kind != ButtonKind.PRIMARY) {
        modifier = modifier.border(ZZSpace.RuleHair, if (kind == ButtonKind.DESTRUCTIVE) palette.accentInk else palette.ruleStrong, ChipShape)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ZZSpace.SpaceSm, vertical = ZZSpace.SpaceXs),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = (textStyle ?: TextStyle(fontFamily = ZZFonts.body, fontSize = ZZType.TextBase, fontWeight = ZZType.WeightSemibold))
                .copy(color = foreground.copy(alpha = if (enabled) 1f else 0.6f)),
        )
    }
}

@Composable
fun RetryButton(waiting: Boolean, onClick: () -> Unit) {
    ZZButton(stringResource(R.string.common_try_again), "common.try_again", ButtonKind.SECONDARY, enabled = !waiting, onClick = onClick)
}

/** A hairline panel, the site's card. */
@Composable
fun Modifier.panel(): Modifier {
    val palette = LocalPalette.current
    return fillMaxWidth()
        .background(palette.panel, ChipShape)
        .border(ZZSpace.RuleHair, palette.rule, ChipShape)
        .padding(ZZSpace.SpaceSm)
}

/** DEP-005 limits, counted the way the server counts them (runtime.md, Screens). */
object FieldLimits {
    const val TITLE = 120
    const val BODY = 4000
    const val REPORT_NOTE = 500

    /** Unicode code points, after trimming for the title and body (FR-007). */
    fun count(text: String, trimmed: Boolean = true): Int {
        val counted = if (trimmed) text.trim() else text
        return counted.codePointCount(0, counted.length)
    }

    /** Drops whole characters from the end until the text fits. */
    fun limit(text: String, max: Int, trimmed: Boolean = true): String {
        var result = text
        while (result.isNotEmpty() && count(result, trimmed) > max) {
            val iterator = BreakIterator.getCharacterInstance().apply { setText(result) }
            result = result.substring(0, iterator.preceding(result.length).coerceAtLeast(0))
        }
        return result
    }

    /** Line breaks become spaces, for a one-line field such as the title. */
    fun oneLine(text: String): String = text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')

    fun isBlank(text: String): Boolean = text.isBlank()
}

/**
 * A labeled field that stops input at [limit] Unicode code points after
 * trimming (runtime.md, Screens; DEP-005). A one-line field still wraps, so
 * a long title stays readable at large text sizes.
 */
@Composable
fun LimitedField(
    @StringRes label: Int,
    value: String,
    onValueChange: (String) -> Unit,
    limit: Int,
    tag: String,
    multiline: Boolean = false,
    countsTrimmed: Boolean = true,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val labelText = stringResource(label)
    Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.Space3xs)) {
        Text(labelText, style = TextRole.LABEL.style())
        BasicTextField(
            value = value,
            onValueChange = { typed ->
                val flat = if (multiline) typed else FieldLimits.oneLine(typed)
                onValueChange(FieldLimits.limit(flat, limit, countsTrimmed))
            },
            textStyle = TextStyle(
                fontFamily = if (mono) ZZFonts.mono else ZZFonts.body,
                fontSize = ZZType.TextBase,
                color = palette.ink,
            ),
            cursorBrush = SolidColor(palette.accentInk),
            minLines = if (multiline) 4 else 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (onDone != null) ImeAction.Go else ImeAction.Default,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onDone?.invoke() }),
            modifier = Modifier
                .testTag(tag)
                .semantics { contentDescription = labelText }
                .fillMaxWidth()
                .heightIn(min = MinTouch)
                .background(palette.panel, ChipShape)
                .border(ZZSpace.RuleHair, palette.ruleStrong, ChipShape)
                .padding(ZZSpace.SpaceXs),
        )
    }
}

/**
 * The photo with a numbered box around each candidate (design/UX.md, Scan
 * with the camera, step 2). Boxes are normalized, origin at the top left.
 */
@Composable
fun PhotoBoxes(image: ImageBitmap, candidates: List<ReadCandidate>) {
    val palette = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val numberStyle = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextXs, fontWeight = ZZType.WeightSemibold, color = palette.onAccent)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(image.width.toFloat() / image.height.toFloat())
            .clip(ChipShape)
            .border(ZZSpace.RuleHair, palette.rule, ChipShape),
    ) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 3.dp.toPx()
            candidates.forEachIndexed { offset, candidate ->
                candidate.boxes.forEachIndexed { boxIndex, box: NormRect ->
                    val left = (box.minX * size.width).toFloat()
                    val top = (box.minY * size.height).toFloat()
                    val width = (box.width * size.width).toFloat()
                    val height = (box.height * size.height).toFloat()
                    drawRect(
                        color = palette.accent,
                        topLeft = Offset(left - pad, top - pad),
                        size = Size(width + 2 * pad, height + 2 * pad),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    if (boxIndex == 0) {
                        val number = measurer.measure(AnnotatedString("${offset + 1}"), numberStyle)
                        val x = maxOf(left, 0f)
                        val y = maxOf(top - pad - number.size.height, 0f)
                        drawRect(palette.accent, Offset(x, y), Size(number.size.width + 8.dp.toPx(), number.size.height.toFloat()))
                        drawText(number, topLeft = Offset(x + 4.dp.toPx(), y))
                    }
                }
            }
        }
    }
}
