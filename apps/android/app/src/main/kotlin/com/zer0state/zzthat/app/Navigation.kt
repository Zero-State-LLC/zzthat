package com.zer0state.zzthat.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.zer0state.zzthat.R
import com.zer0state.zzthat.account.AccountScreen
import com.zer0state.zzthat.account.AccountScreenState
import com.zer0state.zzthat.account.LicensesScreen
import com.zer0state.zzthat.account.SignInContent
import com.zer0state.zzthat.account.SignInProviders
import com.zer0state.zzthat.account.SignInScreen
import com.zer0state.zzthat.account.SignInScreenState
import com.zer0state.zzthat.account.bundledLicenses
import com.zer0state.zzthat.account.googleSignIn
import com.zer0state.zzthat.account.signIn
import com.zer0state.zzthat.api.ApiFailure
import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.CallContext
import com.zer0state.zzthat.api.generated.models.TokenRequest
import com.zer0state.zzthat.api.message
import com.zer0state.zzthat.auth.SignInFlow
import com.zer0state.zzthat.codes.CodeDetailModel
import com.zer0state.zzthat.codes.CodeDetailScreen
import com.zer0state.zzthat.codes.CodeDetailScreenState
import com.zer0state.zzthat.codes.CodesModel
import com.zer0state.zzthat.codes.CodesScreen
import com.zer0state.zzthat.codes.CodesScreenState
import com.zer0state.zzthat.codes.ConfirmDialog
import com.zer0state.zzthat.codes.EditRecordModel
import com.zer0state.zzthat.codes.EditRecordScreen
import com.zer0state.zzthat.codes.EditRecordScreenState
import com.zer0state.zzthat.create.CreateModel
import com.zer0state.zzthat.create.CreateScreen
import com.zer0state.zzthat.create.CreateScreenState
import com.zer0state.zzthat.create.MintedScreen
import com.zer0state.zzthat.create.MintedScreenState
import com.zer0state.zzthat.create.WriteCheckModel
import com.zer0state.zzthat.create.WriteCheckScreen
import com.zer0state.zzthat.create.WriteCheckScreenState
import com.zer0state.zzthat.grammar.Grammar
import com.zer0state.zzthat.grammar.ReadSource
import com.zer0state.zzthat.report.ReportModel
import com.zer0state.zzthat.report.ReportScreen
import com.zer0state.zzthat.resolve.ResolveLoader
import com.zer0state.zzthat.resolve.ResolveModel
import com.zer0state.zzthat.resolve.ResolvePhase
import com.zer0state.zzthat.resolve.ResolveScreen
import com.zer0state.zzthat.saved.SavedScreen
import com.zer0state.zzthat.saved.SavedScreenState
import com.zer0state.zzthat.scan.CandidateAction
import com.zer0state.zzthat.scan.ClarifyInput
import com.zer0state.zzthat.scan.ClarifyModel
import com.zer0state.zzthat.scan.ClarifyScreen
import com.zer0state.zzthat.scan.ClarifyScreenState
import com.zer0state.zzthat.scan.ClarifyStep
import com.zer0state.zzthat.scan.CameraPipeline
import com.zer0state.zzthat.scan.CaptureImage
import com.zer0state.zzthat.scan.DemoPhoto
import com.zer0state.zzthat.scan.ScanModel
import com.zer0state.zzthat.scan.ScanScreen
import com.zer0state.zzthat.scan.ScanScreenState
import com.zer0state.zzthat.scan.TypeModel
import com.zer0state.zzthat.scan.TypeScreen
import com.zer0state.zzthat.scan.TypeScreenState
import com.zer0state.zzthat.ui.Back
import com.zer0state.zzthat.ui.BarAction
import com.zer0state.zzthat.ui.Frame
import com.zer0state.zzthat.ui.LocalPalette
import com.zer0state.zzthat.ui.Tab
import com.zer0state.zzthat.ui.TabBar
import com.zer0state.zzthat.ui.openLink
import com.zer0state.zzthat.ui.rememberPhotoCapture
import com.zer0state.zzthat.ui.share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import llc.zerostate.zzthis.design.ZZMotion

