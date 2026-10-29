package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    var timerSettingsError by remember { mutableStateOf(false) }
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
    var guidanceExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(workout.restUntil) { now = System.currentTimeMillis() }
    val elapsed = workout.elapsedSeconds(now)
    val remaining = ((restUntil - now + 999) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onBack) { Icon(Icons.Rounded.KeyboardArrowDown, "Minimize workout") }
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                { focus.clearFocus(); confirm = "finish" },
                enabled = workout.completedSets() > 0,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { Text("Finish") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Workout options") }
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
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (data.preferences.showWorkoutTimer || workout.pausedAt != null) {
                Text(
                    if (workout.pausedAt != null) "Paused" else "%d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                    style = MaterialTheme.typography.labelLarge,
                    color = OF.muted,
                )
            } else Spacer(Modifier.width(1.dp))
            if (remaining <= 0) TextButton({ restSettings = true }) {
                Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rest timer")
            }
        }
        WorkoutExerciseSelector(
            workout, exercises, selected,
            onSelect = { focus.clearFocus(); vm.selectExercise(it) },
            onAdd = { focus.clearFocus(); onAdd() },
        )
        if (remaining > 0)
            Surface(color = OF.elevated, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton({ restSettings = true }) { Icon(Icons.Rounded.Timer, "Rest timer settings") }
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
                var superset by remember { mutableStateOf(false) }
                var mergeSides by remember { mutableStateOf(false) }
                val logicalSets = se.logicalSets()
                var targets by remember { mutableStateOf(false) }
                val showTargets = se.sets.any { it.targetRepsMin != null || it.targetRepsMax != null || it.targetRir != null }
                Surface(color = OF.background) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExerciseImage(e, Modifier.size(42.dp, 54.dp))
                            Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            TextButton({ focus.clearFocus(); guidanceExerciseId = e.id }) {
                                Text("Guide")
                            }
                            Box {
                                IconButton({ exerciseMenu = true }) { Icon(Icons.Rounded.MoreVert, "Exercise options for ${e.title}") }
                                DropdownMenu(exerciseMenu, { exerciseMenu = false }) {
                                    DropdownMenuItem(text = { Text("Exercise guide") }, onClick = { exerciseMenu = false; focus.clearFocus(); guidanceExerciseId = e.id })
                                    DropdownMenuItem(text = { Text("Targets & rest") }, onClick = { exerciseMenu = false; targets = true })
                                    if (metrics.any { it.isReps }) DropdownMenuItem(text = { Text(if (showRir) "Hide RIR" else "Show RIR") }, onClick = { exerciseMenu = false; vm.settings { it.copy(showRir = !it.showRir) } })
                                    DropdownMenuItem(text = { Text("Exercise notes") }, onClick = { exerciseMenu = false; exerciseNotes = true })
                                    DropdownMenuItem(text = { Text(if (se.sets.any { it.side != null }) "Use left logs only…" else "Log left and right separately") }, onClick = {
                                        exerciseMenu = false
                                        if (se.sets.any { it.side != null }) mergeSides = true else vm.splitSides(se.id)
                                    }, enabled = se.sets.isNotEmpty())
                                    DropdownMenuItem(text = { Text("Create superset…") }, enabled = workout.exercises.size > 1, onClick = { exerciseMenu = false; superset = true })
                                    if (se.supersetId != null) DropdownMenuItem(text = { Text("Leave superset") }, onClick = { exerciseMenu = false; vm.leaveSuperset(se.id) })
                                    DropdownMenuItem(text = { Text("Move earlier") }, enabled = workout.exercises.first().id != se.id, onClick = { exerciseMenu = false; vm.moveSessionExercise(se.id, -1) })
                                    DropdownMenuItem(text = { Text("Move later") }, enabled = workout.exercises.last().id != se.id, onClick = { exerciseMenu = false; vm.moveSessionExercise(se.id, 1) })
                                    DropdownMenuItem(text = { Text("Remove Exercise") }, onClick = { exerciseMenu = false; remove = true })
                                }
                            }
                        }
                        if (se.supersetId != null) Text(
                            "Superset · " + workout.exercises.filter { it.supersetId == se.supersetId }
                                .joinToString(" + ") { entry -> exercises.find { it.id == entry.exerciseId }?.title ?: "Exercise" },
                            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = OF.muted,
                        )
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
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Set", Modifier.width(40.dp), color = OF.muted, fontSize = 12.sp)
                            if (showTargets) Text("Target", Modifier.width(48.dp), color = OF.muted, fontSize = 12.sp)
                            metrics.forEach { metric ->
                                Text(metric.label(data.preferences.pounds, data.preferences.distanceImperial), Modifier.weight(if (metric.isLoad) 1.3f else 1f), color = OF.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                            if (showRir)
                                Text(
                                    "RIR",
                                    Modifier.weight(0.65f),
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            Spacer(Modifier.width(48.dp))
                        }
                        logicalSets.forEachIndexed { index, group ->
                            group.components.forEachIndexed { componentIndex, logs ->
                                if (group.root.kind != SetKind.STANDARD || logs.size > 1) Text(
                                    if (componentIndex == 0) "${if (group.root.warmup) "Warm-up" else "Set ${logicalSets.take(index + 1).count { !it.root.warmup }}"} · ${group.root.kind.label}"
                                    else "Myo component $componentIndex", Modifier.padding(top = 10.dp), color = OF.muted,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                logs.forEach { s -> key(s.id) {
                                    SetRow(
                                        index, s, data.preferences, metrics,
                                        exerciseName = e.title + (s.side?.let { " ${it.label.lowercase()}" } ?: "") +
                                            (if (componentIndex > 0) " Myo component $componentIndex" else ""),
                                        onChange = { changed -> vm.editSet(se.id, s.id) { changed } },
                                        onComplete = { focus.clearFocus(); vm.toggleSetCompletion(se.id, s.id) },
                                        onRemove = { vm.removeSet(se.id, s.id) },
                                        onKind = { kind, isWarmup -> vm.setKind(se.id, s.id, kind, isWarmup) },
                                        hasExtras = group.components.size > 1,
                                        showTargets = showTargets,
                                        displayNumber = logicalSets.take(index + 1).count { it.root.warmup == group.root.warmup },
                                    )
                                } }
                            }
                            if (group.root.kind == SetKind.MYO && !group.root.warmup) TextButton({ vm.addMyoComponent(se.id, group.root.id) }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Text("Add Myo component", Modifier.padding(start = 6.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton({ focus.clearFocus(); vm.addWarmupSet(se.id) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Warmup")
                            }
                            FilledTonalButton({ focus.clearFocus(); vm.addSet(se.id) }, Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add set")
                            }
                        }
                    }
                }
                if (exerciseNotes) SessionNotesDialog("Exercise notes", se.notes, { exerciseNotes = false }) {
                    vm.exerciseNotes(se.id, it)
                    exerciseNotes = false
                }
                if (superset) SupersetDialog(workout, se.id, exercises, { superset = false }) { ids ->
                    vm.groupSuperset(ids)
                    superset = false
                }
                if (mergeSides) ConfirmDialog("Use left logs only?", "Right-side values and completion will be removed. Left-side values stay unchanged.", "Keep left logs", { mergeSides = false }) {
                    vm.keepLeftSide(se.id)
                    mergeSides = false
                }
                if (remove) {
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
    }
    guidanceExerciseId?.let { id ->
        exercises.firstOrNull { it.id == id }?.let { exercise ->
            ModalBottomSheet(
                onDismissRequest = { guidanceExerciseId = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = OF.background,
                dragHandle = null,
            ) {
                Box(Modifier.fillMaxHeight(.95f)) {
                    ExerciseDetail(
                        exercise, data, vm,
                        onBack = { guidanceExerciseId = null },
                        onAdd = null,
                        closeable = true,
                    )
                }
            }
        }
    }
    if (restSettings)
        AlertDialog(
            onDismissRequest = { restSettings = false },
            title = { Text("Rest Timers") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var seconds by remember { mutableStateOf(data.preferences.restSeconds.toString()) }
                    val duration = seconds.toIntOrNull()
                    val durationError = seconds.isNotEmpty() && (duration == null || duration !in 0..1800)
                    OutlinedTextField(
                        seconds,
                        { value ->
                            seconds = value
                            value.toIntOrNull()?.takeIf { it in 0..1800 }?.let { valid -> vm.settings { it.copy(restSeconds = valid) } }
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("Rest duration (seconds)") },
                        isError = durationError,
                        supportingText = { Text(if (durationError) "Use 0–1800 seconds" else "Applies to your next completed set") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                    if (!WorkoutNotifications.preciseTimersAllowed(context)) {
                        Text("Android may delay background alerts without precise timer access.", style = MaterialTheme.typography.bodyMedium, color = OF.muted)
                        TextButton({
                            timerSettingsError = runCatching { context.startActivity(WorkoutNotifications.preciseTimerSettings(context)) }.isFailure
                        }) { Text("Precise timer alerts") }
                        if (timerSettingsError) Text("Open Android Settings → Apps → Open Factor → Alarms & reminders.", color = MaterialTheme.colorScheme.error)
                    }
                    listOf(
                        Triple("Use Rest Timers", data.preferences.autoRest, { checked: Boolean -> vm.settings { it.copy(autoRest = checked) } }),
                        Triple("Rest After Last Warm-up Set", data.preferences.restAfterLastWarmupSet, { checked: Boolean -> vm.settings { it.copy(restAfterLastWarmupSet = checked) } }),
                        Triple("Rest Between Exercises", data.preferences.restBetweenExercises, { checked: Boolean -> vm.settings { it.copy(restBetweenExercises = checked) } }),
                        Triple("Advance within supersets", data.preferences.supersetAutoNext, { checked: Boolean -> vm.settings { it.copy(supersetAutoNext = checked) } }),
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
                    Text("Rest between sides · ${data.preferences.restBetweenSidesPercent}%")
                    Slider(
                        value = data.preferences.restBetweenSidesPercent.toFloat(),
                        onValueChange = { value -> vm.settings { it.copy(restBetweenSidesPercent = value.toInt()) } },
                        valueRange = 0f..100f,
                        modifier = Modifier.semantics { contentDescription = "Rest between sides percentage" },
                    )
                    Text("0% skips side rest; 100% uses the full timer. Myo uses 15 seconds between components.", style = MaterialTheme.typography.bodySmall, color = OF.muted)
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
                    Text("Save ${workout.completedSets()} completed sets to history?")
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
private fun SupersetDialog(workout: Workout, currentId: String, catalog: List<Exercise>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(setOf(currentId)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create superset") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Choose at least two exercises. Existing group memberships for these exercises will be replaced.")
            workout.exercises.forEach { exercise ->
                Row(Modifier.fillMaxWidth().toggleable(exercise.id in selected, role = Role.Checkbox) {
                    selected = if (it) selected + exercise.id else selected - exercise.id
                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(exercise.id in selected, null)
                    Text(catalog.find { it.id == exercise.exerciseId }?.title ?: "Exercise", Modifier.weight(1f))
                }
            }
        } },
        confirmButton = { TextButton({ onSave(selected) }, enabled = selected.size >= 2) { Text("Create") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
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
    val sets = remember { exercise.logicalSets().map { it.root }.filterNot { it.warmup } }
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
    onKind: (SetKind, Boolean) -> Unit,
    hasExtras: Boolean,
    showTargets: Boolean,
    displayNumber: Int,
) {
    val focus = LocalFocusManager.current
    var remove by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<Pair<SetKind, Boolean>?>(null) }
    var rir by remember(set.id, set.rir) { mutableStateOf(set.rir?.toString() ?: "") }
    val rirValue = rir.toIntOrNull()
    val rirError = rir.isNotEmpty() && (rirValue == null || rirValue !in 0..10)
    val showRir = prefs.showRir && metrics.any { it.isReps }
    val spokenKind = if (set.warmup) "warmup set" else "set"
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
                    contentDescription = "${spokenKind.replaceFirstChar { it.uppercase() }} $displayNumber options for $exerciseName"
                },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(set.side?.label?.take(1) ?: if (set.warmup) "W" else "$displayNumber", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(options, { options = false }) {
                SetKind.entries.forEach { kind -> DropdownMenuItem(
                    text = { Text(kind.label) },
                    trailingIcon = if (set.kind == kind && !set.warmup) { { Icon(Icons.Rounded.Check, null) } } else null,
                    onClick = {
                        options = false
                        if (hasExtras && kind != SetKind.MYO) pendingKind = kind to false else onKind(kind, false)
                    },
                ) }
                DropdownMenuItem(
                    text = { Text(if (set.warmup) "Mark as Working Set" else "Mark as Warm-Up") },
                    onClick = {
                        options = false
                        if (hasExtras) pendingKind = SetKind.STANDARD to !set.warmup else onKind(SetKind.STANDARD, !set.warmup)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (set.segmentOf != null) "Remove Myo component" else "Remove Set") },
                    onClick = {
                        options = false
                        remove = true
                    },
                )
            }
        }
        if (showTargets) Text(
            set.targetDescription().takeUnless { it == "No Target" } ?: "—", Modifier.width(48.dp),
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
                        contentDescription = "$exerciseName $spokenKind $displayNumber ${metric.catalogName.lowercase()} (${metric.label(prefs.pounds, prefs.distanceImperial)})"
                        if (invalid) error(if (metric.isReps) "Enter a whole number from 1 to 9999" else "Enter a valid ${metric.label(prefs.pounds, prefs.distanceImperial)} value")
                    },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(if (metric.isReps) "Use 1–9999" else "Invalid value") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric.isReps) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
                    shape = RoundedCornerShape(10.dp),
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
                    contentDescription = "$exerciseName $spokenKind $displayNumber reps in reserve"
                    if (rirError) error("Enter RIR from 0 to 10, or leave empty")
                },
                supportingText =
                    if (rirError) {
                        { Text("Enter 0–10") }
                    } else null,
                singleLine = true,
                isError = rirError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(10.dp),
            )
        IconButton(
            onComplete,
            Modifier.size(48.dp)
                .background(if (set.completed) OF.green else OF.elevated, RoundedCornerShape(8.dp)),
            enabled = set.completed || set.canComplete(metrics) && (!showRir || !rirError),
        ) {
            Icon(
                Icons.Rounded.Check,
                if (set.completed) "Mark $spokenKind incomplete $displayNumber for $exerciseName"
                else "Complete $spokenKind $displayNumber for $exerciseName",
                tint = if (set.completed) Color.Black else if (set.canComplete(metrics)) MaterialTheme.colorScheme.onSurface else OF.muted,
            )
        }
    }
    if (remove) ConfirmDialog(
        "Remove set?", "This removes the set and its logged values.", "Remove",
        { remove = false }, { onRemove(); remove = false },
    )
    pendingKind?.let { (kind, warmup) -> ConfirmDialog(
        "Remove Myo components?", "Changing this set type removes its additional component values on both sides. The main set is kept.",
        "Change set type", { pendingKind = null }, { onKind(kind, warmup); pendingKind = null },
    ) }
}
