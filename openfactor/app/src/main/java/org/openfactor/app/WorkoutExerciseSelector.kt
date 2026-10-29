package org.openfactor.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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

/** Session order stays visible and switching takes one tap, with no modal between sets. */
@Composable
fun WorkoutExerciseSelector(
    workout: Workout,
    exercises: List<Exercise>,
    selected: SessionExercise?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    val listState = rememberLazyListState()
    val position = workout.exercises.indexOfFirst { it.id == selected?.id }
    LaunchedEffect(workout.id, selected?.id, workout.exercises.map { it.id }) {
        if (position < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == position }
        if (item == null || item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset) {
            // Immediate selection, including auto-next and restored sessions; no delayed tab motion.
            listState.scrollToItem(position)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).selectableGroup(),
            contentPadding = PaddingValues(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(workout.exercises, key = { _, entry -> entry.id }) { index, entry ->
                val exercise = byId[entry.exerciseId]
                val current = entry.id == selected?.id
                val progress = entry.workoutExerciseProgress()
                Surface(
                    onClick = { onSelect(entry.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (current) MaterialTheme.colorScheme.primary else OF.background,
                    contentColor = if (current) MaterialTheme.colorScheme.onPrimary else OF.muted,
                    border = if (current) null else BorderStroke(1.dp, OF.line),
                    modifier = Modifier.widthIn(max = 238.dp).heightIn(min = 48.dp).semantics {
                        role = Role.Tab
                        this.selected = current
                        contentDescription = "Select ${exercise?.title ?: "exercise"}, ${index + 1} of ${workout.exercises.size}, ${progress.complete} of ${progress.total} working sets complete"
                    },
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(exercise?.title ?: "Exercise", style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        IconButton(onAdd, Modifier.padding(end = 10.dp).size(48.dp)) {
            Icon(Icons.Rounded.Add, "Add exercise")
        }
    }
}
