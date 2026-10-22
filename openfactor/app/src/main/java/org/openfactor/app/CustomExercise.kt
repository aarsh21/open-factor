package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private enum class CustomChoice { METRIC1, METRIC2, TYPE, LATERALITY, JOINT1, JOINT2 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExercise(data: AppData, catalog: EquipmentCatalog, onBack: () -> Unit, initialDraft: CustomExerciseDraft? = null, onSave: (CustomExerciseDraft) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initialDraft ?: CustomExerciseDraft()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var choice by rememberSaveable { mutableStateOf<CustomChoice?>(null) }
    var equipmentIndex by rememberSaveable { mutableIntStateOf(-1) }
    var supportEdit by rememberSaveable { mutableStateOf(false) }
    fun back() { if (step > 0) step-- else onBack() }
    BackHandler { back() }
    val equipmentNames = remember(catalog) { catalog.items.associate { it.id to it.name } }
    fun groupName(group: Set<String>, empty: String = "Bodyweight Only") =
        group.map { equipmentNames[it] ?: it }.sorted().joinToString(" + ").ifEmpty { empty }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading("Create Custom Exercise", ::back)
        LinearProgressIndicator(
            progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = MaterialTheme.colorScheme.primary, trackColor = OF.line,
        )
        key(step) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    listOf("Build your exercise", "Select muscles", "Select equipment", "Final details", "Does everything look correct?")[step],
                    style = MaterialTheme.typography.headlineLarge,
                )
                when (step) {
                    0 -> {
                        if (initialDraft != null) Text(
                            "Review the copied properties before creating a separate exercise. Choose any missing type or equipment. Demonstration media, history and exercise overrides are not copied.",
                            color = OF.muted,
                        )
                        OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, Modifier.fillMaxWidth(),
                            label = { Text("Exercise Name") }, supportingText = { Text("Required") }, singleLine = true)
                        Text("Trackable Metric · Required", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceField("Trackable Metric 1", draft.metric1?.customLabel ?: "None", Modifier.weight(1f), showLabel = false) { choice = CustomChoice.METRIC1 }
                            ChoiceField("Trackable Metric 2", draft.metric2?.customLabel ?: "None", Modifier.weight(1f), showLabel = false) { choice = CustomChoice.METRIC2 }
                        }
                        if (draft.metrics.isNotEmpty() && !draft.copy(name = "check", type = CustomExerciseType.COMPOUND_UPPER).validBasics)
                            Text("Choose different metric types for the two fields.", color = MaterialTheme.colorScheme.error)
                        ChoiceField("Type", draft.type?.label ?: "Select type") { choice = CustomChoice.TYPE }
                        ChoiceField("Laterality", draft.laterality) { choice = CustomChoice.LATERALITY }
                    }
                    1 -> {
                        Text("Tap a muscle to cycle through Primary, Secondary, and unselected.", color = OF.muted)
                        val upper = listOf("Chest", "Upper Back", "Lats", "Biceps", "Triceps", "Front Delts", "Side Delts", "Lower Back", "Abs", "Rear Delts", "Upper Traps", "Forearms", "Obliques", "Neck", "Serratus")
                        val lower = listOf("Quads", "Glutes", "Hamstrings", "Calves", "Adductors", "Abductors", "Hip flexors", "Tibs")
                        listOf("Upper" to upper, "Lower" to lower).forEach { (label, muscles) ->
                            Text(label, style = MaterialTheme.typography.titleLarge)
                            muscles.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { muscle ->
                                        val role = when (muscle) { in draft.primary -> "Primary"; in draft.secondary -> "Secondary"; else -> "Unselected" }
                                        Column(
                                            Modifier.weight(1f).clickable { draft = draft.cycleMuscle(muscle) }
                                                .semantics { contentDescription = "Select $muscle"; stateDescription = role },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box {
                                                MuscleImage(muscle, Modifier.fillMaxWidth().aspectRatio(1f))
                                                if (role != "Unselected") Icon(Icons.Rounded.CheckCircle, role, Modifier.align(Alignment.TopEnd), tint = if (role == "Primary") OF.blue else MaterialTheme.colorScheme.primary)
                                            }
                                            Text(muscle, style = MaterialTheme.typography.bodyMedium)
                                            if (role != "Unselected") Text(role, color = OF.muted, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    2 -> {
                        if (draft.resistance.isEmpty()) Text("Select resistance equipment or Bodyweight Only to continue.", color = MaterialTheme.colorScheme.error)
                        draft.resistance.forEachIndexed { index, group ->
                            EquipmentRequirementRow(if (index == 0) "Resistance" else "Alternate Resistance ${index + 1}", groupName(group),
                                { equipmentIndex = index; supportEdit = false },
                                if (index == 0) null else ({ draft = draft.copy(resistance = draft.resistance.filterIndexed { i, _ -> i != index }) }))
                        }
                        HorizontalDivider(color = OF.line)
                        Text("Support", style = MaterialTheme.typography.titleLarge)
                        Text("Equipment that assists, stabilizes, or makes the movement possible, without adding resistance", color = OF.muted)
                        draft.support.forEachIndexed { index, group ->
                            EquipmentRequirementRow("Support ${index + 1}", groupName(group),
                                { equipmentIndex = index; supportEdit = true },
                                { draft = draft.copy(support = draft.support.filterIndexed { i, _ -> i != index }) })
                        }
                        OutlinedButton({ equipmentIndex = draft.support.size; supportEdit = true }) { Text(if (draft.support.isEmpty()) "Add Support" else "Add Alternate Support") }
                        if (draft.support.isEmpty()) Text("Optional", color = OF.muted)
                    }
                    3 -> {
                        RatingField("Range of Motion", draft.rom) { draft = draft.copy(rom = it) }
                        RatingField("Stability", draft.stability) { draft = draft.copy(stability = it) }
                        OutlinedTextField(
                            draft.bodyPercent, { draft = draft.copy(bodyPercent = it) }, Modifier.fillMaxWidth(),
                            label = { Text("Body Weight Contribution") }, suffix = { Text("%") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = draft.bodyPercent.isNotBlank() && draft.contribution == null,
                            supportingText = { Text(if (draft.bodyPercent.isNotBlank() && draft.contribution == null) "Enter a percentage from 0 to 100" else "Required · 0% for no body weight contribution") },
                        )
                        val weight = data.scaleWeights.filter { it.date <= LocalDate.now() }.maxByOrNull { it.date }
                        if (weight != null && draft.contribution != null)
                            Text("${(weight.weightKg * requireNotNull(draft.contribution) / 100).displayWeight(data.preferences.pounds)} ${if (data.preferences.pounds) "lb" else "kg"} at your current weight", color = OF.muted)
                        ChoiceField("Primary Joint Action", draft.primaryJoint.ifBlank { "None" }) { choice = CustomChoice.JOINT1 }
                        ChoiceField("Secondary Joint Action", draft.secondaryJoint.ifBlank { "None" }) { choice = CustomChoice.JOINT2 }
                        OutlinedTextField(
                            draft.alternateNames, { if (it.length <= 300 || it.length < draft.alternateNames.length) draft = draft.copy(alternateNames = it) }, Modifier.fillMaxWidth(),
                            isError = draft.alternateNames.length > 300, minLines = 2, maxLines = 4,
                            label = { Text("Alternate Names") }, supportingText = { Text("${draft.alternateNames.length}/300 · Separate names with commas") },
                        )
                        OutlinedTextField(draft.instructions, { draft = draft.copy(instructions = it) }, Modifier.fillMaxWidth(), label = { Text("Instructions and technique notes") }, minLines = 2, maxLines = 6)
                    }
                    4 -> {
                        Surface(color = Color(0xFF443500), contentColor = Color.White, shape = OF.shape) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Warning, null)
                                Column {
                                    Text("Most fields are final", style = MaterialTheme.typography.titleMedium)
                                    Text("You won’t be able to edit them after the exercise is created.")
                                }
                            }
                        }
                        ReviewField("Exercise Name", draft.name)
                        ReviewField("Trackable Metric · Final", draft.metrics.joinToString(", ") { it.customLabel })
                        ReviewField("Type", draft.type?.label ?: "Select type")
                        ReviewField("Laterality · Final", draft.laterality)
                        ReviewField("Target Muscles", (draft.primary.map { "$it (Primary)" } + draft.secondary.map { "$it (Secondary)" }).joinToString(", ").ifEmpty { "None" })
                        ReviewField("Resistance", draft.resistance.joinToString(" or ") { groupName(it) })
                        ReviewField("Support", draft.support.joinToString(" or ") { groupName(it) }.ifEmpty { "None" })
                        ReviewField("Range of Motion", "${draft.rom}/5")
                        ReviewField("Stability", "${draft.stability}/5")
                        ReviewField("Body Weight Contribution", "${draft.bodyPercent}%")
                        ReviewField("Primary Joint Action", draft.primaryJoint.ifBlank { "None" })
                        ReviewField("Secondary Joint Action", draft.secondaryJoint.ifBlank { "None" })
                        ReviewField("Alternate Names", draft.alternateNames.ifBlank { "None" })
                        if (draft.instructions.isNotBlank()) ReviewField("Instructions", draft.instructions)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(OF.top).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step == 1) Text("${draft.primary.size} primary, ${draft.secondary.size} secondary")
            if (step == 2) OutlinedButton(
                { equipmentIndex = draft.resistance.size; supportEdit = false }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (draft.resistance.isEmpty()) "Add Resistance" else "Add Alternate Resistance") }
            PrimaryAction(
                if (step == 4) "Create" else if (step == 1 && draft.primary.isEmpty() && draft.secondary.isEmpty()) "Skip" else "Next",
                { if (step == 4) onSave(draft) else step++ },
                enabled = when (step) { 0 -> draft.validBasics; 2 -> draft.resistance.isNotEmpty(); 3 -> draft.validDetails; 4 -> draft.validBasics && draft.validDetails; else -> true },
            )
        }
    }
    choice?.let { selected ->
        val title = when (selected) {
            CustomChoice.METRIC1 -> "Trackable Metric 1"; CustomChoice.METRIC2 -> "Trackable Metric 2"
            CustomChoice.TYPE -> "Type"; CustomChoice.LATERALITY -> "Laterality"
            CustomChoice.JOINT1 -> "Primary Joint Action"; CustomChoice.JOINT2 -> "Secondary Joint Action"
        }
        val options = when (selected) {
            CustomChoice.METRIC1, CustomChoice.METRIC2 -> listOf("None") + customMetricOrder.map { it.customLabel }
            CustomChoice.TYPE -> CustomExerciseType.entries.map { it.label }
            CustomChoice.LATERALITY -> listOf("Bilateral", "Unilateral", "Asymmetrical")
            CustomChoice.JOINT1, CustomChoice.JOINT2 -> listOf("None") + catalog.jointActions
        }
        val current = when (selected) {
            CustomChoice.METRIC1 -> draft.metric1?.customLabel ?: "None"
            CustomChoice.METRIC2 -> draft.metric2?.customLabel ?: "None"
            CustomChoice.TYPE -> draft.type?.label ?: ""
            CustomChoice.LATERALITY -> draft.laterality
            CustomChoice.JOINT1 -> draft.primaryJoint.ifBlank { "None" }
            CustomChoice.JOINT2 -> draft.secondaryJoint.ifBlank { "None" }
        }
        val descriptions = if (selected in listOf(CustomChoice.METRIC1, CustomChoice.METRIC2))
            customMetricOrder.associate { it.customLabel to it.customDescription } else emptyMap()
        ChoiceSheet(title, options, current, descriptions, { choice = null }) { value ->
            draft = when (selected) {
                CustomChoice.METRIC1 -> draft.copy(metric1 = customMetricOrder.find { it.customLabel == value })
                CustomChoice.METRIC2 -> draft.copy(metric2 = customMetricOrder.find { it.customLabel == value })
                CustomChoice.TYPE -> draft.copy(type = CustomExerciseType.entries.first { it.label == value })
                CustomChoice.LATERALITY -> draft.copy(laterality = value)
                CustomChoice.JOINT1 -> draft.copy(primaryJoint = value.takeUnless { it == "None" }.orEmpty())
                CustomChoice.JOINT2 -> draft.copy(secondaryJoint = value.takeUnless { it == "None" }.orEmpty())
            }
            choice = null
        }
    }
    if (equipmentIndex >= 0) {
        val groups = if (supportEdit) draft.support else draft.resistance
        EquipmentChoiceSheet(
            if (supportEdit) "Support Equipment" else "Resistance Equipment", catalog.items,
            groups.getOrNull(equipmentIndex).orEmpty(), !supportEdit, { equipmentIndex = -1 },
        ) { selected ->
            val updated = groups.toMutableList().apply {
                if (equipmentIndex == size) add(selected) else this[equipmentIndex] = selected
            }
            draft = if (supportEdit) draft.copy(support = updated) else draft.copy(resistance = updated)
            equipmentIndex = -1
        }
    }
}

