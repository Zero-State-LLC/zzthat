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
) {
    val isDemo: Boolean get() = kind == BuildKind.DEMO

    /** FR-048: only Local offers developer sign-in. Release compiles it out. */
    val offersDeveloperSignIn: Boolean get() = FlavorFeatures.developerIdToken != null

    companion object {
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
        )
    }
}
