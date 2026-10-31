package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardCustomization(
    data: AppData,
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onSave: (DashboardPreferences) -> Unit,
) {
    var serialized by rememberSaveable {
        val preferences = data.preferences
        mutableStateOf(preferences.dashboard.copy(
            focuses = if (preferences.showWeeklyWorkouts) preferences.dashboard.focuses else emptyList(),
            insights = if (preferences.showInsights) preferences.dashboard.insights else emptyList(),
            habits = if (preferences.showHabits) preferences.dashboard.habits else emptyList(),
        ).json().toString())
    }
    val draft = remember(serialized) { DashboardPreferences.decode(JSONObject(serialized)) }
    fun change(value: DashboardPreferences) { serialized = value.json().toString() }
    var choosing by rememberSaveable { mutableStateOf<DashboardSection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var reset by rememberSaveable { mutableStateOf(false) }
    val optionsBySection = remember(exercises, data.preferences.dataVisibility) {
        DashboardSection.entries.associateWith { dashboardOptions(data, exercises, it) }
    }
    val labels = remember(optionsBySection) { optionsBySection.mapValues { (_, options) -> options.toMap() } }
    BackHandler { if (choosing != null) choosing = null else onBack() }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Customize Dashboard", onBack, actions = { TextButton({ onSave(draft) }) { Text("Save") } })
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Choose your tiles and their order. Changes apply when you save.", color = OF.muted,
                    modifier = Modifier.padding(bottom = 8.dp))
                Surface(shape = OF.shape, color = OF.card) {
                    val enabled = "weekly" in draft.focuses
                    Row(Modifier.fillMaxWidth().toggleable(enabled, role = Role.Switch, onValueChange = {
                        change(draft.copy(focuses = draft.focuses.toggleDashboardItem("weekly", it)))
                    }).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DateRange, null, tint = OF.muted)
                        Column(Modifier.weight(1f)) {
                            Text("Weekly overview", style = MaterialTheme.typography.titleMedium)
                            Text("Training totals and calendar", style = MaterialTheme.typography.bodySmall, color = OF.muted)
                        }
                        Switch(enabled, null)
                    }
                }
            }
            items(draft.sections, key = { it.name }, contentType = { "section" }) { section ->
                val selected = draft.selected(section)
                val visible = selected.filter { section != DashboardSection.BODY || data.preferences.dataVisibility.isVisible(it) }
                Surface(Modifier.itemMotion(this), shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(section.label, style = MaterialTheme.typography.titleMedium)
                                Text("${visible.size} ${if (visible.size == 1) "tile" else "tiles"}", color = OF.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton({ query = ""; choosing = section }, Modifier.semantics { contentDescription = "Edit ${section.label}" }) { Text("Edit") }
                            ReorderMenu(section.label, draft.sections.indexOf(section), draft.sections.size, onMove = { delta ->
                                change(draft.copy(sections = draft.sections.moveDashboardItem(draft.sections.indexOf(section), delta)))
                            })
                        }
                        visible.forEachIndexed { visibleIndex, key ->
                            key(key) {
                                val label = labels[section]?.get(key) ?: "Unavailable exercise"
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${visibleIndex + 1}", Modifier.width(28.dp), color = OF.muted, style = MaterialTheme.typography.labelMedium)
                                    Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    ReorderMenu(label, selected.indexOf(key), selected.size,
                                        onMove = { delta -> change(draft.withSelection(section, selected.moveDashboardItem(selected.indexOf(key), delta))) },
                                        onRemove = { change(draft.withSelection(section, selected - key)) })
                                }
                            }
                        }
                        if (visible.isEmpty()) Text("Hidden from your dashboard", color = OF.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                TextButton({ reset = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reset to defaults") }
            }
        }
    }
    choosing?.let { section ->
        ModalBottomSheet(onDismissRequest = { choosing = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).imePadding().padding(horizontal = 18.dp)) {
                Text(section.label, style = MaterialTheme.typography.titleLarge)
                Text("${draft.selected(section).size} selected", color = OF.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                if (section == DashboardSection.EXERCISES) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Search exercises") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear exercise search") } })
                }
                val results by produceState<List<Pair<String, String>>?>(null, exercises, section, query, optionsBySection) {
                    value = null
                    value = if (section == DashboardSection.EXERCISES && query.isNotBlank()) withContext(Dispatchers.Default) {
                        ExerciseSearchIndexes.forExercises(exercises).search(query).map { it.id to it.title }
                    } else optionsBySection[section].orEmpty()
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (results == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else if (results!!.isEmpty()) item { Text("No matching exercises", Modifier.padding(vertical = 18.dp), color = OF.muted) }
                    items(results.orEmpty(), key = { it.first }, contentType = { "option" }) { (key, label) ->
                        val checked = key in draft.selected(section)
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Checkbox, onValueChange = {
                            change(draft.withSelection(section, draft.selected(section).toggleDashboardItem(key, it)))
                        }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, Modifier.weight(1f))
                            Checkbox(checked, null)
                        }
                    }
                }
                PrimaryAction("Done", { choosing = null }, Modifier.navigationBarsPadding().padding(vertical = 12.dp))
            }
        }
    }
    if (reset) ConfirmDialog("Reset Dashboard?", "Restore the default tile selections and order. Save applies the reset; Back cancels it. Saved data is not changed.", "Reset",
        { reset = false }, { change(DashboardPreferences()); reset = false })
}

