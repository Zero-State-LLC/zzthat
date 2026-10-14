package com.zer0state.zzthat.app

import kotlinx.serialization.Serializable

// The route list (runtime.md, Navigation), as type-safe Navigation Compose
// routes. Code detail and Edit record take a code id (OwnedCode.id).

@Serializable object ScanGraph

@Serializable object ScanHome

@Serializable object TypeDestination

@Serializable data class ClarifyDestination(val canonical: String, val camera: Boolean)

@Serializable data class ResolveDestination(val canonical: String)

@Serializable object CreateGraph

@Serializable object CreateHome

@Serializable data class MintedDestination(val codeId: String)

@Serializable data class WriteCheckDestination(val canonical: String)

@Serializable object CodesGraph

@Serializable object CodesHome

@Serializable data class DetailDestination(val codeId: String)

@Serializable data class EditDestination(val codeId: String)

/** Saved (FR-050): the codes saved on this device, opened from My codes. */
@Serializable object SavedDestination

/** A saved code's Resolve, inside My codes, so the tab does not change. */
@Serializable data class SavedResolveDestination(val canonical: String)

@Serializable object AccountDestination

@Serializable object LicensesDestination

@Serializable object SignInDestination

/** Report for [canonical]. [creator] is the Resolve `creator` reference, so the sent state can offer Block this creator (FR-035). */
@Serializable data class ReportDestination(val canonical: String, val creator: String? = null)
