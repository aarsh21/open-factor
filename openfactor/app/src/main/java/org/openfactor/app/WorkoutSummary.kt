package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutSummary(
    w: Workout,
    exercises: List<Exercise>,
    prefs: Preferences,
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
    repeatLabel: String = "Repeat workout",
    onEdit: (() -> Unit)? = null,
) {
    var delete by remember { mutableStateOf(false) }
    val catalog = remember(exercises) { exercises.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Workout Complete", onBack, actions = {
            if (onEdit != null) IconButton(onEdit) { Icon(Icons.Rounded.Edit, "Edit workout") }
            IconButton({ delete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete workout") }
        })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(w.name, style = MaterialTheme.typography.headlineMedium)
                    Text(w.localDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), color = OF.muted)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryStat("Duration", historyDuration(w.elapsedSeconds(w.finishedAt ?: w.startedAt)), Modifier.weight(1f))
                    SummaryStat("Sets", w.completedSets().toString(), Modifier.weight(.7f), spokenLabel = "Completed sets")
                    SummaryStat("Volume", "${w.volumeKg().displayWeight(prefs.pounds)} ${if (prefs.pounds) "lb" else "kg"}", Modifier.weight(1.3f))
                }
            }
            item { Text("Workout Details", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge) }
            items(w.exercises, key = { it.id }) { entry ->
                SummaryExercise(entry, catalog[entry.exerciseId], prefs)
            }
            if (w.notes.isNotBlank()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Notes", style = MaterialTheme.typography.titleLarge)
                    Text(w.notes, color = OF.muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) { PrimaryAction(repeatLabel, onRepeat) }
    }
    if (delete) ConfirmDialog(
        "Delete Workout?", "This permanently removes this workout from your local history.", "Delete",
        { delete = false }, onDelete,
    )
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier, spokenLabel: String = label) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$spokenLabel: $value" }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, color = OF.muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SummaryExercise(entry: SessionExercise, exercise: Exercise?, prefs: Preferences) {
    val groups = remember(entry) { entry.logicalSets() }
    val recorded = groups.count { it.logs.any { log -> log.completed } }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("summary-exercise-${entry.id}"),
        shape = OF.shape, color = OF.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (exercise != null) ExerciseImage(exercise, Modifier.size(44.dp, 56.dp), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(exercise?.title ?: "Exercise", style = MaterialTheme.typography.titleMedium)
                    if (entry.supersetId != null) Text("Superset", color = OF.muted, style = MaterialTheme.typography.labelMedium)
                }
                Text("$recorded ${if (recorded == 1) "set" else "sets"}", color = OF.muted, style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider(color = OF.line)
            RecordedSetTable(entry, exercise, prefs)
            if (entry.notes.isNotBlank()) {
                HorizontalDivider(color = OF.line)
                Text(entry.notes, color = OF.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Shared recorded-set presentation for workout completion and per-exercise history. */
@Composable
internal fun RecordedSetTable(entry: SessionExercise, exercise: Exercise?, prefs: Preferences) {
    val groups = remember(entry) { entry.logicalSets() }
    val recorded = groups.count { it.logs.any { log -> log.completed } }
    val metrics = exercise?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS)
    val showRir = groups.any { group -> group.logs.any { it.completed && it.rir != null } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (recorded == 0) Text("No completed sets", color = OF.muted, style = MaterialTheme.typography.bodyMedium)
        else {
            // Keep every metric in catalog order. RIR is an actual, never inferred from set kind.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text("Set", Modifier.weight(1.25f), color = OF.muted, style = MaterialTheme.typography.labelMedium)
                metrics.forEach { metric ->
                    Text(metric.label(prefs.pounds, prefs.distanceImperial), Modifier.weight(1f), color = OF.muted,
                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                }
                if (showRir) Text("RIR", Modifier.weight(.7f), color = OF.muted, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
            var working = 0
            groups.forEach { group ->
                if (!group.root.warmup) working++
                if (group.logs.any { it.completed }) {
                    val label = if (group.root.warmup) "Warm-up" else "Set $working · ${group.root.kind.label}"
                    group.components.forEachIndexed { component, logs ->
                        logs.forEach { log ->
                            val detail = listOfNotNull(if (component > 0) "Myo $component" else null, log.side?.label).joinToString(" · ")
                            SummarySetRow(exercise?.title ?: "Exercise", label, detail, log, metrics, showRir, prefs)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySetRow(
    exercise: String, label: String, detail: String, log: LoggedSet,
    metrics: List<SetMetric>, showRir: Boolean, prefs: Preferences,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp).semantics(mergeDescendants = true) {
            contentDescription = listOf(exercise, label, detail,
                if (log.completed) log.describe(metrics, prefs.pounds, prefs.distanceImperial) else "Not completed")
                .filter(String::isNotBlank).joinToString(", ")
        }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (log.warmup) OF.muted else LocalContentColor.current)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelSmall, color = OF.muted)
        }
        if (!log.completed) Text("Not completed", Modifier.weight(metrics.size.coerceAtLeast(1).toFloat() + if (showRir) .7f else 0f),
            color = OF.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        else {
            metrics.forEach { metric ->
                Text(metric.display(log, prefs.pounds, prefs.distanceImperial).ifEmpty { "—" }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
            }
            if (metrics.isEmpty()) Text("Completed", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
            if (showRir) Text(log.rir?.toString() ?: "—", Modifier.weight(.7f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
        }
    }
}
