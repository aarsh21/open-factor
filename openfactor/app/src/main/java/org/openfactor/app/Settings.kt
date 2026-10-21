package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    p: Preferences,
    vm: WorkoutViewModel,
    snackbar: SnackbarHostState,
    onExercises: () -> Unit,
    onHistory: () -> Unit,
    onGyms: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf("") }
    var restoreText by remember { mutableStateOf<String?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                val result = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(vm.export().toByteArray())
                    } ?: error("Could not open file")
                }
                scope.launch {
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
                val result = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        it.readText().also { s ->
                            require(s.length <= 10_000_000) { "Backup is too large" }
                            DataCodec.decode(s)
                        }
                    } ?: error("Could not read backup")
                }
                result
                    .onSuccess { restoreText = it }
                    .onFailure {
                        scope.launch {
                            snackbar.showSnackbar("Could not read backup: ${it.message}")
                        }
                    }
            }
        }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 18.dp)
            .padding(top = 42.dp, bottom = 28.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        SettingsSection("PROFILE") {
            SettingsRow(Icons.Rounded.AccountCircle, "Profile", p.displayName) {
                dialog = "profile"
            }
        }
        SettingsSection("GENERAL") {
            SettingsRow(
                Icons.Rounded.Straighten,
                "Units",
                if (p.pounds) "Pounds (lb)" else "Kilograms (kg)",
            ) {
                dialog = "units"
            }
            SettingsRow(
                Icons.Rounded.Timer,
                "Rest Timer",
                "${p.restSeconds/60} min ${p.restSeconds%60} sec · ${if(p.autoRest)"Automatic" else "Manual"}",
            ) {
                dialog = "rest"
            }
            SettingsRow(Icons.Rounded.FitnessCenter, "Workout", "Set logging and reps in reserve") {
                dialog = "workout"
            }
            SettingsRow(
                Icons.Rounded.Search,
                "Exercises",
                "Browse the exercise library",
                onClick = onExercises,
            )
        }
        SettingsSection("GYM PROFILES") {
            SettingsRow(
                Icons.Rounded.Warehouse,
                "Gym Profiles",
                "Equipment and weight selections",
                onClick = onGyms,
            )
        }
        SettingsSection("YOUR DATA") {
            SettingsRow(
                Icons.Rounded.History,
                "Workout History",
                "Review your training",
                onClick = onHistory,
            )
            SettingsRow(
                Icons.Rounded.FileUpload,
                "Export Backup",
                "Save workouts, templates and preferences",
            ) {
                export.launch("open-factor-${LocalDate.now()}.json")
            }
            SettingsRow(
                Icons.Rounded.FileDownload,
                "Restore Backup",
                "Import an Open Factor JSON backup",
            ) {
                import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
        SettingsSection("OPEN FACTOR") {
            SettingsRow(Icons.Rounded.Info, "About", "Version ${BuildConfig.VERSION_NAME}") {
                dialog = "about"
            }
            SettingsRow(Icons.Rounded.Code, "Source Code", "aarsh21/open-factor") {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/aarsh21/open-factor"),
                    )
                )
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
        "profile" ->
            NameDialog(
                "Profile Name",
                p.displayName,
                { dialog = "" },
                {
                    vm.settings { p -> p.copy(displayName = it) }
                    dialog = ""
                },
            )
        "units" ->
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Weight Units") },
                text = {
                    Column {
                        listOf(false to "Kilograms (kg)", true to "Pounds (lb)").forEach {
                            (pounds, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.settings { it.copy(pounds = pounds) }
                                    dialog = ""
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    p.pounds == pounds,
                                    {
                                        vm.settings { it.copy(pounds = pounds) }
                                        dialog = ""
                                    },
                                )
                                Text(label)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ dialog = "" }) { Text("Done") } },
            )
        "rest" -> {
            var seconds by remember { mutableStateOf(p.restSeconds.toString()) }
            var automatic by remember { mutableStateOf(p.autoRest) }
            val validSeconds = seconds.toIntOrNull()?.let { it in 0..1800 } == true
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Rest Timer") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Start after completing a set", Modifier.weight(1f))
                            Switch(automatic, { automatic = it })
                        }
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
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Workout") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reps in reserve")
                                Text(
                                    "Show RIR when logging sets",
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Switch(
                                p.showRir,
                                { checked -> vm.settings { it.copy(showRir = checked) } },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Exercise Auto-Next")
                                Text(
                                    "Move to unfinished exercises after the last set",
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                )
                            }
                            Switch(
                                p.exerciseAutoNext,
                                { checked -> vm.settings { it.copy(exerciseAutoNext = checked) } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton({ dialog = "" }) { Text("Done") } },
            )
        "about" ->
            InfoDialog(
                "Open Factor",
                "An open source workout tracker built from the NuvioMobile fork. Your workouts and preferences are stored on this device.\n\nThis development build implements the training foundation. Source-app feature parity is still being verified.",
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
    restoreText?.let { text ->
        ConfirmDialog(
            "Restore Backup?",
            "This replaces the workouts, templates and preferences currently stored on this device.",
            "Restore",
            onDismiss = { restoreText = null },
            onConfirm = {
                val result = runCatching { vm.restore(text) }
                restoreText = null
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
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        Modifier.padding(top = 24.dp, bottom = 12.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = Color(0xFF96999F),
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A))
            .border(0.5.dp, Color(0xFF272727), RoundedCornerShape(14.dp)),
        content = content,
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF343434)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp)
                Text(subtitle, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF96999F))
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 71.dp),
            thickness = 0.5.dp,
            color = Color(0xFF252525),
        )
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
