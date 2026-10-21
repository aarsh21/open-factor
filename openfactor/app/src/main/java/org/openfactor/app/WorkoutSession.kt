package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun WorkoutSession(
    data: AppData,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onExercise: (String) -> Unit,
    onFinished: (String) -> Unit,
) {
    val workout = data.active
    if (workout == null) {
        Column {
            PageHeading("Workout", onBack)
            EmptyContent(
                Icons.Rounded.FitnessCenter,
                "No active workout",
                "Start a workout from the Workout tab.",
                "Back",
                onBack,
            )
        }
        return
    }
    var confirm by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val selected =
        workout.exercises.find { it.id == workout.selectedExerciseId }
            ?: workout.exercises.firstOrNull()
    val restUntil = workout.restUntil
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var saveTemplate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workout.id) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val elapsed = workout.elapsedSeconds(now)
    val remaining = ((restUntil - now + 999) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.Menu, "Workout options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (workout.pausedAt == null) "Pause Workout" else "Resume Workout"
                            )
                        },
                        onClick = {
                            menu = false
                            vm.pauseWorkout(workout.pausedAt == null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Minimize Workout") },
                        onClick = {
                            menu = false
                            onBack()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Finish Workout") },
                        enabled = workout.completedSets() > 0,
                        onClick = {
                            menu = false
                            confirm = "finish"
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename Workout") },
                        onClick = {
                            menu = false
                            rename = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save as Workout Template") },
                        onClick = {
                            menu = false
                            vm.saveRoutine(workout.name, workout.exercises.map { it.exerciseId })
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Discard Workout") },
                        onClick = {
                            menu = false
                            confirm = "discard"
                        },
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "%d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                    fontSize = 22.sp,
                )
                if (workout.pausedAt != null) Text("Paused", color = OF.muted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.Timer, null, Modifier.size(20.dp))
            Text("${remaining/60}:${(remaining%60).toString().padStart(2, '0')}", fontSize = 20.sp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            items(workout.exercises, key = { it.id }) { entry ->
                val exercise =
                    exercises.find { it.id == entry.exerciseId }
                        ?: Exercise(entry.exerciseId, "Exercise", "Other", "Other")
                val active = entry.id == selected?.id
                Column(
                    Modifier.width(80.dp)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { vm.selectExercise(entry.id) },
                        )
                        .semantics { contentDescription = "Select ${exercise.title}" }
                ) {
                    ExerciseImage(exercise, Modifier.fillMaxWidth().height(104.dp))
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .height(3.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.onSurface else OF.line
                            )
                    )
                }
            }
            item {
                FilledTonalIconButton(
                    onAdd,
                    Modifier.size(width = 80.dp, height = 104.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Rounded.Add, "Add Exercise", Modifier.size(32.dp))
                }
            }
        }
        if (remaining > 0)
            Surface(color = OF.elevated, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Timer, null)
                    Text(
                        "  Rest ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}",
                        Modifier.weight(1f),
                    )
                    TextButton({ vm.extendRest(30) }) { Text("+30s") }
                    TextButton({ vm.clearRest() }) { Text("Skip") }
                }
            }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (workout.exercises.isEmpty())
                item {
                    EmptyContent(
                        Icons.Rounded.FitnessCenter,
                        "Build your workout",
                        "Add an exercise to start logging your sets.",
                        "Add Exercise",
                        onAdd,
                    )
                }
            items(listOfNotNull(selected), key = { it.id }) { se ->
                val e =
                    exercises.find { it.id == se.exerciseId }
                        ?: Exercise(se.exerciseId, "Exercise", "Other", "Other")
                var remove by remember { mutableStateOf(false) }
                Surface(shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(e, Modifier.size(48.dp).clickable { onExercise(e.id) })
                            Column(
                                Modifier.weight(1f).padding(horizontal = 10.dp).clickable {
                                    onExercise(e.id)
                                }
                            ) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text(e.muscle, color = OF.muted, fontSize = 12.sp)
                            }
                            IconButton({ remove = true }) {
                                Icon(Icons.Rounded.Close, "Remove ${e.name}", tint = OF.muted)
                            }
                        }
                        OutlinedTextField(
                            se.notes,
                            { vm.exerciseNotes(se.id, it) },
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            placeholder = { Text("Exercise notes") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Set", Modifier.width(48.dp), color = OF.muted, fontSize = 12.sp)
                            Text(
                                if (data.preferences.pounds) "lb" else "kg",
                                Modifier.weight(1f),
                                color = OF.muted,
                                fontSize = 12.sp,
                            )
                            Text("Reps", Modifier.weight(1f), color = OF.muted, fontSize = 12.sp)
                            if (data.preferences.showRir)
                                Text(
                                    "RIR",
                                    Modifier.weight(0.65f),
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                )
                            Spacer(Modifier.width(48.dp))
                        }
                        se.sets.forEachIndexed { index, s ->
                            key(s.id) {
                                SetRow(
                                    index,
                                    s,
                                    data.preferences,
                                    exerciseName = e.title,
                                    onChange = { changed -> vm.editSet(se.id, s.id) { changed } },
                                    onComplete = { vm.toggleSetCompletion(se.id, s.id) },
                                    onRemove = { vm.removeSet(se.id, s.id) },
                                )
                            }
                        }
                        TextButton({ vm.addSet(se.id) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Text(" Add Set")
                        }
                    }
                }
                if (remove)
                    ConfirmDialog(
                        "Remove ${e.name}?",
                        "Its sets will be removed from this workout.",
                        "Remove",
                        { remove = false },
                        {
                            vm.removeExercise(se.id)
                            remove = false
                        },
                    )
            }
            item {
                OutlinedButton(
                    onAdd,
                    Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Text(" Add Exercise")
                }
            }
            item {
                OutlinedTextField(
                    workout.notes,
                    vm::workoutNotes,
                    Modifier.fillMaxWidth(),
                    label = { Text("Workout notes") },
                    minLines = 2,
                )
            }
        }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            PrimaryAction(
                "Finish Workout",
                { confirm = "finish" },
                enabled = workout.completedSets() > 0,
                icon = Icons.Rounded.Check,
            )
        }
    }
    if (rename)
        NameDialog(
            "Rename Workout",
            workout.name,
            { rename = false },
            {
                vm.renameActive(it)
                rename = false
            },
        )
    if (confirm == "discard")
        ConfirmDialog(
            "Discard Workout?",
            "This removes the current session and its sets.",
            "Discard",
            { confirm = "" },
            {
                vm.discard()
                confirm = ""
                onBack()
            },
        )
    if (confirm == "finish")
        AlertDialog(
            onDismissRequest = { confirm = "" },
            title = { Text("Finish Workout?") },
            text = {
                Column {
                    Text("${workout.completedSets()} completed sets will be saved to your history.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saveTemplate, { saveTemplate = it })
                        Text("Save as workout template")
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (saveTemplate)
                        vm.saveRoutine(workout.name, workout.exercises.map { it.exerciseId })
                    val id = workout.id
                    vm.finish()
                    confirm = ""
                    onFinished(id)
                }) {
                    Text("Finish")
                }
            },
            dismissButton = { TextButton({ confirm = "" }) { Text("Keep Training") } },
        )
}

