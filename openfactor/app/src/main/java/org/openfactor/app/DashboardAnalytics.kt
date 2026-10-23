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
) {
    val selected = data.preferences.dashboard.selected(section)
    if (
        selected.isEmpty() ||
            (section == DashboardSection.INSIGHTS && !data.preferences.showInsights) ||
            (section == DashboardSection.HABITS && !data.preferences.showHabits)
    )
        return
    val metrics = data.dashboardMetrics(exercises)
    Column(Modifier.padding(horizontal = 18.dp)) {
        when (section) {
            DashboardSection.INSIGHTS -> {
                SectionTitle(section.label)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    selected.forEach { key ->
                        if (key == "workouts")
                            InsightCard(
                                "Workouts",
                                "Saved workouts · selected scope",
                                metrics.history.size.toString(),
                                "saved",
                                Modifier.width(178.dp),
                                { onDetail("workouts") },
                            )
                        else
                            InsightCard(
                                "Weight Trend",
                                "Calculation unavailable",
                                "—",
                                "",
                                Modifier.width(178.dp),
                                { onDetail("trend") },
                            )
                    }
                }
            }
            DashboardSection.HABITS -> {
                SectionTitle(section.label)
                Surface(shape = OF.shape, color = OF.card) {
                    Column {
                        selected.forEach { key ->
                            val dates =
                                if (key == "weighIn") data.scaleWeights.map { it.date }.toSet()
                                else metrics.history.map { it.localDate() }.toSet()
                            val monday =
                                LocalDate.now()
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
                                        "${(0..6).count { monday.plusDays(it.toLong()) in dates }} days this week",
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
                if ("workouts" in selected) TextButton(onStart) { Text("Start workout") }
            }
            DashboardSection.BODY -> {
                val series =
                    data
                        .bodyChartSeries()
                        .filter {
                            it.key in selected && data.preferences.dataVisibility.isVisible(it.key)
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
                                muscleContributions(metrics.weekly, exercises, 7, muscle).sumOf {
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
                                "$sets saved working sets · 1-RM unavailable",
                            ) {
                                onDetail("exercise:$id")
                            }
                        }
                    }
                }
            }
            DashboardSection.GENERAL -> {
                SectionTitle(section.label)
                Surface(shape = OF.shape, color = OF.card) {
                    DashboardRow("Steps", "No local step data") { onDetail("steps") }
                }
            }
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
) {
    val metrics = data.dashboardMetrics(exercises)
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
        item {
            Text(
                if (metrics.allWorkouts) "All Workouts" else "Active Program",
                Modifier.padding(18.dp),
                color = OF.muted,
            )
        }
        when {
            muscle != null -> {
                val contributions = muscleContributions(metrics.weekly, exercises, 7, muscle)
                item {
                    Text(
                        "Completed working sets this week. Primary muscles count 1 set; secondary muscles count ½ set. Calendar windows and advanced-set eligibility remain local policies.",
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
                            "Saved sets, not estimated 1-RM. The source strength-profile calculation is not recovered.",
                            Modifier.padding(18.dp),
                            color = OF.muted,
                        )
                    }
                if (exercise != null && onExercise != null)
                    item { TextButton({ onExercise(exercise.id) }) { Text("Exercise settings") } }
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
                        "Weight Trend smoothing is not recovered. No smoothed value is calculated. Scale Weight charts show your original dated readings in Body Metrics.",
                        Modifier.padding(18.dp),
                        color = OF.muted,
                    )
                }
            key == "steps" ->
                item {
                    Text(
                        "No step records are stored by this build. Steps requires a local data-entry or Health Connect integration; the selected tile does not imply a connection.",
                        Modifier.padding(18.dp),
                        color = OF.muted,
                    )
                }
        }
    }
}
