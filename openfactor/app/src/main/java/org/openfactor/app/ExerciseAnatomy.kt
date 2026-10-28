package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Original anatomy art stays useful at reading size instead of becoming a tiny thumbnail. */
@Composable
fun ExerciseAnatomy(exercise: Exercise) {
    val muscles = (exercise.muscles + exercise.secondary).distinct()
    var selected by rememberSaveable(exercise.id) { mutableStateOf(muscles.firstOrNull() ?: exercise.muscle) }
    var figure by rememberSaveable { mutableStateOf("male") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val figureSelector: @Composable () -> Unit = {
            SingleChoiceSegmentedButtonRow {
                listOf("male" to "Male", "female" to "Female").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = figure == value,
                        onClick = { figure = value },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.semantics { contentDescription = "$label anatomy figure" },
                    ) { Text(label) }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 380.dp || LocalDensity.current.fontScale > 1.15f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Target muscles", style = MaterialTheme.typography.titleLarge)
                    figureSelector()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target muscles", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    figureSelector()
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(muscles, key = { it }) { muscle ->
                FilterChip(
                    selected = selected == muscle,
                    onClick = { selected = muscle },
                    label = { Text(muscle) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        MuscleImage(selected, Modifier.fillMaxWidth().aspectRatio(1f), figure)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selected, style = MaterialTheme.typography.titleMedium)
            Text(if (selected in exercise.muscles) "Primary" else "Secondary", color = OF.muted)
        }
    }
}