@Composable
private fun SetRow(
    index: Int,
    set: LoggedSet,
    prefs: Preferences,
    exerciseName: String,
    onChange: (LoggedSet) -> Unit,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
) {
    var options by remember { mutableStateOf(false) }
    var weight by
        remember(set.id, prefs.pounds) { mutableStateOf(set.weightKg.displayWeight(prefs.pounds)) }
    var reps by remember(set.id) { mutableStateOf(set.reps.toString()) }
    var rir by remember(set.id) { mutableStateOf(set.rir?.toString() ?: "") }
    val weightError = weight.toWeightKg(prefs.pounds) == null
    val repsValue = reps.toIntOrNull()
    val repsError = repsValue == null || repsValue !in 1..9999
    val rirValue = rir.toIntOrNull()
    val rirError = rir.isNotEmpty() && (rirValue == null || rirValue !in 0..10)
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (set.completed) OF.green.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            TextButton(
                { options = true },
                Modifier.width(48.dp).semantics {
                    contentDescription = "Set ${index + 1} options for $exerciseName"
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (set.warmup) "W" else "${index+1}", fontSize = 13.sp)
            }
            DropdownMenu(options, { options = false }) {
                DropdownMenuItem(
                    text = { Text(if (set.warmup) "Mark as Working Set" else "Mark as Warm-Up") },
                    onClick = {
                        options = false
                        onChange(set.copy(warmup = !set.warmup))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove Set") },
                    onClick = {
                        options = false
                        onRemove()
                    },
                )
            }
        }
        OutlinedTextField(
            weight,
            {
                weight = it
                it.toWeightKg(prefs.pounds)?.let { kg -> onChange(set.copy(weightKg = kg)) }
            },
            Modifier.weight(1f).semantics {
                contentDescription =
                    "$exerciseName set ${index + 1} weight (${if(prefs.pounds) "lb" else "kg"})"
                if (weightError) error("Enter a weight from 0 to 5000")
            },
            supportingText =
                if (weightError) {
                    { Text("Enter 0–5000") }
                } else null,
            singleLine = true,
            isError = weightError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            reps,
            {
                reps = it
                it.toIntOrNull()
                    ?.takeIf { n -> n in 1..9999 }
                    ?.let { n -> onChange(set.copy(reps = n)) }
            },
            Modifier.weight(1f).semantics {
                contentDescription = "$exerciseName set ${index + 1} reps"
                if (repsError) error("Enter reps from 1 to 9999")
            },
            supportingText =
                if (repsError) {
                    { Text("Enter 1–9999") }
                } else null,
            singleLine = true,
            isError = repsError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        if (prefs.showRir)
            OutlinedTextField(
                rir,
                {
                    rir = it
                    if (it.isBlank()) onChange(set.copy(rir = null))
                    else
                        it.toIntOrNull()
                            ?.takeIf { n -> n in 0..10 }
                            ?.let { n -> onChange(set.copy(rir = n)) }
                },
                Modifier.weight(0.65f).semantics {
                    contentDescription = "$exerciseName set ${index + 1} reps in reserve"
                    if (rirError) error("Enter RIR from 0 to 10, or leave empty")
                },
                supportingText =
                    if (rirError) {
                        { Text("Enter 0–10") }
                    } else null,
                singleLine = true,
                isError = rirError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        IconButton(
            onComplete,
            Modifier.size(48.dp)
                .background(if (set.completed) OF.green else OF.elevated, RoundedCornerShape(8.dp)),
            enabled = !weightError && !repsError && !rirError,
        ) {
            Icon(
                Icons.Rounded.Check,
                if (set.completed) "Mark set incomplete ${index + 1} for $exerciseName"
                else "Complete set ${index + 1} for $exerciseName",
                tint = if (set.completed) Color.Black else OF.muted,
            )
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<Workout>,
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageHeading("Workout History", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (history.isEmpty())
                item {
                    EmptyContent(
                        Icons.Rounded.History,
                        "No workouts yet",
                        "Finish your first workout to see your history and records.",
                    )
                }
            items(history, key = { it.id }) { w ->
                Surface(
                    Modifier.fillMaxWidth().clickable { onDetail(w.id) },
                    shape = OF.shape,
                    color = OF.card,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            w.localDate().format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),
                            color = OF.muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            w.name,
                            Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${w.exercises.size} exercises · ${w.completedSets()} sets",
                            color = OF.muted,
                        )
                        Text(
                            w.exercises
                                .mapNotNull { se ->
                                    exercises.find { it.id == se.exerciseId }?.name
                                }
                                .joinToString(", "),
                            Modifier.padding(top = 12.dp),
                            maxLines = 2,
                            color = OF.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutSummary(
    w: Workout,
    exercises: List<Exercise>,
    prefs: Preferences,
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
) {
    var delete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            "Workout Complete",
            onBack,
            actions = {
                IconButton({ delete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete workout")
                }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(w.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    w.localDate().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                    color = OF.muted,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat(
                        "Duration",
                        "${w.elapsedSeconds(w.finishedAt ?: w.startedAt) / 60} min",
                    )
                    SummaryStat("Sets", w.completedSets().toString())
                    SummaryStat(
                        "Volume",
                        "${w.volumeKg().displayWeight(prefs.pounds)} ${if(prefs.pounds)"lb" else "kg"}",
                    )
                }
            }
            item { SectionTitle("Workout Details") }
            items(w.exercises, key = { it.id }) { se ->
                val e = exercises.find { it.id == se.exerciseId }
                Surface(shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e != null) ExerciseImage(e, Modifier.size(48.dp))
                            Text(
                                e?.title ?: "Exercise",
                                Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        se.sets
                            .filter { it.completed }
                            .forEachIndexed { i, s ->
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                    Text(
                                        if (s.warmup) "Warm-up" else "Set ${i+1}",
                                        Modifier.weight(1f),
                                        color = OF.muted,
                                    )
                                    Text(
                                        "${s.weightKg.displayWeight(prefs.pounds)} ${if(prefs.pounds)"lb" else "kg"} × ${s.reps}${s.rir?.let {" · $it RIR"} ?: ""}"
                                    )
                                }
                            }
                        if (se.notes.isNotBlank())
                            Text(se.notes, Modifier.padding(top = 12.dp), color = OF.muted)
                    }
                }
            }
            if (w.notes.isNotBlank())
                item {
                    SectionTitle("Notes")
                    Text(w.notes, color = OF.muted)
                }
        }
        Box(Modifier.padding(18.dp)) { PrimaryAction("Repeat Workout", onRepeat) }
    }
    if (delete)
        ConfirmDialog(
            "Delete Workout?",
            "This permanently removes this workout from your local history.",
            "Delete",
            { delete = false },
            onDelete,
        )
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = OF.muted, fontSize = 12.sp)
    }
}
