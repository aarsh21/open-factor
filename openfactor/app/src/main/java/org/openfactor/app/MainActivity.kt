package org.openfactor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle =
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        coil3.SingletonImageLoader.setSafe { context ->
            coil3.ImageLoader.Builder(context)
                .components { add(coil3.svg.SvgDecoder.Factory()) }
                .build()
        }
        setContent {
            val vm: WorkoutViewModel = viewModel()
            val data by vm.data.collectAsState()
            OpenFactorTheme(data.preferences.theme) {
                val dark = isSystemInDarkTheme()
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                OpenFactorApp(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenFactorApp(vm: WorkoutViewModel = viewModel()) {
    val data by vm.data.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf("") }
    var previousRoute by rememberSaveable { mutableStateOf("") }
    var exerciseInfo by rememberSaveable { mutableStateOf(false) }
    var exerciseSettingsReturnRoute by rememberSaveable { mutableStateOf("") }
    var customReturnRoute by rememberSaveable { mutableStateOf("") }
    var duplicateId by rememberSaveable { mutableStateOf("") }
    var programDayId by rememberSaveable { mutableStateOf("") }
    var programExit by remember { mutableStateOf(false) }
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    var configureShortcuts by rememberSaveable { mutableStateOf(false) }
    var workoutSettings by rememberSaveable { mutableStateOf(false) }
    var gymsReturn by rememberSaveable { mutableStateOf("") }
    var bodyDataTab by rememberSaveable { mutableIntStateOf(0) }
    var bodyChartMetric by rememberSaveable { mutableStateOf("weight") }
    var bodyChartRange by rememberSaveable { mutableStateOf(BodyChartRange.WEEK) }
    var bodyEntryReturn by rememberSaveable { mutableStateOf("body-data") }
    val contentRoute =
        if (route == "measurement-entry" || route == "photo-entry") bodyEntryReturn else route
    val exercises = vm.exercises(data)
    val snackbar = remember { SnackbarHostState() }
    val routeState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepAlive = data.preferences.keepAlive && route == "session" && data.active != null &&
        (data.onboarding.completed || data.onboarding.deferred)
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
    if (!data.onboarding.completed && !data.onboarding.deferred) {
        OnboardingScreen(data, vm) {
            if (data.programDraft != null) route = "program-editor"
            else {
                vm.beginProgram(TrainingProgram(name = "My Program"))
                route = "program-setup"
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
    LaunchedEffect(scrollEnergy, tab) {
        delay(140)
        scrollEnergy = 0f
    }
    BackHandler(route.isNotBlank()) {
        route =
            when (route) {
                "exercise" -> previousRoute
                "exercise-settings" -> exerciseSettingsReturnRoute
                "custom" -> customReturnRoute
                "picker" -> "session"
                "draft-picker" -> "workout-draft"
                "gym" -> "gyms"
                "gyms" -> gymsReturn
                "measurement-entry", "photo-entry" -> bodyEntryReturn
                "weight-entry" -> "body-data"
                "body-charts" -> "body-data"
                "program-picker" -> "program-editor"
                "program-editor",
                "program-setup" -> {
                    programExit = true
                    route
                }
                "programs" -> ""
                else -> ""
            }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OF.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (route.isBlank()) 94.dp else 0.dp))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .nestedScroll(rootScroll)
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = if (contentRoute.isBlank()) 0.dp else padding.calculateBottomPadding(),
                    )
                    .background(OF.background)
            ) {
                when (contentRoute) {
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
                    )
                    "weight-entry" -> key(detailId) {
                        ScaleWeightEditor(
                            data.scaleWeights.find { it.id == detailId }, data.preferences.pounds,
                            { route = "body-data" },
                            { vm.saveScaleWeight(it); bodyDataTab = 0; route = "body-data" },
                            { vm.deleteScaleWeight(detailId); bodyDataTab = 0; route = "body-data" },
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
                            .firstOrNull { it.id == detailId }
                            ?.let { gym ->
                                GymEditorScreen(gym, data, vm, { route = "gyms" }) { detailId = it }
                            }
                    "levels" -> routeState.SaveableStateProvider("levels") {
                        LevelsScreen(data, exercises, { route = "" }) {
                            detailId = it
                            exerciseSettingsReturnRoute = "levels"
                            route = "exercise-settings"
                        }
                    }
                    "exercise-settings" -> exercises.firstOrNull { it.id == detailId }?.let { e ->
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
                            .firstOrNull { it.id == detailId }
                            ?.let { e ->
                                ExerciseDetail(
                                    e,
                                    data,
                                    vm,
                                    { route = previousRoute },
                                    { pickExercise(e.id, previousRoute) },
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
                            )
                        } ?: run { LaunchedEffect(Unit) { route = "" } }
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
                            onFinished = {
                                detailId = it
                                route = "history-detail"
                            },
                        )
                    "history" ->
                        HistoryScreen(data.history, exercises, { route = "" }) {
                            detailId = it
                            route = "history-detail"
                        }
                    "history-detail" ->
                        data.history
                            .firstOrNull { it.id == detailId }
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
                                )
                            } ?: run { LaunchedEffect(Unit) { route = "history" } }
                    "routine" ->
                        RoutineBuilder(exercises, { route = "" }) { name, ids ->
                            vm.saveRoutine(name, ids)
                            route = ""
                            tab = 1
                        }
                    else ->
                        when (tab) {
                            0 ->
                                DashboardScreen(
                                    data,
                                    exercises,
                                    vm,
                                    onWorkout = { tab = 1 },
                                    onHistory = { route = "history" },
                                    onStart = { shortcuts = true },
                                    onResume = { route = "session" },
                                    onLevels = { route = "levels" },
                                    onBodyData = {
                                        bodyDataTab = 0
                                        route = "body-data"
                                    },
                                )
                            1 ->
                                WorkoutScreen(
                                    data,
                                    exercises,
                                    onStart = { shortcuts = true },
                                    onResume = { route = "session" },
                                    onRoutine = { r ->
                                        vm.start(r.name, r.exerciseIds)
                                        route = "session"
                                    },
                                    onNewRoutine = { route = "routine" },
                                    onDeleteRoutine = vm::deleteRoutine,
                                    onReorderRoutines = vm::reorderRoutines,
                                    onPrograms = { route = "programs" },
                                    onHistory = { route = "history" },
                                )
                            2 -> routeState.SaveableStateProvider("exercise-library") {
                                ExerciseLibrary(
                                    exercises,
                                    data.favorites,
                                    vm::favorite,
                                    { detailId = it; exerciseSettingsReturnRoute = ""; route = "exercise-settings" },
                                    onCustom = ::openCustom,
                                    gyms = data.gyms,
                                    equipmentCatalog = vm.equipmentCatalog,
                                    performedIds = data.history.flatMap { w -> w.exercises.filter { entry -> entry.sets.any { it.completed } }.map { it.exerciseId } }.toSet(),
                                    deprioritizedIds = data.exerciseDefaults.filterValues { it.doNotRecommend }.keys,
                                    modifiedIds = data.exerciseDefaults.filterValues { it.hasOverrides }.keys,
                                )
                            }
                            3 ->
                                SettingsScreen(
                                    data.preferences,
                                    vm,
                                    snackbar,
                                    onExercises = { tab = 2 },
                                    onHistory = { route = "history" },
                                    onBodyData = { route = "body-data" },
                                    onGyms = {
                                        gymsReturn = ""
                                        route = "gyms"
                                    },
                                )
                        }
                }
            }
            if (contentRoute.isBlank())
                NuvioWorkoutNavigation(
                    tab,
                    scrollEnergy,
                    onAdd = { shortcuts = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding()),
                ) {
                    tab = it
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
                WorkoutShortcut.WEIGHT -> { detailId = ""; bodyDataTab = 0; route = "weight-entry" }
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
                WorkoutShortcut.NEW_PROGRAM -> {
                    if (data.programDraft != null) route = "program-editor"
                    else { vm.beginProgram(TrainingProgram(name = "My Program")); route = "program-setup" }
                }
                WorkoutShortcut.NEW_WORKOUT -> route = "routine"
            }
        }
}
