package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private val WorkoutShortcut.icon: ImageVector get() = when (this) {
    WorkoutShortcut.WEIGHT -> Icons.Rounded.MonitorWeight
    WorkoutShortcut.PHOTOS -> Icons.Rounded.PhotoLibrary
    WorkoutShortcut.MEASUREMENTS -> Icons.Rounded.Straighten
    WorkoutShortcut.HISTORY -> Icons.Rounded.History
    WorkoutShortcut.EMPTY_WORKOUT -> Icons.Rounded.FitnessCenter
    WorkoutShortcut.NEW_PROGRAM -> Icons.Rounded.EventNote
    WorkoutShortcut.NEW_WORKOUT -> Icons.Rounded.Add
}

@Composable
internal fun ShortcutEditor(initial: ShortcutPreferences, onCancel: () -> Unit, onSave: (ShortcutPreferences) -> Unit) {
    val saver = remember { Saver<ShortcutPreferences, String>(save = { it.json().toString() }, restore = { ShortcutPreferences.decode(JSONObject(it)) }) }
    var draft by rememberSaveable(stateSaver = saver) { mutableStateOf(initial) }
    var mode by rememberSaveable { mutableStateOf("Order") }
    var colorKey by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().background(OF.background)) {
        PageHeading("Shortcuts", onCancel) {
            TextButton({ onSave(draft) }) { Text("Save") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreferenceSwitch("Up Next Banner", "Show the next scheduled workout when scheduling is available", draft.showUpNext) {
                draft = draft.copy(showUpNext = it)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Order", "Color").forEach { label ->
                    FilterChip(mode == label, { mode = label }, label = { Text(label) })
                }
            }
            Text("The first four enabled actions are top shortcuts. Save applies changes; Back or Cancel discards them.", color = OF.muted)
            val visibleOrder = draft.order + WorkoutShortcut.entries.filterNot { it in draft.order }
            visibleOrder.forEach { shortcut ->
                val index = draft.order.indexOf(shortcut)
                Surface(shape = OF.shape, color = OF.card) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(shortcut.icon, null, tint = draft.colors[shortcut]?.let { Color(it.argb) } ?: LocalContentColor.current)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(shortcut.label)
                                Text(if (index in 0..3) "Top Shortcut" else if (index >= 0) "More Shortcuts" else "Hidden", color = OF.muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (mode == "Order") {
                                IconButton({
                                    val list = draft.order.toMutableList()
                                    list.add(index - 1, list.removeAt(index))
                                    draft = draft.copy(order = list)
                                }, enabled = index > 0, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.ArrowUpward, "Move ${shortcut.label} up")
                                }
                                IconButton({
                                    val list = draft.order.toMutableList()
                                    list.add(index + 1, list.removeAt(index))
                                    draft = draft.copy(order = list)
                                }, enabled = index >= 0 && index < draft.order.lastIndex, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.ArrowDownward, "Move ${shortcut.label} down")
                                }
                            } else TextButton({ colorKey = shortcut.name }) {
                                Text(draft.colors[shortcut]?.label ?: "Default")
                            }
                        }
                        PreferenceSwitch("Show ${shortcut.label}", "", index >= 0) { enabled ->
                            draft = draft.copy(order = if (enabled) draft.order + shortcut else draft.order - shortcut)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ draft = ShortcutPreferences() }) { Text("Reset Shortcuts") }
                TextButton(onCancel) { Text("Cancel") }
            }
        }
    }
    if (colorKey.isNotEmpty()) {
        val shortcut = WorkoutShortcut.valueOf(colorKey)
        AlertDialog(onDismissRequest = { colorKey = "" }, title = { Text("${shortcut.label} color") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                (listOf<ShortcutColor?>(null) + ShortcutColor.entries).forEach { color ->
                    val selected = draft.colors[shortcut] == color
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected, role = Role.RadioButton, onClick = {
                            draft = draft.copy(colors = if (color == null) draft.colors - shortcut else draft.colors + (shortcut to color))
                            colorKey = ""
                        }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, null)
                        Text(color?.label ?: "Default", Modifier.weight(1f))
                        if (color != null) Box(Modifier.size(24.dp).background(Color(color.argb)))
                    }
                }
            }
        }, confirmButton = { TextButton({ colorKey = "" }) { Text("Cancel") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortcutSheet(p: Preferences, active: Workout?, onClose: () -> Unit, onConfigure: () -> Unit, onAction: (WorkoutShortcut) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = OF.top) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Rounded.Close, "Close shortcuts") }
                Text("Shortcuts", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onConfigure) { Icon(Icons.Rounded.Tune, "Configure shortcuts") }
            }
            if (p.shortcuts.showUpNext) {
                ListItem(headlineContent = { Text("Up Next") }, supportingContent = {
                    Text("No workout scheduled. Calendar placement is not available yet.")
                }, colors = ListItemDefaults.colors(containerColor = OF.top))
                HorizontalDivider()
            }
            p.shortcuts.order.forEachIndexed { index, shortcut ->
                val label = if (shortcut == WorkoutShortcut.EMPTY_WORKOUT && active != null) "Resume Workout" else shortcut.label
                ListItem(headlineContent = { Text(label) },
                    supportingContent = if (shortcut == WorkoutShortcut.EMPTY_WORKOUT && active != null) ({ Text("Started ${p.clockTime(active.startedAt)}") }) else null,
                    leadingContent = {
                        val color = p.shortcuts.colors[shortcut]
                        Box(Modifier.size(40.dp).background(color?.let { Color(it.argb) } ?: OF.elevated, OF.shape), contentAlignment = Alignment.Center) {
                            Icon(shortcut.icon, null, tint = if (color != null) Color(0xFF202020) else LocalContentColor.current)
                        }
                    },
                    modifier = Modifier.clickable(role = Role.Button) { onAction(shortcut) }
                        .semantics { stateDescription = if (index < 4) "Top shortcut ${index + 1}" else "More shortcuts" },
                    colors = ListItemDefaults.colors(containerColor = OF.top))
            }
            if (p.shortcuts.order.isEmpty()) Text("No shortcuts selected. Use Configure shortcuts to add actions.", Modifier.padding(16.dp), color = OF.muted)
        }
    }
}
