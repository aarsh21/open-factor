package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FitbodImportScreen(vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryReady by vm.libraryReady.collectAsState()
    val data by vm.data.collectAsState()
    val catalog = remember(vm, data.customExercises, libraryReady) { vm.exercises(data) }
    val byId = remember(catalog) { catalog.associateBy { it.id } }
    var preview by remember { mutableStateOf<FitbodPreview?>(null) }
    var matcher by remember { mutableStateOf<SpreadsheetExerciseMatcher?>(null) }
    var choices by remember { mutableStateOf(emptyMap<String, String>()) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf<String?>(null) }
    var reviewed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            error = null
            preview = null
            reviewed = false
            val snapshot = vm.exercises()
            scope.launch {
                try {
                    val (parsed, matching) = withContext(Dispatchers.IO) {
                        val parsed = context.contentResolver.openInputStream(uri)?.use(FitbodImport::read)
                            ?: throw IllegalArgumentException("Could not read that document. Choose another local CSV file.")
                        parsed to SpreadsheetExerciseMatcher(snapshot)
                    }
                    preview = parsed
                    matcher = matching
                    choices = parsed.exerciseNames.mapNotNull { name -> matching.automatic(name)?.let { name to it } }.toMap()
                    skipped = emptySet()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Import could not be previewed: ${failure.message ?: "invalid CSV"}"
                } finally { busy = false }
            }
        }
    }
    val parsed = remember(preview, data.history) { preview?.pending(data.history) }
    val names = remember(parsed) { parsed?.exerciseNames.orEmpty() }
    val unresolved = names.count { it !in choices && it !in skipped }
    val included = remember(parsed, skipped) {
        parsed?.workouts.orEmpty().filter { w -> w.exercises.any { it.name !in skipped } }
    }
    SpreadsheetImportLayout(content = {
        item { Text("Import a local Fitbod strength-sets CSV into completed workout history. Nothing is saved until you review and confirm.", color = OF.muted) }
        if (parsed == null) {
            item { Text("Supports the 18-column strength-sets format with date_local, date_utc, workout_id, exercise_name, reps, weight_kg and set_id. Other CSV layouts, duration and distance sets are not supported.") }
        } else {
            item {
                Text("${parsed.workouts.size} new workouts · ${parsed.setCount} source sets", style = MaterialTheme.typography.titleMedium)
                Text("${preview!!.workouts.size - parsed.workouts.size} previously imported workouts will be skipped · $unresolved exercise names need a choice", color = OF.muted)
                Text("${included.size} workouts will be saved after exercise skips. Empty workouts are omitted. Imported sets contribute to history and training statistics.", color = OF.muted)
            }
            item { Text("Import warnings", style = MaterialTheme.typography.titleMedium) }
            items(parsed.warnings) { Text(it, color = OF.muted) }
            item { Text("Exercise matching", style = MaterialTheme.typography.titleMedium) }
            items(names, key = { "match:$it" }) { name ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name)
                    Text(when {
                        name in skipped -> "Skipped in all new workouts"
                        choices[name] != null -> "→ ${byId[choices[name]]?.name ?: "Unavailable exercise"}"
                        else -> "No unique exact name or alias match. Choose an exercise or explicitly skip."
                    }, color = if (name !in choices && name !in skipped) MaterialTheme.colorScheme.error else OF.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ resolving = name }, enabled = !busy) { Text("Choose exercise") }
                        TextButton({
                            skipped = if (name in skipped) skipped - name else skipped + name
                            reviewed = false
                        }, enabled = !busy) { Text(if (name in skipped) "Include again" else "Skip exercise") }
                    }
                }
            }
            item { Text("Source workouts", style = MaterialTheme.typography.titleMedium) }
            parsed.workouts.forEach { workout ->
                item(key = workout.id) { FitbodWorkoutHeading(workout) }
                items(workout.exercises, key = { "${workout.id}:${it.position}" }) { exercise ->
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Text(exercise.name + if (exercise.name in skipped) " · skipped" else "")
                        Text("${exercise.sets.size} completed sets · source position ${exercise.position}", color = OF.muted)
                        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide source sets" else "Review source sets") }
                        if (expanded) exercise.sets.forEach { set ->
                            Text("Set ${set.number}: ${set.weightKg} kg × ${set.reps}" +
                                (if (set.warmup) " · warm-up" else "") + (if (set.amrap) " · AMRAP" else "") +
                                " · rest ${set.restSeconds?.let { "$it s" } ?: "unspecified"}", color = OF.muted)
                            if (set.notes.isNotEmpty()) Text(set.notes, color = OF.muted)
                        }
                    }
                }
            }
            if (parsed.workouts.isEmpty()) item { Text("All workouts in this file are already in history. Nothing to import.") }
            else item {
                Row(Modifier.fillMaxWidth().toggleable(reviewed, enabled = !busy, role = Role.Checkbox,
                    onValueChange = { reviewed = it })) {
                    Checkbox(reviewed, null, enabled = !busy)
                    Text("I reviewed exercise matches, skipped exercises and workouts, source sets and import limitations.", Modifier.padding(top = 12.dp))
                }
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }, actions = {
        if (parsed == null) {
            Button({ picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel", "application/octet-stream")) },
                enabled = libraryReady && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Reading CSV…" else if (!libraryReady) "Waiting for exercise library…" else "Choose Fitbod CSV")
            }
        } else {
            Button({
                if (busy) return@Button
                busy = true
                error = null
                val selected = choices.toMap()
                val excluded = skipped.toSet()
                scope.launch {
                    try {
                        val imported = withContext(Dispatchers.IO) { vm.appendFitbodHistory(parsed, selected, excluded) }
                        preview = null
                        matcher = null
                        choices = emptyMap()
                        skipped = emptySet()
                        reviewed = false
                        scope.launch { snackbar.showSnackbar("$imported Fitbod workouts imported · existing data unchanged") }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = "Nothing imported: ${failure.message ?: "could not save history"}"
                    } finally { busy = false }
                }
            }, enabled = !busy && unresolved == 0 && included.isNotEmpty() && reviewed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Saving…" else "Append ${included.size} workouts to history")
            }
            OutlinedButton({ preview = null; matcher = null; choices = emptyMap(); skipped = emptySet(); error = null },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel import") }
        }
    })
    resolving?.let { name ->
        SpreadsheetMatchDialog(name, matcher, onDismiss = { resolving = null }) { exercise ->
            choices = choices + (name to exercise.id)
            skipped = skipped - name
            reviewed = false
            resolving = null
        }
    }
}

@Composable
private fun FitbodWorkoutHeading(workout: FitbodWorkout) {
    Text(workout.name.ifBlank { "Fitbod workout" }, style = MaterialTheme.typography.titleSmall)
    Text("${workout.date} · ${workout.exercises.size} exercises", color = OF.muted)
}
