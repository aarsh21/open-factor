package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

/** Logging is the default destination. Planning and analytics remain one action away. */
@Composable
fun WorkoutHome(
    data: AppData,
    exercises: List<Exercise>,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRoutine: (Routine) -> Unit,
    onNewRoutine: () -> Unit,
    onTemplates: () -> Unit,
    onEditRoutine: (Routine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onHistory: () -> Unit,
    onPrograms: () -> Unit,
    onDraft: () -> Unit,
) {
    var delete by remember { mutableStateOf<Routine?>(null) }
    val today = rememberToday()
    val week = remember(data.active, data.history, exercises, today) { data.trainingWeek(exercises, today = today) }
    val catalog = remember(exercises) { exercises.associateBy { it.id } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageHeading("Workout", actions = {
                RoundAction(Icons.Rounded.History, "Workout history", onHistory)
            })
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val active = data.active
                if (active != null) {
                    Text(active.name, style = MaterialTheme.typography.titleLarge)
                    PrimaryAction("Resume workout", onResume, icon = Icons.Rounded.PlayArrow)
                } else {
                    PrimaryAction("Start workout", onStart, icon = Icons.Rounded.Add)
                }
                if (data.workoutDraft != null && active == null) OutlinedButton(onDraft, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Continue workout draft")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onPrograms) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Programs") }
                }
                WeeklyWorkoutCard(week, onHistory)
                SectionTitle("Templates", "Browse", onTemplates)
            }
        }
        if (data.routines.isEmpty()) item {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onTemplates, Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Browse splits") }
                OutlinedButton(onNewRoutine, Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Create template") }
            }
        }
        items(data.routines.take(3), key = { it.id }) { routine ->
            var menu by remember { mutableStateOf(false) }
            Surface(Modifier.itemMotion(this).padding(horizontal = 18.dp).fillMaxWidth(), shape = OF.shape, color = OF.top) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "Manage ${routine.name}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Edit template") }, { menu = false; onEditRoutine(routine) })
                                DropdownMenuItem({ Text("Delete template") }, { menu = false; delete = routine })
                            }
                        }
                    }
                    routine.exerciseIds.take(4).forEach { id ->
                        catalog[id]?.let { exercise ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ExerciseImage(exercise, Modifier.size(40.dp))
                                Text(exercise.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (routine.exerciseIds.size > 4) Text("+${routine.exerciseIds.size - 4} exercises", color = OF.muted)
                    PrimaryAction("Start ${routine.name}", { onRoutine(routine) }, enabled = data.active == null)
                }
            }
        }
        if (data.routines.isNotEmpty()) item {
            TextButton(onTemplates, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("All templates (${data.routines.size})"); Icon(Icons.Rounded.ChevronRight, null) }
        }
        if (data.history.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 18.dp)) { SectionTitle("Recent workouts", "All", onHistory) } }
            items(data.history.take(5), key = { "history-${it.id}" }) { workout ->
                Row(
                    Modifier.itemMotion(this).fillMaxWidth().clickable(onClick = onHistory).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = OF.complete)
                    Text(workout.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(workout.localDate().format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted)
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
    delete?.let { routine ->
        ConfirmDialog("Delete ${routine.name}?", "Your saved workouts stay in History.", "Delete", { delete = null }, {
            onDeleteRoutine(routine.id)
            delete = null
        })
    }
}
