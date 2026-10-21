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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    var shortcuts by rememberSaveable { mutableStateOf(false) }
    var routineName by rememberSaveable { mutableStateOf(false) }
    val exercises = vm.exercises(data)
    val snackbar = remember { SnackbarHostState() }
    fun openExercise(id: String) {
        previousRoute = route
        detailId = id
        route = "exercise"
    }
    BackHandler(route.isNotBlank()) { route = if (route == "exercise") previousRoute else "" }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OF.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (route.isBlank()) NuvioWorkoutNavigation(tab) { tab = it } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom =
                        if (route.isBlank()) padding.calculateBottomPadding()
                        else
                            androidx.compose.foundation.layout.WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding(),
                )
                .background(OF.background)
        ) {
            when (route) {
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
                                onWorkout = { tab = 1 },
                                onHistory = { route = "history" },
                                onStart = { shortcuts = true },
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
                                onHistory = { route = "history" },
                            )
                        2 ->
                            ExerciseLibrary(
                                exercises,
                                data.favorites,
                                vm::favorite,
                                ::openExercise,
                                onCustom = { route = "custom" },
                            )
                        3 ->
                            SettingsScreen(
                                data.preferences,
                                vm,
                                snackbar,
                                onExercises = { tab = 2 },
                                onHistory = { route = "history" },
                            )
                    }
            }
        }
    }
    if (shortcuts)
        ModalBottomSheet(onDismissRequest = { shortcuts = false }, containerColor = OF.top) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Shortcuts", style = MaterialTheme.typography.headlineSmall)
                ListItem(
                    headlineContent = { Text("Workout History") },
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    modifier =
                        Modifier.clickableCompat {
                            shortcuts = false
                            route = "history"
                        },
                    colors = ListItemDefaults.colors(containerColor = OF.top),
                )
                PrimaryAction(
                    if (data.active != null) "Resume Workout" else "Empty Workout",
                    {
                        shortcuts = false
                        vm.start()
                        route = "session"
                    },
                    icon = Icons.Rounded.FitnessCenter,
                )
                TextButton({
                    shortcuts = false
                    route = "routine"
                }) {
                    Text("New Workout")
                }
            }
        }
}

private fun Modifier.clickableCompat(action: () -> Unit) =
    this.then(Modifier.clickable(onClick = action))
