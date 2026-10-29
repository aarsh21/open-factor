package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
        mutableStateOf(
            preferences.dashboard
                .copy(
                    focuses =
                        if (preferences.showWeeklyWorkouts) preferences.dashboard.focuses
                        else emptyList(),
                    insights =
                        if (preferences.showInsights) preferences.dashboard.insights
                        else emptyList(),
                    habits =
                        if (preferences.showHabits) preferences.dashboard.habits else emptyList(),
                )
                .json()
                .toString()
        )
    }
    val draft = DashboardPreferences.decode(JSONObject(serialized))
    fun change(value: DashboardPreferences) {
        serialized = value.json().toString()
    }
    var choosing by rememberSaveable { mutableStateOf<DashboardSection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val exerciseSearch = remember(exercises) { ExerciseSearchIndex(exercises) }
    var reset by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (choosing != null) choosing = null else onBack() }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            "Customize Dashboard",
            onBack,
            actions = { TextButton({ onSave(draft) }) { Text("Save") } },
        )
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 112.dp)) {
            item {
                SectionTitle("Primary Focus")
                val focuses =
                    listOf("weekly")
                focuses.forEach { key ->
                    val label = if (key == "weekly") "Weekly Workouts" else "Recent Records"
                    val enabled = key in draft.focuses
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (enabled)
                            MoveButtons(label, draft.focuses.indexOf(key), draft.focuses.size) {
                                delta ->
                                change(
                                    draft.copy(
                                        focuses =
                                            draft.focuses.moveDashboardItem(
                                                draft.focuses.indexOf(key),
                                                delta,
                                            )
                                    )
                                )
                            }
                        Switch(
                            enabled,
                            {
                                change(
                                    draft.copy(focuses = draft.focuses.toggleDashboardItem(key, it))
                                )
                            },
                            Modifier.semantics { contentDescription = label },
                        )
                    }
                }
            }
            items(draft.sections, key = { it.name }) { section ->
                val labels = dashboardOptions(data, exercises, section)
                val selected = draft.selected(section)
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            section.label,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val index = draft.sections.indexOf(section)
                        MoveButtons(section.label, index, draft.sections.size) { delta ->
                            change(
                                draft.copy(
                                    sections = draft.sections.moveDashboardItem(index, delta)
                                )
                            )
                        }
                    }
                    Surface(
                        shape = OF.shape,
                        color = OF.card,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val visible =
                                selected.filter {
                                    section != DashboardSection.BODY ||
                                        data.preferences.dataVisibility.isVisible(it)
                                }
                            visible.forEach { key ->
                                val index = selected.indexOf(key)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                        Text(
                                            labels.find { it.first == key }?.second
                                                ?: "Unavailable exercise"
                                        )
                                        if (section == DashboardSection.MUSCLES)
                                            Text(
                                                "Sets · local counts",
                                                color = OF.muted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        if (section == DashboardSection.EXERCISES)
                                            Text(
                                                "Recorded working sets",
                                                color = OF.muted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                    }
                                    MoveButtons(
                                        labels.find { it.first == key }?.second ?: key,
                                        index,
                                        selected.size,
                                    ) { delta ->
                                        change(
                                            draft.withSelection(
                                                section,
                                                selected.moveDashboardItem(index, delta),
                                            )
                                        )
                                    }
                                }
                                HorizontalDivider(color = OF.line)
                            }
                            if (visible.isEmpty())
                                Text("No tiles selected", Modifier.padding(12.dp), color = OF.muted)
                            OutlinedButton(
                                {
                                    query = ""
                                    choosing = section
                                },
                                Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) {
                                Text(
                                    if (section == DashboardSection.GENERAL) "Add or Remove"
                                    else
                                        "Add or Remove ${when(section) { DashboardSection.INSIGHTS -> "Insights"
 DashboardSection.BODY -> "Body Metrics"
 else -> section.label }}"
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton({ reset = true }, Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text("Reset to App Defaults", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    choosing?.let { section ->
        ModalBottomSheet(
            onDismissRequest = { choosing = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).padding(horizontal = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ choosing = null }) {
                        Icon(Icons.Rounded.Close, "Close tile selector")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(section.label, style = MaterialTheme.typography.titleLarge)
                        Text("Add or remove tiles", color = OF.muted)
                    }
                }
                if (section == DashboardSection.EXERCISES) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search for an exercise") },
                        trailingIcon = {
                            if (query.isNotEmpty())
                                IconButton({ query = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear exercise search")
                                }
                        },
                    )
                }
                val options = remember(data, exercises, section, query) {
                    if (section == DashboardSection.EXERCISES)
                        exerciseSearch.search(query).map { it.id to it.title }
                    else dashboardOptions(data, exercises, section)
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (options.isEmpty())
                        item {
                            Text("No matching exercises", Modifier.padding(18.dp), color = OF.muted)
                        }
                    items(options, key = { it.first }) { (key, label) ->
                        val checked = key in draft.selected(section)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    change(
                                        draft.withSelection(
                                            section,
                                            draft
                                                .selected(section)
                                                .toggleDashboardItem(key, !checked),
                                        )
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f).padding(end = 12.dp))
                            Switch(
                                checked,
                                {
                                    change(
                                        draft.withSelection(
                                            section,
                                            draft.selected(section).toggleDashboardItem(key, it),
                                        )
                                    )
                                },
                                Modifier.semantics { contentDescription = label },
                            )
                        }
                        HorizontalDivider(color = OF.line)
                    }
                }
                PrimaryAction("Done", { choosing = null }, Modifier.padding(vertical = 12.dp))
            }
        }
    }
    if (reset)
        ConfirmDialog(
            "Reset Dashboard?",
            "Restore the default focus, tile selections and order. Save applies the reset; Back cancels it. Saved training and body data are not changed.",
            "Reset",
            { reset = false },
            {
                change(DashboardPreferences())
                reset = false
            },
        )
}

