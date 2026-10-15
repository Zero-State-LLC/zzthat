package com.zer0state.zzthat.api

import com.zer0state.zzthat.api.generated.apis.AuthApi
import com.zer0state.zzthat.api.generated.apis.CodesApi
import com.zer0state.zzthat.api.generated.apis.MetaApi
import com.zer0state.zzthat.api.generated.apis.RecordsApi
import com.zer0state.zzthat.api.generated.apis.ReportsApi
import com.zer0state.zzthat.api.generated.models.Account
import com.zer0state.zzthat.api.generated.models.AddRecordVersion201Response
import com.zer0state.zzthat.api.generated.models.Code
import com.zer0state.zzthat.api.generated.models.CodePage
import com.zer0state.zzthat.api.generated.models.CreateReport202Response
import com.zer0state.zzthat.api.generated.models.Discovery
import com.zer0state.zzthat.api.generated.models.EverydayRecord
import com.zer0state.zzthat.api.generated.models.MintRequest
import com.zer0state.zzthat.api.generated.models.Nonce
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.OwnerRecord
import com.zer0state.zzthat.api.generated.models.RefreshRequest
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.Resolve
import com.zer0state.zzthat.api.generated.models.ResolveRecord
import com.zer0state.zzthat.api.generated.models.ResolveShare
import com.zer0state.zzthat.api.generated.models.RevokeCode200Response
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.api.generated.models.TokenResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The in-memory implementation of the generated interfaces for Demo builds
 * (runtime.md, API client step 4; tasks.md T017). It makes no network call
 * and issues nothing: the fixture codes are not real codes.
 *
 * Fixture text is zzThis's own demo data at the pin: the resolve example in
 * the pinned OpenAPI file, the demo records in `src/content/demo.ts` (docs/
 * SPEC.md Section 4.2), and the fixture-7 issuable codes in vectors.json. It
 * is the same data the iOS DemoAPI answers with.
 */
class DemoApi : MetaApi, AuthApi, CodesApi, RecordsApi, ReportsApi {
    data class Record(val title: String, val body: String)

    private class Owned(var code: OwnedCode, var record: Record, var version: Int)

    private val lock = Any()
    private val owned = mutableListOf(
        Owned(owned("demo-code-1", "zz-copper-lantern-sky-zz", "sky", OwnedCode.Status.active, "demo-record-1"),
            RECORDS.getValue("zz-copper-lantern-sky-zz"), 1),
        Owned(owned("demo-code-2", "zz-river-falcon-sky-zz", "sky", OwnedCode.Status.active, "demo-record-2"),
            RECORDS.getValue("zz-river-falcon-sky-zz"), 1),
        Owned(owned("demo-code-3", "zz-b2-4-zz", null, OwnedCode.Status.revoked, "demo-record-3"),
            RECORDS.getValue("zz-b2-4-zz"), 1),
    )
    private var minted = 0

    /** The generated interfaces, all answered from memory. */
    fun apis(): ZZApis = ZZApis(meta = this, auth = this, codes = this, records = this, reports = this)

    // MARK: MetaApi

    override suspend fun getDiscovery(xZZContract: MetaApi.XZZContractGetDiscovery): Response<Discovery> = ok(DISCOVERY)

    override suspend fun getOpenApi(xZZContract: MetaApi.XZZContractGetOpenApi): Response<Any> = notReady()

    // MARK: AuthApi

    override suspend fun createNonce(xZZContract: AuthApi.XZZContractCreateNonce): Response<Nonce> =
        ok(Nonce("demo-nonce", 600))

    override suspend fun exchangeToken(
        xZZContract: AuthApi.XZZContractExchangeToken,
        tokenRequest: TokenRequest,
    ): Response<TokenResponse> = ok(TOKENS)

    override suspend fun refreshToken(
        xZZContract: AuthApi.XZZContractRefreshToken,
        refreshRequest: RefreshRequest,
    ): Response<TokenResponse> = ok(TOKENS)