private val customMetricOrder = listOf(SetMetric.REPS, SetMetric.REPS_PER_SIDE, SetMetric.WEIGHT, SetMetric.WEIGHT_PER_SIDE, SetMetric.WEIGHT_PER_SIDE_PERSISTENT, SetMetric.ASSISTANCE, SetMetric.DURATION, SetMetric.DURATION_PER_SIDE, SetMetric.DISTANCE_SHORT, SetMetric.DISTANCE_LONG, SetMetric.DISTANCE_PER_SIDE)
private val SetMetric.customLabel get() = when (this) {
    SetMetric.WEIGHT_PER_SIDE_PERSISTENT -> "Weight per side (Persistent)"
    SetMetric.ASSISTANCE -> "Weight (Assistance)"
    else -> catalogName
}
private val SetMetric.customDescription get() = when (this) {
    SetMetric.REPS -> "Track the number of repetitions performed"
    SetMetric.REPS_PER_SIDE -> "Track the number of repetitions performed per side"
    SetMetric.WEIGHT -> "Track the load used for each set"
    SetMetric.WEIGHT_PER_SIDE -> "Track the load used for each set per side"
    SetMetric.WEIGHT_PER_SIDE_PERSISTENT -> "Track singular weight used on both sides"
    SetMetric.ASSISTANCE -> "Track assisted or supported weight"
    SetMetric.DURATION -> "Track time for each set"
    SetMetric.DURATION_PER_SIDE -> "Track time for each set per side"
    SetMetric.DISTANCE_SHORT -> "Track short distances, like sprints or carries"
    SetMetric.DISTANCE_LONG -> "Track longer distances"
    SetMetric.DISTANCE_PER_SIDE -> "Track short distances per side"
}

