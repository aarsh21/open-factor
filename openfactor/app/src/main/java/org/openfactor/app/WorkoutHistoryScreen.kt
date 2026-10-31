package org.openfactor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Stable reverse chronology, independent of the order imported backups used. */
internal fun workoutHistoryDays(history: List<Workout>, selected: LocalDate? = null): List<Pair<LocalDate, List<Workout>>> =
    history.distinctBy { it.id }.filter { selected == null || it.localDate() == selected }
        .sortedByDescending { it.startedAt }.groupBy { it.localDate() }.toList()

internal fun historyDuration(seconds: Long): String = if (seconds >= 3600)
    "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
else "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
fun HistoryScreen(
    history: List<Workout>,
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    prefs: Preferences = Preferences(),
    onRepeat: ((String) -> Unit)? = null,
    onStart: (() -> Unit)? = null,
) {
    val today = rememberToday()
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
    val selected = selectedDate?.let(LocalDate::parse)
    val groups = remember(history, selected) { workoutHistoryDays(history, selected) }
    val catalog = remember(exercises) { exercises.associateBy { it.id } }
    val dates = remember(history) { history.groupBy { it.localDate() } }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Workout History", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().background(OF.card).padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(first.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton({ weekOffset--; selectedDate = null }, enabled = weekOffset > -520) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous history week")
                        }
                        IconButton({ weekOffset++; selectedDate = null }, enabled = weekOffset < 0) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next history week")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(7) { index ->
                            val day = first.plusDays(index.toLong())
                            val saved = dates[day].orEmpty()
                            val isSelected = day == selected
                            Column(
                                Modifier.weight(1f).background(if (isSelected) OF.elevated else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { selectedDate = if (isSelected) null else day.toString() }
                                    .padding(vertical = 10.dp).semantics(mergeDescendants = true) {
                                        contentDescription = "$day, ${saved.size} workouts${if (isSelected) ", selected" else ""}"
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(day.format(DateTimeFormatter.ofPattern("EEEEE")), color = OF.muted, style = MaterialTheme.typography.labelMedium)
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium,
                                    color = if (day == today) MaterialTheme.colorScheme.primary else OF.muted)
                                Text(if (saved.isEmpty()) "·" else "${saved.sumOf { it.elapsedSeconds(it.finishedAt ?: it.startedAt) } / 60}m",
                                    style = MaterialTheme.typography.labelSmall, color = if (saved.isEmpty()) OF.muted else OF.complete)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selected == null) "Past workouts" else selected.format(DateTimeFormatter.ofPattern("d MMMM")),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (selected != null) TextButton({ selectedDate = null }) { Text("Show all") }
                }
            }
            if (groups.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.History, null, Modifier.size(40.dp), tint = OF.muted)
                    Text(if (history.isEmpty()) "No workouts yet" else "No workouts on this day", style = MaterialTheme.typography.titleMedium)
                    if (onStart != null) PrimaryAction("Start workout", onStart, icon = Icons.Rounded.Add)
                    else if (selected != null) TextButton({ selectedDate = null }) { Text("Show all workouts") }
                }
            }
            groups.forEach { (day, workouts) ->
                item(key = "date:$day") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(OF.muted, CircleShape))
                        Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), Modifier.padding(start = 18.dp), color = OF.muted,
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(workouts, key = { it.id }) { workout ->
                    HistoryWorkoutCard(workout, catalog, prefs, { onDetail(workout.id) }, onRepeat?.let { repeat -> { repeat(workout.id) } })
                }
            }
        }
        if (onStart != null && history.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            PrimaryAction("Start workout", onStart, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun HistoryWorkoutCard(
    workout: Workout,
    catalog: Map<String, Exercise>,
    prefs: Preferences,
    onOpen: () -> Unit,
    onRepeat: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    val totals = remember(workout, catalog) { muscleWorkloads(listOf(workout), catalog).filter { it.weightedSets > 0 }.associate { it.muscle to it.weightedSets } }
    val names = remember(workout, catalog) { workout.exercises.map { catalog[it.exerciseId]?.name ?: "Custom exercise" } }
    val line = OF.line
    Column(Modifier.fillMaxWidth().drawBehind { drawLine(line, Offset(29.dp.toPx(), 0f), Offset(29.dp.toPx(), size.height), 1.dp.toPx()) }) {
        Surface(color = OF.card, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
            .semantics { contentDescription = "Open ${workout.name}, ${workout.localDate()}, ${workout.completedWorkingSets()} working sets" }) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = OF.elevated) {
                        TrainingMuscleMap(totals, Modifier.size(60.dp, 78.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(workout.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(historyDuration(workout.elapsedSeconds(workout.finishedAt ?: workout.startedAt)), color = OF.muted,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions for ${workout.name}") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Open workout") }, onClick = { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
                            if (onRepeat != null) DropdownMenuItem(text = { Text("Repeat workout") }, onClick = { menu = false; onRepeat() }, leadingIcon = { Icon(Icons.Rounded.Replay, null) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HistoryMetric("Exercises", workout.exercises.size.toString(), Modifier.weight(1f))
                    HistoryMetric("Working sets", workout.completedWorkingSets().toString(), Modifier.weight(1f))
                    HistoryMetric("Volume", "${workout.volumeKg().displayWeight(prefs.pounds)} ${if (prefs.pounds) "lb" else "kg"}", Modifier.weight(1.25f))
                }
                HorizontalDivider(color = OF.line)
                Text(names.take(3).joinToString(", ") + if (names.size > 3) " and ${names.size - 3} more" else "",
                    color = OF.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (onRepeat != null) TextButton(onRepeat, Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Repeat")
                }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = OF.muted, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