    override suspend fun revokeToken(xZZContract: AuthApi.XZZContractRevokeToken, refreshRequest: RefreshRequest): Response<Unit> =
        noContent()

    override suspend fun getMe(xZZContract: AuthApi.XZZContractGetMe): Response<Account> = ok(ACCOUNT)

    override suspend fun deleteMe(xZZContract: AuthApi.XZZContractDeleteMe): Response<Unit> = noContent()

    // MARK: CodesApi

    override suspend fun mintCode(xZZContract: CodesApi.XZZContractMintCode, mintRequest: MintRequest): Response<Code> =
        synchronized(lock) { ok(nextCode("demo-record-new", Record(mintRequest.record.title, mintRequest.record.body), 3), 201) }

    override suspend fun rerollCode(xZZContract: CodesApi.XZZContractRerollCode, id: String): Response<Code> = synchronized(lock) {
        val index = owned.indexOfFirst { it.code.id == id }
        if (index < 0) return notFound()
        val current = owned[index]
        if (current.code.rerollsRemaining <= 0 || current.code.status != OwnedCode.Status.active) {
            return error(403, """{"error":"reroll-cap"}""")
        }
        // The previous words are retired and leave the owner list (spec 005 FR-006).
        owned.removeAt(index)
        ok(nextCode(current.code.recordId, current.record, current.code.rerollsRemaining - 1))
    }

    override suspend fun listMyCodes(xZZContract: CodesApi.XZZContractListMyCodes, limit: Int?, cursor: String?): Response<CodePage> =
        synchronized(lock) { ok(CodePage(owned.map { it.code }, nextCursor = null)) }

    override suspend fun revokeCode(xZZContract: CodesApi.XZZContractRevokeCode, id: String): Response<RevokeCode200Response> =
        synchronized(lock) {
            val row = owned.firstOrNull { it.code.id == id } ?: return notFound()
            row.code = row.code.copy(status = OwnedCode.Status.revoked)
            ok(RevokeCode200Response(RevokeCode200Response.Status.revoked))
        }

    override suspend fun resolveCode(xZZContract: CodesApi.XZZContractResolveCode, code: String): Response<Resolve> =
        synchronized(lock) {
            val mine = owned.firstOrNull { it.code.canonical == code && it.code.status == OwnedCode.Status.active }?.record
            val revoked = owned.any { it.code.canonical == code && it.code.status != OwnedCode.Status.active }
            val record = mine ?: (if (revoked) null else RECORDS[code]) ?: return notFound()
            ok(
                Resolve(
                    view = Resolve.View.`public`,
                    canonical = code,
                    record = ResolveRecord(record.title, record.body, FIXED_DATE),
                    share = ResolveShare(text = code, url = null),
                    creator = DEMO_CREATOR,
                ),
            )
        }

    // MARK: RecordsApi

    override suspend fun getRecord(xZZContract: RecordsApi.XZZContractGetRecord, id: String): Response<OwnerRecord> =
        synchronized(lock) {
            val row = owned.firstOrNull { it.code.recordId == id } ?: return notFound()
            ok(OwnerRecord(row.code.recordId, row.version, row.record.title, row.record.body, FIXED_DATE))
        }

    override suspend fun addRecordVersion(
        xZZContract: RecordsApi.XZZContractAddRecordVersion,
        id: String,
        everydayRecord: EverydayRecord,
    ): Response<AddRecordVersion201Response> = synchronized(lock) {
        val row = owned.firstOrNull { it.code.recordId == id } ?: return notFound()
        row.record = Record(everydayRecord.title, everydayRecord.body)
        row.code = row.code.copy(title = everydayRecord.title)
        row.version += 1
        ok(AddRecordVersion201Response(id, row.version), 201)
    }

    // MARK: ReportsApi

    override suspend fun createReport(
        xZZContract: ReportsApi.XZZContractCreateReport,
        reportRequest: ReportRequest,
    ): Response<CreateReport202Response> = ok(CreateReport202Response("demo-report"), 202)

