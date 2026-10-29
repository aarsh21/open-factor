package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    data: AppData,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    onWorkout: () -> Unit,
    onHistory: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onBodyData: () -> Unit,
    onLogWeight: (() -> Unit)? = null,
    onHealthConnect: (() -> Unit)? = null,
) {
    val today = rememberToday()
    var customize by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var detailParent by rememberSaveable { mutableStateOf<String?>(null) }
    var chart by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf(BodyChartRange.MONTH) }
    var trainingPeriod by rememberSaveable { mutableStateOf(TrainingPeriod.WEEK) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    val detailState = rememberSaveableStateHolder()
    val selection = data.preferences.dashboard
    fun closeDetail() { detail = detailParent; detailParent = null }
    androidx.activity.compose.BackHandler(customize || detail != null || chart != null) {
        customize = false
        if (detail != null) closeDetail()
        chart = null
    }
    if (customize) {
        DashboardCustomization(data, exercises, { customize = false }) { selected ->
            vm.settings { it.copy(dashboard = selected, showWeeklyWorkouts = true, showInsights = true, showHabits = true) }
            customize = false
        }
        return
    }
    chart?.let { key ->
        BodyChartScreen(data, key, range, { chart = it }, { range = it }, { chart = null },
            onDismissTutorial = { vm.settings { it.copy(dismissedTutorials = it.dismissedTutorials + "bodyCharts") } }, onLogWeight = onLogWeight)
        return
    }
    val training = remember(data.active, data.history, exercises, trainingPeriod, periodOffset, today) {
        data.trainingPeriod(exercises, trainingPeriod, periodOffset, today)
    }
    if (detail == "workload" || detail?.startsWith("muscle:") == true) {
        LevelsScreen(data, exercises, { detail = null }, { detail = "exercise:$it" },
            referenceDate = minOf(today, training.last), referenceStart = training.first,
            initialMuscle = detail?.takeIf { it.startsWith("muscle:") }?.removePrefix("muscle:"))
        return
    }
    detail?.let { key ->
        detailState.SaveableStateProvider(key) {
            DashboardDetail(data.copy(preferences = data.preferences.copy(dashboardAllWorkouts = true)), exercises, key,
                ::closeDetail, { detailParent = detail; detail = it },
                today = minOf(today, training.last))
        }
        return
    }
    val visibleSections = remember(data.preferences, data.scaleWeights, data.bodyMeasurements, today) {
        selection.sections.filter { data.hasVisibleDashboardSection(it, today) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PageHeading("Dashboard", actions = { RoundAction(Icons.Rounded.Tune, "Customize Dashboard", { customize = true }) }) }
        if (data.active != null) item {
            Box(Modifier.padding(horizontal = 18.dp)) { PrimaryAction("Resume ${data.active.name}", onResume, icon = Icons.Rounded.PlayArrow) }
        }
        if (data.preferences.showWeeklyWorkouts && "weekly" in selection.focuses) item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TrainingPeriodPanel(training, trainingPeriod, periodOffset,
                    onPeriod = { trainingPeriod = it; periodOffset = 0 },
                    onOffset = { periodOffset = it.coerceIn(-520, 0) })
                if (training.workingSets == 0 && periodOffset == 0) PrimaryAction("Start workout", onStart, icon = Icons.Rounded.Add)
            }
        }
        item { Box(Modifier.padding(horizontal = 18.dp)) { TrainingMusclePanel(training, { detail = "workload" }) { detail = "muscle:$it" } } }
        items(visibleSections, key = { it.name }) { section ->
            DashboardTiles(data, exercises, section, { detail = it }, { chart = it }, onHistory, onBodyData, onHealthConnect, onLogWeight)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onWorkout, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Templates") }
                OutlinedButton(onHistory, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("History") }
            }
        }
    }
}
