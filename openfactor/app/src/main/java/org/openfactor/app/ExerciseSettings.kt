package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ExerciseSettingsScreen(
    exercise: Exercise,
    defaults: ExerciseDefaults,
    workoutRestSeconds: Int,
    onBack: () -> Unit,
    onSave: (ExerciseDefaults) -> Unit,
    onInfo: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var editor by rememberSaveable(exercise.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeading(exercise.title, onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            SectionTitle("General")
            Surface(shape = OF.shape, color = OF.card) {
                Column {
                    ListItem(
                        headlineContent = { Text("Info") },
                        supportingContent = { Text("Instructions, details and history") },
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        modifier = Modifier.clickable(onClick = onInfo),
                        colors = ListItemDefaults.colors(containerColor = OF.card),
                    )
                    HorizontalDivider(color = OF.line)
                    ListItem(
                        headlineContent = { Text("Exercise Note") },
                        supportingContent = { Text(defaults.note.ifBlank { "Display a note every time you work out with this exercise." }) },
                        leadingContent = { Icon(Icons.Rounded.EditNote, null) },
                        trailingContent = { Text(if (defaults.note.isBlank()) "Add" else "Edit", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { editor = "note" },
                        colors = ListItemDefaults.colors(containerColor = OF.card),
                    )
                    HorizontalDivider(color = OF.line)
                    ListItem(
                        headlineContent = { Text("Do Not Recommend") },
                        supportingContent = { Text("Keep this exercise in Deprioritized. Automatic program generation is not available yet.") },
                        leadingContent = { Icon(Icons.Rounded.Block, null) },
                        trailingContent = { Switch(defaults.doNotRecommend, null) },
                        modifier = Modifier.toggleable(defaults.doNotRecommend, role = Role.Switch) {
                            onSave(defaults.copy(doNotRecommend = it))
                        },
                        colors = ListItemDefaults.colors(containerColor = OF.card),
                    )
                    HorizontalDivider(color = OF.line)
                    ListItem(
                        headlineContent = { Text("Edit Duplicate") },
                        supportingContent = { Text("To edit all available exercise properties, create a duplicate.") },
                        leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        modifier = Modifier.clickable(onClick = onDuplicate),
                        colors = ListItemDefaults.colors(containerColor = OF.card),
                    )
                }
            }
            SectionTitle("Overrides")
            Surface(shape = OF.shape, color = OF.card) {
                ListItem(
                    headlineContent = { Text("Rest Timer") },
                    supportingContent = { Text(defaults.restSeconds?.let { "${it}s" } ?: "Default · ${workoutRestSeconds}s") },
                    leadingContent = { Icon(Icons.Rounded.Timer, null) },
                    trailingContent = { Text("Edit", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { editor = "rest" },
                    colors = ListItemDefaults.colors(containerColor = OF.card),
                )
            }
            Text(
                "Rest timer changes apply to new entries. Program rest targets take priority. Existing entries keep their workout notes and timers. Exercise notes stay pinned to the exercise, separate from workout history.",
                Modifier.padding(vertical = 16.dp), color = OF.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (editor.isNotEmpty()) {
        val rest = editor == "rest"
        var text by rememberSaveable(exercise.id, editor) {
            mutableStateOf(if (rest) defaults.restSeconds?.toString().orEmpty() else defaults.note)
        }
        val valid = !rest || text.isBlank() || text.toIntOrNull()?.let { it in 0..1800 } == true
        AlertDialog(
            onDismissRequest = { editor = "" },
            title = { Text(if (rest) "Rest Timer" else "Exercise Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (rest) "Override the workout rest timer for new entries." else "This note stays pinned to the exercise and is not copied into workout history.")
                    OutlinedTextField(
                        text, { text = it }, Modifier.fillMaxWidth(),
                        label = { Text(if (rest) "Rest seconds" else "Note") },
                        minLines = if (rest) 1 else 3,
                        singleLine = rest,
                        isError = !valid,
                        supportingText = { Text(if (rest) "0–1800; leave blank for Default" else "Leave blank to remove the note") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (rest) KeyboardType.Number else KeyboardType.Text),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    onSave(if (rest) defaults.copy(restSeconds = text.toIntOrNull()) else defaults.copy(note = text))
                    editor = ""
                }, enabled = valid) { Text("Save") }
            },
            dismissButton = { TextButton({ editor = "" }) { Text("Cancel") } },
        )
    }
}
