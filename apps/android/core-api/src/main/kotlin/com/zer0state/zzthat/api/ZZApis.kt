package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.apis.AuthApi
import com.zer0state.zzthat.api.generated.apis.CodesApi
import com.zer0state.zzthat.api.generated.apis.MetaApi
import com.zer0state.zzthat.api.generated.apis.RecordsApi
import com.zer0state.zzthat.api.generated.apis.ReportsApi
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The generated interfaces the apps call (spec.md, API contract). A live
 * build passes Retrofit's implementations; a Demo build passes [DemoApi].
 * The apps never call `GET /v1/audit`, and v1 never uploads a photo.
 */
class ZZApis(
    val meta: MetaApi,
    val auth: AuthApi,
    val codes: CodesApi,
    val records: RecordsApi,
    val reports: ReportsApi,
)

/** Builds the live client over OkHttp and Retrofit (runtime.md, API client step 2). */
object ZZApiFactory {
    private val JSON = "application/json".toMediaType()

    /**
     * A live service on [origin]. Refresh runs on its own client with only
     * the contract interceptor and its own dispatcher, so it never recurses
     * into the 401 retry and never waits behind the calls it unblocks.
     * [networkInterceptors] is a test seam.
     */
    fun live(
        origin: String,
        storage: RefreshTokenStorage,
        networkInterceptors: List<Interceptor> = emptyList(),
    ): ZZService = build(origin, storage, emptyList(), networkInterceptors)

    /** A build with no API origin: every call answers `error.unavailable` (it never falls back to demo data). */
    fun unconfigured(storage: RefreshTokenStorage): ZZService =
        build("https://origin.invalid", storage, listOf(UnconfiguredInterceptor()), emptyList())

    private fun build(
        origin: String,
        storage: RefreshTokenStorage,
        first: List<Interceptor>,
        networkInterceptors: List<Interceptor>,
    ): ZZService {
        val tokens = TokenStore(storage)
        val refreshApis = apis(origin, client(first, networkInterceptors, tokens = null))
        tokens.refresher = Refresher { refreshToken -> ZZService.refresh(refreshApis.auth, refreshToken) }
        return ZZService(apis(origin, client(first, networkInterceptors, tokens)), tokens)
    }

    private fun client(first: List<Interceptor>, networkInterceptors: List<Interceptor>, tokens: TokenStore?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // FR-049: no HTTP cache. FR-041 is the only resolve cache.
            .cache(null)
            .dispatcher(Dispatcher())
            .callTimeout(30, TimeUnit.SECONDS)
        first.forEach(builder::addInterceptor)
        builder.addInterceptor(ContractInterceptor())
        if (tokens != null) {
            builder.addInterceptor(AuthInterceptor(tokens)).authenticator(RefreshAuthenticator(tokens))
        }
        networkInterceptors.forEach(builder::addNetworkInterceptor)
        return builder.build()
    }

    private fun apis(origin: String, client: OkHttpClient): ZZApis {
        val retrofit = Retrofit.Builder()
            .baseUrl(origin.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(ZZJson.wire.asConverterFactory(JSON))
            .build()
        return ZZApis(
            meta = retrofit.create(MetaApi::class.java),
            auth = retrofit.create(AuthApi::class.java),
            codes = retrofit.create(CodesApi::class.java),
            records = retrofit.create(RecordsApi::class.java),
            reports = retrofit.create(ReportsApi::class.java),
        )
    }
}
