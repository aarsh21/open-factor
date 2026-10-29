package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseEffortSheet(
    exercise: Exercise,
    set: LoggedSet,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var selected by rememberSaveable(set.id, set.rir) { mutableStateOf(set.rir) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(set.id) { focus.clearFocus(); keyboard?.hide() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.card,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Rate your last set", style = MaterialTheme.typography.titleLarge)
            Text(
                exercise.title + (set.side?.let { " · ${it.label}" } ?: "") +
                    (if (set.segmentOf != null) " · Myo component" else ""),
                style = MaterialTheme.typography.bodyLarge,
                color = OF.muted,
            )
            Text("How many more reps could you do?", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(4, 3, 2, 1, 0).forEach { value ->
                    val checked = selected?.let { if (value == 4) it >= 4 else it == value } == true
                    Surface(
                        color = if (checked) MaterialTheme.colorScheme.primary else OF.elevated,
                        contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                            .selectable(checked, role = Role.RadioButton) { selected = value }
                            .semantics {
                                contentDescription = if (value == 4) "4 or more reps left"
                                    else if (value == 0) "0 reps left, maximum effort" else "$value reps left"
                            },
                    ) {
                        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text(if (value == 4) "4+" else "$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("More in reserve", style = MaterialTheme.typography.labelMedium, color = OF.muted)
                Text("Max effort", style = MaterialTheme.typography.labelMedium, color = OF.muted)
            }
            PrimaryAction("Done", { selected?.let(onSave) }, enabled = selected != null)
            TextButton(onDismiss, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Skip effort rating" }) { Text("Skip") }
        }
    }
}
