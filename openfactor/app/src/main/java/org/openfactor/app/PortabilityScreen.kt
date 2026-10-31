package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DataPortabilityRows(onOpen: (String) -> Unit) {
    SettingsRow(Icons.Rounded.IosShare, "Data Export", "Quick or granular category and time selection") { onOpen("export") }
    SettingsRow(Icons.Rounded.TableChart, "Import spreadsheet", "Preview workout targets before appending new programs") { onOpen("spreadsheet") }
    SettingsRow(Icons.Rounded.Visibility, "Data Visibility", "Hide editor fields without deleting history") { onOpen("visibility") }
}

@Composable
internal fun OfflineHelpRows(onOpen: (String) -> Unit) {
    SettingsRow(Icons.Rounded.School, "Tutorials", "Review local guidance and reset tutorials") { onOpen("tutorials") }
    SettingsRow(Icons.Rounded.MenuBook, "Help & support", "Offline answers, backup safety and troubleshooting") { onOpen("help") }
}

@Composable
internal fun PortabilityScreen(
    route: String,
    p: Preferences,
    vm: WorkoutViewModel,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize()) {
        PageHeading(when {
            route == "export" -> "Data Export"
            route == "spreadsheet" -> "Import spreadsheet"
            route == "visibility" -> "Metric visibility"
            route == "tutorials" -> "Tutorials"
            else -> "Help & support"
        }, onClose)
        when (route) {
            "export" -> ScopedExportScreen(vm, snackbar)
            "spreadsheet" -> SpreadsheetImportScreen(vm, snackbar)
            "visibility" -> VisibilityScreen(p, vm)
            "tutorials" -> TutorialsScreen(p, vm, snackbar)
            else -> OfflineHelpScreen()
        }
    }
}

@Composable
private fun ScopedExportScreen(vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quick by rememberSaveable { mutableStateOf(true) }
    var frameName by rememberSaveable { mutableStateOf(ExportTimeFrame.SEVEN_DAYS.name) }
    var keys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingQuick by rememberSaveable { mutableStateOf(true) }
    var pendingFrame by rememberSaveable { mutableStateOf(ExportTimeFrame.SEVEN_DAYS.name) }
    var pendingKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingDate by rememberSaveable { mutableStateOf("") }
    var pendingZone by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) pendingDate = ""
        else {
            busy = true
            scope.launch {
                try {
                    val request = ScopedExportRequest(pendingQuick, ExportTimeFrame.valueOf(pendingFrame),
                        ExportCategory.entries.filter { it.key in pendingKeys }.toSet())
                    val snapshot = vm.data.value
                    val date = LocalDate.parse(pendingDate)
                    val zone = ZoneId.of(pendingZone)
                    withContext(Dispatchers.IO) {
                        val document = ScopedDataExport.document(snapshot, vm.catalog, request, date, zone)
                        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(document.toString(2))
                        } ?: error("Could not open file. Choose another local folder.")
                    }
                    error = null
                    scope.launch { snackbar.showSnackbar("Data exported · not a restore backup") }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    error = "Export failed: ${failure.message}. Try another local document."
                } finally {
                    pendingDate = ""
                    busy = false
                }
            }
        }
    }
    val frame = ExportTimeFrame.valueOf(frameName)
    val selected = ExportCategory.entries.filter { it.key in keys && it.unavailable == null }.toSet()
    val ready = !busy && pendingDate.isEmpty() && (quick || selected.isNotEmpty())
    Column(Modifier.fillMaxSize()) {
        TabRow(if (quick) 0 else 1) {
            Tab(quick, { quick = true }, enabled = !busy, text = { Text("Quick Export") })
            Tab(!quick, { quick = false }, enabled = !busy, text = { Text("Granular Export") })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!quick) {
                Text("Choose categories first, then the time frame. Only selected categories are written.", color = OF.muted)
                ExportCategory.entries.filter { it.unavailable == null }.groupBy { it.group }.forEach { (group, categories) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        val all = categories.all { it.key in keys }
                        TextButton({ keys = if (all) keys - categories.map { it.key }.toSet() else (keys + categories.map { it.key }).distinct() }) {
                            Text(if (all) "Deselect All" else "Select All")
                        }
                    }
                    categories.forEach { category ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .toggleable(category in selected, role = Role.Checkbox, onValueChange = { checked ->
                                        keys = if (checked) keys + category.key else keys - category.key
                                    }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category.label)
                            }
                            Checkbox(category in selected, null)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Text("Choose Time Frame", style = MaterialTheme.typography.titleMedium)
            var choosing by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ choosing = true }, Modifier.fillMaxWidth(), enabled = !busy) {
                    Text(frame.label, Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, null)
                }
                DropdownMenu(choosing, { choosing = false }) {
                    ExportTimeFrame.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { frameName = option.name; choosing = false })
                    }
                }
            }
            val dates = frame.dates(LocalDate.now())
            Text(dates?.let { "${it.start} – ${it.endInclusive} · inclusive dates" } ?: "All saved dates", color = OF.muted)
            Text("What's Included?", style = MaterialTheme.typography.titleMedium)
            Text(if (quick) "Muscle-group set records, the active training program, and completed workout history including notes."
                else "${selected.size} categories. Dated records use the time frame; libraries and settings export their current state. Workouts means saved routines. User Profile contains your display name, gender, height and weekly workout goal.")
            Text("Local JSON for analysis, not a restorable backup. No original photos, exercise media, active sessions or unsaved drafts. Hidden measurements still export. Use Export Backup for a complete restore file, including photos. Keep exports private.", color = OF.muted)
            Text("Exercise and muscle-group data contains raw saved sets and catalog muscle labels, not estimated strength, trend or volume calculations.", color = OF.muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(
            onClick = {
                pendingQuick = quick
                pendingFrame = frameName
                pendingKeys = keys
                pendingDate = LocalDate.now().toString()
                pendingZone = ZoneId.systemDefault().id
                save.launch("open-factor-data-${pendingDate}.json")
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 108.dp).heightIn(min = 52.dp),
        ) { Text(if (busy) "Exporting…" else "Export") }
    }
}

@Composable
private fun VisibilityScreen(p: Preferences, vm: WorkoutViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Choose which fields appear when logging new body records. Saved readings stay in history, charts and backups.", color = OF.muted)
        val groups = linkedMapOf("Progress photos" to listOf("frontPhoto" to "Front photo", "sidePhoto" to "Side photo", "backPhoto" to "Back photo")) +
            BodyMeasurement.entries.groupBy { it.group }.mapValues { (_, fields) -> fields.map { it.key to it.label } }
        groups.forEach { (group, fields) ->
            Text(group, style = MaterialTheme.typography.titleMedium)
            fields.forEach { (key, label) ->
                val visible = p.dataVisibility.isVisible(key)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(visible, role = Role.Switch, onValueChange = { checked ->
                        vm.settings { it.copy(dataVisibility = it.dataVisibility.withVisible(key, checked)) }
                    }), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f))
                    Switch(visible, null)
                }
                HorizontalDivider()
            }
        }
    }
}
