package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    p: Preferences,
    vm: WorkoutViewModel,
    snackbar: SnackbarHostState,
    onExercises: () -> Unit,
    onHistory: () -> Unit,
    onGyms: () -> Unit,
    onBodyData: () -> Unit,
    onProfile: (() -> Unit)? = null,
    integrationSettings: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsState()
    var dialog by rememberSaveable { mutableStateOf("") }
    var portabilityPage by rememberSaveable { mutableStateOf("") }
    var restoreBackup by remember { mutableStateOf<PreparedPhotoBackup?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    DisposableEffect(restoreBackup) {
        val prepared = restoreBackup
        onDispose { prepared?.close() }
    }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                backupBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { vm.export(it) }
                                ?: error("Could not open file")
                        }
                    }
                    backupBusy = false
                    snackbar.showSnackbar(
                        if (result.isSuccess) "Backup exported"
                        else "Export failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                backupBusy = true
                scope.launch(Dispatchers.IO) {
                    var prepared: PreparedPhotoBackup? = null
                    try {
                        prepared = context.contentResolver.openInputStream(uri)?.use {
                            vm.photoStore.prepare(it)
                        } ?: error("Could not read backup")
                        withContext(Dispatchers.Main) { restoreBackup = prepared }
                        prepared = null
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        withContext(Dispatchers.Main) {
                            snackbar.showSnackbar("Could not read backup: ${error.message}")
                        }
                    } finally {
                        prepared?.close()
                        withContext(Dispatchers.Main) { backupBusy = false }
                    }
                }
            }
        }
    if (portabilityPage.isNotEmpty()) {
        PortabilityScreen(portabilityPage, p, vm, snackbar, onClose = { portabilityPage = "" })
        return
    }
    if (dialog == "shortcuts") {
        ShortcutEditor(p.shortcuts, { dialog = "" }) { changed ->
            vm.settings { it.copy(shortcuts = changed) }
            dialog = ""
        }
        return
    }
    if (dialog == "profile") {
        ProfileEditor(data, vm) { dialog = "" }
        return
    }
    if (dialog == "units") {
        UnitsSettings(p, vm) { dialog = "" }
        return
    }
    if (dialog == "media-credits") {
        MediaCreditsScreen { dialog = "" }
        return
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(if (isSystemInDarkTheme()) Color(0xFF0D0D0D) else OF.background)
            .padding(horizontal = 18.dp)
            .padding(top = 42.dp, bottom = 112.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        SettingsSection("YOU") {
            SettingsRow(Icons.Rounded.AccountCircle, "Profile", "") {
                if (onProfile != null) onProfile() else dialog = "profile"
            }
            SettingsRow(Icons.Rounded.MonitorWeight, "Body data", "", onClick = onBodyData)
            integrationSettings()
        }
        SettingsSection("TRAINING") {
            SettingsRow(Icons.Rounded.FitnessCenter, "Workout logging", "") { dialog = "workout" }
            SettingsRow(Icons.Rounded.Timer, "Rest timer", "") { dialog = "rest" }
            SettingsRow(Icons.Rounded.Warehouse, "Gym equipment", "", onClick = onGyms)
            SettingsRow(Icons.Rounded.Search, "Exercise library", "", onClick = onExercises)
            SettingsRow(Icons.Rounded.History, "Workout history", "", onClick = onHistory)
        }
        SettingsSection("APP") {
            SettingsRow(Icons.Rounded.Straighten, "Units", "") { dialog = "units" }
            SettingsRow(Icons.Rounded.AddCircle, "Shortcuts", "") { dialog = "shortcuts" }
            SettingsRow(Icons.Rounded.Contrast, "Appearance", "") { dialog = "theme" }
        }
        SettingsSection("DATA") {
            DataPortabilityRows(onOpen = { portabilityPage = it })
            SettingsRow(
                Icons.Rounded.FileUpload,
                "Export Backup",
                if (backupBusy) "Preparing backup…" else "Includes original photos; keep the file private",
            ) {
                if (!backupBusy) export.launch("open-factor-${LocalDate.now()}.json")
            }
            SettingsRow(
                Icons.Rounded.FileDownload,
                "Restore Backup",
                "Import an Open Factor JSON backup",
            ) {
                if (!backupBusy) import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        SettingsSection("ABOUT") {
            StorageRecoveryExportRow(vm, snackbar)
            OfflineHelpRows(onOpen = { portabilityPage = it })
            SettingsRow(Icons.Rounded.PhotoLibrary, "Exercise media credits", "") {
                dialog = "media-credits"
            }
            SettingsRow(Icons.Rounded.Info, "About", "Version ${BuildConfig.VERSION_NAME}") {
                dialog = "about"
            }
            SettingsRow(Icons.Rounded.Code, "Source Code", "aarsh21/open-factor") {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/aarsh21/open-factor")))
                }.onFailure { scope.launch { snackbar.showSnackbar("Couldn’t open the repository. Try again with a browser installed.") } }
            }
            SettingsRow(
                Icons.Rounded.Description,
                "Licenses & Attribution",
                "GPLv3 · Nuvio contributors",
            ) {
                dialog = "licenses"
            }
        }
        Text(
            "Open Factor",
            Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp),
            color = OF.muted,
            fontSize = 12.sp,
        )
    }
    when (dialog) {
        "theme" ->
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Appearance") },
                text = {
                    Column(Modifier.selectableGroup()) {
                        AppTheme.entries.forEach { theme ->
                            PreferenceChoice(theme.label, p.theme == theme) {
                                vm.settings { it.copy(theme = theme) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ dialog = "" }) { Text("Done") } },
            )
        "rest" -> {
            var seconds by rememberSaveable { mutableStateOf(p.restSeconds.toString()) }
            var automatic by rememberSaveable { mutableStateOf(p.autoRest) }
            val validSeconds = seconds.toIntOrNull()?.let { it in 0..1800 } == true
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Rest Timer") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            seconds,
                            { seconds = it },
                            label = { Text("Seconds (0–1800)") },
                            singleLine = true,
                            keyboardOptions =
                                androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType =
                                        androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                        )
                        PreferenceSwitch("Start after completing a set", "", automatic) { automatic = it }
                    }
                },
                confirmButton = {
                    TextButton(
                        {
                            vm.settings {
                                it.copy(
                                    restSeconds = seconds.toInt().coerceIn(0, 1800),
                                    autoRest = automatic,
                                )
                            }
                            dialog = ""
                        },
                        enabled = validSeconds,
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = { TextButton({ dialog = "" }) { Text("Cancel") } },
            )
        }
        "workout" ->
            WorkoutSettings(p, vm) { dialog = "" }
        "about" ->
            InfoDialog(
                "Open Factor",
                "Open Factor ${BuildConfig.VERSION_NAME}\n\nWorkouts, progress photos and preferences stay on this device. Exercise demonstrations can stream over HTTPS and use a local cache. Health Connect is optional.\n\nBuilt on NuvioMobile. Back up your data before uninstalling or clearing app storage.",
            ) {
                dialog = ""
            }
        "licenses" ->
            InfoDialog(
                "Licenses & Attribution",
                "Application code: GNU General Public License v3.0, inherited from NuvioMedia/NuvioMobile.\n\nNavigation geometry and design tokens are adapted from Nuvio contributors. AndroidX, Kotlin and Material icons are used under their respective open source licenses.\n\nLocally imported reference content retains its original ownership and is not relicensed by this application. See the repository's asset provenance records.",
            ) {
                dialog = ""
            }
    }
    restoreBackup?.let { prepared ->
        ConfirmDialog(
            "Restore Backup?",
            "This replaces all training, body data, private progress photos and preferences on this device. Records absent from the backup will be removed, including photos when restoring an older backup.",
            "Restore",
            onDismiss = { restoreBackup = null },
            onConfirm = {
                val result = runCatching { vm.restore(prepared) }
                restoreBackup = null
                scope.launch {
                    snackbar.showSnackbar(
                        if (result.isSuccess) "Backup restored"
                        else "Restore failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            },
        )
    }
}

@Composable
fun WorkoutSettings(p: Preferences, vm: WorkoutViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PreferenceSwitch("Rate effort after exercise", "Ask for reps left after all sets are logged", p.rateEffortAfterExercise) { checked ->
                    vm.settings { it.copy(rateEffortAfterExercise = checked) }
                }
                PreferenceSwitch("Advance after exercise", "Move to unfinished exercises after the last set", p.exerciseAutoNext) { checked ->
                    vm.settings { it.copy(exerciseAutoNext = checked) }
                }
                PreferenceSwitch("Keep screen on", "Keep the screen on while the workout logger is visible", p.keepAlive) { checked ->
                    vm.settings { it.copy(keepAlive = checked) }
                }
                PreferenceSwitch("Workout timer", "Show elapsed time during workout sessions", p.showWorkoutTimer) { checked ->
                    vm.settings { it.copy(showWorkoutTimer = checked) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        Modifier.padding(top = 24.dp, bottom = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = if (isSystemInDarkTheme()) Color(0xFF96999F) else OF.muted,
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else OF.card)
            .border(0.5.dp, if (isSystemInDarkTheme()) Color(0xFF272727) else OF.elevated, RoundedCornerShape(14.dp)),
        content = content,
    )
}

@Composable
internal fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (isSystemInDarkTheme()) Color(0xFF343434) else OF.elevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
        }
        HorizontalDivider(
            Modifier.padding(start = 71.dp),
            thickness = 0.5.dp,
            color = if (isSystemInDarkTheme()) Color(0xFF252525) else OF.elevated,
        )
    }
}