/** The state that lives as long as the tabs: one scan, the Create form, and the owner list. */
class TabModels {
    val scan = ScanModel()
    val create = CreateModel()
    val codes = CodesModel()

    /** The code whose record was just saved, so Code detail says `edit.saved`. */
    var savedCodeId by mutableStateOf<String?>(null)
}

/**
 * Opens a canonical code. Offline with nothing cached, resolve waits and the
 * screen says so (US6 acceptance 4); a cached copy still opens.
 */
suspend fun openCanonical(
    canonical: String,
    app: AppModel,
    nav: NavHostController,
    destination: (String) -> Any = ::ResolveDestination,
): Int? {
    if (!app.isOnline && ResolveLoader.offline(canonical, app) == ResolvePhase.NotOnDevice) {
        app.lastCanonical.set(canonical)
        return R.string.resolve_not_on_device
    }
    nav.onMain { navigate(destination(canonical)) }
    return null
}

/** Navigation after a suspension point runs on the main thread, where the back stack lives. */
suspend fun NavHostController.onMain(block: NavHostController.() -> Unit) = withContext(Dispatchers.Main.immediate) { block() }

/** The back arrow of a pushed screen (Danny, #43 C3). */
private fun back(nav: NavHostController): Back = Back { nav.popBackStack() }

/** The tab shell: three tabs, each with its own back stack, and Account, Sign in, Report, and Licenses over them. */
@Composable
fun Root(app: AppModel, models: TabModels, camera: CameraPipeline, reducedMotion: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val hierarchy = entry?.destination?.hierarchy.orEmpty().toList()
    val tab = when {
        hierarchy.any { it.hasRoute(CreateGraph::class) } -> Tab.CREATE
        hierarchy.any { it.hasRoute(CodesGraph::class) } -> Tab.CODES
        else -> Tab.SCAN
    }
    val onTab = hierarchy.any { it.hasRoute(ScanGraph::class) || it.hasRoute(CreateGraph::class) || it.hasRoute(CodesGraph::class) }
    // Reduced motion removes transitions; state changes stay (runtime.md, Accessibility).
    val enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(ZZMotion.DurShortMs, easing = ZZMotion.EaseOut))
    val exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(ZZMotion.DurShortMs, easing = ZZMotion.EaseIn))
    Scaffold(
        containerColor = LocalPalette.current.paper,
        bottomBar = {
            if (onTab) {
                TabBar(tab) { selected ->
                    val graph: Any = when (selected) {
                        Tab.SCAN -> ScanGraph
                        Tab.CREATE -> CreateGraph
                        Tab.CODES -> CodesGraph
                    }
                    nav.navigate(graph) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        // The keyboard pushes the screen up, so Open and Save stay in reach.
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = ScanGraph,
                enterTransition = { enter },
                exitTransition = { exit },
                popEnterTransition = { enter },
                popExitTransition = { exit },
            ) {
                navigation<ScanGraph>(startDestination = ScanHome) {
                    composable<ScanHome> { ScanRoute(app, models.scan, camera, nav) }
                    composable<TypeDestination> { TypeRoute(app, nav) }
                    composable<ClarifyDestination> { ClarifyRoute(app, it.toRoute(), nav) }
                    composable<ResolveDestination> { ResolveRoute(app, it.toRoute<ResolveDestination>().canonical, nav) }
                }
                navigation<CreateGraph>(startDestination = CreateHome) {
                    composable<CreateHome> { CreateRoute(app, models.create, nav) }
                    composable<MintedDestination> { MintedRoute(app, models.create, it.toRoute<MintedDestination>().codeId, nav) }
                    composable<WriteCheckDestination> { WriteCheckRoute(app, it.toRoute<WriteCheckDestination>().canonical, camera, nav) }
                }
                navigation<CodesGraph>(startDestination = CodesHome) {
                    composable<CodesHome> { CodesRoute(app, models, nav) }
                    composable<DetailDestination> { DetailRoute(app, models, it.toRoute<DetailDestination>().codeId, nav) }
                    composable<EditDestination> { EditRoute(app, models, it.toRoute<EditDestination>().codeId, nav) }
                    composable<SavedDestination> { SavedRoute(app, nav) }
                    composable<SavedResolveDestination> { ResolveRoute(app, it.toRoute<SavedResolveDestination>().canonical, nav) }
                }
                composable<AccountDestination> { AccountRoute(app, nav) }
                composable<LicensesDestination> { LicensesRoute(app, nav) }
                composable<SignInDestination> { SignInRoute(app, nav) }
                composable<ReportDestination> { ReportRoute(app, it.toRoute(), nav) }
            }
        }
    }
}

