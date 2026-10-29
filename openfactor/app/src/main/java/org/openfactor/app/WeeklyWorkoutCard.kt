package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
internal fun WeeklyWorkoutCard(week: TrainingWeek, onHistory: () -> Unit) {
    Surface(onClick = onHistory, modifier = Modifier.fillMaxWidth().semantics {
        contentDescription = "This week: ${week.workingSets} working sets, ${week.sessions} sessions, ${week.trainedMuscles} muscles. Open workout history"
    }, shape = OF.shape, color = OF.card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    val pattern = DateTimeFormatter.ofPattern("d MMM")
                    Text("${week.first.format(pattern)} – ${week.last.format(pattern)}", color = OF.muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(week.workingSets.toString(), style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                Text(if (week.workingSets == 1) "working set" else "working sets", color = OF.muted,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(bottom = 5.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${week.sessions} ${if (week.sessions == 1) "session" else "sessions"}", Modifier.weight(1f),
                    color = OF.muted, style = MaterialTheme.typography.bodyMedium)
                Text("${week.trainedMuscles} ${if (week.trainedMuscles == 1) "muscle" else "muscles"}", Modifier.weight(1f),
                    color = OF.muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (week.includesActive) Text("Includes your active workout", color = OF.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
