package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class WorkoutExerciseProgress(val complete: Int, val total: Int) {
    val label: String get() = if (total == 0) "No working sets" else "$complete / $total sets"
}

/** A left/right pair or a Myo group is one working set; warmups never inflate this progress. */
fun SessionExercise.workoutExerciseProgress(): WorkoutExerciseProgress {
    val sets = logicalSets().filterNot { it.root.warmup }
    return WorkoutExerciseProgress(sets.count { it.completed }, sets.size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExerciseSelector(
    workout: Workout,
    exercises: List<Exercise>,
    selected: SessionExercise?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var open by rememberSaveable(workout.id) { mutableStateOf(false) }
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    val exercise = selected?.let { byId[it.exerciseId] }
    val position = workout.exercises.indexOfFirst { it.id == selected?.id }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp), color = OF.top,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "Choose workout exercise${exercise?.let { ", ${it.title}, ${position + 1} of ${workout.exercises.size}" }.orEmpty()}"
            },
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (exercise != null) ExerciseImage(exercise, Modifier.size(36.dp, 44.dp))
                else Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(28.dp))
                Text(exercise?.title ?: "Exercises", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (position >= 0) Text("${position + 1} / ${workout.exercises.size}",
                    style = MaterialTheme.typography.labelLarge, color = OF.muted)
                Icon(Icons.Rounded.ExpandMore, null)
            }
        }
        FilledTonalIconButton(onAdd, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "Add exercise") }
    }
    if (open) ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.8f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Exercises", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ open = false }) { Icon(Icons.Rounded.Close, "Close workout exercises") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(workout.exercises, key = { _, entry -> entry.id }) { index, entry ->
                    val item = byId[entry.exerciseId]
                    val progress = entry.workoutExerciseProgress()
                    val current = entry.id == selected?.id
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(current, role = Role.RadioButton) { onSelect(entry.id); open = false }
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .semantics { contentDescription = "Select ${item?.title ?: "exercise"}, ${index + 1} of ${workout.exercises.size}, ${progress.complete} of ${progress.total} working sets complete" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (item != null) ExerciseImage(item, Modifier.size(40.dp, 52.dp))
                        Text(item?.title ?: "Exercise", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(progress.label, style = MaterialTheme.typography.labelLarge, color = OF.muted)
                        if (current) Icon(Icons.Rounded.Check, "Current exercise")
                    }
                    HorizontalDivider(Modifier.padding(start = 70.dp), color = OF.line)
                }
            }
            Box(Modifier.padding(18.dp)) {
                PrimaryAction("Add exercise", { open = false; onAdd() }, icon = Icons.Rounded.Add)
            }
        }
    }
}
