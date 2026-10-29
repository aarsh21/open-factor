package org.openfactor.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class WorkoutDraft(
    val name: String = "Workout",
    val exercises: List<SessionExercise> = emptyList(),
    val gymId: String? = null,
    val notes: String = "",
)

fun AppData.startWorkoutDraft(now: Long): AppData {
    val draft = workoutDraft ?: return this
    if (active != null) return this
    return copy(
        active = Workout(
            name = draft.name,
            startedAt = now,
            exercises = draft.exercises,
            selectedExerciseId = draft.exercises.firstOrNull()?.id,
            gymId = draft.gymId,
            notes = draft.notes,
        ),
        workoutDraft = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutComposition(
    draft: WorkoutDraft,
    exercises: List<Exercise>,
    gyms: List<GymProfile>,
    onEdit: ((WorkoutDraft) -> WorkoutDraft) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onDetail: (String) -> Unit,
    onStart: () -> Unit,
    onDiscard: () -> Unit,
    canStart: Boolean = true,
) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var gymPicker by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var reordering by rememberSaveable { mutableStateOf(false) }
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    val muscles = draft.exercises.flatMap { byId[it.exerciseId]?.muscles.orEmpty() }.distinct()
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(draft.name, maxLines = 2, textAlign = TextAlign.Center) },
            navigationIcon = {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            },
            actions = {
                IconButton({ gymPicker = true }) { Icon(Icons.Rounded.Warehouse, "Workout gym") }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Composition options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename Workout") },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (reordering) "Done reordering" else "Re-order") },
                            onClick = { menu = false; reordering = !reordering },
                        )
                        DropdownMenuItem(
                            text = { Text("Discard Workout Draft") },
                            onClick = { menu = false; discard = true },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = OF.top),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Target Muscles", style = MaterialTheme.typography.headlineSmall)
            }
            if (muscles.isEmpty()) item {
                Surface(
                    shape = OF.shape,
                    color = OF.background,
                    border = BorderStroke(1.dp, OF.line),
                ) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccessibilityNew, null, Modifier.size(48.dp), tint = OF.line)
                        Text(
                            "You haven’t added any exercises yet. Once you add an exercise, target muscles will appear here.",
                            Modifier.padding(start = 18.dp),
                            color = OF.muted,
                        )
                    }
                }
            } else item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(muscles, key = { it }) { muscle ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MuscleImage(muscle, Modifier.size(84.dp))
                            Text(muscle, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${draft.exercises.size} ${if (draft.exercises.size == 1) "Exercise" else "Exercises"}",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            if (draft.exercises.isEmpty()) "Estimated workout time is 0 min"
                            else draft.exercises.sumOf { it.logicalSets().size }.let { "$it ${if (it == 1) "set" else "sets"}" },
                            color = OF.muted,
                        )
                    }
                    FilledTonalIconButton(onAdd, Modifier.size(52.dp)) {
                        Icon(Icons.Rounded.Add, "Add Exercises")
                    }
                }
            }
            items(draft.exercises, key = { it.id }) { entry ->
                val exercise = byId[entry.exerciseId]
                Surface(shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (exercise != null) ExerciseImage(exercise, Modifier.size(56.dp))
                            TextButton({ onDetail(entry.exerciseId) }, Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(exercise?.title ?: "Exercise")
                                    Text(entry.logicalSets().size.let { "$it ${if (it == 1) "Set" else "Sets"}" }, color = OF.muted)
                                }
                            }
                            IconButton({ onEdit { it.copy(exercises = it.exercises.filterNot { e -> e.id == entry.id }) } }) {
                                Icon(Icons.Rounded.Close, "Remove ${exercise?.title ?: "exercise"}")
                            }
                        }
                        if (reordering) {
                            val index = draft.exercises.indexOfFirst { it.id == entry.id }
                            Row {
                                listOf(-1 to Icons.Rounded.ArrowUpward, 1 to Icons.Rounded.ArrowDownward).forEach { (delta, icon) ->
                                    IconButton(
                                        onClick = {
                                            onEdit { current ->
                                                val from = current.exercises.indexOfFirst { it.id == entry.id }
                                                val to = from + delta
                                                if (from < 0 || to !in current.exercises.indices) current
                                                else current.copy(exercises = current.exercises.toMutableList().apply {
                                                    java.util.Collections.swap(this, from, to)
                                                })
                                            }
                                        },
                                        enabled = index + delta in draft.exercises.indices,
                                    ) {
                                        Icon(icon, "Move ${exercise?.title ?: "exercise"} ${if (delta < 0) "up" else "down"}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(18.dp)) { PrimaryAction("Start Workout", onStart, enabled = canStart) }
    }
    if (rename) NameDialog("Rename Workout", draft.name, { rename = false }) { name ->
        onEdit { it.copy(name = name.trim()) }
        rename = false
    }
    if (gymPicker) AlertDialog(
        onDismissRequest = { gymPicker = false },
        title = { Text("Workout Gym") },
        text = {
            LazyColumn {
                items(gyms, key = { it.id }) { gym ->
                    TextButton({
                        onEdit { it.copy(gymId = gym.id) }
                        gymPicker = false
                    }) { Text(gym.name) }
                }
            }
        },
        confirmButton = { TextButton({ gymPicker = false }) { Text("Cancel") } },
    )
    if (discard) ConfirmDialog(
        "Discard Workout Draft?",
        "This removes this composition. Saved workouts, programs and history stay unchanged.",
        "Discard",
        { discard = false },
        { discard = false; onDiscard() },
    )
}
