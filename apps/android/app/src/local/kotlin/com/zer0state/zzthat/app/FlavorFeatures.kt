package com.zer0state.zzthat.app

/** Local: the local zzThis Worker, and developer sign-in (FR-048, zzThis spec 005 FR-022). */
object FlavorFeatures {
    /** The fixed developer ID token, sent with the server nonce and provider `dev`. Only this flavor has it. */
    val developerIdToken: String? = "dev:local-tester"
}
