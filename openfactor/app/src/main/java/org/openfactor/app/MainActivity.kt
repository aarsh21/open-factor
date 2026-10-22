package org.openfactor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
        setContent { OpenFactorTheme { OpenFactorApp() } }
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
    var programDayId by rememberSaveable { mutableStateOf("") }
    var programExit by remember { mutableStateOf(false) }
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    var workoutSettings by rememberSaveable { mutableStateOf(false) }
    var gymsReturn by rememberSaveable { mutableStateOf("") }
    val exercises = vm.exercises(data)
    val snackbar = remember { SnackbarHostState() }
    fun openExercise(id: String) {
        previousRoute = route
        detailId = id
        route = "exercise"
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
                "gym" -> "gyms"
                "gyms" -> gymsReturn
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
                        bottom = if (route.isBlank()) 0.dp else padding.calculateBottomPadding(),
                    )
                    .background(OF.background)
            ) {
                when (route) {
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
                    "program-picker" ->
                        ExerciseLibrary(
                            exercises,
                            data.favorites,
                            vm::favorite,
                            ::openExercise,
                            { id ->
                                vm.editProgramDraft { plan ->
                                    plan.copy(
                                        days =
                                            plan.days.map { day ->
                                                if (day.id == programDayId)
                                                    day.addExercise(
                                                        id,
                                                        plan.cycleCount,
                                                        "Workout ${('A'.code + plan.days.count { it.exercises.isNotEmpty() }).toChar()}",
                                                    )
                                                else day
                                            }
                                    )
                                }
                                route = "program-editor"
                            },
                            onBack = { route = "program-editor" },
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
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
                    "levels" -> LevelsScreen(data, exercises, { route = "" }, ::openExercise)
                    "exercise" ->
                        exercises
                            .firstOrNull { it.id == detailId }
                            ?.let { e ->
                                ExerciseDetail(
                                    e,
                                    data,
                                    vm,
                                    { route = previousRoute },
                                    {
                                        vm.start()
                                        vm.addExercise(e.id)
                                        route = "session"
                                    },
                                )
                            }
                    "picker" ->
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
                            onCustom = { route = "custom" },
                            picking = true,
                            gyms = data.gyms,
                            equipmentCatalog = vm.equipmentCatalog,
                        )
                    "custom" ->
                        CustomExercise({ route = "" }) { n, m, e, i ->
                            val id = vm.addCustom(n, m, e, i)
                            openExercise(id)
                        }
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
                                    onPrograms = { route = "programs" },
                                    onHistory = { route = "history" },
                                )
                            2 ->
                                ExerciseLibrary(
                                    exercises,
                                    data.favorites,
                                    vm::favorite,
                                    ::openExercise,
                                    onCustom = { route = "custom" },
                                    gyms = data.gyms,
                                    equipmentCatalog = vm.equipmentCatalog,
                                )
                            3 ->
                                SettingsScreen(
                                    data.preferences,
                                    vm,
                                    snackbar,
                                    onExercises = { tab = 2 },
                                    onHistory = { route = "history" },
                                    onGyms = {
                                        gymsReturn = ""
                                        route = "gyms"
                                    },
                                )
                        }
                }
            }
            if (route.isBlank())
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
    if (shortcuts)
        ModalBottomSheet(onDismissRequest = { shortcuts = false }, containerColor = OF.top) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ shortcuts = false }) {
                        Icon(Icons.Rounded.Close, "Close shortcuts")
                    }
                    Text("Shortcuts", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                }
                ListItem(
                    headlineContent = { Text("History") },
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    modifier =
                        Modifier.clickableCompat {
                            shortcuts = false
                            route = "history"
                        },
                    colors = ListItemDefaults.colors(containerColor = OF.top),
                )
                ListItem(
                    headlineContent = { Text(if (data.active != null) "Resume Workout" else "Empty Workout") },
                    leadingContent = { Icon(Icons.Rounded.FitnessCenter, null) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable {
                            shortcuts = false
                            vm.start()
                            route = "session"
                        },
                    colors = ListItemDefaults.colors(containerColor = OF.top),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("New Program") },
                    leadingContent = { Icon(Icons.Rounded.EventNote, null) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable {
                        shortcuts = false
                        if (data.programDraft != null) route = "program-editor"
                        else {
                            vm.beginProgram(TrainingProgram(name = "My Program"))
                            route = "program-setup"
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = OF.top),
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("New Workout") },
                    leadingContent = { Icon(Icons.Rounded.Add, null) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable {
                        shortcuts = false
                        route = "routine"
                    },
                    colors = ListItemDefaults.colors(containerColor = OF.top),
                )
            }
        }
}

private fun Modifier.clickableCompat(action: () -> Unit) =
    this.then(Modifier.clickable(onClick = action))