@Composable
internal fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )

@Composable
internal fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title)
        }
        Switch(checked, null)
    }
}

@Composable
private fun PreferenceChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, null)
        Text(label)
    }
}

@Composable
private fun UnitsSettings(p: Preferences, vm: WorkoutViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(OF.background)) {
        PageHeading("Units", onClose)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 112.dp)) {
            SettingsSection("WEIGHT UNITS") {
                Column(Modifier.selectableGroup()) {
                    PreferenceChoice("Kilograms (kg)", !p.pounds) { vm.settings { it.copy(pounds = false) } }
                    PreferenceChoice("Pounds (lb)", p.pounds) { vm.settings { it.copy(pounds = true) } }
                }
            }
            SettingsSection("HEIGHT UNITS") {
                Column(Modifier.selectableGroup()) {
                    PreferenceChoice("Centimeters", !p.heightInFeet) { vm.settings { it.copy(heightInFeet = false) } }
                    PreferenceChoice("Feet and Inches", p.heightInFeet) { vm.settings { it.copy(heightInFeet = true) } }
                }
                Text("Applies to profile height. Body circumferences remain in centimeters.",
                    Modifier.padding(16.dp), color = OF.muted)
            }
            SettingsSection("CLOCK UNITS") {
                Column(Modifier.selectableGroup()) {
                    PreferenceChoice("12 Hour", !p.clock24Hour) { vm.settings { it.copy(clock24Hour = false) } }
                    PreferenceChoice("24 Hour", p.clock24Hour) { vm.settings { it.copy(clock24Hour = true) } }
                }
                Text("Workout start times use this format.", Modifier.padding(16.dp), color = OF.muted)
            }
            SettingsSection("DISTANCE") {
                Column(Modifier.selectableGroup()) {
                    PreferenceChoice("Kilometers and Meters", !p.distanceImperial) { vm.settings { it.copy(distanceImperial = false) } }
                    PreferenceChoice("Miles and Yards", p.distanceImperial) { vm.settings { it.copy(distanceImperial = true) } }
                }
            }
        }
    }
}
