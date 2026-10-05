package com.zer0state.zzthat.account

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.common.SignInButton
import com.zer0state.zzthat.R
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.app.FlavorFeatures
import com.zer0state.zzthat.app.providerRes
import com.zer0state.zzthat.auth.GoogleIdTokens
import com.zer0state.zzthat.auth.ProviderToken
import com.zer0state.zzthat.auth.SignInFlow
import com.zer0state.zzthat.design.ZZFonts
import com.zer0state.zzthat.design.ZZLicenses
import com.zer0state.zzthat.ui.ButtonKind
import com.zer0state.zzthat.ui.ChipShape
import com.zer0state.zzthat.ui.DemoBadge
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Message
import com.zer0state.zzthat.ui.MinTouch
import com.zer0state.zzthat.ui.RoleText
import com.zer0state.zzthat.ui.TextRole
import com.zer0state.zzthat.ui.ZZButton
import com.zer0state.zzthat.ui.ZZScreen
import com.zer0state.zzthat.ui.panel
import com.zer0state.zzthat.ui.style
import llc.zerostate.zzthis.design.ZZRadius
import llc.zerostate.zzthis.design.ZZSpace
import llc.zerostate.zzthis.design.ZZType

data class AccountScreenState(
    val signedIn: Boolean,
    val provider: String? = null,
    val supportEmail: String? = null,
    val privacyUrl: String? = null,
    val deletePhase: DeletePhase = DeletePhase.Idle,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val demo: Boolean = false,
) {
    sealed interface DeletePhase {
        data object Idle : DeletePhase

        data object Deleting : DeletePhase

        data class Failure(@StringRes val key: Int) : DeletePhase
    }
}

/**
 * Account (design/UX.md): the provider, Sign out, Delete account, the support
 * contact and privacy link from build settings (FR-047), and
 * `account.licenses`. No profile form (ZQ7). Signed out, it shows Sign in.
 */
@Composable
fun AccountScreen(
    state: AccountScreenState,
    onSignOut: () -> Unit = {},
    onDelete: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onSupport: (String) -> Unit = {},
    onPrivacy: (String) -> Unit = {},
    signedOut: @Composable ColumnScope.() -> Unit = {},
) {
    ZZScreen(demo = state.demo, loading = state.loading || state.deletePhase == AccountScreenState.DeletePhase.Deleting) {
        if (state.signedIn) {
            val provider = state.provider?.let(::providerRes)
            if (provider != null) {
                RoleText(
                    stringResource(R.string.account_signed_in_with, stringResource(provider)),
                    TextRole.TITLE,
                    Modifier.testTag("account.signed_in_with"),
                )
            }
            ZZButton(stringResource(R.string.account_sign_out), "account.sign_out", ButtonKind.SECONDARY, onClick = onSignOut)
            (state.deletePhase as? AccountScreenState.DeletePhase.Failure)?.let { Message(it.key, emphasis = true) }
            ZZButton(
                stringResource(R.string.account_delete),
                "account.delete",
                ButtonKind.DESTRUCTIVE,
                enabled = !state.offline && state.deletePhase != AccountScreenState.DeletePhase.Deleting,
                onClick = onDelete,
            )
            if (state.offline) RoleText(stringResource(R.string.error_no_connection), TextRole.CAPTION)
        } else {
            signedOut()
        }
        AccountLinks(state.supportEmail, state.privacyUrl, onSupport, onPrivacy, onLicenses)
    }
}

@Composable
fun AccountLinks(supportEmail: String?, privacyUrl: String?, onSupport: (String) -> Unit, onPrivacy: (String) -> Unit, onLicenses: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.panel, ChipShape)
            .border(ZZSpace.RuleHair, palette.rule, ChipShape),
    ) {
        if (supportEmail != null) {
            LinkRow(stringResource(R.string.account_support, supportEmail), "account.support") { onSupport(supportEmail) }
            HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
        }
        if (privacyUrl != null) {
            LinkRow(stringResource(R.string.account_privacy), "account.privacy") { onPrivacy(privacyUrl) }
            HorizontalDivider(color = palette.rule, thickness = ZZSpace.RuleHair)
        }
        LinkRow(stringResource(R.string.account_licenses), "account.licenses", onClick = onLicenses)
    }
}

@Composable
private fun LinkRow(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = TextRole.BODY.style().copy(color = LocalPalette.current.accentInk),
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .defaultMinSize(minHeight = MinTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ZZSpace.SpaceSm, vertical = ZZSpace.SpaceXs),
    )
}

/**
 * Licenses (runtime.md, Screens): the bundled `NOTICE` and `OFL.txt`, then
 * the third-party notices the build generates (T087), verbatim, the one
 * FR-042 exception. The notices run to about two megabytes, so the screen is
 * a lazy list with ZZScreen's layout: each text is cut at line breaks into
 * runs of [LICENSE_RUN_LINES] lines that draw one panel together, and a run is
 * laid out only when it scrolls into view.
 */
