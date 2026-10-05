package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.infrastructure.Serializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** The JSON settings for the generated models. */
@OptIn(ExperimentalSerializationApi::class)
object ZZJson {
    /**
     * Requests and success bodies. Defaults are written, so mint sends `kind`
     * `plain`, and nulls are left out, so no request carries an empty field
     * (mint sends no code string, sign-in no `authorization_code` or
     * redirect URI). A success body may carry a field the contract adds later.
     * Times use the generated adapters, which read RFC 3339 with or without
     * fractional seconds (runtime.md, API client step 9). Lenient, as the
     * generated Serializer is, so the integer enum `expires_in: 900` decodes.
     */
    val wire: Json = Json {
        serializersModule = Serializer.kotlinxSerializationAdapters
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Error bodies. Their schemas set `additionalProperties: false`, so the
     * 404 body is exactly `{"error":"not-found"}` and the 429 body exactly
     * `{"error":"rate-limited"}`; another field means the body is refused.
     */
    val strict: Json = Json {
        serializersModule = Serializer.kotlinxSerializationAdapters
        ignoreUnknownKeys = false
    }
}
