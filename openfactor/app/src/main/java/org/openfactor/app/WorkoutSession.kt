package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
    onWorkoutSettings: () -> Unit,
    onGymSettings: () -> Unit,
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
    var workoutNotes by remember { mutableStateOf(false) }
    var restSettings by remember { mutableStateOf(false) }
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
                        text = { Text("Workout settings") },
                        onClick = { menu = false; onWorkoutSettings() },
                    )
                    DropdownMenuItem(
                        text = { Text("Gym settings") },
                        onClick = { menu = false; onGymSettings() },
                    )
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
                        text = { Text("Workout notes") },
                        onClick = { menu = false; workoutNotes = true },
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
                if (data.preferences.showWorkoutTimer)
                    Text(
                        "%d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                        fontSize = 22.sp,
                    )
                if (workout.pausedAt != null) Text("Paused", color = OF.muted, fontSize = 12.sp)
            }
            TextButton({ restSettings = true }) {
                Icon(Icons.Rounded.Timer, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${remaining/60}:${(remaining%60).toString().padStart(2, '0')}",
                    Modifier.semantics { contentDescription = "Rest timer settings, $remaining seconds remaining" },
                    fontSize = 20.sp,
                )
            }
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
                val metrics = remember(e) { e.loggingMetrics() }
                val showRir = data.preferences.showRir && metrics.any { it.isReps }
                var remove by remember { mutableStateOf(false) }
                var exerciseMenu by remember { mutableStateOf(false) }
                var exerciseNotes by remember { mutableStateOf(false) }
                Surface(color = OF.background) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                Modifier.weight(1f).clickable {
                                    onExercise(e.id)
                                }
                            ) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${se.sets.count { it.completed }} of ${se.sets.size} sets completed",
                                    color = OF.muted, fontSize = 12.sp,
                                )
                            }
                            if (data.preferences.showBodyweightContribution && e.bodyweight > 0.0) {
                                val weight = data.scaleWeights.filter { it.date <= workout.localDate() }.maxByOrNull { it.date }
                                val unit = if (data.preferences.pounds) "lb" else "kg"
                                Surface(
                                    shape = RoundedCornerShape(8.dp), color = OF.background,
                                    border = BorderStroke(1.dp, OF.green),
                                ) {
                                    Text(
                                        if (weight == null) "No scale weight\n${(e.bodyweight * 100).toInt()}% contribution"
                                        else "${weight.weightKg.displayWeight(data.preferences.pounds)} $unit\n" +
                                            "+${(weight.weightKg * e.bodyweight).displayWeight(data.preferences.pounds)} $unit (${(e.bodyweight * 100).toInt()}%)",
                                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = OF.green,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        var targets by remember { mutableStateOf(false) }
                        var warmup by remember { mutableStateOf(false) }
                        Row {
                            Box {
                                IconButton({ exerciseMenu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options for ${e.title}") }
                                DropdownMenu(exerciseMenu, { exerciseMenu = false }) {
                                    DropdownMenuItem(text = { Text("Exercise notes") }, onClick = { exerciseMenu = false; exerciseNotes = true })
                                    DropdownMenuItem(text = { Text("Remove Exercise") }, onClick = { exerciseMenu = false; remove = true })
                                }
                            }
                            TextButton({ onExercise(e.id) }) { Text("Info") }
                            TextButton({ warmup = true }) { Text("Warm Up") }
                            TextButton({ targets = true }) { Text("Targets") }
                        }
                        val pinnedNote = data.exerciseDefaults[e.id]?.note.orEmpty()
                        if (pinnedNote.isNotBlank()) Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.PushPin, null, Modifier.size(20.dp), tint = OF.muted)
                            Column(Modifier.weight(1f)) {
                                Text("Pinned exercise note", style = MaterialTheme.typography.bodySmall, color = OF.muted)
                                Text(pinnedNote, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (se.notes.isNotBlank()) Text(
                            se.notes,
                            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit exercise notes") { exerciseNotes = true }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge, color = OF.muted,
                        )
                        if (targets)
                            WorkoutTargetsDialog(se, { targets = false }) { values, rest ->
                                vm.saveExerciseTargets(se.id, values, rest)
                                targets = false
                            }
                        if (warmup)
                            AlertDialog(
                                onDismissRequest = { warmup = false },
                                title = { Text("Warm Up") },
                                text = { Text("Add an empty warm-up set before your working sets.") },
                                confirmButton = {
                                    TextButton({ vm.addWarmupSet(se.id); warmup = false }) {
                                        Text("Add empty warm-up set")
                                    }
                                },
                                dismissButton = {
                                    TextButton({ warmup = false }) { Text("Cancel") }
                                },
                            )
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Set", Modifier.width(40.dp), color = OF.muted, fontSize = 12.sp)
                            Text("Auto", Modifier.width(48.dp), color = OF.muted, fontSize = 12.sp)
                            metrics.forEach { metric ->
                                Text(metric.label(data.preferences.pounds, data.preferences.distanceImperial), Modifier.weight(if (metric.isLoad) 1.3f else 1f), color = OF.muted, fontSize = 12.sp)
                            }
                            if (showRir)
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
                                    metrics,
                                    exerciseName = e.title,
                                    onChange = { changed -> vm.editSet(se.id, s.id) { changed } },
                                    onComplete = { vm.toggleSetCompletion(se.id, s.id) },
                                    onRemove = { vm.removeSet(se.id, s.id) },
                                )
                            }
                        }
                        FilledTonalIconButton({ vm.addSet(se.id) }) {
                            Icon(Icons.Rounded.Add, "Add Set")
                        }
                    }
                }
                if (exerciseNotes) SessionNotesDialog("Exercise notes", se.notes, { exerciseNotes = false }) {
                    vm.exerciseNotes(se.id, it)
                    exerciseNotes = false
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
        }
    }
    if (restSettings)
        AlertDialog(
            onDismissRequest = { restSettings = false },
            title = { Text("Rest Timers") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Changes apply to the next set you complete. A running timer keeps its deadline.")
                    listOf(
                        Triple("Use Rest Timers", data.preferences.autoRest, { checked: Boolean -> vm.settings { it.copy(autoRest = checked) } }),
                        Triple("Rest After Last Warm-up Set", data.preferences.restAfterLastWarmupSet, { checked: Boolean -> vm.settings { it.copy(restAfterLastWarmupSet = checked) } }),
                        Triple("Rest Between Exercises", data.preferences.restBetweenExercises, { checked: Boolean -> vm.settings { it.copy(restBetweenExercises = checked) } }),
                    ).forEach { (label, checked, change) ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = change),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(label, Modifier.weight(1f))
                            Switch(checked, onCheckedChange = null)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ restSettings = false }) { Text("Done") } },
        )
    if (workoutNotes) SessionNotesDialog("Workout notes", workout.notes, { workoutNotes = false }) {
        vm.workoutNotes(it)
        workoutNotes = false
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
private fun SessionNotesDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(title) }, minLines = 3) },
        confirmButton = { TextButton({ onSave(notes) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WorkoutTargetsDialog(
    exercise: SessionExercise,
    onDismiss: () -> Unit,
    onSave: (Map<String, WorkingSetTarget>, Int?) -> Unit,
) {
    val sets = remember { exercise.sets.filterNot { it.warmup } }
    var values by remember {
        mutableStateOf(sets.associate { it.id to listOf(
            it.targetRepsMin?.toString().orEmpty(),
            it.targetRepsMax?.toString().orEmpty(),
            it.targetRir?.toString().orEmpty(),
        ) })
    }
    var rest by remember { mutableStateOf(exercise.restSeconds?.toString().orEmpty()) }
    val parsed = values.mapValues { (_, fields) ->
        WorkingSetTarget(fields[0].toIntOrNull(), fields[1].toIntOrNull(), fields[2].toIntOrNull())
    }
    val valid = values.values.all { fields ->
        fields.all { it.isBlank() || it.toIntOrNull() != null }
    } && parsed.values.all { it.isValid() } &&
        (rest.isBlank() || rest.toIntOrNull()?.let { it in 0..1800 } == true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout Targets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Today's targets apply only to this workout. Set minimum/maximum reps and RIR, or leave fields blank for no target.")
                sets.forEachIndexed { index, set ->
                    Text("Working set ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Reps Min" to "Min", "Reps Max" to "Max", "RIR" to "RIR").forEachIndexed { field, (description, label) ->
                            OutlinedTextField(
                                values.getValue(set.id)[field],
                                { text ->
                                    values = values + (set.id to values.getValue(set.id).toMutableList().apply { this[field] = text })
                                },
                                Modifier.weight(1f).semantics {
                                    contentDescription = "Set ${index + 1} $description target"
                                },
                                label = { Text(label) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    rest, { rest = it }, Modifier.fillMaxWidth(),
                    label = { Text("Set Rest Timers (seconds)") },
                    supportingText = { Text("0–1800; blank uses workout settings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (!valid) Text("Use positive ordered rep bounds, RIR 0–10 and rest 0–1800.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({ onSave(parsed, rest.toIntOrNull()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetRow(
    index: Int,
    set: LoggedSet,
    prefs: Preferences,
    metrics: List<SetMetric>,
    exerciseName: String,
    onChange: (LoggedSet) -> Unit,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
) {
    var options by remember { mutableStateOf(false) }
    var rir by remember(set.id) { mutableStateOf(set.rir?.toString() ?: "") }
    val rirValue = rir.toIntOrNull()
    val rirError = rir.isNotEmpty() && (rirValue == null || rirValue !in 0..10)
    val showRir = prefs.showRir && metrics.any { it.isReps }
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
                Modifier.width(40.dp).semantics {
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
        Text(
            set.targetDescription(), Modifier.width(48.dp),
            color = OF.muted, style = MaterialTheme.typography.labelSmall,
        )
        metrics.forEach { metric ->
            key(metric) {
                var text by rememberSaveable(set.id, prefs.pounds, prefs.distanceImperial, metric) {
                    mutableStateOf(metric.display(set, prefs.pounds, prefs.distanceImperial))
                }
                val invalid = text.isNotBlank() && metric.parse(text, prefs.pounds, prefs.distanceImperial) == null
                OutlinedTextField(
                    text,
                    { entered ->
                        text = entered
                        val changed = metric.update(set, metric.parse(entered, prefs.pounds, prefs.distanceImperial))
                        onChange(changed.copy(completed = set.completed && changed.canComplete(metrics)))
                    },
                    Modifier.weight(if (metric.isLoad) 1.3f else 1f).semantics {
                        contentDescription = "$exerciseName set ${index + 1} ${metric.catalogName.lowercase()} (${metric.label(prefs.pounds, prefs.distanceImperial)})"
                        if (invalid) error(if (metric.isReps) "Enter a whole number from 1 to 9999" else "Enter a valid ${metric.label(prefs.pounds, prefs.distanceImperial)} value")
                    },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(if (metric.isReps) "Use 1–9999" else "Invalid value") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric.isReps) KeyboardType.Number else KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (showRir)
            OutlinedTextField(
                rir,
                {
                    rir = it
                    val value = it.toIntOrNull()?.takeIf { n -> n in 0..10 }
                    onChange(set.copy(rir = value, completed = set.completed && (it.isBlank() || value != null)))
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
            enabled = set.completed || set.canComplete(metrics) && (!showRir || !rirError),
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
                                        s.describe(e?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS), prefs.pounds, prefs.distanceImperial)
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