@Composable
fun LicensesScreen(texts: List<String>, demo: Boolean) {
    val palette = LocalPalette.current
    val runs = remember(texts) { texts.map(::licenseRuns) }
    val style = TextStyle(fontFamily = ZZFonts.mono, fontSize = ZZType.TextXs, color = palette.ink)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.paper).testTag("licenses.list"),
        contentPadding = PaddingValues(horizontal = ZZSpace.Gutter, vertical = ZZSpace.SpaceSm),
    ) {
        if (demo) item(key = "demo") { DemoBadge() }
        item(key = "heading") {
            RoleText(
                stringResource(R.string.account_licenses),
                TextRole.HEADING,
                Modifier.padding(top = if (demo) ZZSpace.SpaceSm else 0.dp).semantics { heading() },
            )
        }
        runs.forEachIndexed { index, textRuns ->
            items(textRuns.size, key = { run -> "text-$index-$run" }) { run ->
                val first = run == 0
                val last = run == textRuns.lastIndex
                val panel = if (first && last) Modifier.panel() else Modifier.panelRun(first, last, palette.panel, palette.rule)
                SelectionContainer(modifier = Modifier.padding(top = if (first) ZZSpace.SpaceSm else 0.dp).then(panel)) {
                    Text(textRuns[run], style = style)
                }
            }
        }
    }
}

/** Lines in one run of a license text on the Licenses screen. */
const val LICENSE_RUN_LINES = 100

/**
 * [text] cut at every [LICENSE_RUN_LINES]th line break, the break itself
 * dropped, so the runs joined with line breaks give back [text] exactly. A
 * final line break stays with the last run.
 */
fun licenseRuns(text: String): List<String> {
    val runs = mutableListOf<String>()
    var start = 0
    var lines = 0
    var index = text.indexOf('\n')
    while (index in 0 until text.length - 1) {
        lines++
        if (lines == LICENSE_RUN_LINES) {
            runs += text.substring(start, index)
            start = index + 1
            lines = 0
        }
        index = text.indexOf('\n', index + 1)
    }
    runs += text.substring(start)
    return runs
}

/**
 * One run of a long license text: [panel]'s fill, rule, and padding, open at
 * the edges the run shares with the runs before and after it, so the runs of
 * one text draw one panel.
 */
private fun Modifier.panelRun(top: Boolean, bottom: Boolean, fill: Color, rule: Color): Modifier = fillMaxWidth()
    .drawBehind {
        val stroke = ZZSpace.RuleHair.toPx()
        // The panel's shape, stretched past the open edges, then clipped to the run.
        val over = ZZRadius.RadiusChip.toPx() + stroke
        val above = if (top) 0f else over
        val below = if (bottom) 0f else over
        clipRect {
            translate(top = -above) {
                drawOutline(ChipShape.createOutline(Size(size.width, size.height + above + below), layoutDirection, this), fill)
                // Modifier.border keeps the rule inside the shape.
                translate(left = stroke / 2, top = stroke / 2) {
                    val inner = Size(size.width - stroke, size.height + above + below - stroke)
                    drawOutline(ChipShape.createOutline(inner, layoutDirection, this), rule, style = Stroke(stroke))
                }
            }
        }
    }
    .padding(
        start = ZZSpace.SpaceSm,
        end = ZZSpace.SpaceSm,
        top = if (top) ZZSpace.SpaceSm else 0.dp,
        bottom = if (bottom) ZZSpace.SpaceSm else 0.dp,
    )

/** The third-party notices in `assets/licenses`, written at build time by the app's `generateThirdPartyNotices` task (T087). */
const val THIRD_PARTY_NOTICES = "third-party-notices.txt"

/**
 * `assets/licenses/NOTICE` and `assets/licenses/OFL.txt`, from the pin
 * (design.md, Sync), then `assets/licenses/third-party-notices.txt` (T087).
 */
