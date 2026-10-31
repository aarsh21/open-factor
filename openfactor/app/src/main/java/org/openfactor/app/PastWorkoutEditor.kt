package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWorkoutEditor(
    edit: PastWorkoutEdit, catalog: List<Exercise>, data: AppData, vm: WorkoutViewModel,
    onChange: ((Workout) -> Workout) -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> String?,
    onSaved: () -> Unit,
) {
    val workout = edit.edited
    val prefs = data.preferences
    var discard by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var invalidInputs by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var date by rememberSaveable(workout.id) { mutableStateOf(workout.localDate().toString()) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val setIds = workout.exercises.flatMap { it.sets }.mapTo(HashSet()) { it.id }
    val error = workout.pastEditError(catalog) ?: when {
        parsedDate == null -> "Enter a valid date."
        invalidInputs.any { it.key.substringBefore(':') in setIds && it.value } -> "Check the highlighted set values."
        else -> null
    }
    fun leave() { if (edit.edited != edit.original) discard = true else onDiscard() }
    BackHandler { if (guide != null) guide = null else if (picker) picker = false else leave() }
    fun change(block: (Workout) -> Workout) { saveError = null; onChange(block) }
    fun entry(id: String, block: (SessionExercise) -> SessionExercise) = change { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) block(it) else it })
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading("Edit Workout", ::leave, actions = {
            TextButton({ saveError = onSave(); if (saveError == null) onSaved() }, enabled = error == null) { Text("Save") }
        })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(workout.name, { name -> change { it.copy(name = name) } }, Modifier.fillMaxWidth(),
                        label = { Text("Workout name") }, singleLine = true, isError = workout.name.isBlank())
                    BodyDateField(date) { value ->
                        date = value
                        runCatching { LocalDate.parse(value) }.getOrNull()?.let { valid -> change { it.onDate(valid) } }
                    }
                }
            }
            items(workout.exercises, key = { it.id }) { session ->
                val exercise = catalog.find { it.id == session.exerciseId }
                val title = exercise?.title ?: "Exercise"
                val metrics = exercise?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS)
                var options by remember { mutableStateOf(false) }
                var remove by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxWidth(), color = OF.card, shape = OF.shape) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Box {
                                IconButton({ options = true }) { Icon(Icons.Rounded.MoreVert, "Edit options for $title") }
                                DropdownMenu(options, { options = false }) {
                                    DropdownMenuItem(text = { Text("Remove exercise") }, onClick = { options = false; remove = true })
                                }
                            }
                        }
                        session.logicalSets().forEachIndexed { index, group ->
                            group.components.forEachIndexed { component, logs -> logs.forEach { log ->
                                val number = session.logicalSets().take(index + 1).count { it.root.warmup == group.root.warmup }
                                val label = listOfNotNull(if (log.warmup) "Warm-up $number" else "Set $number", log.side?.label,
                                    if (component > 0) "Myo $component" else if (log.kind != SetKind.STANDARD) log.kind.label else null).joinToString(" · ")
                                PastSetEditor(title, label, log, metrics, prefs, hasExtras = group.components.size > 1,
                                    onInvalid = { key, invalid -> invalidInputs = invalidInputs + (key to invalid) },
                                    onChange = { next -> entry(session.id) { it.copy(sets = it.sets.map { s -> if (s.id == log.id) next else s }) } },
                                    onRemove = { entry(session.id) { it.withoutSet(log.id) } },
                                    onWarmup = {
                                        if (!log.warmup) {
                                            // Effort inputs disappear for the whole logical warmup, including both sides.
                                            invalidInputs = invalidInputs - group.logs.map { "${it.id}:RIR" }.toSet()
                                        }
                                        entry(session.id) { it.withSetKind(log.id, log.kind, !log.warmup) }
                                    })
                            } }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ entry(session.id) { it.withNewSet(warmup = true) } }, Modifier.weight(1f)) { Text("Warmup") }
                            FilledTonalButton({ entry(session.id) { it.withNewSet() } }, Modifier.weight(1f)) { Text("Add set") }
                        }
                        if (session.notes.isNotBlank()) Text(session.notes, color = OF.muted)
                    }
                }
                if (remove) ConfirmDialog("Remove exercise?", "This removes its sets from this edit. The saved workout changes only after Save.", "Remove", { remove = false }) {
                    change { it.copy(exercises = it.exercises.filterNot { e -> e.id == session.id }).normalizeSupersets() }
                    remove = false
                }
            }
            item { OutlinedButton({ picker = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
            } }
            item { OutlinedTextField(workout.notes, { notes -> change { it.copy(notes = notes) } }, Modifier.fillMaxWidth(), label = { Text("Workout notes") }) }
            if (saveError != null || error != null) item { Text(saveError ?: error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (discard) ConfirmDialog("Discard changes?", "Your saved workout will stay unchanged.", "Discard", { discard = false }, onDiscard)
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OF.background) {
        Box(Modifier.fillMaxHeight(.94f)) {
            val pick: (String) -> Unit = { id ->
                change { it.copy(exercises = it.exercises + SessionExercise(exerciseId = id)) }
                picker = false
                guide = null
            }
            val exercise = catalog.find { it.id == guide }
            if (exercise != null) ExerciseDetail(exercise, data, vm, { guide = null }, { pick(exercise.id) }, closeable = true)
            else ExerciseLibrary(catalog, data.favorites, vm::favorite, onDetail = { guide = it }, onPick = pick,
                onBack = { picker = false }, onCustom = {}, picking = true, allowCustom = false)
        }
    }
}

@Composable
private fun PastSetEditor(
    exercise: String, label: String, set: LoggedSet, metrics: List<SetMetric>, prefs: Preferences, hasExtras: Boolean,
    onInvalid: (String, Boolean) -> Unit,
    onChange: (LoggedSet) -> Unit, onRemove: () -> Unit, onWarmup: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmWarmup by remember(set.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = OF.muted)
            Checkbox(set.completed, { checked -> onChange(set.copy(completed = checked)) },
                Modifier.semantics { contentDescription = "$exercise $label completed" })
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Options for $exercise $label") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(if (set.warmup) "Mark as working set" else "Mark as warmup") }, onClick = {
                        menu = false
                        if (hasExtras) confirmWarmup = true else onWarmup()
                    })
                    DropdownMenuItem(text = { Text(if (set.segmentOf != null) "Remove Myo component" else "Remove set group") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEach { metric -> key(set.id, metric) {
                var text by rememberSaveable(set.id, metric, prefs.pounds, prefs.distanceImperial) { mutableStateOf(metric.display(set, prefs.pounds, prefs.distanceImperial)) }
                val invalid = (text.isNotBlank() || set.completed) && metric.parse(text, prefs.pounds, prefs.distanceImperial) == null
                SideEffect { onInvalid("${set.id}:${metric.name}", invalid) }
                OutlinedTextField(text, { value ->
                    text = value
                    val parsed = metric.parse(value, prefs.pounds, prefs.distanceImperial)
                    if (value.isBlank() || parsed != null) onChange(metric.update(set, parsed))
                },
                    Modifier.weight(1f).semantics { contentDescription = "Edit $exercise $label ${metric.catalogName}" },
                    label = { Text(metric.label(prefs.pounds, prefs.distanceImperial)) }, singleLine = true, isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric.isReps) KeyboardType.Number else KeyboardType.Decimal))
            } }
        }
        if (!set.warmup && metrics.any { it.isReps }) {
            var effort by rememberSaveable(set.id, set.rir) { mutableStateOf(set.rir?.toString().orEmpty()) }
            val invalid = effort.isNotBlank() && effort.toIntOrNull()?.let { it in 0..10 } != true
            SideEffect { onInvalid("${set.id}:RIR", invalid) }
            OutlinedTextField(effort, { value ->
                effort = value
                val parsed = value.toIntOrNull()?.takeIf { it in 0..10 }
                if (value.isBlank() || parsed != null) onChange(set.copy(rir = parsed))
            },
                Modifier.fillMaxWidth().semantics { contentDescription = "Edit $exercise $label RIR" }, label = { Text("Reps left (optional)") }, singleLine = true,
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = OF.line)
    }
    if (confirmWarmup) ConfirmDialog(
        "Remove Myo components?", "Marking this set as a warmup removes its additional component values on both sides. The main set is kept. Saved history changes only after Save.",
        "Change set type", { confirmWarmup = false }, { confirmWarmup = false; onWarmup() },
    )
}
