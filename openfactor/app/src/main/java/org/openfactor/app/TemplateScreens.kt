package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Text stays raw until Save; invalid edits survive recreation without changing a saved template. */
private data class TemplateTargetInput(val exerciseId: String, val sets: String, val min: String, val max: String, val rest: String, val rowId: String = newId(), val note: String = "") {
    fun target(hasReps: Boolean): TemplateExercise? {
        val count = sets.toIntOrNull()?.takeIf { it in 1..30 } ?: return null
        val restValue = rest.takeIf(String::isNotBlank)?.toIntOrNull()
        if (rest.isNotBlank() && (restValue == null || restValue !in 0..1800)) return null
        val low = min.takeIf(String::isNotBlank)?.toIntOrNull()
        val high = max.takeIf(String::isNotBlank)?.toIntOrNull()
        if (hasReps && ((min.isNotBlank() && (low == null || low !in 1..9999)) ||
            (max.isNotBlank() && (high == null || high !in 1..9999)) || (low != null && high != null && low > high))) return null
        if (note.trim().length > TEMPLATE_NOTE_LIMIT) return null
        return TemplateExercise(exerciseId, count, low.takeIf { hasReps }, high.takeIf { hasReps }, restValue, note.trim())
    }
    fun json() = JSONObject().put("rowId", rowId).put("id", exerciseId).put("sets", sets).put("min", min).put("max", max).put("rest", rest).put("note", note)
    companion object {
        fun from(target: TemplateExercise) = TemplateTargetInput(target.exerciseId, target.sets.toString(),
            target.repsMin?.toString().orEmpty(), target.repsMax?.toString().orEmpty(), target.restSeconds?.toString().orEmpty(), note = target.note)
        fun decode(j: JSONObject) = TemplateTargetInput(j.getString("id"), j.getString("sets"), j.getString("min"), j.getString("max"), j.getString("rest"), j.getString("rowId"), j.optString("note"))
    }
}
private data class TemplateEditorInput(val id: String, val name: String, val targets: List<TemplateTargetInput>) {
    fun json() = JSONObject().put("id", id).put("name", name).put("targets", JSONArray(targets.map { it.json() })).toString()
    companion object {
        fun from(routine: Routine) = TemplateEditorInput(routine.id, routine.name, routine.effectivePrescriptions().map(TemplateTargetInput::from))
        fun decode(raw: String): TemplateEditorInput = JSONObject(raw).let { j ->
            val list = j.getJSONArray("targets")
            TemplateEditorInput(j.getString("id"), j.getString("name"), (0 until list.length()).map { TemplateTargetInput.decode(list.getJSONObject(it)) })
        }
    }
}

