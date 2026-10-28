package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StorageRecoveryAlert(vm: WorkoutViewModel) {
    val recovery by vm.storageRecovery.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use(vm::exportStorageRecovery) ?: error("Could not open file") }
            }
            busy = false
            if (result.isSuccess) vm.dismissStorageRecovery() else error = "Could not export. Choose another location and try again."
        }
    }
    val state = recovery ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Saved data needs attention") },
        text = {
            Text(error ?: if (state.restoredPreviousData)
                "An earlier saved snapshot was recovered. Recent changes may be missing. The unreadable original is preserved. Export it for safekeeping, or restore your latest backup in Settings."
                else "Your saved data could not be read. The original is preserved. Export it for safekeeping, then restore a backup in Settings. Recovery exports remain available there.")
        },
        confirmButton = { TextButton({ export.launch("open-factor-storage-recovery.zip") }, enabled = !busy) { Text(if (busy) "Exporting…" else "Export original") } },
        dismissButton = { TextButton(vm::dismissStorageRecovery, enabled = !busy) { Text("Continue") } },
    )
}

@Composable
fun StorageRecoveryExportRow(vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    if (!vm.hasStorageRecovery) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use(vm::exportStorageRecovery) ?: error("Could not open file") }
            }
            busy = false
            snackbar.showSnackbar(if (result.isSuccess) "Recovery originals exported" else "Could not export. Choose another location and try again.")
        }
    }
    SettingsRow(Icons.Rounded.FileDownload, "Export recovery originals", "") {
        if (!busy) export.launch("open-factor-storage-recovery.zip")
    }
}
