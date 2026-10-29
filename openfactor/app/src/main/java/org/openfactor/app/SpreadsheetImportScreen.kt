package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SpreadsheetImportScreen(vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryReady by vm.libraryReady.collectAsState()
    var preview by remember { mutableStateOf<SpreadsheetPreview?>(null) }
    var matcher by remember { mutableStateOf<SpreadsheetExerciseMatcher?>(null) }
    var choices by remember { mutableStateOf(emptyMap<String, String>()) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    var sets by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf<String?>(null) }
    var reviewedWarnings by remember { mutableStateOf(false) }
    val data by vm.data.collectAsState()
    val catalog = remember(vm, data.customExercises, libraryReady) { vm.exercises(data) }
    val byId = remember(catalog) { catalog.associateBy { it.id } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            error = null
            preview = null
            reviewedWarnings = false
            val snapshot = vm.exercises()
            scope.launch {
                try {
                    val (parsed, matching) = withContext(Dispatchers.IO) {
                        val parsed = context.contentResolver.openInputStream(uri)?.use(SpreadsheetImport::read)
                            ?: throw IllegalArgumentException("Could not read that document. Choose another local .xlsx file.")
                        parsed to SpreadsheetExerciseMatcher(snapshot)
                    }
                    preview = parsed
                    matcher = matching
                    choices = parsed.exerciseNames.mapNotNull { name -> matching.automatic(name)?.let { name to it } }.toMap()
                    skipped = emptySet()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Import could not be previewed: ${failure.message ?: "invalid workbook"}"
                } finally {
                    busy = false
                }
            }
        }
    }
    val parsed = preview
    val count = sets.toIntOrNull()?.takeIf { it in 1..30 }
    val unresolved = parsed?.exerciseNames?.count { it !in choices && it !in skipped } ?: 0
    SpreadsheetImportLayout(content = {
        item {
            Text("Import local .xlsx workout targets into new manual programs. Existing templates, programs and history are never replaced. Nothing is saved until you confirm.", color = OF.muted)
        }
        if (parsed == null) {
            item {
                Text("Supports Phase 1 / Phase 2 / Phase 3 sheets with DAY sections and EXERCISE, WEEK, REPS and RIR columns. Other spreadsheet layouts are not supported. For Fitbod strength-set CSV history, use Settings → Import Fitbod CSV.")
            }
        } else {
            item {
                Text("${parsed.phases.size} new programs · ${parsed.phases.sumOf { it.days.size }} workout days", style = MaterialTheme.typography.titleMedium)
                Text("${parsed.exerciseNames.size} distinct source names · $unresolved need a choice", color = OF.muted)
                Text("Programs stay inactive after import. Open Workout → Programs to review or start a day.", color = OF.muted)
            }
            item {
                OutlinedTextField(sets, { sets = it; reviewedWarnings = false }, enabled = !busy,
                    label = { Text("Working sets per exercise (1–30)") },
                    supportingText = { Text("Not supplied by this weekly-row format. Choose explicitly, then edit individual targets in Programs.") },
                    isError = sets.isNotEmpty() && count == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item { Text("Import warnings", style = MaterialTheme.typography.titleMedium) }
            items(parsed.warnings) { warning -> Text(warning, color = OF.muted) }
            item { Text("Exercise matching", style = MaterialTheme.typography.titleMedium) }
            items(parsed.exerciseNames, key = { "match:$it" }) { name ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name)
                    Text(when {
                        name in skipped -> "Skipped in all phases"
                        choices[name] != null -> "→ ${byId[choices[name]]?.name ?: "Unavailable exercise"}"
                        else -> "No unique exact name or alias match. Choose an exercise or explicitly skip."
                    }, color = if (name !in choices && name !in skipped) MaterialTheme.colorScheme.error else OF.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ resolving = name }, enabled = !busy) { Text("Choose exercise") }
                        TextButton({
                            skipped = if (name in skipped) skipped - name else skipped + name
                            reviewedWarnings = false
                        }, enabled = !busy) { Text(if (name in skipped) "Include again" else "Skip exercise") }
                    }
                }
            }
            item { Text("Source targets", style = MaterialTheme.typography.titleMedium) }
            parsed.phases.forEach { phase ->
                item {
                    Text("${phase.name} · ${phase.weekCount} weeks", style = MaterialTheme.typography.titleMedium)
                    Text("Only listed workout days are imported; no rest-day schedule is guessed.", color = OF.muted)
                }
                phase.days.forEach { day ->
                    item { Text(day.name, style = MaterialTheme.typography.titleSmall) }
                    items(day.exercises) { exercise ->
                        Column {
                            Text(exercise.name + if (exercise.name in skipped) " · skipped" else "")
                            Text(exercise.restSeconds?.let { "Rest: $it seconds" } ?: "Rest: unspecified (no rest target imported)", color = OF.muted)
                            exercise.weeks.forEach { week ->
                                Text("Week ${week.week}: ${week.reps.ifBlank { "no rep target" }} reps · RIR ${week.rir.ifBlank { "unspecified" }}", color = OF.muted)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().toggleable(reviewedWarnings, enabled = !busy,
                    role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { reviewedWarnings = it })) {
                    Checkbox(reviewedWarnings, null, enabled = !busy)
                    Text("I reviewed matching, skipped exercises, set count and warnings, including targets that cannot be imported.", Modifier.padding(top = 12.dp))
                }
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }, actions = {
        if (parsed == null) {
            Button({ picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                enabled = libraryReady && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "Reading workbook…" else if (!libraryReady) "Waiting for exercise library…" else "Choose .xlsx document")
            }
        } else {
            Button({
                if (busy) return@Button
                val setCount = count ?: return@Button
                busy = true
                error = null
                val selected = choices.toMap()
                val excluded = skipped.toSet()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val programs = parsed.materializePrograms(selected, excluded, setCount, vm.exercises())
                            vm.appendSpreadsheetPrograms(programs)
                        }
                        preview = null
                        matcher = null
                        choices = emptyMap()
                        skipped = emptySet()
                        reviewedWarnings = false
                        scope.launch { snackbar.showSnackbar("${parsed.phases.size} new programs imported · existing data unchanged") }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = "Nothing imported: ${failure.message ?: "could not save programs"}"
                    } finally { busy = false }
                }
            }, enabled = !busy && count != null && unresolved == 0 && reviewedWarnings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Saving…" else "Append ${parsed.phases.size} new programs") }
            OutlinedButton({ preview = null; matcher = null; choices = emptyMap(); skipped = emptySet(); error = null },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel import") }
        }
    })
    resolving?.let { name ->
        SpreadsheetMatchDialog(name, matcher, onDismiss = { resolving = null }) { exercise ->
            choices = choices + (name to exercise.id)
            skipped = skipped - name
            reviewedWarnings = false
            resolving = null
        }
    }
}

@Composable
internal fun SpreadsheetImportLayout(
    navigationBars: WindowInsets = WindowInsets.navigationBars,
    ime: WindowInsets = WindowInsets.ime,
    content: LazyListScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    // The keyboard covers the floating navigation; reserve the larger obstruction, not both.
    val bottomInsets = navigationBars.add(WindowInsets(bottom = 108.dp)).union(ime)
    Column(Modifier.fillMaxSize().windowInsetsPadding(bottomInsets)) {
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = actions,
        )
    }
}

@Composable
internal fun SpreadsheetMatchDialog(
    name: String,
    matcher: ImportExerciseSuggestions?,
    onDismiss: () -> Unit,
    onChoose: (Exercise) -> Unit,
) {
    var query by remember(name) { mutableStateOf(name) }
    var results by remember { mutableStateOf(emptyList<Exercise>()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query, matcher) {
        searching = true
        results = withContext(Dispatchers.Default) { matcher?.suggestions(query).orEmpty() }
        searching = false
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Match $name") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it.take(160) }, label = { Text("Search catalog") }, singleLine = true)
            Text("Suggestions are not automatic matches. Select only the correct movement and equipment.", color = OF.muted)
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (results.isEmpty()) Text("No results. Try another name, create a custom exercise first, or skip this exercise in the preview.")
            else results.forEach { exercise ->
                TextButton({ onChoose(exercise) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${exercise.name} · ${exercise.equipment}", modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Cancel") } })
}