@Composable
private fun ChoiceField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), showLabel: Boolean = true, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showLabel) Text(label, style = MaterialTheme.typography.titleMedium)
        Surface(onClick, color = OF.elevated, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }) {
            Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
            }
        }
    }
}

@Composable
private fun RatingField(label: String, value: Int, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { Box(Modifier.size(18.dp, 6.dp).background(if (it < value) OF.blue else OF.line)) }
            }
        }
        IconButton({ change(value - 1) }, enabled = value > 0) { Icon(Icons.Rounded.Remove, "Decrease $label") }
        Text(value.toString(), Modifier.padding(horizontal = 8.dp))
        IconButton({ change(value + 1) }, enabled = value < 5) { Icon(Icons.Rounded.Add, "Increase $label") }
    }
}

@Composable
private fun ReviewField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(8.dp), color = OF.card, border = BorderStroke(1.dp, OF.line)) {
            Text(value, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

@Composable
private fun EquipmentRequirementRow(label: String, names: String, edit: () -> Unit, remove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleMedium); Text(names, color = OF.muted) }
        TextButton(edit, Modifier.semantics { contentDescription = "Edit $label" }) { Text("Edit") }
        if (remove != null) IconButton(remove) { Icon(Icons.Rounded.Close, "Remove $label") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(title: String, options: List<String>, selected: String, descriptions: Map<String, String>, dismiss: () -> Unit, select: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            PageHeading(title, dismiss)
            if (options.size > 20) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp), placeholder = { Text("Search $title") }, singleLine = true)
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(options.filter { it.contains(query, true) }) { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        supportingContent = descriptions[option]?.let { description -> ({ Text(description) }) },
                        trailingContent = { RadioButton(option == selected, null) },
                        modifier = Modifier.clickable { select(option) },
                    )
                    HorizontalDivider(color = OF.line)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EquipmentChoiceSheet(title: String, equipment: List<EquipmentItem>, initial: Set<String>, bodyweight: Boolean, dismiss: () -> Unit, save: (Set<String>) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).imePadding()) {
            PageHeading(title, dismiss)
            Text("Select all equipment needed for this option.", Modifier.padding(horizontal = 18.dp), color = OF.muted)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(18.dp), placeholder = { Text("Search equipment") }, singleLine = true)
            LazyColumn(Modifier.weight(1f)) {
                if (bodyweight && query.isBlank()) item {
                    ListItem(headlineContent = { Text("Bodyweight Only") }, trailingContent = { RadioButton(selected.isEmpty(), { selected = emptyList() }) }, modifier = Modifier.clickable { selected = emptyList() })
                }
                items(equipment.filter { it.name.contains(query, true) && !it.name.equals("Bodyweight only", true) }, key = { it.id }) { item ->
                    fun toggle() { selected = if (item.id in selected) selected - item.id else selected + item.id }
                    ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text(item.category) }, trailingContent = { Checkbox(item.id in selected, { toggle() }) }, modifier = Modifier.clickable { toggle() })
                }
            }
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(dismiss) { Text("Cancel") }
                PrimaryAction("Done", { save(selected.toSet()) }, modifier = Modifier.weight(1f), enabled = bodyweight || selected.isNotEmpty())
            }
        }
    }
}
