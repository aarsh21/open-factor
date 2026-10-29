package org.openfactor.app

import android.os.CancellationSignal
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
internal fun FitbodImportScreen(vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryReady by vm.libraryReady.collectAsState()
    val data by vm.data.collectAsState()
    val catalog = remember(vm, data.customExercises, libraryReady) { vm.exercises(data) }
    var preview by remember { mutableStateOf<FitbodPreview?>(null) }
    var matcher by remember { mutableStateOf<FitbodExerciseMatcher?>(null) }
    var plan by remember { mutableStateOf<FitbodMatchPlan?>(null) }
    var newExercises by remember { mutableStateOf(emptyList<Exercise>()) }
    var choices by remember { mutableStateOf(emptyMap<String, String>()) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var readJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf<String?>(null) }
    var reviewed by remember { mutableStateOf(false) }
    var showMatches by remember { mutableStateOf(false) }
    var visibleWorkouts by remember { mutableIntStateOf(5) }
    var expandedWorkout by remember { mutableStateOf<String?>(null) }
    val byId = remember(catalog, newExercises) { (catalog + newExercises).associateBy { it.id } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val request = ++generation
            readJob?.cancel()
            busy = true
            progress = "Reading Fitbod CSV…"
            error = null
            preview = null
            reviewed = false
            val snapshot = vm.exercises()
            readJob = scope.launch {
                try {
                    val signal = CancellationSignal()
                    val parsed = readFitbodInput(cancelOpen = signal::cancel) {
                        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r", signal)
                            ?: throw IllegalArgumentException("Could not open that document. Choose a local CSV file.")
                        try { descriptor.createInputStream() }
                        catch (failure: Exception) { descriptor.close(); throw failure }
                    }
                    progress = "Matching ${parsed.exerciseNames.size} exercise names…"
                    val (matching, matches) = withTimeout(30_000) {
                        runInterruptible(Dispatchers.Default) {
                            val matching = FitbodExerciseMatcher(snapshot)
                            matching to matching.plan(parsed)
                        }
                    }
                    if (request != generation) return@launch
                    preview = parsed
                    matcher = matching
                    plan = matches
                    newExercises = matches.newExercises
                    choices = matches.choices
                    skipped = emptySet()
                    showMatches = false
                    visibleWorkouts = 5
                    expandedWorkout = null
                } catch (_: TimeoutCancellationException) {
                    if (request == generation) error = "Import preview timed out. Nothing was saved. Copy the CSV to Downloads and try again, or choose a smaller export."
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (request == generation) error = "Import could not be previewed: ${failure.message ?: "invalid CSV"}"
                } finally { if (request == generation) busy = false }
            }
        }
    }
    val parsed = remember(preview, data.history) { preview?.pending(data.history) }
    val names = remember(parsed) { parsed?.exerciseNames.orEmpty() }
    val unresolved = names.count { it !in choices && it !in skipped }
    val included = remember(parsed, skipped) { parsed?.workouts.orEmpty().filter { w -> w.exercises.any { it.name !in skipped } } }
    val includedIds = remember(names, choices, skipped) { names.filterNot { it in skipped }.mapNotNull { choices[it] }.toSet() }
    val customCount = newExercises.count { it.id in includedIds }
    val setCount = remember(included, skipped) { included.sumOf { w -> w.exercises.filterNot { it.name in skipped }.sumOf { it.sets.size } } }
    SpreadsheetImportLayout(content = {
        item { Text("Import completed Fitbod strength history. Close compatible names are matched automatically; uncertain names are kept as custom exercises so no sets are lost. Nothing is saved until you confirm.", color = OF.muted) }
        if (parsed == null) {
            item { Text("Choose the 18-column strength-sets CSV. Duration/distance sets and other CSV layouts are not supported.") }
        } else {
            item {
                Text("${workoutCount(included.size)} · $setCount sets to import", style = MaterialTheme.typography.titleMedium)
                Text("${preview!!.workouts.size - parsed.workouts.size} previously imported workouts skipped · $customCount new custom exercises", color = OF.muted)
                Text("Existing history and active workouts stay unchanged. Empty workouts after explicit exercise skips are omitted.", color = OF.muted)
            }
            item {
                Text("Exercise matching", style = MaterialTheme.typography.titleMedium)
                Text("${names.size - unresolved} of ${names.size} source names resolved. $customCount keep their original Fitbod name as a custom exercise, with no invented muscle targets. You can change any match before saving.", color = OF.muted)
                TextButton({ showMatches = !showMatches }, enabled = !busy) { Text(if (showMatches) "Hide exercise matches" else "Review exercise matches") }
            }
            if (showMatches) items(names, key = { "match:$it" }) { name ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name)
                    val selected = choices[name]
                    Text(when {
                        name in skipped -> "Skipped in all new workouts"
                        selected == null -> "Choose an exercise or keep the original name."
                        newExercises.any { it.id == selected } -> "→ Keep as custom: ${byId[selected]?.name ?: name}"
                        plan?.matches?.get(name)?.let { it.exercise.id == selected && it.kind == FitbodMatchKind.CLOSEST } == true -> "→ Closest compatible match: ${byId[selected]?.name}"
                        else -> "→ ${byId[selected]?.name ?: "Unavailable exercise"}"
                    }, color = if (selected == null && name !in skipped) MaterialTheme.colorScheme.error else OF.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ resolving = name }, enabled = !busy) { Text("Change match") }
                        TextButton({
                            val preserved = newExercises.firstOrNull { it.properties["Fitbod source name"] == name }
                                ?: FitbodExerciseMatcher.preserve(name).also { newExercises = newExercises + it }
                            choices = choices + (name to preserved.id)
                            skipped = skipped - name
                            reviewed = false
                        }, enabled = !busy) { Text("Keep original") }
                    }
                    TextButton({
                        skipped = if (name in skipped) skipped - name else skipped + name
                        reviewed = false
                    }, enabled = !busy) { Text(if (name in skipped) "Include again" else "Skip exercise") }
                }
            }
            item { Text("Import limits", style = MaterialTheme.typography.titleMedium) }
            items(parsed.warnings) { Text(it, color = OF.muted) }
            item { Text("Source workouts", style = MaterialTheme.typography.titleMedium) }
            parsed.workouts.take(visibleWorkouts).forEach { workout ->
                item(key = workout.id) {
                    Text(workout.name.ifBlank { "Fitbod workout" }, style = MaterialTheme.typography.titleSmall)
                    Text("${workout.date} · ${workout.exercises.sumOf { it.sets.size }} source sets", color = OF.muted)
                    TextButton({ expandedWorkout = workout.id.takeIf { expandedWorkout != it } }) {
                        Text(if (expandedWorkout == workout.id) "Hide source workout" else "Review source workout")
                    }
                }
                if (expandedWorkout == workout.id) items(workout.exercises, key = { "${workout.id}:${it.position}" }) { exercise ->
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Text(exercise.name + if (exercise.name in skipped) " · skipped" else "")
                        Text("${exercise.sets.size} completed sets", color = OF.muted)
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
            if (parsed.workouts.size > visibleWorkouts) item {
                TextButton({ visibleWorkouts += 10 }, enabled = !busy) { Text("Show more source workouts (${parsed.workouts.size - visibleWorkouts} remaining)") }
            }
            if (parsed.workouts.isEmpty()) item { Text("All workouts in this file are already in history. Nothing to import.") }
        }
        if (busy) item {
            Text(progress, color = OF.muted)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, actions = {
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (parsed == null) {
            Button({ picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel", "application/octet-stream")) },
                enabled = libraryReady && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Preparing import…" else if (!libraryReady) "Waiting for exercise library…" else "Choose Fitbod CSV")
            }
            if (busy) OutlinedButton({
                generation++
                readJob?.cancel()
                busy = false
                error = "Import cancelled. Nothing was saved."
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel reading") }
        } else {
            if (included.isNotEmpty()) FitbodReviewAcknowledgement(reviewed, !busy) { reviewed = it }
            Button({
                if (busy) return@Button
                busy = true
                progress = "Saving completed workouts…"
                error = null
                val selected = choices.toMap()
                val excluded = skipped.toSet()
                val preserved = newExercises.toList()
                scope.launch {
                    try {
                        val imported = withContext(Dispatchers.IO) { vm.appendFitbodHistory(parsed, selected, excluded, preserved) }
                        preview = null
                        matcher = null
                        plan = null
                        choices = emptyMap()
                        skipped = emptySet()
                        newExercises = emptyList()
                        reviewed = false
                        scope.launch { snackbar.showSnackbar("${workoutCount(imported)} imported · existing data unchanged") }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = "Nothing imported: ${failure.message ?: "could not save history"}"
                    } finally { busy = false }
                }
            }, enabled = !busy && unresolved == 0 && included.isNotEmpty() && reviewed,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Saving…" else "Import ${workoutCount(included.size)}")
            }
            OutlinedButton({
                preview = null; matcher = null; plan = null; choices = emptyMap(); skipped = emptySet()
                newExercises = emptyList(); error = null; reviewed = false
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel import") }
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
internal fun FitbodReviewAcknowledgement(checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChecked)) {
        Checkbox(checked, null, enabled = enabled)
        Text("I reviewed the exercise matches and limits.", Modifier.weight(1f).padding(top = 12.dp))
    }
}

private fun workoutCount(count: Int) = "$count ${if (count == 1) "workout" else "workouts"}"
