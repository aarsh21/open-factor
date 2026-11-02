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
        androidx.lifecycle.ViewModelProvider(this)[WorkoutViewModel::class.java].commands.flushPendingSaves()
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
        // A recreated activity restores its current route. The retained launch intent is not a new tap.
        if (savedInstanceState == null && intent.action == WorkoutNotifications.OPEN_WORKOUT) workoutOpenRequest++
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
    // Initial values come from vm.data.value: subscribing the shell to all data here would
    // recompose it for every typed digit. The Saver restores the entire typed return chain.
    val navigator = rememberSaveable(saver = AppNavigatorSaver) {
        vm.data.value.let { d -> AppNavigator(
            d.pastWorkoutEdit?.let { AppScreen.HistoryEdit(AppScreen.HistoryDetail(it.original.id)) }
                ?: if (d.active != null) AppScreen.Session() else AppScreen.Root(),
        ) }
    }
    val navigationWorkouts = remember(vm) { ViewModelNavigationWorkouts(vm) }
    WorkoutFinishNavigation(vm.finishWorkout, vm.commands, navigator)
    val currentDestination = navigator.destination
    val contentScreen = navigator.contentScreen
    val tab = navigator.rootTab
    var exerciseInfo by rememberSaveable { mutableStateOf(false) }
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    var quickWeight by rememberSaveable { mutableStateOf(false) }
    var configureShortcuts by rememberSaveable { mutableStateOf(false) }
    var workoutSettings by rememberSaveable { mutableStateOf(false) }
    var healthConnect by rememberSaveable { mutableStateOf(false) }
    var autoConnectHealth by rememberSaveable { mutableStateOf(false) }
    var bodyDataTab by rememberSaveable { mutableIntStateOf(0) }
    var bodyChartMetric by rememberSaveable { mutableStateOf("weight") }
    var bodyChartRange by rememberSaveable { mutableStateOf(BodyChartRange.WEEK) }
    val exercises = remember(vm, customExercises, libraryReady) { vm.exercises() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val routeState = rememberSaveableStateHolder()
    val context = LocalContext.current
    HealthConnectForegroundSync(healthConnectState, vm.commands, enabled = !healthConnect)
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    StorageRecoveryAlert(vm)
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { vm.refreshNotifications() }
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
        if (workoutOpenRequest > 0 && data.active != null) navigator.navigate(AppScreen.Session())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshNotifications()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val keepAlive by remember(vm) { derivedStateOf { data.preferences.keepAlive && navigator.destination is AppScreen.Session && data.active != null &&
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
    if (healthConnect) HealthConnectPanel(
        data, vm.commands::importHealthConnectWeights, onClose = { healthConnect = false; autoConnectHealth = false },
        onSync = vm.commands::importHealthConnectSync,
        autoConnectOnOpen = autoConnectHealth,
        onSuccessfulSync = if (autoConnectHealth) { { healthConnect = false; autoConnectHealth = false } } else null,
    )
    if (quickWeight) QuickWeightSheet(
        data.preferences.pounds,
        onClose = { quickWeight = false },
        onSave = { vm.commands.saveScaleWeight(it); quickWeight = false },
    )
    if (onboardingPending) {
        QuickStartScreen(data, vm.commands, onConnect = { autoConnectHealth = true; healthConnect = true }) { navigator.selectTab(AppTab.WORKOUT) }
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
        remember(currentDestination) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (
                        currentDestination is AppScreen.Root &&
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
    val motion = LocalAppMotionEnabled.current
    val destination = contentScreen
    val navigation = remember { SeekableTransitionState(destination) }
    // True only while a back gesture drives the transition; taps and 3-button back never set it.
    var keypadInset by remember { mutableStateOf(0.dp) }
    /**
     * One Undo snackbar at a time: a newer offer replaces the old one, and the first later edit
     * (canUndo turns false) withdraws it, so Undo can never erase new work.
     */
    fun offerUndo(message: String, canUndo: (AppData) -> Boolean, undo: () -> Boolean) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            var retrying = false
            while (canUndo(vm.data.value)) {
                val offer = launch {
                    vm.data.first { !canUndo(it) }
                    snackbar.currentSnackbarData?.dismiss()
                }
                val result = snackbar.showSnackbar(
                    if (retrying) "$message. Undo wasn't saved; free up storage and retry." else message,
                    if (retrying) "Retry Undo" else "Undo",
                    withDismissAction = retrying,
                    duration = if (retrying) SnackbarDuration.Indefinite else SnackbarDuration.Long,
                )
                offer.cancel()
                if (result != SnackbarResult.ActionPerformed || !canUndo(vm.data.value) || undo()) break
                retrying = true
            }
        }
    }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(destination) {
        navigation.animateTo(destination)
        scrubbing = false
    }
    val screenTransition = rememberTransition(navigation, label = "screen")
    PredictiveBackHandler(currentDestination !is AppScreen.Root) { gesture ->
        val from = currentDestination
        val target = navigator.backDestination(from)
        val revealed = target?.contentScreen
        try {
            gesture.collect { event ->
                // The gesture scrubs the same pop transition a tap would play, so lifting the
                // finger continues from exactly where the screen already is.
                if (motion && revealed != null && revealed != navigation.currentState) {
                    scrubbing = true
                    navigation.seekTo(event.progress, revealed)
                }
            }
            navigator.back(from)
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
        topBar = { PersistenceStatusNotice(vm.persistence, vm::retryPersistence, vm::dismissPersistenceFailure) },
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (currentDestination is AppScreen.Root) 94.dp else if (currentDestination is AppScreen.Session) keypadInset else 0.dp))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(OF.background)) {
            screenTransition.AnimatedContent(
                Modifier.fillMaxSize().nestedScroll(rootScroll).padding(top = padding.calculateTopPadding()),
                transitionSpec = { appTransition(motion, scrubbing) },
                contentKey = { it.transitionKey },
            ) { screen ->
            fun go(to: AppDestination) = navigator.navigateFrom(screen, to)
            fun back() = navigator.back(screen)
            fun openExercise(id: String) = navigator.openExercise(id, screen)
            fun openCustom() = navigator.openCustom(screen)
            fun editTemplate(routine: Routine? = null) = navigator.editTemplate(routine?.id, newId(), screen)
            fun pickExercise(id: String) = navigator.pickExercise(id, screen, navigationWorkouts)
            // Without motion there is nothing to watch leave: never stack two opaque screens.
            if (motion || screen == screenTransition.targetState)
            Column(Modifier.fillMaxSize().padding(bottom = if (screen is AppScreen.Root) 0.dp else padding.calculateBottomPadding())) {
                when (screen) {
                    AppScreen.Profile -> ProfileEditor(data, vm) { go(navigator.root) }
                    AppScreen.BodyData -> {
                        BodyDataScreen(
                            data, bodyDataTab, { bodyDataTab = it }, { go(navigator.root) },
                            { go(AppScreen.WeightEntry(it)) },
                            { go(MeasurementEntry(it, screen)) },
                            { go(PhotoEntry(it, screen)) }, vm.photoStore,
                            {
                                bodyChartMetric = if (bodyDataTab == 1) BodyMeasurement.NECK.key else "weight"
                                go(AppScreen.BodyCharts)
                            },
                        )
                    }
                    AppScreen.BodyCharts -> BodyChartScreen(
                        data, bodyChartMetric, bodyChartRange,
                        { bodyChartMetric = it }, { bodyChartRange = it }, { go(AppScreen.BodyData) },
                        onDismissTutorial = {
                            runPersistenceAction { vm.commands.settings { it.copy(dismissedTutorials = it.dismissedTutorials + "bodyCharts") } }
                        },
                        onLogWeight = { quickWeight = true },
                    )
                    is AppScreen.WeightEntry -> key(screen.entryId) {
                        ScaleWeightEditor(
                            data.scaleWeights.find { it.id == screen.entryId }, data.preferences.pounds,
                            { go(AppScreen.BodyData) },
                            { vm.commands.saveScaleWeight(it); bodyDataTab = 0; go(AppScreen.BodyData) },
                            { vm.commands.deleteScaleWeight(screen.entryId); bodyDataTab = 0; go(AppScreen.BodyData) },
                        )
                    }
                    AppScreen.Programs ->
                        ProgramLibrary(
                            data,
                            vm,
                            { go(navigator.root) },
                            {
                                if (data.programDraft != null) go(AppScreen.ProgramEditor())
                                else { runPersistenceAction {
                                    vm.commands.beginProgram(
                                        TrainingProgram(
                                            name =
                                                java.time.LocalDate.now()
                                                    .format(
                                                        java.time.format.DateTimeFormatter
                                                            .ofPattern("d MMM yyyy")
                                                    )
                                        )
                                    )
                                    go(AppScreen.ProgramSetup)
                                } }
                            },
                            { go(AppScreen.ProgramEditor()) },
                            { program, day, cycle ->
                                runPersistenceAction {
                                    if (vm.commands.startProgramDay(program, day, cycle)) go(AppScreen.Session())
                                    else shortcuts = true
                                }
                            },
                        )
                    AppScreen.ProgramSetup ->
                        data.programDraft?.let {
                            ProgramSetup(
                                it,
                                vm,
                                ::back,
                                { go(AppScreen.ProgramEditor()) },
                            )
                        }
                    is AppScreen.ProgramEditor ->
                        ProgramEditor(
                            data,
                            screen.selectedDayId,
                            { go(screen.copy(selectedDayId = it)) },
                            exercises,
                            vm,
                            ::back,
                            { day ->
                                go(AppScreen.ProgramPicker(day, screen.copy(selectedDayId = day)))
                            },
                            { go(AppScreen.Programs) },
                        )
                    is AppScreen.ProgramPicker -> routeState.SaveableStateProvider("program-picker:${screen.dayId}") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm.commands::favorite,
                            ::openExercise,
                            ::pickExercise,
                            onBack = ::back,
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    is AppScreen.Gyms ->
                        GymProfilesScreen(data, vm, ::back) {
                            go(AppScreen.Gym(it, screen))
                        }
                    is AppScreen.Gym ->
                        data.gyms
                            .firstOrNull { it.id == screen.gymId }
                            ?.let { gym ->
                                GymEditorScreen(gym, data, vm, ::back) { go(screen.copy(gymId = it)) }
                            }
                    AppScreen.Levels -> routeState.SaveableStateProvider("levels") {
                        LevelsScreen(data, exercises, { go(navigator.root) }, onExercise = {
                            openExercise(it)
                        })
                    }
                    is AppScreen.ExerciseSettings -> exercises.firstOrNull { it.id == screen.exerciseId }?.let { e ->
                        ExerciseSettingsScreen(
                            e, data.exerciseDefaults[e.id] ?: ExerciseDefaults(), data.preferences.restSeconds,
                            ::back, { value -> vm.commands.saveExerciseDefaults(e.id, value) }, { exerciseInfo = true },
                            { navigator.openCustom(screen, e.id) },
                            { value -> vm.commands.archiveExercise(e.id, value) },
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
                    is AppScreen.Exercise ->
                        exercises
                            .firstOrNull { it.id == screen.exerciseId }
                            ?.let { e ->
                                ExerciseDetail(
                                    e,
                                    data,
                                    vm,
                                    ::back,
                                    { navigator.pickExercise(e.id, screen, navigationWorkouts) },
                                    onSettings = {
                                        go(AppScreen.ExerciseSettings(e.id, screen))
                                    },
                                    onOpenWorkout = { go(AppScreen.HistoryDetail(it, screen)) },
                                )
                            }
                    AppScreen.SessionPicker -> routeState.SaveableStateProvider("picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm.commands::favorite,
                            ::openExercise,
                            ::pickExercise,
                            onBack = { go(AppScreen.Session()) },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    is AppScreen.SwapPicker -> routeState.SaveableStateProvider("swap-picker:${screen.entryId}") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm.commands::favorite,
                            ::openExercise,
                            ::pickExercise,
                            onBack = { go(AppScreen.Session()) },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                            pickTitle = "Swap exercise",
                        )
                    }
                    is AppScreen.CustomExercise -> {
                        val initial = remember(screen.duplicateExerciseId) {
                            exercises.find { it.id == screen.duplicateExerciseId }?.let { e ->
                                val (resistance, support) = vm.equipmentCatalog.requirementsFor(e)
                                val instructions = if (e.instructions.startsWith("reference/"))
                                    runCatching { context.assets.open(e.instructions).bufferedReader().use { it.readText() } }.getOrDefault("")
                                else e.instructions
                                e.duplicateDraft(resistance, support, instructions)
                            }
                        }
                        CustomExercise(data, vm.equipmentCatalog, ::back, initial) { draft ->
                            val id = vm.commands.addCustom(draft)
                            navigator.customCreated(id, screen)
                        }
                    }
                    AppScreen.DraftPicker -> routeState.SaveableStateProvider("draft-picker") {
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm.commands::favorite,
                            ::openExercise,
                            ::pickExercise,
                            onBack = { go(AppScreen.WorkoutDraft) },
                            onCustom = ::openCustom,
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    }
                    AppScreen.WorkoutDraft ->
                        data.workoutDraft?.let { draft ->
                            WorkoutComposition(
                                draft,
                                exercises,
                                data.gyms,
                                vm.commands::editWorkoutDraft,
                                onBack = { go(navigator.root) },
                                onAdd = { go(AppScreen.DraftPicker) },
                                onDetail = ::openExercise,
                                onStart = {
                                    runPersistenceAction { if (vm.commands.startWorkoutDraft()) go(AppScreen.Session()) }
                                },
                                onDiscard = { runPersistenceAction { vm.commands.discardWorkoutDraft(); go(navigator.root) } },
                                canStart = data.active == null,
                            )
                        } ?: run { LaunchedEffect(Unit) { go(navigator.root) } }
                    is AppScreen.Session ->
                        WorkoutSession(
                            data,
                            exercises,
                            vm,
                            onBack = { go(navigator.root) },
                            onAdd = { go(AppScreen.SessionPicker) },
                            onExercise = ::openExercise,
                            onWorkoutSettings = { workoutSettings = true },
                            onGymSettings = {
                                go(AppScreen.Gyms(screen))
                            },
                            onOpenWorkout = { go(AppScreen.HistoryDetail(it, screen)) },
                            onSwapSearch = { navigator.openSwapPicker(it, screen) },
                            swapPick = screen.swap?.let { it.entryId to it.exerciseId },
                            onSwapPickHandled = { screen.swap?.let(navigator::consumeSwap) },
                            onExerciseSwapped = { undo, title ->
                                offerUndo("Swapped for $title", { it.canUndoExerciseSwap(undo) }) { runPersistenceAction { vm.commands.undoExerciseSwap(undo) } }
                            },
                            undoOffered = snackbar.currentSnackbarData != null,
                            onKeypadInset = { keypadInset = it },
                            onSetRemoved = { undo, label ->
                                offerUndo("$label removed", { it.canUndoSetRemoval(undo) }) { runPersistenceAction { vm.commands.undoSetRemoval(undo) } }
                            },
                        )
                    AppScreen.History ->
                        HistoryScreen(
                            data.history, exercises, { go(navigator.root) },
                            onDetail = { go(AppScreen.HistoryDetail(it, screen)) },
                            prefs = data.preferences,
                            onRepeat = if (data.active == null) ({ id ->
                                data.history.find { it.id == id }?.let { workout ->
                                    runPersistenceAction {
                                        vm.commands.start(workout.name, workout.exercises.map { it.exerciseId })
                                        go(AppScreen.Session())
                                    }
                                }
                            }) else null,
                            onStart = {
                                runPersistenceAction { navigator.prepareOrResumeWorkout(navigationWorkouts, screen) }
                            },
                        )
                    is AppScreen.HistoryDetail ->
                        data.history
                            .firstOrNull { it.id == screen.workoutId }
                            ?.let { w ->
                                WorkoutSummary(
                                    w,
                                    exercises,
                                    data.preferences,
                                    ::back,
                                    {
                                        runPersistenceAction {
                                            vm.commands.start(w.name, w.exercises.map { it.exerciseId })
                                            go(AppScreen.Session())
                                        }
                                    },
                                    {
                                        runPersistenceAction {
                                            vm.commands.deleteWorkout(w.id)
                                            go(AppScreen.History)
                                        }
                                    },
                                    repeatLabel = if (data.active != null) "Resume active workout" else "Repeat workout",
                                    onEdit = { runPersistenceAction { vm.commands.beginPastWorkoutEdit(w.id); go(AppScreen.HistoryEdit(screen)) } },
                                )
                            } ?: run { LaunchedEffect(Unit) { go(AppScreen.History) } }
                    is AppScreen.HistoryEdit -> data.pastWorkoutEdit?.takeIf { it.original.id == screen.workoutId }?.let { edit ->
                        PastWorkoutEditor(edit, exercises, data, vm,
                            onChange = vm.commands::editPastWorkout,
                            onDiscard = { runPersistenceAction { vm.commands.discardPastWorkoutEdit(); go(screen.returnTo) } },
                            onSave = vm.commands::savePastWorkoutEdit,
                            onSaved = { go(screen.returnTo) },
                        )
                    } ?: run { LaunchedEffect(Unit) { go(AppScreen.History) } }
                    AppScreen.Templates -> routeState.SaveableStateProvider("template-library") {
                        TemplateLibraryScreen(data, exercises, { go(navigator.root) },
                            onCreate = { editTemplate() },
                            onEdit = { editTemplate(it) },
                            onStart = { routine -> vm.commands.startRoutine(routine.id).also { if (it == null) go(AppScreen.Session()) } },
                            onInstall = vm.commands::installStarterSplit, onDelete = vm.commands::deleteRoutine)
                    }
                    is AppScreen.RoutineEditor -> key(screen.draftKey) {
                        TemplateEditor(data.routines.find { it.id == screen.routineId }, exercises,
                            onBack = ::back,
                            onSave = { routine -> vm.commands.saveRoutine(routine).also { if (it == null) navigator.templateSaved(screen) } },
                            favorites = data.favorites, onFavorite = vm.commands::favorite,
                            gyms = data.gyms, equipmentCatalog = vm.equipmentCatalog,
                            preview = { exercise, close, add ->
                                ExerciseDetail(exercise, data, vm, close, add, closeable = true)
                            })
                    }
                    is AppScreen.Root ->
                        when (screen.tab) {
                            AppTab.DASHBOARD ->
                                DashboardScreen(
                                    data,
                                    exercises,
                                    vm,
                                    onWorkout = { go(AppScreen.Root(AppTab.WORKOUT)) },
                                    onHistory = { go(AppScreen.History) },
                                    onStart = { shortcuts = true },
                                    onResume = { go(AppScreen.Session()) },
                                    onBodyData = {
                                        bodyDataTab = 0
                                        go(AppScreen.BodyData)
                                    },
                                    onLogWeight = { quickWeight = true },
                                    onHealthConnect = { healthConnect = true },
                                )
                            AppTab.WORKOUT ->
                                WorkoutHome(
                                    data,
                                    exercises,
                                    onStart = { runPersistenceAction { navigator.startWorkout(navigationWorkouts, screen) } },
                                    onResume = { go(AppScreen.Session()) },
                                    onRoutine = { r ->
                                        runPersistenceAction {
                                            val failure = vm.commands.startRoutine(r.id)
                                            if (failure == null) go(AppScreen.Session())
                                            else scope.launch { snackbar.showSnackbar(failure) }
                                        }
                                    },
                                    onNewRoutine = { editTemplate() },
                                    onTemplates = { go(AppScreen.Templates) },
                                    onEditRoutine = { editTemplate(it) },
                                    onDeleteRoutine = vm.commands::deleteRoutine,
                                    onDraft = { go(AppScreen.WorkoutDraft) },
                                    onPrograms = { go(AppScreen.Programs) },
                                    onHistory = { go(AppScreen.History) },
                                )
                            AppTab.EXERCISES -> routeState.SaveableStateProvider("exercise-library") {
                                ExerciseLibrary(
                                    exercises,
                                    data.favorites,
                                    vm.commands::favorite,
                                    ::openExercise,
                                    onCustom = ::openCustom,
                                    gyms = data.gyms,
                                    equipmentCatalog = vm.equipmentCatalog,
                                    performedIds = remember(data.history) { data.history.flatMap { w -> w.exercises.filter { entry -> entry.sets.any { it.completed } }.map { it.exerciseId } }.toSet() },
                                    deprioritizedIds = remember(data.exerciseDefaults) { data.exerciseDefaults.filterValues { it.doNotRecommend }.keys },
                                    modifiedIds = remember(data.exerciseDefaults) { data.exerciseDefaults.filterValues { it.hasOverrides }.keys },
                                )
                            }
                            AppTab.SETTINGS ->
                                SettingsScreen(
                                    data.preferences,
                                    vm,
                                    snackbar,
                                    onProfile = { go(AppScreen.Profile) },
                                    onExercises = { go(AppScreen.Root(AppTab.EXERCISES)) },
                                    onHistory = { go(AppScreen.History) },
                                    onBodyData = { go(AppScreen.BodyData) },
                                    onGyms = {
                                        go(AppScreen.Gyms(screen))
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
                { it is AppScreen.Root },
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
                    tab.ordinal,
                    { scrollEnergy },
                    onAdd = {
                        shortcuts = true
                    },
                ) {
                    navigator.selectTab(AppTab.entries[it])
                }
            }
        }
    }
    when (val sheet = currentDestination) {
        is MeasurementEntry -> key(sheet.entryId) {
            BodyMeasurementEditor(
                data.bodyMeasurements.find { it.id == sheet.entryId },
                { navigator.back(sheet) },
                { vm.commands.saveBodyMeasurements(it); bodyDataTab = 1; navigator.navigate(AppScreen.BodyData) },
                { vm.commands.deleteBodyMeasurements(sheet.entryId); bodyDataTab = 1; navigator.navigate(AppScreen.BodyData) },
                visibility = data.preferences.dataVisibility,
            )
        }
        is PhotoEntry -> key(sheet.entryId) {
            ProgressPhotoEditor(data.progressPhotos.find { it.id == sheet.entryId }, vm,
                { navigator.back(sheet) }, { bodyDataTab = 2; navigator.navigate(AppScreen.BodyData) })
        }
        is AppScreen -> Unit
    }
    if (navigator.programExitRequested)
        AlertDialog(
            onDismissRequest = { navigator.cancelProgramExit() },
            title = { Text("Leave program draft?") },
            text = {
                Text(
                    "Keep the draft to continue later, or discard these unsaved changes. Saved programs stay unchanged."
                )
            },
            confirmButton = {
                TextButton({
                    navigator.leaveProgramDraft()
                }) {
                    Text("Keep Draft")
                }
            },
            dismissButton = {
                TextButton({
                    runPersistenceAction {
                        vm.commands.discardProgramDraft()
                        navigator.leaveProgramDraft()
                    }
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
                runPersistenceAction {
                    vm.commands.settings { it.copy(shortcuts = changed) }
                    configureShortcuts = false
                }
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
                    bodyDataTab = 1; navigator.navigate(MeasurementEntry("", navigator.contentScreen))
                }
                WorkoutShortcut.PHOTOS -> {
                    bodyDataTab = 2; navigator.navigate(PhotoEntry("", navigator.contentScreen))
                }
                WorkoutShortcut.HISTORY -> navigator.navigate(AppScreen.History)
                WorkoutShortcut.EMPTY_WORKOUT -> {
                    runPersistenceAction { navigator.prepareOrResumeWorkout(navigationWorkouts) }
                }
                WorkoutShortcut.NEW_PROGRAM -> navigator.editTemplate(null, newId())
                WorkoutShortcut.NEW_WORKOUT -> navigator.editTemplate(null, newId())
            }
        }
}
