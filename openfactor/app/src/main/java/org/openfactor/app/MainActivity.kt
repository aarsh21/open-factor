package org.openfactor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.setContent
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var workoutOpenRequest by mutableIntStateOf(0)
    private var windowRevealed by mutableStateOf(android.os.Build.VERSION.SDK_INT < 31)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == WorkoutNotifications.OPEN_WORKOUT) workoutOpenRequest++
    }

    override fun onPause() {
        // Typed set values are saved off the main thread; make them durable before the app can be killed.
        androidx.lifecycle.ViewModelProvider(this)[WorkoutViewModel::class.java].flushPendingSaves()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowRevealed = savedInstanceState != null || android.os.Build.VERSION.SDK_INT < 31
        if (android.os.Build.VERSION.SDK_INT >= 31 && savedInstanceState == null) {
            // Start the welcome after the system's starting surface is gone, not underneath it.
            // No extra splash hold or timer: Android still decides when the first frame is ready.
            splashScreen.setOnExitAnimationListener { splash ->
                splash.remove()
                windowRevealed = true
            }
        }
        if (intent.action == WorkoutNotifications.OPEN_WORKOUT) workoutOpenRequest++
        enableEdgeToEdge(
            statusBarStyle =
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle =
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            // Android 8 focuses a window's first focusable view even in touch mode, and refocuses it whenever
            // focus is cleared. Compose hands that to the first set field, so the workout keypad opened by itself
            // and Back or an outside tap could never close it. Android 9 dropped both; on 8 the frame absorbs it.
            findViewById<android.view.ViewGroup>(android.R.id.content).apply {
                isFocusableInTouchMode = true
                descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                // Focusable would make it one screen-sized accessibility node; its children stay accessible.
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        setContent {
            val vm: WorkoutViewModel = viewModel()
            val data by vm.data.collectAsState()
            // The window root needs only the theme; set-row edits must not recompose it.
            val theme by remember { derivedStateOf { data.preferences.theme } }
            val palette by remember { derivedStateOf { data.preferences.palette } }
            val libraryReady by vm.libraryReady.collectAsState()
            ReportDrawnWhen { libraryReady }
            OpenFactorTheme(theme, palette) {
                // Bar icons follow the palette's actual tone, mid cross-fade included, so neither they nor
                // the window behind the app keep charcoal under another palette.
                val dark = OF.palette.isDark
                // Only a settled tone replaces the window drawable, not every frame of the cross-fade.
                LaunchedEffect(palette, dark) { window.setBackgroundDrawable(palette.colors(dark).background.toArgb().toDrawable()) }
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                CompositionLocalProvider(LocalAppWindowRevealed provides windowRevealed) {
                    OpenFactorApp(vm, workoutOpenRequest)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenFactorApp(vm: WorkoutViewModel = viewModel(), workoutOpenRequest: Int = 0) {
    val data by vm.data.collectAsState()
    val libraryReady by vm.libraryReady.collectAsState()
    val libraryLoad by vm.libraryLoad.collectAsState()
    // Each typed digit replaces data.active. The shell reads data only through these slices (routes
    // and dialogs below read it in their own scopes), so it does not recompose per keystroke.
    val customExercises by remember(vm) { derivedStateOf { data.customExercises } }
    val healthConnectState by remember(vm) { derivedStateOf { data.healthConnect } }
    val activeId by remember(vm) { derivedStateOf { data.active?.id } }
    val onboardingPending by remember(vm) { derivedStateOf { !data.onboarding.completed && !data.onboarding.deferred } }
    TraceRecomposition("OpenFactorApp")
    var tab by rememberSaveable { mutableIntStateOf(1) }
    // Initial values come from vm.data.value, not data: reading data inside a remember calculation subscribes
    // the whole shell to it, and then every keystroke recomposes the shell.
    var route by rememberSaveable { vm.data.value.let { d -> mutableStateOf(when { d.pastWorkoutEdit != null -> "history-edit"; d.active != null -> "session"; else -> "" }) } }
    var detailId by rememberSaveable { mutableStateOf("") }
    var previousRoute by rememberSaveable { mutableStateOf("") }
    var exerciseInfo by rememberSaveable { mutableStateOf(false) }
    var exerciseSettingsReturnRoute by rememberSaveable { mutableStateOf("") }
    var customReturnRoute by rememberSaveable { mutableStateOf("") }
    var duplicateId by rememberSaveable { mutableStateOf("") }
    var programDayId by rememberSaveable { mutableStateOf("") }
    var programExit by remember { mutableStateOf(false) }
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    var quickWeight by rememberSaveable { mutableStateOf(false) }
    var configureShortcuts by rememberSaveable { mutableStateOf(false) }
    var workoutSettings by rememberSaveable { mutableStateOf(false) }
    var healthConnect by rememberSaveable { mutableStateOf(false) }
    var autoConnectHealth by rememberSaveable { mutableStateOf(false) }
    var routineEditId by rememberSaveable { mutableStateOf("") }
    var routineDraftKey by rememberSaveable { mutableStateOf("") }
    var routineReturn by rememberSaveable { mutableStateOf("") }
    var gymsReturn by rememberSaveable { mutableStateOf("") }
    var bodyDataTab by rememberSaveable { mutableIntStateOf(0) }
    var bodyChartMetric by rememberSaveable { mutableStateOf("weight") }
    var bodyChartRange by rememberSaveable { mutableStateOf(BodyChartRange.WEEK) }
    var bodyEntryReturn by rememberSaveable { mutableStateOf("body-data") }
    var swapEntryId by rememberSaveable { mutableStateOf("") }
    var swapPickId by rememberSaveable { mutableStateOf("") }
    val contentRoute =
        if (route == "measurement-entry" || route == "photo-entry") bodyEntryReturn else route
    val exercises = remember(vm, customExercises, libraryReady) { vm.exercises() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val routeState = rememberSaveableStateHolder()
    val context = LocalContext.current
    HealthConnectForegroundSync(healthConnectState, vm::importHealthConnectSync, enabled = !healthConnect)
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    StorageRecoveryAlert(vm)
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { WorkoutNotifications.sync(context, vm.data.value, forceRefresh = true) }
    LaunchedEffect(activeId) {
        if (data.active != null && android.os.Build.VERSION.SDK_INT >= 33) {
            val prefs = context.getSharedPreferences("notification-permission", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("requested", false) && !WorkoutNotifications.notificationsAllowed(context)) {
                prefs.edit().putBoolean("requested", true).apply()
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(workoutOpenRequest) {
        if (workoutOpenRequest > 0 && data.active != null) route = "session"
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) WorkoutNotifications.sync(context, vm.data.value, forceRefresh = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val keepAlive by remember(vm) { derivedStateOf { data.preferences.keepAlive && route == "session" && data.active != null &&
        (data.onboarding.completed || data.onboarding.deferred) } }
    DisposableEffect(view, lifecycleOwner, keepAlive) {
        if (!keepAlive) return@DisposableEffect onDispose { }
        val original = view.keepScreenOn
        fun update() { view.keepScreenOn = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycleOwner.lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.keepScreenOn = original
        }
    }
    fun editTemplate(routine: Routine? = null, returnRoute: String = "") {
        routineEditId = routine?.id.orEmpty()
        routineDraftKey = newId()
        routineReturn = returnRoute
        route = "routine"
    }
    fun openExercise(id: String) {
        previousRoute = route
        detailId = id
        route = "exercise"
    }
    fun openCustom() {
        duplicateId = ""
        customReturnRoute = route
        route = "custom"
    }
    fun pickExercise(id: String, origin: String) {
        // The first Add navigates away synchronously. Ignore any queued second tap from
        // that departed picker/detail instead of adding the same exercise twice.
        if (route != origin && !(route == "exercise" && previousRoute == origin)) return
        when (origin) {
            "program-picker" -> {
                vm.editProgramDraft { plan ->
                    plan.copy(days = plan.days.map { day ->
                        if (day.id == programDayId) day.addExercise(
                            id,
                            plan.cycleCount,
                            "Workout ${('A'.code + plan.days.count { it.exercises.isNotEmpty() }).toChar()}",
                        ) else day
                    })
                }
                route = "program-editor"
            }
            "workout-draft", "draft-picker" -> {
                vm.addDraftExercise(id)
                route = "workout-draft"
            }
            "swap-picker" -> {
                swapPickId = id
                route = "session"
            }
            else -> {
                if (data.active != null) {
                    vm.addExercise(id)
                    route = "session"
                } else {
                    vm.prepareWorkout()
                    vm.addDraftExercise(id)
                    route = "workout-draft"
                }
            }
        }
    }
    if (healthConnect) HealthConnectPanel(
        data, vm::importHealthConnectWeights, onClose = { healthConnect = false; autoConnectHealth = false },
        onSync = vm::importHealthConnectSync,
        autoConnectOnOpen = autoConnectHealth,
        onSuccessfulSync = if (autoConnectHealth) { { healthConnect = false; autoConnectHealth = false } } else null,
    )
    if (quickWeight) QuickWeightSheet(
        data.preferences.pounds,
        onClose = { quickWeight = false },
        onSave = { vm.saveScaleWeight(it); quickWeight = false },
    )
    if (onboardingPending) {
        QuickStartScreen(data, vm, onConnect = { autoConnectHealth = true; healthConnect = true }) { tab = 1 }
        return
    }
    if (!libraryReady) {
        Surface(Modifier.fillMaxSize(), color = OF.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Open Factor", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                val failure = libraryLoad as? ExerciseLibraryLoad.Failed
                if (failure == null) CircularProgressIndicator()
                else {
                    Text(failure.message)
                    Spacer(Modifier.height(16.dp))
                    PrimaryAction("Try again", vm::retryLibrary)
                }
            }
        }
        return
    }
    var scrollEnergy by remember { mutableFloatStateOf(0f) }
    val rootScroll =
        remember(route) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (
                        route.isBlank() &&
                            source == NestedScrollSource.UserInput &&
                            android.animation.ValueAnimator.areAnimatorsEnabled()
                    ) {
                        scrollEnergy = (scrollEnergy - consumed.y / 160f).coerceIn(-1f, 1f)
                    }
                    return Offset.Zero
                }
            }
        }
    // Scroll energy changes every scroll frame: observe it outside composition so scrolling a tab
    // never recomposes the shell. Each change restarts the 140ms settle, as a keyed effect did.
    LaunchedEffect(tab) {
        snapshotFlow { scrollEnergy }.collectLatest {
            delay(140)
            scrollEnergy = 0f
        }
    }
    /** Null means back asks first instead of leaving. */
    fun backRoute(): String? = when (route) {
        "exercise" -> previousRoute
        "exercise-settings" -> exerciseSettingsReturnRoute
        "custom" -> customReturnRoute
        "picker", "swap-picker" -> "session"
        "draft-picker" -> "workout-draft"
        "gym" -> "gyms"
        "gyms" -> gymsReturn
        "measurement-entry", "photo-entry" -> bodyEntryReturn
        "weight-entry" -> "body-data"
        "body-charts" -> "body-data"
        "program-picker" -> "program-editor"
        "program-editor", "program-setup" -> null
        else -> ""
    }
    val motion = LocalAppMotionEnabled.current
    val destination = AppDestination.of(contentRoute, detailId, tab)
    val navigation = remember { SeekableTransitionState(destination) }
    // True only while a back gesture drives the transition; taps and 3-button back never set it.
    var keypadInset by remember { mutableStateOf(0.dp) }
    /**
     * One Undo snackbar at a time: a newer offer replaces the old one, and the first later edit
     * (canUndo turns false) withdraws it, so Undo can never erase new work.
     */
    fun offerUndo(message: String, canUndo: (AppData) -> Boolean, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val offer = launch {
                vm.data.first { !canUndo(it) }
                snackbar.currentSnackbarData?.dismiss()
            }
            val result = snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Long)
            offer.cancel()
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        navigation.animateTo(destination)
        scrubbing = false
    }
    val screenTransition = rememberTransition(navigation, label = "screen")
    PredictiveBackHandler(route.isNotBlank()) { gesture ->
        val target = backRoute()
        val revealed = target?.let {
            AppDestination.of(it, detailId, tab)
        }
        try {
            gesture.collect { event ->
                // The gesture scrubs the same pop transition a tap would play, so lifting the
                // finger continues from exactly where the screen already is.
                if (motion && revealed != null && revealed != navigation.currentState) {
                    scrubbing = true
                    navigation.seekTo(event.progress, revealed)
                }
            }
            if (target == null) programExit = true else route = target
        } catch (cancelled: CancellationException) {
            scope.launch {
                navigation.animateTo(navigation.currentState)
                scrubbing = false
            }
            throw cancelled
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OF.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (route.isBlank()) 94.dp else if (route == "session") keypadInset else 0.dp))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(OF.background)) {
            screenTransition.AnimatedContent(
                Modifier.fillMaxSize().nestedScroll(rootScroll).padding(top = padding.calculateTopPadding()),
                transitionSpec = { appTransition(motion, scrubbing) },
                contentKey = { it },
            ) { screen ->
            val shownId = screen.key
            val shownTab = screen.key.removePrefix("tab:").toIntOrNull() ?: tab
            // A departing screen keeps rendering its own record; only the current one redirects.
            fun leave(to: String) { if (route == screen.route) route = to }
            // Without motion there is nothing to watch leave: never stack two opaque screens.
            if (motion || screen == screenTransition.targetState)
            Column(Modifier.fillMaxSize().padding(bottom = if (screen.route.isBlank()) 0.dp else padding.calculateBottomPadding())) {
                when (screen.route) {
                    "profile" -> ProfileEditor(data, vm) { route = "" }
                    "body-data" -> {
                        BodyDataScreen(
                            data, bodyDataTab, { bodyDataTab = it }, { route = "" },
                            { detailId = it; route = "weight-entry" },
                            { detailId = it; bodyEntryReturn = "body-data"; route = "measurement-entry" },
                            { detailId = it; bodyEntryReturn = "body-data"; route = "photo-entry" }, vm.photoStore,
                            {
                                bodyChartMetric = if (bodyDataTab == 1) BodyMeasurement.NECK.key else "weight"
                                route = "body-charts"
                            },
                        )
                    }
                    "body-charts" -> BodyChartScreen(
                        data, bodyChartMetric, bodyChartRange,
                        { bodyChartMetric = it }, { bodyChartRange = it }, { route = "body-data" },
                        onDismissTutorial = {
                            vm.settings { it.copy(dismissedTutorials = it.dismissedTutorials + "bodyCharts") }
                        },
                        onLogWeight = { quickWeight = true },
                    )
                    "weight-entry" -> key(shownId) {
                        ScaleWeightEditor(
                            data.scaleWeights.find { it.id == shownId }, data.preferences.pounds,
                            { route = "body-data" },
                            { vm.saveScaleWeight(it); bodyDataTab = 0; route = "body-data" },
                            { vm.deleteScaleWeight(shownId); bodyDataTab = 0; route = "body-data" },
                        )
                    }
                    "programs" ->
                        ProgramLibrary(
                            data,
                            vm,
                            { route = "" },
                            {
                                if (data.programDraft != null) route = "program-editor"
                                else {
                                    vm.beginProgram(
                                        TrainingProgram(
                                            name =
                                                java.time.LocalDate.now()
                                                    .format(
                                                        java.time.format.DateTimeFormatter
                                                            .ofPattern("d MMM yyyy")
                                                    )
                                        )
                                    )
                                    route = "program-setup"
                                }
                            },
                            { route = "program-editor" },
                            { program, day, cycle ->
                                if (vm.startProgramDay(program, day, cycle)) route = "session"
                                else shortcuts = true
                            },
                        )
                    "program-setup" ->
                        data.programDraft?.let {
                            ProgramSetup(
                                it,
                                vm,
                                { programExit = true },
                                { route = "program-editor" },
                            )
                        }
                    "program-editor" ->
                        ProgramEditor(
                            data,
                            programDayId,
                            { programDayId = it },
                            exercises,
                            vm,
                            { programExit = true },
                            { day ->
                                programDayId = day
                                route = "program-picker"
                            },
                            { route = "programs" },
                        )
                    "program-picker" -> routeState.SaveableStateProvider("program-picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm::favorite,
                            ::openExercise,
                            { id -> pickExercise(id, "program-picker") },
                            onBack = { route = "program-editor" },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    "gyms" ->
                        GymProfilesScreen(data, vm, { route = gymsReturn }) {
                            detailId = it
                            route = "gym"
                        }
                    "gym" ->
                        data.gyms
                            .firstOrNull { it.id == shownId }
                            ?.let { gym ->
                                GymEditorScreen(gym, data, vm, { route = "gyms" }) { detailId = it }
                            }
                    "levels" -> routeState.SaveableStateProvider("levels") {
                        LevelsScreen(data, exercises, { route = "" }, onExercise = {
                            openExercise(it)
                        })
                    }
                    "exercise-settings" -> exercises.firstOrNull { it.id == shownId }?.let { e ->
                        ExerciseSettingsScreen(
                            e, data.exerciseDefaults[e.id] ?: ExerciseDefaults(), data.preferences.restSeconds,
                            { route = exerciseSettingsReturnRoute }, { vm.saveExerciseDefaults(e.id, it) }, { exerciseInfo = true },
                            { duplicateId = e.id; customReturnRoute = route; route = "custom" },
                            { vm.archiveExercise(e.id, it) },
                        )
                        if (exerciseInfo) ModalBottomSheet(
                            onDismissRequest = { exerciseInfo = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = OF.background, dragHandle = null,
                        ) {
                            Box(Modifier.fillMaxHeight(.95f)) {
                                ExerciseDetail(e, data, vm, { exerciseInfo = false }, null, closeable = true)
                            }
                        }
                    }
                    "exercise" ->
                        exercises
                            .firstOrNull { it.id == shownId }
                            ?.let { e ->
                                ExerciseDetail(
                                    e,
                                    data,
                                    vm,
                                    { route = previousRoute },
                                    { pickExercise(e.id, previousRoute) },
                                    onSettings = {
                                        exerciseSettingsReturnRoute = "exercise"
                                        route = "exercise-settings"
                                    },
                                    onOpenWorkout = { detailId = it; route = "history-detail" },
                                )
                            }
                    "picker" -> routeState.SaveableStateProvider("picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm::favorite,
                            ::openExercise,
                            {
                                vm.addExercise(it)
                                route = "session"
                            },
                            onBack = { route = "session" },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    "swap-picker" -> routeState.SaveableStateProvider("swap-picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm::favorite,
                            ::openExercise,
                            { id -> pickExercise(id, "swap-picker") },
                            onBack = { route = "session" },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                            pickTitle = "Swap exercise",
                        )
                    }
                    "custom" -> {
                        val initial = remember(duplicateId) {
                            exercises.find { it.id == duplicateId }?.let { e ->
                                val (resistance, support) = vm.equipmentCatalog.requirementsFor(e)
                                val instructions = if (e.instructions.startsWith("reference/"))
                                    runCatching { context.assets.open(e.instructions).bufferedReader().use { it.readText() } }.getOrDefault("")
                                else e.instructions
                                e.duplicateDraft(resistance, support, instructions)
                            }
                        }
                        CustomExercise(data, vm.equipmentCatalog, { route = customReturnRoute }, initial) { draft ->
                            val id = vm.addCustom(draft)
                            previousRoute = customReturnRoute
                            detailId = id
                            route = "exercise"
                        }
                    }
                    "draft-picker" -> routeState.SaveableStateProvider("draft-picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm::favorite,
                            ::openExercise,
                            { id -> pickExercise(id, "draft-picker") },
                            onBack = { route = "workout-draft" },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    "workout-draft" ->
                        data.workoutDraft?.let { draft ->
                            WorkoutComposition(
                                draft,
                                exercises,
                                data.gyms,
                                vm::editWorkoutDraft,
                                onBack = { route = "" },
                                onAdd = { route = "draft-picker" },
                                onDetail = ::openExercise,
                                onStart = {
                                    if (vm.startWorkoutDraft()) route = "session"
                                },
                                onDiscard = { vm.discardWorkoutDraft(); route = "" },
                                canStart = data.active == null,
                            )
                        } ?: run { LaunchedEffect(Unit) { leave("") } }
                    "session" ->
                        WorkoutSession(
                            data,
                            exercises,
                            vm,
                            onBack = { route = "" },
                            onAdd = { route = "picker" },
                            onExercise = ::openExercise,
                            onWorkoutSettings = { workoutSettings = true },
                            onGymSettings = {
                                gymsReturn = "session"
                                route = "gyms"
                            },
                            onOpenWorkout = { detailId = it; route = "history-detail" },
                            onFinished = {
                                detailId = it
                                route = "history-detail"
                            },
                            onSwapSearch = { swapEntryId = it; swapPickId = ""; route = "swap-picker" },
                            swapPick = (swapEntryId to swapPickId).takeIf { swapEntryId.isNotEmpty() && swapPickId.isNotEmpty() },
                            onSwapPickHandled = { swapEntryId = ""; swapPickId = "" },
                            onExerciseSwapped = { undo, title ->
                                offerUndo("Swapped for $title", { it.canUndoExerciseSwap(undo) }) { vm.undoExerciseSwap(undo) }
                            },
                            undoOffered = snackbar.currentSnackbarData != null,
                            onKeypadInset = { keypadInset = it },
                            onSetRemoved = { undo, label ->
                                offerUndo("$label removed", { it.canUndoSetRemoval(undo) }) { vm.undoSetRemoval(undo) }
                            },
                        )
                    "history" ->
                        HistoryScreen(
                            data.history, exercises, { route = "" },
                            onDetail = { detailId = it; route = "history-detail" },
                            prefs = data.preferences,
                            onRepeat = if (data.active == null) ({ id ->
                                data.history.find { it.id == id }?.let { workout ->
                                    vm.start(workout.name, workout.exercises.map { it.exerciseId })
                                    route = "session"
                                }
                            }) else null,
                            onStart = {
                                if (data.active != null) route = "session"
                                else { vm.prepareWorkout(); route = "workout-draft" }
                            },
                        )
                    "history-detail" ->
                        data.history
                            .firstOrNull { it.id == shownId }
                            ?.let { w ->
                                WorkoutSummary(
                                    w,
                                    exercises,
                                    data.preferences,
                                    { route = "history" },
                                    {
                                        vm.start(w.name, w.exercises.map { it.exerciseId })
                                        route = "session"
                                    },
                                    {
                                        vm.deleteWorkout(w.id)
                                        route = "history"
                                    },
                                    repeatLabel = if (data.active != null) "Resume active workout" else "Repeat workout",
                                    onEdit = { vm.beginPastWorkoutEdit(w.id); route = "history-edit" },
                                )
                            } ?: run { LaunchedEffect(Unit) { leave("history") } }
                    "history-edit" -> data.pastWorkoutEdit?.let { edit ->
                        PastWorkoutEditor(edit, exercises, data, vm,
                            onChange = vm::editPastWorkout,
                            onDiscard = { detailId = edit.original.id; vm.discardPastWorkoutEdit(); route = "history-detail" },
                            onSave = vm::savePastWorkoutEdit,
                            onSaved = { detailId = edit.original.id; route = "history-detail" },
                        )
                    } ?: run { LaunchedEffect(Unit) { leave("history") } }
                    "templates" -> routeState.SaveableStateProvider("template-library") {
                        TemplateLibraryScreen(data, exercises, { route = "" },
                            onCreate = { editTemplate(returnRoute = "templates") },
                            onEdit = { editTemplate(it, "templates") },
                            onStart = { routine -> vm.startRoutine(routine.id).also { if (it == null) route = "session" } },
                            onInstall = vm::installStarterSplit, onDelete = vm::deleteRoutine)
                    }
                    "routine" -> key(routineDraftKey) {
                        TemplateEditor(data.routines.find { it.id == routineEditId }, exercises,
                            onBack = { route = routineReturn },
                            onSave = { routine -> vm.saveRoutine(routine).also { if (it == null) { route = "templates"; tab = 1 } } },
                            favorites = data.favorites, onFavorite = vm::favorite,
                            gyms = data.gyms, equipmentCatalog = vm.equipmentCatalog,
                            preview = { exercise, close, add ->
                                ExerciseDetail(exercise, data, vm, close, add, closeable = true)
                            })
                    }
                    else ->
                        when (shownTab) {
                            0 ->
                                DashboardScreen(
                                    data,
                                    exercises,
                                    vm,
                                    onWorkout = { tab = 1 },
                                    onHistory = { route = "history" },
                                    onStart = { shortcuts = true },
                                    onResume = { route = "session" },
                                    onBodyData = {
                                        bodyDataTab = 0
                                        route = "body-data"
                                    },
                                    onLogWeight = { quickWeight = true },
                                    onHealthConnect = { healthConnect = true },
                                )
                            1 ->
                                WorkoutHome(
                                    data,
                                    exercises,
                                    onStart = { vm.start(); route = "picker" },
                                    onResume = { route = "session" },
                                    onRoutine = { r ->
                                        val failure = vm.startRoutine(r.id)
                                        if (failure == null) route = "session"
                                        else scope.launch { snackbar.showSnackbar(failure) }
                                    },
                                    onNewRoutine = { editTemplate() },
                                    onTemplates = { route = "templates" },
                                    onEditRoutine = { editTemplate(it) },
                                    onDeleteRoutine = vm::deleteRoutine,
                                    onDraft = { route = "workout-draft" },
                                    onPrograms = { route = "programs" },
                                    onHistory = { route = "history" },
                                )
                            2 -> routeState.SaveableStateProvider("exercise-library") {
                                ExerciseLibrary(
                                    exercises,
                                    data.favorites,
                                    vm::favorite,
                                    ::openExercise,
                                    onCustom = ::openCustom,
                                    gyms = data.gyms,
                                    equipmentCatalog = vm.equipmentCatalog,
                                    performedIds = remember(data.history) { data.history.flatMap { w -> w.exercises.filter { entry -> entry.sets.any { it.completed } }.map { it.exerciseId } }.toSet() },
                                    deprioritizedIds = remember(data.exerciseDefaults) { data.exerciseDefaults.filterValues { it.doNotRecommend }.keys },
                                    modifiedIds = remember(data.exerciseDefaults) { data.exerciseDefaults.filterValues { it.hasOverrides }.keys },
                                )
                            }
                            3 ->
                                SettingsScreen(
                                    data.preferences,
                                    vm,
                                    snackbar,
                                    onProfile = { route = "profile" },
                                    onExercises = { tab = 2 },
                                    onHistory = { route = "history" },
                                    onBodyData = { route = "body-data" },
                                    onGyms = {
                                        gymsReturn = ""
                                        route = "gyms"
                                    },
                                    integrationSettings = {
                                        if (healthConnectAvailability(context) != HealthConnectAvailability.UNAVAILABLE)
                                            HealthConnectSettingsRow { autoConnectHealth = false; healthConnect = true }
                                    },
                                )
                        }
                }
            }
            }
            // The bar belongs to the root screens, so it rides the same (scrubbable) transition:
            // it sinks away as a screen pushes over it and rises back when that screen pops.
            screenTransition.AnimatedVisibility(
                { it.route.isBlank() },
                Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()),
                enter = if (!motion) EnterTransition.None else
                    fadeIn(tween(AppMotion.Step, easing = AppMotion.EaseOut)) +
                        // Never longer than the screen pop, or a back gesture would outrun its own scrub.
                        slideInVertically(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { it / 2 },
                exit = if (!motion) ExitTransition.None else
                    fadeOut(tween(AppMotion.Quick)) +
                        slideOutVertically(tween(AppMotion.State, easing = AppMotion.EaseOut)) { it / 3 },
            ) {
                WorkoutNavigation(
                    tab,
                    { scrollEnergy },
                    onAdd = {
                        shortcuts = true
                    },
                ) {
                    tab = it
                }
            }
        }
    }
    if (route == "measurement-entry") key(detailId) {
        BodyMeasurementEditor(
            data.bodyMeasurements.find { it.id == detailId },
            { route = bodyEntryReturn },
            { vm.saveBodyMeasurements(it); bodyDataTab = 1; route = "body-data" },
            { vm.deleteBodyMeasurements(detailId); bodyDataTab = 1; route = "body-data" },
            visibility = data.preferences.dataVisibility,
        )
    }
    if (route == "photo-entry") key(detailId) {
        ProgressPhotoEditor(data.progressPhotos.find { it.id == detailId }, vm,
            { route = bodyEntryReturn }, { bodyDataTab = 2; route = "body-data" })
    }
    if (programExit)
        AlertDialog(
            onDismissRequest = { programExit = false },
            title = { Text("Leave program draft?") },
            text = {
                Text(
                    "Keep the draft to continue later, or discard these unsaved changes. Saved programs stay unchanged."
                )
            },
            confirmButton = {
                TextButton({
                    programExit = false
                    route = "programs"
                }) {
                    Text("Keep Draft")
                }
            },
            dismissButton = {
                TextButton({
                    vm.discardProgramDraft()
                    programExit = false
                    route = "programs"
                }) {
                    Text("Discard Draft")
                }
            },
        )
    if (workoutSettings)
        WorkoutSettings(data.preferences, vm) { workoutSettings = false }
    if (configureShortcuts)
        ModalBottomSheet(onDismissRequest = { configureShortcuts = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OF.background) {
            ShortcutEditor(data.preferences.shortcuts, { configureShortcuts = false }) { changed ->
                vm.settings { it.copy(shortcuts = changed) }
                configureShortcuts = false
            }
        }
    if (shortcuts)
        ShortcutSheet(data.preferences, data.active, { shortcuts = false }, {
            shortcuts = false
            configureShortcuts = true
        }) { shortcut ->
            shortcuts = false
            when (shortcut) {
                WorkoutShortcut.WEIGHT -> { quickWeight = true }
                WorkoutShortcut.MEASUREMENTS -> {
                    detailId = ""; bodyDataTab = 1; bodyEntryReturn = route; route = "measurement-entry"
                }
                WorkoutShortcut.PHOTOS -> {
                    detailId = ""; bodyDataTab = 2; bodyEntryReturn = route; route = "photo-entry"
                }
                WorkoutShortcut.HISTORY -> route = "history"
                WorkoutShortcut.EMPTY_WORKOUT -> {
                    if (data.active != null) route = "session"
                    else { vm.prepareWorkout(); route = "workout-draft" }
                }
                WorkoutShortcut.NEW_PROGRAM -> editTemplate()
                WorkoutShortcut.NEW_WORKOUT -> editTemplate()
            }
        }
}