@Composable
private fun MoveButtons(label: String, index: Int, size: Int, onMove: (Int) -> Unit) {
    IconButton({ onMove(-1) }, enabled = index > 0) {
        Icon(Icons.Rounded.ArrowUpward, "Move $label up")
    }
    IconButton({ onMove(1) }, enabled = index < size - 1) {
        Icon(Icons.Rounded.ArrowDownward, "Move $label down")
    }
}

internal fun dashboardOptions(
    data: AppData,
    exercises: List<Exercise>,
    section: DashboardSection,
): List<Pair<String, String>> =
    when (section) {
        DashboardSection.INSIGHTS -> listOf("workouts" to "Workouts", "trend" to "Weight Trend")
        DashboardSection.HABITS -> listOf("weighIn" to "Weigh-In", "workouts" to "Workouts")
        DashboardSection.BODY ->
            data
                .bodyChartSeries()
                .filter { it.key != "bodyFat" && data.preferences.dataVisibility.isVisible(it.key) }
                .map { it.key to it.label }
        DashboardSection.MUSCLES -> (upperMuscleGroups + lowerMuscleGroups).map { it to it }
        DashboardSection.EXERCISES -> exercises.sortedBy { it.title }.map { it.id to it.title }
        DashboardSection.GENERAL -> listOf("steps" to "Steps")
    }

internal fun DashboardPreferences.selected(section: DashboardSection): List<String> =
    when (section) {
        DashboardSection.INSIGHTS -> insights
        DashboardSection.HABITS -> habits
        DashboardSection.BODY -> bodyMetrics
        DashboardSection.MUSCLES -> muscles
        DashboardSection.EXERCISES -> exerciseIds
        DashboardSection.GENERAL -> if (showSteps) listOf("steps") else emptyList()
    }

internal fun DashboardPreferences.withSelection(
    section: DashboardSection,
    keys: List<String>,
): DashboardPreferences =
    when (section) {
        DashboardSection.INSIGHTS -> copy(insights = keys)
        DashboardSection.HABITS -> copy(habits = keys)
        DashboardSection.BODY -> copy(bodyMetrics = keys)
        DashboardSection.MUSCLES -> copy(muscles = keys)
        DashboardSection.EXERCISES -> copy(exerciseIds = keys)
        DashboardSection.GENERAL -> copy(showSteps = "steps" in keys)
    }