fun bundledLicenses(context: Context): List<String> = (ZZLicenses.FILES + THIRD_PARTY_NOTICES).mapNotNull { name ->
    try {
        context.assets.open("${ZZLicenses.FOLDER}/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (error: Exception) {
        null
    }
}

// MARK: Sign in

data class SignInScreenState(
    val providers: List<TokenRequest.Provider>,
    val phase: Phase = Phase.Idle,
    val offline: Boolean = false,
    val demo: Boolean = false,
    val loadingProviders: Boolean = false,
) {
    sealed interface Phase {
        data object Idle : Phase

        data object SigningIn : Phase

        data class Failure(@StringRes val key: Int) : Phase
    }
}

/**
 * Sign in (design/UX.md): `signin.why`, then the provider buttons the
 * platform draws: Google on Android (FR-040, ZQ23). Local builds add
 * `signin.dev`. Demo buttons do not call a provider.
 */
@Composable
fun SignInScreen(state: SignInScreenState, onSignIn: (TokenRequest.Provider) -> Unit = {}) {
    ZZScreen(demo = state.demo, loading = state.phase == SignInScreenState.Phase.SigningIn || state.loadingProviders) {
        SignInContent(state, onSignIn)
    }
}

/** The sign-in block on its own, so a signed-out Account shows it inside its own screen. */
@Composable
fun SignInContent(state: SignInScreenState, onSignIn: (TokenRequest.Provider) -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceSm)) {
        RoleText(stringResource(R.string.signin_title), TextRole.HEADING, Modifier.semantics { heading() })
        RoleText(stringResource(R.string.signin_why), TextRole.BODY)
        (state.phase as? SignInScreenState.Phase.Failure)?.let { Message(it.key, emphasis = true) }
        if (state.offline) Message(R.string.error_no_connection)
        val enabled = !state.offline && state.phase != SignInScreenState.Phase.SigningIn
        Column(verticalArrangement = Arrangement.spacedBy(ZZSpace.SpaceXs)) {
            for (provider in state.providers) {
                when (provider) {
                    TokenRequest.Provider.google -> GoogleButton(enabled) { onSignIn(TokenRequest.Provider.google) }
                    TokenRequest.Provider.dev -> ZZButton(
                        stringResource(R.string.signin_dev),
                        "signin.dev",
                        ButtonKind.SECONDARY,
                        enabled = enabled,
                    ) { onSignIn(TokenRequest.Provider.dev) }
                    // Android does not offer Apple (ZQ23).
                    TokenRequest.Provider.apple -> Unit
                }
            }
        }
    }
}

/** Credential Manager for this activity, with the build's web client id. */
fun googleSignIn(app: AppModel, activityContext: Context): suspend (String) -> ProviderToken =
    { nonce -> GoogleIdTokens.request(activityContext, app.config.googleWebClientId, nonce) }

/**
 * Google's own button, `com.google.android.gms.common.SignInButton`, from
 * play-services-base (runtime.md, Auth step 4). It only draws; its tap starts
 * the nonce flow, and only then does Credential Manager run.
 */
@Composable
fun GoogleButton(enabled: Boolean, onClick: () -> Unit) {
    val dark = LocalPalette.current == com.zer0state.zzthat.design.Palette.Dark
    AndroidView(
        factory = { context -> SignInButton(context).apply { setSize(SignInButton.SIZE_WIDE) } },
        update = { button ->
            button.setColorScheme(if (dark) SignInButton.COLOR_DARK else SignInButton.COLOR_LIGHT)
            button.isEnabled = enabled
            button.setOnClickListener { onClick() }
        },
        modifier = Modifier
            .testTag("signin.google")
            .fillMaxWidth()
            .height(MinTouch),
    )
}

object SignInProviders {
    /**
     * Discovery decides the buttons (FR-040). Android draws Google, once its
     * web client id is set (or as a demo button); never Apple (ZQ23); and
     * `dev` only in Local (FR-048).
     */
    fun shown(app: AppModel): List<TokenRequest.Provider> {
        val shown = mutableListOf<TokenRequest.Provider>()
        if ("google" in app.authProviders && (app.isDemo || GoogleIdTokens.isConfigured(app.config.googleWebClientId))) {
            shown += TokenRequest.Provider.google
        }
        if (app.config.offersDeveloperSignIn) shown += TokenRequest.Provider.dev
        return shown
    }
}

/**
 * Sign-in (runtime.md, Auth; zzThis spec 005 FR-020): the server nonce, the
 * provider's own sign-in with it, then the exchange. A failure leaves no half
 * session. Developer sign-in sends the fixed Local token with provider `dev`.
 * [google] runs Credential Manager for the nonce.
 */
suspend fun signIn(
    provider: TokenRequest.Provider,
    app: AppModel,
    google: suspend (nonce: String) -> ProviderToken,
): SignInFlow.Outcome {
    val outcome = SignInFlow(app.service).run(provider, app.isOnline, app.isDemo) { nonce ->
        when (provider) {
            TokenRequest.Provider.google -> if (GoogleIdTokens.isConfigured(app.config.googleWebClientId)) google(nonce) else ProviderToken.Failed
            TokenRequest.Provider.dev -> FlavorFeatures.developerIdToken?.let(ProviderToken::Token) ?: ProviderToken.Failed
            TokenRequest.Provider.apple -> ProviderToken.Failed
        }
    }
    if (outcome == SignInFlow.Outcome.SignedIn) app.completeSignIn(provider)
    return outcome
}