@Composable
fun TemplateEditor(
    initial: Routine?,
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onSave: (Routine) -> String?,
    favorites: Set<String> = emptySet(),
    onFavorite: (String) -> Unit = {},
    gyms: List<GymProfile> = emptyList(),
    equipmentCatalog: EquipmentCatalog? = null,
    preview: (@Composable (Exercise, () -> Unit, () -> Unit) -> Unit)? = null,
) {
    var baseline by rememberSaveable(initial?.id) { mutableStateOf(TemplateEditorInput.from(initial ?: Routine(name = "", exerciseIds = emptyList())).json()) }
    var raw by rememberSaveable(initial?.id) { mutableStateOf(baseline) }
    val draft = remember(raw) { TemplateEditorInput.decode(raw) }
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    var picking by rememberSaveable { mutableStateOf(false) }
    var inspecting by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val pickerState = rememberSaveableStateHolder()
    fun change(value: TemplateEditorInput) { raw = value.json(); error = null }
    fun hasReps(id: String) = byId[id]?.loggingMetrics()?.any { it.isReps } == true
    fun add(id: String) {
        if (draft.targets.none { it.exerciseId == id }) change(draft.copy(targets = draft.targets + TemplateTargetInput.from(
            TemplateExercise(id, repsMin = 8.takeIf { hasReps(id) }, repsMax = 12.takeIf { hasReps(id) }))))
    }
    fun leave() { if (raw != baseline) discard = true else onBack() }
    BackHandler {
        when { inspecting.isNotEmpty() -> inspecting = ""; picking -> picking = false; else -> leave() }
    }
    val inspected = byId[inspecting]
    if (inspected != null && preview != null) {
        key(inspected.id) { preview(inspected, { inspecting = "" }, { add(inspected.id); inspecting = "" }) }
        return
    }
    if (picking) {
        pickerState.SaveableStateProvider("template-picker") {
            ExerciseLibrary(exercises, favorites, onFavorite,
                onDetail = { id -> if (preview != null) inspecting = id else {
                    if (draft.targets.any { it.exerciseId == id }) change(draft.copy(targets = draft.targets.filterNot { it.exerciseId == id })) else add(id)
                } },
                onBack = { picking = false }, onCustom = {}, picking = true, gyms = gyms,
                equipmentCatalog = equipmentCatalog, allowCustom = false,
                selectedIds = draft.targets.mapTo(mutableSetOf()) { it.exerciseId },
                onSelectionChange = { id ->
                    if (draft.targets.any { it.exerciseId == id }) change(draft.copy(targets = draft.targets.filterNot { it.exerciseId == id })) else add(id)
                }, showMuscleShortcuts = true)
        }
        return
    }
    val targets = draft.targets.map { it.target(hasReps(it.exerciseId)) }
    val duplicates = draft.targets.map { it.exerciseId }.distinct().size != draft.targets.size
    val valid = !duplicates && draft.name.trim().isNotBlank() && draft.name.trim().length <= 80 && draft.targets.size in 1..100 && targets.all { it != null } && draft.targets.all { it.exerciseId in byId }
    val muscles = remember(draft.targets.map { it.exerciseId }, exercises) {
        draft.targets.flatMap { byId[it.exerciseId]?.muscles.orEmpty() }.distinct()
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading(if (initial == null) "Create template" else "Edit template", ::leave)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                OutlinedTextField(draft.name, { change(draft.copy(name = it)) }, Modifier.fillMaxWidth(),
                    label = { Text("Template name") }, singleLine = true, isError = draft.name.length > 80,
                    supportingText = if (draft.name.length > 80) ({ Text("Use 80 characters or fewer.") }) else null)
            }
            if (muscles.isNotEmpty()) item { TemplateMuscleOverview(muscles) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.targets.size.let { "$it ${if (it == 1) "exercise" else "exercises"}" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    FilledTonalIconButton({ picking = true }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Add exercises") }
                }
            }
            if (draft.targets.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(40.dp), tint = OF.muted)
                    Text("Choose your first exercise", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton({ picking = true }, Modifier.heightIn(min = 48.dp)) { Text("Add exercises") }
                }
            }
            items(draft.targets, key = { it.rowId }) { input ->
                val exercise = byId[input.exerciseId]
                val title = exercise?.title ?: "Unavailable exercise"
                val index = draft.targets.indexOf(input)
                var menu by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxWidth(), shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (exercise != null) ExerciseImage(exercise, Modifier.size(52.dp))
                            Column(Modifier.weight(1f).then(if (preview != null && exercise != null) Modifier.clickable { inspecting = input.exerciseId } else Modifier)) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Text(templateTargetSummary(input), color = OF.muted, style = MaterialTheme.typography.bodyMedium)
                                if (input.note.isNotBlank() && expanded != input.rowId) Row(Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(TemplateNoteIcon, null, Modifier.size(14.dp), tint = OF.muted)
                                    Text(input.note.trim(), color = OF.muted, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton({ expanded = if (expanded == input.rowId) "" else input.rowId }) {
                                Icon(Icons.Rounded.Tune, "Edit targets for $title")
                            }
                            Box {
                                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Manage $title") }
                                DropdownMenu(menu, { menu = false }) {
                                    listOf(-1 to "Move up", 1 to "Move down").forEach { (delta, label) ->
                                        DropdownMenuItem({ Text(label) }, {
                                            change(draft.copy(targets = draft.targets.toMutableList().apply { java.util.Collections.swap(this, index, index + delta) }))
                                            menu = false
                                        }, enabled = index + delta in draft.targets.indices)
                                    }
                                    DropdownMenuItem({ Text("Remove exercise") }, { change(draft.copy(targets = draft.targets.filterNot { it.rowId == input.rowId })); menu = false })
                                }
                            }
                        }
                        if (expanded == input.rowId) {
                            fun edit(value: TemplateTargetInput) { change(draft.copy(targets = draft.targets.map { if (it.rowId == input.rowId) value else it })) }
                            val invalid = input.target(hasReps(input.exerciseId)) == null
                            val setsInvalid = input.sets.toIntOrNull()?.let { it !in 1..30 } != false
                            val restInvalid = input.rest.isNotBlank() && input.rest.toIntOrNull()?.let { it !in 0..1800 } != false
                            val rangeReversed = input.min.toIntOrNull()?.let { low -> input.max.toIntOrNull()?.let { low > it } } == true
                            val minInvalid = rangeReversed || (input.min.isNotBlank() && input.min.toIntOrNull()?.let { it !in 1..9999 } != false)
                            val maxInvalid = rangeReversed || (input.max.isNotBlank() && input.max.toIntOrNull()?.let { it !in 1..9999 } != false)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TemplateNumberField("Sets", input.sets, { edit(input.copy(sets = it)) }, Modifier.weight(1f), setsInvalid)
                                TemplateNumberField("Rest (seconds)", input.rest, { edit(input.copy(rest = it)) }, Modifier.weight(1f), restInvalid)
                            }
                            if (hasReps(input.exerciseId)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TemplateNumberField("Min reps", input.min, { edit(input.copy(min = it)) }, Modifier.weight(1f), minInvalid)
                                TemplateNumberField("Max reps", input.max, { edit(input.copy(max = it)) }, Modifier.weight(1f), maxInvalid)
                            }
                            val noteTooLong = input.note.trim().length > TEMPLATE_NOTE_LIMIT
                            OutlinedTextField(input.note, { edit(input.copy(note = it)) }, Modifier.fillMaxWidth(),
                                label = { Text("Template note") }, leadingIcon = { Icon(TemplateNoteIcon, null) },
                                placeholder = { Text("Only for this template, e.g. slow tempo") },
                                supportingText = { Text(if (noteTooLong) "Use $TEMPLATE_NOTE_LIMIT characters or fewer."
                                    else "Shows when you start ${draft.name.trim().ifBlank { "this template" }}.") },
                                isError = noteTooLong, maxLines = 5)
                            if (invalid && !noteTooLong) Text(when {
                                setsInvalid -> "Enter 1–30 sets."
                                restInvalid -> "Rest must be 0–1800 seconds."
                                rangeReversed -> "Minimum reps cannot exceed maximum reps."
                                else -> "Enter 1–9999 reps or leave the target blank."
                            }, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { if (draft.targets.isNotEmpty()) OutlinedButton({ picking = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercises") } }
            if (draft.targets.size > 100) item { Text("Use at most 100 exercises in a template.", color = MaterialTheme.colorScheme.error) }
            if (draft.targets.any { it.exerciseId !in byId }) item { Text("An exercise is unavailable. Remove it before saving.", color = MaterialTheme.colorScheme.error) }
            if (duplicates) item { Text("Each exercise can appear once. Remove duplicate entries to save.", color = MaterialTheme.colorScheme.error) }
            if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            PrimaryAction("Save template", {
                val routine = Routine(draft.id, draft.name.trim(), draft.targets.map { it.exerciseId }, targets.filterNotNull())
                error = onSave(routine)
                if (error == null) baseline = raw
            }, enabled = valid)
        }
    }
    if (discard) ConfirmDialog("Discard template changes?", "Your saved templates stay as they are.", "Discard", { discard = false }, { discard = false; onBack() })
}

private fun templateTargetSummary(input: TemplateTargetInput): String = buildString {
    append(input.sets.ifBlank { "—" }); append(if (input.sets == "1") " set" else " sets")
    if (input.min.isNotBlank() || input.max.isNotBlank()) {
        append(" · "); append(input.min.ifBlank { input.max })
        if (input.min.isNotBlank() && input.max.isNotBlank() && input.max != input.min) { append("–"); append(input.max) }
        append(" reps")
    }
}
@Composable private fun TemplateNumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, error: Boolean) {
    OutlinedTextField(value, onChange, modifier, label = { Text(label) }, singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
@Composable internal fun TemplateMuscleOverview(muscles: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Target muscles", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(muscles, key = { it }) { muscle ->
                Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleImage(muscle, Modifier.size(72.dp))
                    Text(muscle, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun TemplateLibraryScreen(
    data: AppData, exercises: List<Exercise>, onBack: () -> Unit, onCreate: () -> Unit,
    onEdit: (Routine) -> Unit, onStart: (Routine) -> String?, onInstall: (String) -> String?, onDelete: (String) -> Unit,
) {
    val starters = remember(exercises) { starterTemplateSplits(exercises) }
    val byId = remember(data.routines) { data.routines.associateBy { it.id } }
    var showStarters by rememberSaveable { mutableStateOf(data.routines.isEmpty()) }
    var seenCount by rememberSaveable { mutableIntStateOf(data.routines.size) }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    var starterId by rememberSaveable { mutableStateOf("") }
    var splitId by rememberSaveable { mutableStateOf("") }
    var pendingName by rememberSaveable { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<Routine?>(null) }
    LaunchedEffect(data.routines.size) {
        if (data.routines.size > seenCount) showStarters = false
        seenCount = data.routines.size
    }
    LaunchedEffect(data.templateSplits, pendingName) {
        if (pendingName.isNotEmpty()) data.templateSplits.lastOrNull { it.name == pendingName }?.let {
            splitId = it.id; starterId = ""; pendingName = ""; installing = false; showStarters = false
        }
    }
    val starter = starters.find { it.id == starterId }
    val split = data.templateSplits.find { it.id == splitId }
    val selectedRoutine = split?.days?.getOrNull(selectedDay)?.let(byId::get)
    LaunchedEffect(starterId, splitId, selectedDay, showStarters) { listState.scrollToItem(0) }
    fun back() { if (starterId.isNotEmpty() || splitId.isNotEmpty()) { starterId = ""; splitId = ""; selectedDay = 0; error = null } else onBack() }
    BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize()) {
        PageHeading(starter?.name ?: split?.name ?: "Templates", ::back,
            actions = { if (starter == null && split == null) IconButton(onCreate) { Icon(Icons.Rounded.Add, "Create template") } })
        if (starter == null && split == null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            listOf(false to "Your templates", true to "Starter splits").forEachIndexed { index, (value, label) ->
                SegmentedButton(showStarters == value, { showStarters = value }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
            }
        }
        if (starter != null || split != null) TemplateWeekStrip(
            starter?.days?.map { it?.name } ?: split!!.days.map { byId[it]?.name },
            selectedDay, { selectedDay = it }, Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (starter != null || split != null) {
                items(listOf(selectedDay), key = { it }) { index ->
                    val suggested = starter?.days?.get(index)
                    val routine = split?.days?.get(index)?.let(byId::get)
                    val name = suggested?.name ?: routine?.name
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Day ${index + 1}", Modifier.width(64.dp), color = OF.muted, style = MaterialTheme.typography.labelLarge)
                            Text(name ?: "Rest", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            if (routine != null) IconButton({ onEdit(routine) }) { Icon(Icons.Rounded.Edit, "Edit ${routine.name}") }
                        }
                        val prescriptions = suggested?.prescriptions ?: routine?.effectivePrescriptions().orEmpty()
                        val muscles = prescriptions.flatMap { target -> exercises.find { it.id == target.exerciseId }?.muscles.orEmpty() }.distinct()
                        if (muscles.isNotEmpty()) TemplateMuscleOverview(muscles)
                        if (prescriptions.isNotEmpty()) Text(
                            prescriptions.size.let { "$it ${if (it == 1) "exercise" else "exercises"}" } + " · " +
                                prescriptions.sumOf { it.sets }.let { "$it ${if (it == 1) "set" else "sets"}" }, color = OF.muted,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                        if (name == null) Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Bedtime, null, Modifier.size(48.dp), tint = OF.muted)
                        }
                        prescriptions.forEach { target ->
                            val exercise = exercises.find { it.id == target.exerciseId }
                            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (exercise != null) ExerciseImage(exercise, Modifier.size(42.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(exercise?.title ?: "Unavailable exercise", style = MaterialTheme.typography.bodyLarge)
                                    Text(templateTargetSummary(TemplateTargetInput.from(target)), color = OF.muted, style = MaterialTheme.typography.labelMedium)
                                    if (target.note.isNotBlank()) Text(target.note, color = OF.muted, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                    }
                }
            } else {
                if (!showStarters && data.templateSplits.isNotEmpty()) {
                    item { SectionTitle("Your splits") }
                    items(data.templateSplits, key = { "split-${it.id}" }) { saved ->
                        TemplateSplitRow(saved.name, saved.days.map { byId[it]?.name }, { selectedDay = 0; splitId = saved.id })
                    }
                }
                if (!showStarters && data.routines.isNotEmpty()) {
                    item { SectionTitle("Your templates", "Create", onCreate) }
                    items(data.routines, key = { it.id }) { routine ->
                        Surface(Modifier.fillMaxWidth(), shape = OF.shape, color = OF.card) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                    IconButton({ onEdit(routine) }) { Icon(Icons.Rounded.Edit, "Edit ${routine.name}") }
                                    IconButton({ delete = routine }) { Icon(Icons.Rounded.DeleteOutline, "Delete ${routine.name}") }
                                }
                                Text(routine.exerciseIds.size.let { "$it ${if (it == 1) "exercise" else "exercises"}" } + " · " +
                                    routine.effectivePrescriptions().sumOf { it.sets }.let { "$it ${if (it == 1) "set" else "sets"}" }, color = OF.muted)
                                OutlinedButton({ error = onStart(routine) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = data.active == null) { Text("Start ${routine.name}") }
                            }
                        }
                    }
                }
                if (showStarters) items(starters, key = { "starter-${it.id}" }) { preset ->
                    TemplateSplitRow(preset.name, preset.days.map { it?.name }, { selectedDay = 0; starterId = preset.id })
                }
                if (!showStarters && data.routines.isEmpty()) item {
                    Text("No templates yet", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton({ showStarters = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Browse starter splits") }
                }
                item { OutlinedButton(onCreate, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Create template") } }
            }
            if (error != null) item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
        if (selectedRoutine != null) Box(Modifier.padding(18.dp)) {
            PrimaryAction("Start ${selectedRoutine.name}", { error = onStart(selectedRoutine) }, enabled = data.active == null)
        }
        if (starter != null) Box(Modifier.padding(18.dp)) {
            PrimaryAction(if (installing) "Adding templates" else "Use split", {
                if (!installing) {
                    installing = true
                    error = onInstall(starter.id)
                    if (error == null) pendingName = starter.name else installing = false
                }
            }, enabled = !installing)
        }
    }
    delete?.let { routine -> ConfirmDialog("Delete ${routine.name}?", "Your saved workouts stay in History.", "Delete", { delete = null }, { onDelete(routine.id); delete = null }) }
}
@Composable private fun TemplateSplitRow(name: String, days: List<String?>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Rounded.ChevronRight, null)
        }
        TemplateWeekStrip(days)
    }
}
@Composable private fun TemplateWeekStrip(
    days: List<String?>, selectedDay: Int? = null, onSelect: ((Int) -> Unit)? = null, modifier: Modifier = Modifier,
) {
    val cell: @Composable (Int, String?, Modifier) -> Unit = { index, name, cellModifier ->
        val label = when {
            name == null -> "Rest"
            name.startsWith("Full body", true) -> name.replace("body", "").replace("  ", " ")
            name.startsWith("Shoulder", true) -> "Delts"
            else -> name
        }
        val selected = selectedDay == index
        Column(cellModifier.heightIn(min = 64.dp).then(if (onSelect != null) Modifier
            .selectable(selected, role = Role.Tab) { onSelect(index) }
            .semantics(mergeDescendants = true) { contentDescription = "Day ${index + 1}, ${name ?: "Rest"}" } else Modifier)
            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((index + 1).toString(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.onSurface else if (name == null) OF.background else OF.card,
                contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) {
                Text(label, Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall, minLines = 2, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (onSelect != null && maxWidth < 336.dp) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(days.mapIndexed { index, name -> index to name }, key = { it.first }) { (index, name) -> cell(index, name, Modifier.width(52.dp)) }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEachIndexed { index, name -> cell(index, name, Modifier.weight(1f)) }
        }
    }
}