    // MARK: Fixtures

    private fun nextCode(recordId: String, record: Record, rerolls: Int): Code {
        val next = MINT_SEQUENCE[minted % MINT_SEQUENCE.size]
        minted += 1
        val id = "demo-minted-$minted"
        owned.add(0, Owned(owned(id, next.first, next.second, OwnedCode.Status.active, recordId, record.title, rerolls), record, 1))
        return Code(id, next.first, next.second, Code.Status.active, recordId, Code.Scope.free_public, rerolls, FIXED_DATE)
    }

    companion object {
        /** 2026-10-04T00:00:00Z. */
        val FIXED_DATE: OffsetDateTime = OffsetDateTime.of(2026, 10, 4, 0, 0, 0, 0, ZoneOffset.UTC)

        /** The Resolve `creator` of every demo record (zzThis RM-112), so Block this creator shows in Demo. */
        const val DEMO_CREATOR = "demo-creator"

        /** Public demo records by canonical code. */
        val RECORDS: Map<String, Record> = mapOf(
            "zz-kathy-lost-cat-zz" to Record("Lost cat", "Answers on this page."),
            "zz-copper-lantern-sky-zz" to Record(
                "Crate, field supply (demo)",
                "NSN: DEMO-0000-00-000-0001\nDocument number: DEMO-DOC-0001\nHand receipt: DEMO-HR-01",
            ),
            "zz-river-falcon-sky-zz" to Record("Parcel (demo)", "Reference: DEMO-PARCEL-01\nStatus: Ready for drop-off (demo)"),
            "zz-b2-4-zz" to Record("Duffel group B2, item 4 (demo)", "Hand receipt: DEMO-HR-02"),
        )

        /** The words a demo mint and its re-rolls show (canonical, check word), from the fixture-7 list. */
        val MINT_SEQUENCE: List<Pair<String, String>> = listOf(
            "zz-copper-sky-river-zz" to "river",
            "zz-lantern-maple-copper-zz" to "copper",
            "zz-sky-river-maple-zz" to "maple",
            "zz-harbor-falcon-maple-zz" to "maple",
        )

        val DISCOVERY = Discovery(
            contractVersion = Discovery.ContractVersion._1,
            freePublic = true,
            authProviders = listOf(Discovery.AuthProviders.apple, Discovery.AuthProviders.google),
            scopes = listOf(Discovery.Scopes.free_public),
            wordlistVersion = "fixture-7",
            photoReads = false,
        )

        val ACCOUNT = Account("demo-account", listOf(Account.Providers.google), FIXED_DATE)

        private val TOKENS = TokenResponse("demo-access", TokenResponse.TokenType.Bearer, TokenResponse.ExpiresIn._900, "demo-refresh")

        private val JSON = "application/json".toMediaType()

        private fun owned(
            id: String,
            canonical: String,
            checkWord: String?,
            status: OwnedCode.Status,
            recordId: String,
            title: String = RECORDS.getValue(canonical).title,
            rerolls: Int = 0,
        ) = OwnedCode(id, canonical, checkWord, status, recordId, OwnedCode.Scope.free_public, rerolls, FIXED_DATE, title)

        private fun raw(code: Int): okhttp3.Response = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://demo.invalid/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("demo")
            .header(CONTRACT_HEADER, CONTRACT_VERSION)
            .header("Cache-Control", "no-store")
            .build()

        private fun <T> ok(body: T, code: Int = 200): Response<T> = Response.success(body, raw(code))

        private fun noContent(): Response<Unit> = Response.success(null, raw(204))

        private fun <T> error(code: Int, json: String): Response<T> = Response.error(json.toResponseBody(JSON), raw(code))

        private fun <T> notFound(): Response<T> = error(404, """{"error":"not-found"}""")

        private fun <T> notReady(): Response<T> = error(503, """{"error":"not-ready"}""")
    }
}
