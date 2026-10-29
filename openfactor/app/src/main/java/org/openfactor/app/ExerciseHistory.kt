package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.dp

/** A history visit uses the same recorded values and table as the saved workout. */
@Composable
internal fun ExerciseHistoryCard(
    workout: Workout,
    exercise: Exercise,
    preferences: Preferences,
    onOpenWorkout: ((String) -> Unit)? = null,
) {
    val entries = remember(workout, exercise.id) { workout.exercises.filter { it.exerciseId == exercise.id } }
    val entry = remember(entries) {
        SessionExercise(exerciseId = exercise.id, sets = entries.flatMap { it.sets })
    }
    val notes = remember(entries) { entries.map { it.notes }.filter(String::isNotBlank).distinct() }
    Surface(Modifier.fillMaxWidth().testTag("exercise-history-${workout.id}"), color = OF.card, shape = OF.shape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (onOpenWorkout != null) Modifier
                    .clickable { onOpenWorkout(workout.id) }
                    .semantics { contentDescription = "Open workout ${workout.name}" }
                    .heightIn(min = 48.dp) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(workout.name, style = MaterialTheme.typography.titleMedium)
                    Text(workout.localDate().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                        color = OF.muted, style = MaterialTheme.typography.labelMedium)
                }
                if (onOpenWorkout != null) Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
            }
            HorizontalDivider(color = OF.line)
            RecordedSetTable(entry, exercise, preferences)
            if (notes.isNotEmpty()) {
                HorizontalDivider(color = OF.line)
                notes.forEach { Text(it, color = OF.muted, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
