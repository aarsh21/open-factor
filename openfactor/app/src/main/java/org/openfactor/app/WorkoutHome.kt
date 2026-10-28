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
    onDeleteRoutine: (String) -> Unit,
    onHistory: () -> Unit,
    onPrograms: () -> Unit,
    onDraft: () -> Unit,
) {
    var delete by remember { mutableStateOf<Routine?>(null) }
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
                data.programs.firstOrNull { it.id == data.activeProgramId }?.let { program ->
                    Surface(shape = OF.shape, color = OF.top) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarMonth, null)
                                Spacer(Modifier.width(12.dp))
                                Text(program.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            }
                            program.days.filter { it.exercises.isNotEmpty() }.take(4).forEach { day ->
                                TextButton(onPrograms, Modifier.fillMaxWidth()) {
                                    Text(day.name, Modifier.weight(1f))
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                            OutlinedButton(onPrograms, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open program") }
                        }
                    }
                }
                SectionTitle("Templates", if (data.routines.isNotEmpty()) "Create" else null, onNewRoutine)
            }
        }
        if (data.routines.isEmpty()) item {
            OutlinedButton(onNewRoutine, Modifier.padding(horizontal = 18.dp).fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Rounded.PlaylistAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Create template")
            }
        }
        items(data.routines, key = { it.id }) { routine ->
            var menu by remember { mutableStateOf(false) }
            Surface(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), shape = OF.shape, color = OF.top) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreHoriz, "Manage ${routine.name}") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Delete template") }, { menu = false; delete = routine })
                            }
                        }
                    }
                    routine.exerciseIds.take(4).forEach { id ->
                        exercises.firstOrNull { it.id == id }?.let { exercise ->
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
        if (data.history.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 18.dp)) { SectionTitle("Recent workouts", "All", onHistory) } }
            items(data.history.take(5), key = { "history-${it.id}" }) { workout ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onHistory).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = OF.green)
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