/** The Account action on the title bar of every tab (runtime.md, Navigation). */
@Composable
private fun AccountAction(nav: NavHostController) {
    BarAction(stringResource(R.string.nav_account), "nav.account") { nav.navigate(AccountDestination) { launchSingleTop = true } }
}

// MARK: Scan

@Composable
private fun ScanRoute(app: AppModel, scan: ScanModel, camera: CameraPipeline, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun open(canonical: String) {
        scope.launch { scan.message = openCanonical(canonical, app, nav) }
    }
    suspend fun read(image: CaptureImage) {
        scan.read(image, app, camera)?.let(::open)
    }
    fun act(action: CandidateAction?) {
        when (action) {
            is CandidateAction.Resolve -> open(action.canonical)
            is CandidateAction.Clarify -> nav.navigate(ClarifyDestination(Grammar.format(action.input.parts), camera = true))
            is CandidateAction.Message -> scan.message = action.key
            null -> Unit
        }
    }
    val capture = rememberPhotoCapture(
        app,
        onPhoto = { read(it) },
        onUnreadable = { scan.message = R.string.scan_no_code },
        onCameraOff = { scan.cameraIsOff() },
    )
    // Leaving the Scan tab ends the scan and deletes its photo (FR-004);
    // opening Type, Clarify, or Resolve from it does not.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val inScan = nav.currentBackStackEntry?.destination?.hierarchy?.any { it.hasRoute(ScanGraph::class) } == true
            if (!inScan) scan.end(app)
        }
    }
    Frame(stringResource(R.string.nav_scan), actions = { AccountAction(nav) }) {
        ScanScreen(
            state = ScanScreenState(
                photo = scan.photo?.asImageBitmap(),
                rows = scan.rows,
                reading = scan.reading,
                message = scan.message,
                demo = app.isDemo,
                cameraAvailable = capture.cameraAvailable,
            ),
            onTakePhoto = {
                if (app.isDemo) {
                    // Demo reads the fixture photo on the device instead of the camera.
                    scope.launch { read(DemoPhoto.make(context, listOf(DemoPhoto.CANONICAL)).image) }
                } else {
                    capture.takePhoto()
                }
            },
            onPickPhoto = capture.pickPhoto,
            onTypeInstead = { nav.navigate(TypeDestination) },
            onSelect = { row -> scope.launch { act(scan.select(row, app, camera)) } },
        )
    }
}

@Composable
private fun TypeRoute(app: AppModel, nav: NavHostController) {
    val model = remember { TypeModel().apply { if (app.isDemo) text = DemoPhoto.CANONICAL } }
    val scope = rememberCoroutineScope()
    Frame(stringResource(R.string.type_field_label), back(nav)) {
        TypeScreen(
            text = model.text,
            onTextChange = {
                model.text = it
                model.message = null
            },
            state = TypeScreenState(message = model.message, demo = app.isDemo),
            onOpen = {
                when (val action = model.action(app.pipeline)) {
                    is CandidateAction.Resolve -> scope.launch { model.message = openCanonical(action.canonical, app, nav) }
                    is CandidateAction.Clarify -> nav.navigate(ClarifyDestination(Grammar.format(action.input.parts), camera = false))
                    is CandidateAction.Message -> model.message = action.key
                }
            },
        )
    }
}

