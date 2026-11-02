package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProgramLibrary(
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onStart: (String, String, Int) -> Unit,
) {
    var deleting by remember { mutableStateOf<TrainingProgram?>(null) }
    var replaceDraft by remember { mutableStateOf<TrainingProgram?>(null) }
    val catalog = vm.exercises()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading(
                "Programs",
                onBack,
                actions = { RoundAction(Icons.Rounded.Add, "Create program", onCreate) },
            )
        }
        if (data.programDraft != null)
            item {
                Column(Modifier.padding(18.dp)) {
                    PrimaryAction("Continue ${data.programDraft?.name}", onEdit)
                    TextButton({ runPersistenceAction { vm.commands.discardProgramDraft() } }) { Text("Discard Draft") }
                }
            }
        if (data.programs.isEmpty())
            item {
                Column(Modifier.padding(18.dp)) {
                    EmptyContent(
                        Icons.Rounded.ViewWeek,
                        "Your program library",
                        "Build a program from scratch with workout and rest days.",
                        "Build From Scratch",
                        onCreate,
                    )
                }
            }
        items(data.programs, key = { it.id }) { p ->
            val periods = p.deloadPlacement?.cycleKeys(p.cycleCount) ?: (0 until p.cycleCount).toList()
            var selectedPeriod by rememberSaveable(p.id) { mutableIntStateOf(periods.first()) }
            val cycle = selectedPeriod.takeIf { it in periods } ?: periods.first()
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        programIcon(p.icon),
                        null,
                        tint = Color(p.color),
                        modifier = Modifier.size(32.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${p.days.size} days · ${p.cycleCount} ${p.cycleUnit.plural.lowercase()}${if (data.activeProgramId == p.id) " · Active" else ""}",
                            color = OF.muted,
                        )
                    }
                    IconButton({
                        if (data.programDraft == null) {
                            runPersistenceAction {
                                vm.commands.beginProgram(p)
                                onEdit()
                            }
                        } else if (data.programDraft?.id == p.id) onEdit() else replaceDraft = p
                    }) {
                        Icon(Icons.Rounded.Edit, "Edit ${p.name}")
                    }
                    IconButton({ deleting = p }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete ${p.name}")
                    }
                }
                Row {
                    TextButton({
                        runPersistenceAction {
                            vm.commands.activateProgram(if (data.activeProgramId == p.id) null else p.id)
                        }
                    }) {
                        Text(if (data.activeProgramId == p.id) "Deactivate" else "Activate Program")
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    periods.forEach { n ->
                        FilterChip(
                            cycle == n,
                            { selectedPeriod = n },
                            label = { Text(if (n in 0 until p.cycleCount) "${p.cycleUnit.singular} ${n + 1}" else "Deload") },
                        )
                    }
                }
                p.days.forEach { day ->
                    val deload = p.deloadPlacement?.takeIf { it.sourceCycle == cycle }
                    val targets = day.exercises.map { planned ->
                        if (deload == null) planned.cycles.getOrNull(cycle)
                        else planned.targetForDeload(deload, catalog.find { it.id == planned.exerciseId })
                    }
                    val missingMetrics = deload != null && targets.any { it == null }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(day.name)
                            Text(
                                if (day.exercises.isEmpty()) "Rest Day"
                                else
                                    "${day.exercises.size} exercise${if (day.exercises.size == 1) "" else "s"}",
                                color = OF.muted,
                                fontSize = 12.sp,
                            )
                        }
                        if (day.exercises.isNotEmpty())
                            TextButton(
                                { onStart(p.id, day.id, cycle) },
                                enabled = !missingMetrics && targets.any { it != null && !it.skip && it.sets.isNotEmpty() },
                            ) {
                                Text("Start ${day.name}")
                            }
                    }
                    if (missingMetrics) Text(
                        "Deload unavailable: exercise metric information is missing. Choose a normal ${p.cycleUnit.singular.lowercase()} or replace the exercise.",
                        color = OF.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = OF.line)
            }
        }
    }
    replaceDraft?.let { p ->
        ConfirmDialog(
            "Replace current draft?",
            "This discards the unsaved draft before editing ${p.name}. Saved programs stay unchanged.",
            "Replace Draft",
            { replaceDraft = null },
            {
                runPersistenceAction {
                    vm.commands.beginProgram(p)
                    replaceDraft = null
                    onEdit()
                }
            },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            "Delete ${p.name}?",
            "Saved workout history will remain on this device.",
            "Delete",
            { deleting = null },
            {
                runPersistenceAction {
                    vm.commands.deleteProgram(p.id)
                    deleting = null
                }
            },
        )
    }
}