@Composable
private fun ReorderMenu(label: String, index: Int, size: Int, onMove: (Int) -> Unit, onRemove: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    if (size <= 1 && onRemove == null) return
    Box {
        IconButton({ expanded = true }) { Icon(Icons.Rounded.MoreVert, "Arrange $label") }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text("Move up") }, { expanded = false; onMove(-1) }, enabled = index > 0,
                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) })
            DropdownMenuItem({ Text("Move down") }, { expanded = false; onMove(1) }, enabled = index < size - 1,
                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) })
            if (onRemove != null) DropdownMenuItem({ Text("Remove tile") }, { expanded = false; onRemove() },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) })
        }
    }
}

internal fun dashboardOptions(data: AppData, exercises: List<Exercise>, section: DashboardSection): List<Pair<String, String>> = when (section) {
    DashboardSection.INSIGHTS -> listOf("workouts" to "Workouts", "trend" to "Weight Trend")
    DashboardSection.HABITS -> listOf("weighIn" to "Weigh-In", "workouts" to "Workouts")
    DashboardSection.BODY -> (listOf("weight" to "Scale Weight", "visualBodyFat" to "Visual Body Fat") + BodyMeasurement.entries.map { it.key to it.label })
        .filter { data.preferences.dataVisibility.isVisible(it.first) }
    DashboardSection.MUSCLES -> (upperMuscleGroups + lowerMuscleGroups).map { it to it }
    DashboardSection.EXERCISES -> exercises.sortedBy { it.title }.map { it.id to it.title }
    DashboardSection.GENERAL -> listOf("steps" to "Steps")
}

internal fun DashboardPreferences.selected(section: DashboardSection): List<String> = when (section) {
    DashboardSection.INSIGHTS -> insights
    DashboardSection.HABITS -> habits
    DashboardSection.BODY -> bodyMetrics
    DashboardSection.MUSCLES -> muscles
    DashboardSection.EXERCISES -> exerciseIds
    DashboardSection.GENERAL -> if (showSteps) listOf("steps") else emptyList()
}

internal fun DashboardPreferences.withSelection(section: DashboardSection, keys: List<String>): DashboardPreferences = when (section) {
    DashboardSection.INSIGHTS -> copy(insights = keys)
    DashboardSection.HABITS -> copy(habits = keys)
    DashboardSection.BODY -> copy(bodyMetrics = keys)
    DashboardSection.MUSCLES -> copy(muscles = keys)
    DashboardSection.EXERCISES -> copy(exerciseIds = keys)
    DashboardSection.GENERAL -> copy(showSteps = "steps" in keys)
}
