package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
internal fun DashboardTiles(
    data: AppData,
    exercises: List<Exercise>,
    section: DashboardSection,
    onDetail: (String) -> Unit,
    onChart: (String) -> Unit,
    onHistory: () -> Unit,
    onBodyData: () -> Unit,
    onStart: () -> Unit,
    onHealthConnect: (() -> Unit)? = null,
    onLogWeight: (() -> Unit)? = null,
) {
    val selected = data.preferences.dashboard.selected(section)
    if (
        selected.isEmpty() ||
            (section == DashboardSection.INSIGHTS && !data.preferences.showInsights) ||
            (section == DashboardSection.HABITS && !data.preferences.showHabits)
    )
        return
    val today = rememberToday()
    val metrics = data.copy(preferences = data.preferences.copy(dashboardAllWorkouts = true)).dashboardMetrics(exercises, today)
    Column(Modifier.padding(horizontal = 18.dp)) {
        when (section) {
            DashboardSection.INSIGHTS -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    selected.forEach { key ->
                        if (key == "trend") WeightTrendCard(data, { onChart("weight") }, onLogWeight ?: onBodyData)
                        else DashboardRow("Workouts", "${data.history.distinctBy { it.id }.size} saved") { onHistory() }
                    }
                }
            }
            DashboardSection.HABITS -> {
                SectionTitle(section.label)
                Surface(shape = OF.shape, color = OF.card) {
                    Column {
                        selected.forEach { key ->
                            val dates =
                                if (key == "weighIn") data.scaleWeights.map { it.date }.filter { !it.isAfter(today) }.toSet()
                                else data.history.filter { it.localDate() <= today && it.completedSets() > 0 }.map { it.localDate() }.toSet()
                            val monday =
                                today
                                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable(
                                        onClick = if (key == "weighIn") onBodyData else onHistory
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (key == "weighIn") "Weigh-In" else "Workouts",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        if (key == "workouts" && data.preferences.weeklyWorkoutGoal != null) {
                                            val count = data.history.distinctBy { it.id }.count { it.localDate() in monday..today && it.completedSets() > 0 }
                                            "$count of ${data.preferences.weeklyWorkoutGoal} workouts"
                                        } else "${(0..6).count { monday.plusDays(it.toLong()) in dates }} ${if ((0..6).count { monday.plusDays(it.toLong()) in dates } == 1) "day" else "days"} this week",
                                        color = OF.muted,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    (0..6).forEach { index ->
                                        val date = monday.plusDays(index.toLong())
                                        val done = date in dates
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                if (done) Icons.Rounded.CheckCircle
                                                else Icons.Rounded.RadioButtonUnchecked,
                                                "${date}: ${if (done) "logged" else "not logged"}",
                                                tint = if (done) OF.green else OF.muted,
                                                modifier = Modifier.size(22.dp),
                                            )
                                            Text(
                                                date.dayOfWeek.name.take(1),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OF.muted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            DashboardSection.BODY -> {
                val series =
                    data
                        .bodyChartSeries()
                        .filter {
                            it.key in selected && data.preferences.dataVisibility.isVisible(it.key) &&
                                it.points.any { point -> !point.date.isAfter(today) } &&
                                (it.key != "weight" || "trend" !in data.preferences.dashboard.insights || !data.preferences.showInsights)
                        }
                        .sortedBy { selected.indexOf(it.key) }
                if (series.isNotEmpty()) {
                    SectionTitle(section.label, "History", onBodyData)
                    Surface(shape = OF.shape, color = OF.card) {
                        Column {
                            series.forEach { metric ->
                                val last =
                                    metric.points
                                        .filter { !it.date.isAfter(LocalDate.now()) }
                                        .maxByOrNull { it.date }
                                DashboardRow(
                                    metric.label,
                                    last?.let {
                                        "${it.value.bodyNumber()} ${metric.unit} · ${it.date}"
                                    } ?: "No readings yet",
                                ) {
                                    onChart(metric.key)
                                }
                            }
                        }
                    }
                }
            }
            DashboardSection.MUSCLES -> {
                SectionTitle(section.label)
                Surface(shape = OF.shape, color = OF.card) {
                    Column {
                        selected.forEach { muscle ->
                            val count =
                                muscleContributions(metrics.weekly, exercises, 7, muscle, today).sumOf {
                                    it.weightedSets
                                }
                            DashboardRow(muscle, "${count.bodyNumber()} weighted sets this week") {
                                onDetail("muscle:$muscle")
                            }
                        }
                    }
                }
            }
            DashboardSection.EXERCISES -> {
                val catalog = exercises.associateBy { it.id }
                SectionTitle(section.label)
                Surface(shape = OF.shape, color = OF.card) {
                    Column {
                        selected.forEach { id ->
                            val exercise = catalog[id]
                            val sets =
                                metrics.history
                                    .flatMap { it.exercises }
                                    .filter { it.exerciseId == id }
                                    .sumOf { it.completedWorkingSets() }
                            DashboardRow(
                                exercise?.title ?: "Unavailable exercise",
                                "$sets recorded working sets",
                            ) {
                                onDetail("exercise:$id")
                            }
                        }
                    }
                }
            }
            DashboardSection.GENERAL -> StepsCard(data.healthConnect, onHealthConnect)
        }
    }
}

@Composable
private fun DashboardRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = OF.card),
    )
}

@Composable
internal fun DashboardDetail(
    data: AppData,
    exercises: List<Exercise>,
    key: String,
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    onExercise: ((String) -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    val metrics = data.dashboardMetrics(exercises, today)
    val muscle = key.takeIf { it.startsWith("muscle:") }?.removePrefix("muscle:")
    val exercise =
        key.takeIf { it.startsWith("exercise:") }
            ?.removePrefix("exercise:")
            ?.let { id -> exercises.find { it.id == id } }
    val title =
        muscle
            ?: exercise?.title
            ?: when (key) {
                "workouts" -> "Workouts"
                "trend" -> "Weight Trend"
                "steps" -> "Steps"
                else -> "Exercise history"
            }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        item { PageHeading(title, onBack) }
        when {
            muscle != null -> {
                val contributions = muscleContributions(metrics.weekly, exercises, 7, muscle, today)
                item {
                    Text(
                        "Primary sets count 1. Secondary sets count ½. Warmups are excluded.",
                        Modifier.padding(18.dp),
                        color = OF.muted,
                    )
                }
                if (contributions.isEmpty())
                    item { Text("No logged sets in this period.", Modifier.padding(18.dp)) }
                items(contributions, key = { it.exercise.id }) { contribution ->
                    DashboardRow(
                        contribution.exercise.title,
                        "${contribution.weightedSets.bodyNumber()} weighted sets",
                    ) {
                        onDetail("exercise:${contribution.exercise.id}")
                    }
                }
            }
            key == "workouts" || key.startsWith("exercise:") -> {
                val history =
                    metrics.history.filter {
                        key == "workouts" ||
                            it.exercises.any { e -> e.exerciseId == key.removePrefix("exercise:") }
                    }
                if (key.startsWith("exercise:"))
                    item {
                        Text(
                            "Completed working sets",
                            Modifier.padding(18.dp),
                            color = OF.muted,
                        )
                    }
                if (exercise != null && onExercise != null)
                    item { TextButton({ onExercise(exercise.id) }) { Text("Open exercise") } }
                if (history.isEmpty()) item { Text("No exercise history", Modifier.padding(18.dp)) }
                items(history, key = { it.id }) { workout ->
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(workout.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${workout.localDate()} · ${workout.completedWorkingSets()} working sets",
                            color = OF.muted,
                        )
                        if (key.startsWith("exercise:"))
                            workout.exercises
                                .filter { it.exerciseId == key.removePrefix("exercise:") }
                                .flatMap { it.logicalSets() }
                                .forEachIndexed { index, logical ->
                                    logical.components.forEachIndexed { componentIndex, logs ->
                                        logs
                                            .filter { it.completed }
                                            .forEach { set ->
                                                Text(
                                                    listOfNotNull(
                                                            "Set ${index + 1}",
                                                            set.side?.label,
                                                            if (componentIndex > 0)
                                                                "Myo $componentIndex"
                                                            else null,
                                                            set.weightKg?.let {
                                                                "${it.displayWeight(data.preferences.pounds)} ${if (data.preferences.pounds) "lb" else "kg"}"
                                                            },
                                                            set.reps?.let { "$it reps" },
                                                            set.durationSeconds?.let {
                                                                "${it.bodyNumber()} s"
                                                            },
                                                            set.distanceMeters?.let {
                                                                "${it.bodyNumber()} m"
                                                            },
                                                            if (set.warmup) "Warm-up" else null,
                                                        )
                                                        .joinToString(" · ")
                                                )
                                            }
                                    }
                                }
                    }
                    HorizontalDivider(color = OF.line)
                }
            }
            key == "trend" ->
                item {
                    Text(
                        "Weight trend is the average of available readings in the preceding seven calendar days. Missing dates are never counted as zero.",
                        Modifier.padding(18.dp),
                        color = OF.muted,
                    )
                }
            key == "steps" ->
                item {
                    Text(
                        "Connect Health Connect from Settings to import actual daily step totals.",
                        Modifier.padding(18.dp),
                        color = OF.muted,
                    )
                }
        }
    }
}