@Composable
private fun ClarifyRoute(app: AppModel, destination: ClarifyDestination, nav: NavHostController) {
    val model = remember(destination) {
        val code = Grammar.parse(destination.canonical).codeOrNull()
        ClarifyModel(
            ClarifyInput(
                parts = code?.words.orEmpty(),
                kind = code?.kind ?: com.zer0state.zzthat.grammar.CodeKind.PLAIN,
                source = if (destination.camera) ReadSource.CAMERA else ReadSource.TYPED,
            ),
        )
    }
    var message by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    fun advance() {
        val step = model.step(app.pipeline)
        if (step is ClarifyStep.Done) scope.launch { message = openCanonical(step.canonical, app, nav) }
    }
    Frame(stringResource(R.string.scan_clarify_title), back(nav)) {
        ClarifyScreen(
            state = ClarifyScreenState(model.parts, model.step(app.pipeline), app.isDemo),
            onPick = { word, position ->
                model.pick(word, position)
                advance()
            },
            onKeep = {
                model.keepAsWritten(it)
                advance()
            },
            onConfirm = {
                model.confirm()
                advance()
            },
            onTypeInstead = {
                nav.popBackStack()
                nav.navigate(TypeDestination)
            },
            onRetake = { nav.popBackStack(ScanHome, inclusive = false) },
            message = message,
        )
    }
}

@Composable
private fun ResolveRoute(app: AppModel, canonical: String, nav: NavHostController) {
    val model = remember(canonical) { ResolveModel(canonical) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(canonical) { model.loadOnce(app) }
    // A hide or block from the Report sheet, or the Account reset, shows here on return.
    LaunchedEffect(app.hiddenRevision) { model.sync(app) }
    // The Account screen's Clear saved codes shows here on return.
    LaunchedEffect(app.savedRevision) { model.syncSaved(app) }
    Frame(stringResource(R.string.nav_scan), back(nav)) {
        ResolveScreen(
            canonical = canonical,
            phase = model.phase,
            demo = app.isDemo,
            waiting = model.waiting,
            saved = model.saved,
            onRetry = { scope.launch { model.load(app) } },
            onShare = { share(context, it) },
            onReport = { nav.navigate(ReportDestination(canonical, model.creator)) },
            onHide = { scope.launch { model.hide(app) } },
            onUnhide = { scope.launch { model.unhide(app) } },
            onBlock = { scope.launch { model.block(app) } },
            onUnblock = { scope.launch { model.unblock(app) } },
            onSave = { scope.launch { model.save(app) } },
            onUnsave = { scope.launch { model.unsave(app) } },
        )
    }
}

@Composable
private fun ReportRoute(app: AppModel, destination: ReportDestination, nav: NavHostController) {
    val canonical = destination.canonical
    val model = remember(destination) { ReportModel(canonical, destination.creator) }
    val scope = rememberCoroutineScope()
    val offline = !app.isOnline
    LaunchedEffect(app.isDemo, offline) { model.state = model.state.copy(demo = app.isDemo, offline = offline) }
    LaunchedEffect(canonical) { model.loadHidden(app) }
    Frame(null) {
        ReportScreen(
            state = model.state,
            onReason = { model.state = model.state.copy(reason = it) },
            onNote = { model.state = model.state.copy(note = it) },
            onSend = { scope.launch { model.send(app) } },
            onDone = { nav.popBackStack() },
            onHide = { scope.launch { model.hide(app) } },
            onBlock = { scope.launch { model.block(app) } },
        )
    }
}

// MARK: Create

@Composable
private fun CreateRoute(app: AppModel, model: CreateModel, nav: NavHostController) {
    var checking by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // GET /v1 before Create (spec.md, API contract).
        model.loadDraft(app)
        app.refreshDiscovery()
        checking = false
    }
    val online = app.isOnline
    LaunchedEffect(online) { if (!online && model.hasDraft) model.saveDraft(app) }
    val gate = when {
        model.mintUnavailable || app.discovery?.freePublic == false -> CreateScreenState.Gate.UNAVAILABLE
        checking && app.discovery == null && app.isOnline -> CreateScreenState.Gate.LOADING
        app.isSignedIn -> CreateScreenState.Gate.FORM
        else -> CreateScreenState.Gate.SIGNED_OUT
    }
    Frame(stringResource(R.string.nav_create), actions = { AccountAction(nav) }) {
        CreateScreen(
            title = model.title,
            body = model.body,
            state = CreateScreenState(gate, model.phase, offline = !online, demo = app.isDemo),
            onTitle = {
                model.title = it
                if (!app.isOnline) scope.launch { model.saveDraft(app) }
            },
            onBody = {
                model.body = it
                if (!app.isOnline) scope.launch { model.saveDraft(app) }
            },
            onSubmit = { scope.launch { model.mint(app)?.let { nav.onMain { navigate(MintedDestination(it.id)) } } } },
            onSignIn = { nav.navigate(SignInDestination) },
        )
    }
}

