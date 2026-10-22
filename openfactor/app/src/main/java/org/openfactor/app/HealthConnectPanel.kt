package org.openfactor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthConnectSettingsRow(onClick: () -> Unit) {
    SettingsRow(Icons.Rounded.HealthAndSafety, "Health Connect", "Optional device-local transfers", onClick = onClick)
}

/** Opening this panel only checks provider availability. Every transfer requires its own tap. */
@Composable
fun HealthConnectPanel(
    data: AppData,
    onImportWeights: (List<ScaleWeightRecord>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val transfers = remember(context) { HealthConnectTransfers(context.applicationContext) }
    val versions = remember(context) { HealthConnectVersions(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var availability by remember { mutableStateOf(healthConnectAvailability(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("No transfer has run.") }
    var permissionAction by remember { mutableStateOf<HealthConnectAction?>(null) }
    var requestedAction by rememberSaveable { mutableStateOf<String?>(null) }
    var steps by remember { mutableStateOf<List<HealthConnectStepDay>>(emptyList()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        availability = healthConnectAvailability(context)
        // Permission state is intentionally never cached as an authorization for a later transfer.
    }
    val permissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        val action = HealthConnectAction.entries.find { it.name == requestedAction }
        message = if (action != null && granted.containsAll(action.permissions))
            "Permissions allowed. Tap ${action.label} again to transfer."
        else "Permission denied or partially allowed. No transfer ran. Local use is unchanged."
        requestedAction = null
    }

    fun run(action: HealthConnectAction) {
        if (busy) return
        val today = LocalDate.now()
        val first = today.minusDays(29)
        val zone = ZoneId.systemDefault()
        fun exportRecords(version: Long) = when (action) {
            HealthConnectAction.EXPORT_SCALE -> healthConnectScaleExport(
                data.scaleWeights.filter { it.date in first..today }, zone, version,
                includeWeight = true, includeBodyFat = true,
            )
            HealthConnectAction.EXPORT_WORKOUTS -> data.history.filter { it.localDate() in first..today }
                .mapNotNull { healthConnectWorkoutExport(it, zone, version) }
            else -> emptyList()
        }
        if (action == HealthConnectAction.EXPORT_SCALE || action == HealthConnectAction.EXPORT_WORKOUTS) {
            val preview = runCatching { exportRecords(0) }
            if (preview.isFailure || preview.getOrThrow().size > 1000) {
                message = "These records cannot be exported. Check values and the 1000-record limit. No permissions were requested."
                return
            }
            if (preview.getOrThrow().isEmpty()) {
                message = "No exportable records in the last 30 days. No permissions were requested or data written."
                return
            }
        }
        busy = true
        scope.launch {
            try {
                val result: HealthConnectResult<*> = withContext(Dispatchers.IO) {
                    when (action) {
                        HealthConnectAction.IMPORT_SCALE ->
                            transfers.importScale(first, today, zone, context.packageName)
                        HealthConnectAction.READ_STEPS -> transfers.readSteps(first, today)
                        HealthConnectAction.EXPORT_SCALE, HealthConnectAction.EXPORT_WORKOUTS -> transfers.export(action) {
                            exportRecords(versions.reserve())
                        }
                    }
                }
                when (result) {
                    is HealthConnectResult.Success -> when (action) {
                        HealthConnectAction.IMPORT_SCALE -> {
                            @Suppress("UNCHECKED_CAST")
                            val records = result.value as List<ScaleWeightRecord>
                            try {
                                onImportWeights(records)
                                message = "Read ${records.size} dated weight readings. Manual dates were kept."
                            } catch (_: Exception) {
                                message = "Health Connect read finished, but local save failed. Retry the import."
                            }
                        }
                        HealthConnectAction.READ_STEPS -> {
                            @Suppress("UNCHECKED_CAST")
                            val days = result.value as List<HealthConnectStepDay>
                            steps = days
                            message = "Read steps for ${days.size} days. Results stay in this panel only."
                        }
                        else -> message = if (result.value == 0) "No records were written."
                            else "Health Connect accepted an export of ${result.value} records."
                    }
                    is HealthConnectResult.PermissionRequired -> {
                        permissionAction = action
                        message = "Permissions are required for this action. No transfer ran."
                    }
                    is HealthConnectResult.ProviderUnavailable -> {
                        availability = result.availability
                        message = "Health Connect is unavailable. No transfer ran."
                    }
                    HealthConnectResult.PermissionRevoked ->
                        message = "Permission changed during the action. No completed transfer is confirmed. Retry after reviewing access."
                    HealthConnectResult.Failed ->
                        message = "The action failed. No completed transfer is confirmed. Check Health Connect and retry."
                }
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                PageHeading("Health Connect", back = { if (!busy) onClose() })
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Optional device-local transfers", style = MaterialTheme.typography.titleLarge)
                    Text("Import weight and scale body fat, read daily steps, or export manual scale readings and finished workouts. No account or Internet access is required. Nothing runs automatically.")
                    Text("Last 30 calendar days, including today. Manual dates take priority. Imported scale readings are not exported back to Health Connect. Circumferences and visual body fat are not transferred.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when (availability) {
                        HealthConnectAvailability.AVAILABLE -> "Health Connect is available. Access is checked for each action."
                        HealthConnectAvailability.UPDATE_REQUIRED -> "Install or update Health Connect, then return here. You can keep using Open Factor without it."
                        HealthConnectAvailability.UNAVAILABLE -> "Health Connect is not supported on this device or profile. Local training, body data and backups still work."
                    })
                    HealthConnectAction.entries.forEach { action ->
                        OutlinedButton(onClick = { run(action) },
                            enabled = !busy && availability == HealthConnectAvailability.AVAILABLE,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(action.label)
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (steps.isNotEmpty()) {
                        Text("Daily steps", style = MaterialTheme.typography.titleLarge)
                        Text("Health Connect's daily totals avoid double-counting overlapping sources. They are not saved in Open Factor backups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        steps.sortedByDescending { it.date }.forEach { day ->
                            Text("${day.date} · ${day.count} steps")
                        }
                    }
                    Text("Remove access in Android's Health Connect settings. Removing access does not delete existing records in either app. Exports update this app's stable record IDs; deleting local data does not delete a prior export.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    permissionAction?.let { action ->
        AlertDialog(
            onDismissRequest = { permissionAction = null },
            title = { Text("Allow ${action.label.lowercase()}?") },
            text = { Text("Android will ask only for this action's categories and direction. A partial denial stops this action. Allowing access does not start a transfer; tap the action again when you return.") },
            confirmButton = { TextButton(onClick = {
                permissionAction = null
                requestedAction = action.name
                try {
                    permissions.launch(action.permissions)
                } catch (_: android.content.ActivityNotFoundException) {
                    requestedAction = null
                    availability = healthConnectAvailability(context)
                    message = "The Health Connect permission screen is unavailable. No transfer ran."
                }
            }) { Text("Review permissions") } },
            dismissButton = { TextButton(onClick = { permissionAction = null }) { Text("Not now") } },
        )
    }
}

/** Health Connect opens this rationale on Android 13 and below, or through the Android 14 alias. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenFactorTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding()) {
                        PageHeading("Health Connect privacy", back = { finish() })
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Health Connect is optional. Open Factor works without health permissions, an account, a provider or Internet access.")
                            Text("Only actions you choose in Health Connect settings transfer data: read weight and scale body fat into local body records, display daily steps, or write manual scale readings and finished workouts to your device's Health Connect store.")
                            Text("Open Factor has no Internet permission and does not upload health data. Other apps with Health Connect access follow their own privacy policies. Local imports are included in Open Factor backups and selected scale-weight exports, so share those files only with people you trust.")
                            Text("You can deny or revoke any grant in Android's Health Connect settings. Revoking access does not erase saved body records or prior exports. Open Factor never schedules health transfers and does not delete Health Connect records.")
                        }
                    }
                }
            }
        }
    }
}
