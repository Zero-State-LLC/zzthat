package com.zer0state.zzthat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zer0state.zzthat.account.AccountScreen
import com.zer0state.zzthat.account.AccountScreenState
import com.zer0state.zzthat.account.LicensesScreen
import com.zer0state.zzthat.account.SignInContent
import com.zer0state.zzthat.account.SignInScreen
import com.zer0state.zzthat.account.SignInScreenState
import com.zer0state.zzthat.account.bundledLicenses
import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.api.generated.models.OwnedCode
import com.zer0state.zzthat.api.generated.models.OwnerRecord
import com.zer0state.zzthat.api.generated.models.ReportRequest
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.codes.CodeDetailModel
import com.zer0state.zzthat.codes.CodeDetailScreen
import com.zer0state.zzthat.codes.CodeDetailScreenState
import com.zer0state.zzthat.codes.CodesModel
import com.zer0state.zzthat.codes.CodesScreen
import com.zer0state.zzthat.codes.CodesScreenState
import com.zer0state.zzthat.codes.ConfirmCard
import com.zer0state.zzthat.codes.EditRecordModel
import com.zer0state.zzthat.codes.EditRecordScreen
import com.zer0state.zzthat.codes.EditRecordScreenState
import com.zer0state.zzthat.create.CreateModel
import com.zer0state.zzthat.create.CreateScreen
import com.zer0state.zzthat.create.CreateScreenState
import com.zer0state.zzthat.create.MintedModel
import com.zer0state.zzthat.create.MintedScreen
import com.zer0state.zzthat.create.MintedScreenState
import com.zer0state.zzthat.create.WriteCheckModel
import com.zer0state.zzthat.create.WriteCheckScreen
import com.zer0state.zzthat.create.WriteCheckScreenState
import com.zer0state.zzthat.grammar.NormRect
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.WordListLoader
import com.zer0state.zzthat.report.ReportScreen
import com.zer0state.zzthat.report.ReportState
import com.zer0state.zzthat.resolve.ResolvePhase
import com.zer0state.zzthat.resolve.ResolveScreen
import com.zer0state.zzthat.resolve.ResolvedView
import com.zer0state.zzthat.scan.ClarifyScreen
import com.zer0state.zzthat.scan.ClarifyScreenState
import com.zer0state.zzthat.scan.ClarifyStep
import com.zer0state.zzthat.scan.DemoPhoto
import com.zer0state.zzthat.scan.ScanScreen
import com.zer0state.zzthat.scan.ScanScreenState
import com.zer0state.zzthat.scan.TypeScreen
import com.zer0state.zzthat.scan.TypeScreenState
import com.zer0state.zzthat.ui.ZZTheme
import llc.zerostate.zzthis.design.ZZColor
import llc.zerostate.zzthis.design.ZZSpace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * One snapshot per screen state in runtime.md, Screens, light and dark
 * (SC-13, tasks.md T085), on the demo flavor with DeviceConfig.PIXEL_5. The
 * cases are the iOS SnapshotTests cases: a state the table marks "Not used"
 * has no snapshot, and Loading is snapshotted where the screen waits on the
 * camera, the reader, or a call.
 */
class SnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")

    private class Case(val name: String, val content: @Composable () -> Unit)

    private fun snap(cases: List<Case>) {
        for (case in cases) {
            for (dark in listOf(false, true)) {
                paparazzi.snapshot(name = "${case.name}-${if (dark) "dark" else "light"}") {
                    ZZTheme(dark = dark) { case.content() }
                }
            }
        }
    }

    // MARK: Fixtures

    private val context get() = paparazzi.context
    private val fixture by lazy { DemoPhoto.make(context, listOf(DemoPhoto.CANONICAL)) }
    private val twoCodes by lazy { DemoPhoto.make(context, listOf("zz-river-maple-sky-zz", "zz-b2-4-zz")) }
    private val noCode by lazy { DemoPhoto.make(context, listOf("the buzz word")) }

    /** The pipeline as the Demo build runs it: the bundled lists, with the demo server's `wordlist_version` (fixture-7). */
    private fun candidates(photo: DemoPhoto.Photo): List<ReadCandidate> {
        val lists = WordListLoader.load(File(requireNotNull(System.getProperty("zz.wordlists"))))
        val lines = photo.lines.map { (text, box) -> RecognizedLine(text, 0.97, box) }
        return ReadPipeline.of(lists, DemoApi.DISCOVERY.wordlistVersion).read(lines).candidates
    }

    private val demoRecord = DemoApi.RECORDS.getValue("zz-kathy-lost-cat-zz")
    private val demoView = ResolvedView("zz-kathy-lost-cat-zz", demoRecord.title, demoRecord.body, "zz-kathy-lost-cat-zz")
    private val demoMint = DemoApi.MINT_SEQUENCE[0]
    private val demoRows = listOf(
        row("demo-code-1", "zz-copper-lantern-sky-zz", "sky", OwnedCode.Status.active, "demo-record-1"),
        row("demo-code-2", "zz-river-maple-sky-zz", "sky", OwnedCode.Status.active, "demo-record-2"),
        row("demo-code-3", "zz-b2-4-zz", null, OwnedCode.Status.revoked, "demo-record-3"),
    )
    private val demoOwnerRecord = DemoApi.RECORDS.getValue("zz-copper-lantern-sky-zz").let {
        OwnerRecord("demo-record-1", 1, it.title, it.body, DemoApi.FIXED_DATE)
    }
    private val signedInAccount = AccountScreenState(
        signedIn = true,
        provider = "google",
        supportEmail = "support@zer0state.com",
        privacyUrl = "https://zero-state-llc.github.io/zzthis/privacy/",
    )

    private fun row(id: String, canonical: String, checkWord: String?, status: OwnedCode.Status, recordId: String) =
        OwnedCode(id, canonical, checkWord, status, recordId, OwnedCode.Scope.free_public, 0, DemoApi.FIXED_DATE, DemoApi.RECORDS.getValue(canonical).title)

    @Composable
    private fun scan(state: ScanScreenState) = ScanScreen(state)

    @Composable
    private fun account(state: AccountScreenState) = AccountScreen(state) {
        SignInContent(SignInScreenState(listOf(TokenRequest.Provider.google), offline = state.offline))
    }

    /** A system dialog over its Demo screen: the screen, the window dim, and the dialog card. */
    @Composable
    private fun confirm(background: @Composable () -> Unit, title: Int, body: Int, action: Int, tag: String) {
        Box(modifier = Modifier.fillMaxSize()) {
            background()
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(ZZColor.ShadowDark).padding(ZZSpace.SpaceMd)) {
                ConfirmCard(title, body, action, tag)
            }
        }
    }

    // MARK: Cases

    @Test
    fun scan() = snap(
        listOf(
            Case("scan-loading") { scan(ScanScreenState(photo = fixture.bitmap.asImageBitmap(), reading = true, cameraAvailable = true)) },
            Case("scan-empty") { scan(ScanScreenState(photo = noCode.bitmap.asImageBitmap(), message = R.string.scan_no_code, cameraAvailable = true)) },
            Case("scan-error") { scan(ScanScreenState(message = R.string.scan_camera_off, cameraAvailable = true)) },
            Case("scan-offline") {
                scan(ScanScreenState(fixture.bitmap.asImageBitmap(), candidates(fixture), message = R.string.resolve_not_on_device, cameraAvailable = true))
            },
            Case("scan-demo") { scan(ScanScreenState(fixture.bitmap.asImageBitmap(), candidates(fixture), demo = true)) },
            Case("pick-offline") {
                scan(ScanScreenState(twoCodes.bitmap.asImageBitmap(), candidates(twoCodes), message = R.string.resolve_not_on_device, cameraAvailable = true))
            },
            Case("pick-demo") { scan(ScanScreenState(twoCodes.bitmap.asImageBitmap(), candidates(twoCodes), demo = true)) },
            Case("clarify-error") { ClarifyScreen(ClarifyScreenState(listOf("copper", "maple", "sky"), ClarifyStep.CheckMismatch)) },
            Case("clarify-offline") { ClarifyScreen(ClarifyScreenState(listOf("b2", "smith", "1"), ClarifyStep.Confirm)) },
            Case("clarify-demo") {
                ClarifyScreen(ClarifyScreenState(listOf("coper", "lantern", "sky"), ClarifyStep.NearWord(1, "coper", listOf("copper")), demo = true))
            },
            Case("type-empty") { TypeScreen("", {}, TypeScreenState()) },
            Case("type-error") { TypeScreen("zz-copper-lantern", {}, TypeScreenState(message = R.string.type_malformed)) },
            Case("type-offline") { TypeScreen(DemoPhoto.CANONICAL, {}, TypeScreenState(message = R.string.resolve_not_on_device)) },
            Case("type-demo") { TypeScreen(DemoPhoto.CANONICAL, {}, TypeScreenState(demo = true)) },
        ),
    )

    @Test
    fun resolve() = snap(
        listOf(
            Case("resolve-loading") { ResolveScreen(demoView.canonical, ResolvePhase.Loading(null), demo = false) },
            Case("resolve-error") { ResolveScreen(demoView.canonical, ResolvePhase.NotFound, demo = false) },
            Case("resolve-offline") { ResolveScreen(demoView.canonical, ResolvePhase.Record(demoView, offline = true), demo = false) },
            Case("resolve-offline-not-on-device") { ResolveScreen(demoView.canonical, ResolvePhase.NotOnDevice, demo = false) },
            Case("resolve-demo") { ResolveScreen(demoView.canonical, ResolvePhase.Record(demoView, offline = false), demo = true) },
            Case("report-loading") { ReportScreen(ReportState(ReportRequest.Reason.spam, phase = ReportState.Phase.Sending)) },
            Case("report-empty") { ReportScreen(ReportState()) },
            Case("report-error") {
                ReportScreen(ReportState(ReportRequest.Reason.spam, phase = ReportState.Phase.Failure(R.string.resolve_rate_limited)))
            },
            Case("report-offline") { ReportScreen(ReportState(ReportRequest.Reason.spam, offline = true)) },
            Case("report-demo") { ReportScreen(ReportState(ReportRequest.Reason.personalMinusData, demo = true)) },
        ),
    )

    @Test
    fun create() = snap(
        listOf(
            Case("create-loading") {
                CreateScreen(demoRecord.title, demoRecord.body, CreateScreenState(CreateScreenState.Gate.FORM, CreateModel.Phase.Minting))
            },
            Case("create-empty") { CreateScreen("", "", CreateScreenState(CreateScreenState.Gate.SIGNED_OUT)) },
            Case("create-error") {
                CreateScreen(
                    demoRecord.title,
                    demoRecord.body,
                    CreateScreenState(CreateScreenState.Gate.FORM, CreateModel.Phase.Failure(R.string.create_content_refused)),
                )
            },
            Case("create-offline") { CreateScreen(demoRecord.title, demoRecord.body, CreateScreenState(CreateScreenState.Gate.FORM, offline = true)) },
            Case("create-demo") { CreateScreen(demoRecord.title, demoRecord.body, CreateScreenState(CreateScreenState.Gate.FORM, demo = true)) },
            Case("unavailable-empty") { CreateScreen("", "", CreateScreenState(CreateScreenState.Gate.UNAVAILABLE)) },
            Case("unavailable-offline") { CreateScreen("", "", CreateScreenState(CreateScreenState.Gate.UNAVAILABLE, offline = true)) },
            Case("unavailable-demo") { CreateScreen("", "", CreateScreenState(CreateScreenState.Gate.UNAVAILABLE, demo = true)) },
            Case("minted-loading") {
                MintedScreen(MintedScreenState(demoMint.first, demoMint.second, capReached = false, phase = MintedModel.Phase.Rerolling))
            },
            Case("minted-error") {
                MintedScreen(MintedScreenState(demoMint.first, demoMint.second, capReached = true, phase = MintedModel.Phase.Failure(R.string.minted_reroll_cap)))
            },
            Case("minted-offline") { MintedScreen(MintedScreenState(demoMint.first, demoMint.second, capReached = false, offline = true)) },
            Case("minted-demo") { MintedScreen(MintedScreenState(demoMint.first, demoMint.second, capReached = false, demo = true)) },
            Case("writecheck-loading") {
                WriteCheckScreen(WriteCheckScreenState(DemoPhoto.CANONICAL, fixture.bitmap.asImageBitmap(), reading = true, cameraAvailable = true))
            },
            Case("writecheck-empty") { WriteCheckScreen(WriteCheckScreenState(DemoPhoto.CANONICAL, cameraAvailable = true)) },
            Case("writecheck-error") {
                WriteCheckScreen(
                    WriteCheckScreenState(demoMint.first, fixture.bitmap.asImageBitmap(), candidates(fixture), verdict = WriteCheckModel.Verdict.MISMATCH, cameraAvailable = true),
                )
            },
            Case("writecheck-offline") {
                WriteCheckScreen(
                    WriteCheckScreenState(DemoPhoto.CANONICAL, fixture.bitmap.asImageBitmap(), candidates(fixture), verdict = WriteCheckModel.Verdict.MATCH, cameraAvailable = true),
                )
            },
            Case("writecheck-demo") {
                WriteCheckScreen(
                    WriteCheckScreenState(DemoPhoto.CANONICAL, fixture.bitmap.asImageBitmap(), candidates(fixture), verdict = WriteCheckModel.Verdict.MATCH, demo = true),
                )
            },
        ),
    )

    @Test
    fun codes() = snap(
        listOf(
            Case("codes-loading") { CodesScreen(CodesScreenState(true, demoRows, CodesModel.Phase.Loading)) },
            Case("codes-empty") { CodesScreen(CodesScreenState(true, phase = CodesModel.Phase.Loaded)) },
            Case("codes-empty-signed-out") { CodesScreen(CodesScreenState(false)) },
            Case("codes-error") { CodesScreen(CodesScreenState(true, phase = CodesModel.Phase.Failure(R.string.error_forbidden))) },
            Case("codes-offline") { CodesScreen(CodesScreenState(true, phase = CodesModel.Phase.Failure(R.string.error_no_connection))) },
            Case("codes-demo") { CodesScreen(CodesScreenState(true, demoRows, CodesModel.Phase.Loaded, demo = true)) },
            Case("detail-loading") { CodeDetailScreen(CodeDetailScreenState(demoRows[0], phase = CodeDetailModel.Phase.Loading)) },
            Case("detail-error") { CodeDetailScreen(CodeDetailScreenState(phase = CodeDetailModel.Phase.NotFound)) },
            Case("detail-offline") { CodeDetailScreen(CodeDetailScreenState(phase = CodeDetailModel.Phase.Failure(R.string.error_no_connection))) },
            Case("detail-demo") { CodeDetailScreen(CodeDetailScreenState(demoRows[0], demoOwnerRecord, CodeDetailModel.Phase.Loaded, demo = true)) },
            Case("edit-loading") { EditRecordScreen("", "", EditRecordScreenState(EditRecordModel.Phase.Loading)) },
            Case("edit-empty") { EditRecordScreen("", demoOwnerRecord.body, EditRecordScreenState(EditRecordModel.Phase.Editing)) },
            Case("edit-error") {
                EditRecordScreen(
                    demoOwnerRecord.title,
                    demoOwnerRecord.body,
                    EditRecordScreenState(EditRecordModel.Phase.Failure(R.string.create_content_refused)),
                )
            },
            Case("edit-offline") { EditRecordScreen(demoOwnerRecord.title, demoOwnerRecord.body, EditRecordScreenState(EditRecordModel.Phase.Editing, offline = true)) },
            Case("edit-demo") { EditRecordScreen(demoOwnerRecord.title, demoOwnerRecord.body, EditRecordScreenState(EditRecordModel.Phase.Editing, demo = true)) },
            Case("revoke-confirm-loading") {
                CodeDetailScreen(CodeDetailScreenState(demoRows[0], demoOwnerRecord, CodeDetailModel.Phase.Loaded, CodeDetailModel.RevokePhase.Revoking))
            },
            Case("revoke-confirm-error") {
                CodeDetailScreen(
                    CodeDetailScreenState(demoRows[0], demoOwnerRecord, CodeDetailModel.Phase.Loaded, CodeDetailModel.RevokePhase.Failure(R.string.error_nothing_saved)),
                )
            },
            Case("revoke-confirm-offline") {
                CodeDetailScreen(
                    CodeDetailScreenState(demoRows[0], demoOwnerRecord, CodeDetailModel.Phase.Loaded, CodeDetailModel.RevokePhase.Failure(R.string.error_no_connection)),
                )
            },
            Case("revoke-confirm-demo") {
                confirm(
                    { CodeDetailScreen(CodeDetailScreenState(demoRows[0], demoOwnerRecord, CodeDetailModel.Phase.Loaded, demo = true)) },
                    R.string.revoke_title,
                    R.string.revoke_body,
                    R.string.revoke_action,
                    "revoke.confirm",
                )
            },
        ),
    )

    @Test
    fun account() = snap(
        listOf(
            Case("signin-loading") { SignInScreen(SignInScreenState(listOf(TokenRequest.Provider.google), SignInScreenState.Phase.SigningIn)) },
            Case("signin-empty") { SignInScreen(SignInScreenState(listOf(TokenRequest.Provider.google))) },
            Case("signin-error") {
                SignInScreen(SignInScreenState(listOf(TokenRequest.Provider.google), SignInScreenState.Phase.Failure(R.string.signin_failed)))
            },
            Case("signin-offline") { SignInScreen(SignInScreenState(listOf(TokenRequest.Provider.google), offline = true)) },
            Case("signin-demo") { SignInScreen(SignInScreenState(listOf(TokenRequest.Provider.google), demo = true)) },
            Case("account-loading") { account(signedInAccount.copy(loading = true)) },
            Case("account-empty") { account(signedInAccount.copy(signedIn = false)) },
            Case("account-error") { account(signedInAccount.copy(deletePhase = AccountScreenState.DeletePhase.Failure(R.string.delete_failed))) },
            Case("account-offline") { account(signedInAccount.copy(offline = true)) },
            Case("account-demo") { account(signedInAccount.copy(demo = true)) },
            Case("delete-confirm-loading") { account(signedInAccount.copy(deletePhase = AccountScreenState.DeletePhase.Deleting)) },
            Case("delete-confirm-error") { account(signedInAccount.copy(deletePhase = AccountScreenState.DeletePhase.Failure(R.string.delete_failed))) },
            Case("delete-confirm-offline") {
                account(signedInAccount.copy(offline = true, deletePhase = AccountScreenState.DeletePhase.Failure(R.string.error_no_connection)))
            },
            Case("delete-confirm-demo") {
                confirm({ account(signedInAccount.copy(demo = true)) }, R.string.delete_title, R.string.delete_body, R.string.delete_action, "delete.confirm")
            },
            Case("licenses-offline") { LicensesScreen(bundledLicenses(context), demo = false) },
            Case("licenses-demo") { LicensesScreen(bundledLicenses(context), demo = true) },
        ),
    )

    /** The Licenses screen shows exactly the bundled files (FR-042). */
    @Test
    fun theLicensesAreBundled() {
        assertEquals(2, bundledLicenses(context).size)
    }
}
