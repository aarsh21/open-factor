package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProgressPhotoHistory(
    records: List<ProgressPhotoRecord>,
    store: ProgressPhotoStore,
    onEdit: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PrimaryAction("Log Photos", { onEdit("") }, icon = Icons.Rounded.Add) }
        item {
            Text(
                "Private on this device · original images included in JSON backups",
                color = OF.muted,
            )
        }
        if (records.isEmpty())
            item {
                EmptyContent(
                    Icons.Rounded.PhotoLibrary,
                    "No progress photos yet",
                    "Add a dated Front, Side or Back photo. Choose files stored on this device to stay offline.",
                )
            }
        items(records.sortedByDescending { it.date }, key = { it.id }) { record ->
            Surface(
                shape = OF.shape,
                color = OF.card,
                modifier =
                    Modifier.clickable { onEdit(record.id) }
                        .semantics { contentDescription = "Edit photos ${record.date}" },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.date.toString(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Icon(Icons.Rounded.Edit, null)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoPosition.entries.forEach { position ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(position.label, style = MaterialTheme.typography.labelLarge)
                                val reference = record.photos[position]
                                if (reference != null)
                                    PhotoImage(
                                        store,
                                        reference,
                                        position.label,
                                        Modifier.fillMaxWidth().aspectRatio(.75f),
                                    )
                                else
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(.75f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Not added",
                                            color = OF.muted,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressPhotoEditor(
    record: ProgressPhotoRecord?,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val id = rememberSaveable { record?.id ?: newId() }
    var date by rememberSaveable { mutableStateOf((record?.date ?: LocalDate.now()).toString()) }
    var references by rememberSaveable {
        mutableStateOf(PhotoPosition.entries.map { record?.photos?.get(it) ?: "" })
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoPosition?>(null) }
    val scope = rememberCoroutineScope()
    fun leave() {
        vm.cleanPhotos()
        onBack()
    }
    BackHandler { leave() }
    LaunchedEffect(id) { vm.cleanPhotos(references.filter { it.isNotEmpty() }) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                busy = true
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) { runCatching { vm.photoStore.attach(uri) } }
                    result
                        .onSuccess { reference ->
                            references =
                                references.mapIndexed { index, previous ->
                                    if (index == selected) reference else previous
                                }
                            vm.cleanPhotos(references.filter { it.isNotEmpty() })
                            error = ""
                        }
                        .onFailure { error = it.message ?: "Could not attach photo" }
                    busy = false
                }
            }
        }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading(
            "Progress Photos",
            { leave() },
            actions = {
                if (record != null)
                    IconButton({ deleting = true }, enabled = !busy) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete photo record")
                    }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BodyDateField(date) {
                date = it
                error = ""
            }
            Text(
                "Add at least one view. Originals are copied privately; removing a slot takes effect when you save.",
                color = OF.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoPosition.entries.forEachIndexed { index, position ->
                    val reference = references[index]
                    fun attach() {
                        selected = index
                        picker.launch(arrayOf("image/*"))
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(position.label, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth()
                                .aspectRatio(.75f)
                                .background(OF.card, OF.shape)
                                .clickable(enabled = !busy) {
                                    if (reference.isEmpty()) attach() else viewing = position
                                }
                                .semantics {
                                    contentDescription =
                                        if (reference.isEmpty()) "Add ${position.label} photo"
                                        else "View ${position.label} photo"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (reference.isEmpty())
                                Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(32.dp))
                            else PhotoImage(vm.photoStore, reference, null, Modifier.fillMaxSize())
                        }
                        TextButton(
                            { attach() },
                            enabled = !busy,
                            modifier =
                                Modifier.semantics {
                                    contentDescription =
                                        "${if (reference.isEmpty()) "Add" else "Replace"} ${position.label} photo"
                                },
                        ) {
                            Text(if (reference.isEmpty()) "Add" else "Replace")
                        }
                        if (reference.isNotEmpty())
                            IconButton(
                                {
                                    references =
                                        references.mapIndexed { i, value ->
                                            if (i == index) "" else value
                                        }
                                    error = ""
                                },
                                enabled = !busy,
                            ) {
                                Icon(Icons.Rounded.Close, "Remove ${position.label} photo")
                            }
                    }
                }
            }
            if (busy)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Copying photo…")
                }
            Text(
                "Choose an image up to 20 MiB / 100 megapixels. The back arrow or system Back discards unsaved changes. Backups include original image metadata; keep them private.",
                color = OF.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BodySaveFooter(error, enabled = !busy) {
            runCatching {
                    val photos =
                        PhotoPosition.entries
                            .mapIndexedNotNull { index, position ->
                                references[index].takeIf { it.isNotEmpty() }?.let { position to it }
                            }
                            .toMap()
                    vm.saveProgressPhotos(ProgressPhotoRecord(id, LocalDate.parse(date), photos))
                    onSaved()
                }
                .onFailure { error = it.message ?: "Could not save photos" }
        }
    }
    if (deleting)
        ConfirmDialog(
            "Delete progress photos?",
            "This removes this dated record and any private images not used by another saved record. Your original files are unchanged.",
            "Delete",
            { deleting = false },
            {
                runCatching {
                        vm.deleteProgressPhotos(id)
                        onSaved()
                    }
                    .onFailure { error = it.message ?: "Could not delete photos" }
                deleting = false
            },
        )
    viewing?.let { position ->
        Dialog(
            onDismissRequest = { viewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = OF.background) {
                Column(Modifier.safeDrawingPadding()) {
                    PageHeading("${position.label} · $date", { viewing = null })
                    PhotoImage(
                        vm.photoStore,
                        references[position.ordinal],
                        "${position.label} photo",
                        Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoImage(
    store: ProgressPhotoStore,
    reference: String,
    label: String?,
    modifier: Modifier,
) {
    var failed by remember(reference) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            store.file(reference),
            label,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            onSuccess = { failed = false },
        )
        if (failed)
            Text(
                "Photo unavailable\nReattach the original",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
    }
}
