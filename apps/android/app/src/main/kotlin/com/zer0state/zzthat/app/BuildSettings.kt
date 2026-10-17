package com.zer0state.zzthat.app

import com.zer0state.zzthat.BuildConfig

/** The three builds (runtime.md, Build configurations). */
enum class BuildKind {
    DEMO,
    LOCAL,
    PROD,
}

/**
 * The build's settings from BuildConfig. Nothing here is a secret, and
 * Release values are injected at build time, not committed.
 */
data class BuildSettings(
    val kind: BuildKind,
    /** Null in Demo, which makes no network call, and in a Release build without its injected origin. */
    val apiOrigin: String?,
    val supportEmail: String?,
    val privacyUrl: String?,
    /** The Google web client id, or the "unset" placeholder until Danny creates the client. */
    val googleWebClientId: String,
    /**
     * The Terms of Use (design/UX.md, Terms), a build setting like the privacy
     * link. Release injects it. Null when no URL is configured: then
     * `terms.link` and `account.terms` are hidden, and the Terms step still
     * asks for both confirmations.
     */
    val termsUrl: String? = null,
) {
    val isDemo: Boolean get() = kind == BuildKind.DEMO

    /** FR-048: only Local offers developer sign-in. Release compiles it out. */
    val offersDeveloperSignIn: Boolean get() = FlavorFeatures.developerIdToken != null

    companion object {
        /**
         * The zzThat community links on Account (design/UX.md, Account: early
         * release and community links; RM-111). Build constants, not copy: the
         * same addresses as the zzThis site footer, `src/content/navigation.ts`.
         */
        const val DISCORD_URL = "https://discord.gg/hwXvVKYzn4"
        const val INSTAGRAM_URL = "https://www.instagram.com/zzthatcom/"

        val current: BuildSettings = BuildSettings(
            kind = when (BuildConfig.BUILD_KIND) {
                "local" -> BuildKind.LOCAL
                "prod" -> BuildKind.PROD
                else -> BuildKind.DEMO
            },
            apiOrigin = BuildConfig.API_ORIGIN.ifBlank { null },
            supportEmail = BuildConfig.SUPPORT_EMAIL.ifBlank { null },
            privacyUrl = BuildConfig.PRIVACY_URL.ifBlank { null },
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            termsUrl = BuildConfig.TERMS_URL.ifBlank { null },
        )
    }
}