@Composable
private fun MintedRoute(app: AppModel, create: CreateModel, codeId: String, nav: NavHostController) {
    val model = create.mintedModel(codeId)
    val scope = rememberCoroutineScope()
    Frame(stringResource(R.string.nav_create), back(nav)) {
        if (model == null) {
            // The tab that minted this code is gone (the process restarted): Create starts again.
            LaunchedEffect(codeId) { nav.onMain { popBackStack(CreateHome, inclusive = false) } }
        } else {
            MintedScreen(
                state = MintedScreenState(
                    canonical = model.code.canonical,
                    checkWord = model.code.checkWord,
                    capReached = model.capReached,
                    phase = model.phase,
                    offline = !app.isOnline,
                    demo = app.isDemo,
                    kept = model.kept,
                ),
                onReroll = { scope.launch { model.reroll(app) } },
                onWriteCheck = { nav.navigate(WriteCheckDestination(model.code.canonical)) },
                onKeep = model::keep,
            )
        }
    }
}

@Composable
private fun WriteCheckRoute(app: AppModel, canonical: String, camera: CameraPipeline, nav: NavHostController) {
    val model = remember(canonical) { WriteCheckModel(canonical) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = rememberPhotoCapture(
        app,
        onPhoto = { model.read(it, app, camera) },
        onUnreadable = {},
        onCameraOff = { model.cameraIsOff() },
    )
    androidx.compose.runtime.DisposableEffect(canonical) {
        onDispose { model.end(app) }
    }
    Frame(stringResource(R.string.minted_write_check), back(nav)) {
        WriteCheckScreen(
            state = WriteCheckScreenState(
                canonical = canonical,
                photo = model.photo?.asImageBitmap(),
                rows = model.rows,
                reading = model.reading,
                verdict = model.verdict,
                message = model.message,
                demo = app.isDemo,
                cameraAvailable = capture.cameraAvailable,
            ),
            onTakePhoto = {
                if (app.isDemo) {
                    // The fixture photo shows the minted code, drawn on the device.
                    scope.launch { model.read(DemoPhoto.make(context, listOf(canonical)).image, app, camera) }
                } else {
                    capture.takePhoto()
                }
            },
            onPickPhoto = capture.pickPhoto,
            onSelect = { row -> scope.launch { model.pick(row, app, camera) } },
        )
    }
}

// MARK: My codes

@Composable
private fun CodesRoute(app: AppModel, models: TabModels, nav: NavHostController) {
    val codes = models.codes
    val scope = rememberCoroutineScope()
    LaunchedEffect(app.isSignedIn, app.ownerRevision, app.isOnline) { codes.reloadIfNeeded(app) }
    LaunchedEffect(Unit) { models.savedCodeId = null }
    Frame(stringResource(R.string.nav_my_codes), actions = { AccountAction(nav) }) {
        CodesScreen(
            state = CodesScreenState(app.isSignedIn, codes.rows, codes.phase, app.isDemo),
            onSignIn = { nav.navigate(SignInDestination) },
            onSelect = { nav.navigate(DetailDestination(it.id)) },
            onLastRow = { scope.launch { codes.loadMore(app) } },
            onRetry = { scope.launch { codes.reload(app) } },
            onSaved = { nav.navigate(SavedDestination) },
        )
    }
}

/** Saved (FR-050): the list on this device, read again after every save, removal, or clear. A row opens Resolve. */
@Composable
private fun SavedRoute(app: AppModel, nav: NavHostController) {
    val codes by produceState(emptyList<String>(), app.savedRevision) { value = app.saved.list() }
    var message by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Frame(stringResource(R.string.saved_title), back(nav)) {
        SavedScreen(
            state = SavedScreenState(codes, message, app.isDemo),
            onSelect = { canonical ->
                scope.launch { message = openCanonical(canonical, app, nav, ::SavedResolveDestination) }
            },
        )
    }
}

@Composable
private fun DetailRoute(app: AppModel, models: TabModels, codeId: String, nav: NavHostController) {
    val model = remember(codeId) { CodeDetailModel(codeId, models.codes.row(codeId)) }
    var confirming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(codeId, app.ownerRevision) { model.load(app) }
    Frame(stringResource(R.string.nav_my_codes), back(nav)) {
        CodeDetailScreen(
            state = CodeDetailScreenState(
                row = model.row,
                record = model.record,
                phase = model.phase,
                revokePhase = model.revokePhase,
                saved = models.savedCodeId == codeId,
                demo = app.isDemo,
            ),
            onShare = { share(context, it) },
            onEdit = { nav.navigate(EditDestination(codeId)) },
            onRevoke = { confirming = true },
            onRetry = { scope.launch { model.load(app) } },
        )
        if (confirming) {
            ConfirmDialog(
                title = R.string.revoke_title,
                body = R.string.revoke_body,
                action = R.string.revoke_action,
                tag = "revoke.confirm",
                onConfirm = {
                    confirming = false
                    scope.launch { model.revoke(app) }
                },
                onCancel = { confirming = false },
            )
        }
    }
}

@Composable
private fun EditRoute(app: AppModel, models: TabModels, codeId: String, nav: NavHostController) {
    val model = remember(codeId) { EditRecordModel(codeId, models.codes.row(codeId)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(codeId) { model.loadOnce(app) }
    Frame(stringResource(R.string.detail_edit), back(nav)) {
        EditRecordScreen(
            title = model.title,
            body = model.body,
            state = EditRecordScreenState(model.phase, app.isDemo, offline = !app.isOnline),
            onTitle = { model.title = it },
            onBody = { model.body = it },
            onSave = {
                scope.launch {
                    if (model.save(app)) {
                        // Back to Code detail with `edit.saved` (design/UX.md, Edit record).
                        models.savedCodeId = codeId
                        nav.onMain { popBackStack() }
                    }
                }
            },
        )
    }
}

// MARK: Account and Sign in

@Composable
private fun AccountRoute(app: AppModel, nav: NavHostController) {
    var deletePhase by remember { mutableStateOf<AccountScreenState.DeletePhase>(AccountScreenState.DeletePhase.Idle) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var signInPhase by remember { mutableStateOf<SignInScreenState.Phase>(SignInScreenState.Phase.Idle) }
    var unhideAllDone by remember { mutableStateOf(false) }
    var clearSavedDone by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(app.isSignedIn) {
        // GET /v1/me names the provider. Offline, the cached provider shows.
        if (app.isSignedIn && app.isOnline) {
            loading = true
            (app.service.me() as? ApiResult.Ok)?.let { app.noteAccount(it.value) }
            loading = false
        }
    }
    Frame(stringResource(R.string.account_title), actions = { BarAction(stringResource(R.string.common_done), "account.done") { nav.popBackStack() } }) {
        AccountScreen(
            state = AccountScreenState(
                signedIn = app.isSignedIn,
                provider = app.provider,
                supportEmail = app.config.supportEmail,
                privacyUrl = app.config.privacyUrl,
                deletePhase = deletePhase,
                loading = loading,
                offline = !app.isOnline,
                demo = app.isDemo,
                unhideAllDone = unhideAllDone,
                clearSavedDone = clearSavedDone,
            ),
            onSignOut = { scope.launch { app.signOut() } },
            onDelete = { confirmingDelete = true },
            onLicenses = { nav.navigate(LicensesDestination) },
            onSupport = { openLink(context, "mailto:$it") },
            onPrivacy = { openLink(context, it) },
            onUnhideAll = {
                scope.launch {
                    app.unhideAll()
                    unhideAllDone = true
                }
            },
            onClearSaved = {
                scope.launch {
                    app.clearSaved()
                    clearSavedDone = true
                }
            },
        ) {
            SignInContent(
                SignInScreenState(SignInProviders.shown(app), signInPhase, offline = !app.isOnline, demo = app.isDemo),
            ) { provider ->
                signInPhase = SignInScreenState.Phase.SigningIn
                scope.launch {
                    signInPhase = when (val outcome = signIn(provider, app, googleSignIn(app, context))) {
                        is SignInFlow.Outcome.Failed -> SignInScreenState.Phase.Failure(outcome.message.res)
                        else -> SignInScreenState.Phase.Idle
                    }
                }
            }
        }
        if (confirmingDelete) {
            ConfirmDialog(
                title = R.string.delete_title,
                body = R.string.delete_body,
                action = R.string.delete_action,
                tag = "delete.confirm",
                onConfirm = {
                    confirmingDelete = false
                    if (!app.isOnline) {
                        deletePhase = AccountScreenState.DeletePhase.Failure(R.string.error_no_connection)
                    } else {
                        deletePhase = AccountScreenState.DeletePhase.Deleting
                        scope.launch {
                            deletePhase = when (val result = app.deleteAccount()) {
                                is ApiResult.Ok -> AccountScreenState.DeletePhase.Idle
                                is ApiResult.Err -> AccountScreenState.DeletePhase.Failure(
                                    if (result.failure == ApiFailure.NoConnection) {
                                        R.string.error_no_connection
                                    } else {
                                        result.failure.message(CallContext.DELETE_ACCOUNT).res
                                    },
                                )
                            }
                        }
                    }
                },
                onCancel = { confirmingDelete = false },
            )
        }
    }
}

@Composable
private fun LicensesRoute(app: AppModel, nav: NavHostController) {
    val context = LocalContext.current
    // The notices run to about two megabytes, so they are read off the main thread.
    val texts by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { bundledLicenses(context) } }
    Frame(stringResource(R.string.account_licenses), back(nav)) {
        LicensesScreen(texts, app.isDemo)
    }
}

/** The Sign in screen: the providers discovery lists, the ones this build can run, then the flow. A success closes it. */
@Composable
private fun SignInRoute(app: AppModel, nav: NavHostController) {
    var phase by remember { mutableStateOf<SignInScreenState.Phase>(SignInScreenState.Phase.Idle) }
    var loadingProviders by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (app.discovery == null && app.isOnline) {
            loadingProviders = true
            app.refreshDiscovery()
            loadingProviders = false
        }
    }
    Frame(null, actions = { BarAction(stringResource(R.string.common_cancel), "signin.cancel") { nav.popBackStack() } }) {
        SignInScreen(
            SignInScreenState(SignInProviders.shown(app), phase, offline = !app.isOnline, demo = app.isDemo, loadingProviders = loadingProviders),
        ) { provider: TokenRequest.Provider ->
            phase = SignInScreenState.Phase.SigningIn
            scope.launch {
                when (val outcome = signIn(provider, app, googleSignIn(app, context))) {
                    SignInFlow.Outcome.SignedIn -> {
                        phase = SignInScreenState.Phase.Idle
                        nav.onMain { popBackStack() }
                    }
                    SignInFlow.Outcome.Cancelled -> phase = SignInScreenState.Phase.Idle
                    is SignInFlow.Outcome.Failed -> phase = SignInScreenState.Phase.Failure(outcome.message.res)
                }
            }
        }
    }
}
