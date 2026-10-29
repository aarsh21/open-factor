package org.openfactor.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
                    if (record.notes.isNotBlank()) Text(record.notes, color = OF.muted, maxLines = 3)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var notes by rememberSaveable { mutableStateOf(record?.notes ?: "") }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var captureReady by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PhotoPosition?>(null) }
    var photoMenu by remember { mutableIntStateOf(-1) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var proposedDate by rememberSaveable { mutableStateOf(date) }
    val scope = rememberCoroutineScope()
    val visibility = vm.data.collectAsState().value.preferences.dataVisibility
    val visiblePositions = PhotoPosition.entries.filter { visibility.isVisible(it.key + "Photo") }
    fun leave() {
        pendingCapture?.let { vm.photoStore.discardCapture(it) }
        pendingCapture = null
        captureReady = false
        vm.cleanPhotos()
        onBack()
    }
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
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val name = pendingCapture
        if (name != null) {
            if (!captured) {
                vm.photoStore.discardCapture(name)
                pendingCapture = null
                captureReady = false
            } else captureReady = true
        }
    }
    // The ready result and its stage filename survive recreation. Keep the source until the
    // reference reaches saveable editor state; cancelling this coroutine must not consume it.
    LaunchedEffect(pendingCapture, captureReady) {
        val name = pendingCapture ?: return@LaunchedEffect
        if (!captureReady) return@LaunchedEffect
        busy = true
        try {
            val reference = withContext(Dispatchers.IO) { vm.photoStore.importCapture(name) }
            references = references.mapIndexed { index, previous -> if (index == selected) reference else previous }
            vm.cleanPhotos(references.filter(String::isNotEmpty))
            vm.photoStore.discardCapture(name)
            pendingCapture = null
            captureReady = false
            error = ""
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            vm.photoStore.discardCapture(name)
            pendingCapture = null
            captureReady = false
            error = failure.message ?: "Could not save camera photo. Try again or choose from your library."
        } finally { busy = false }
    }
    fun takePhoto(index: Int) {
        if (busy || pendingCapture != null) return
        selected = index
        runCatching {
            val name = vm.photoStore.createCapture()
            captureReady = false
            pendingCapture = name
            camera.launch(vm.photoStore.captureUri(name))
        }.onFailure {
            pendingCapture?.let(vm.photoStore::discardCapture)
            pendingCapture = null
            captureReady = false
            error = "No camera is available. Choose a photo from your library."
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!busy && pendingCapture == null) leave() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.card,
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 4.dp).background(OF.line, RoundedCornerShape(2.dp)))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton({ leave() }, enabled = !busy && pendingCapture == null) { Icon(Icons.Rounded.Close, "Close photos") }
                TextButton(
                    {
                        proposedDate = date
                        choosingDate = true
                    },
                    Modifier.weight(1f).semantics { contentDescription = "Change photo date" },
                    enabled = !busy && pendingCapture == null,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                LocalDate.parse(date)
                                    .format(DateTimeFormatter.ofPattern("dd/MM/uuuu")),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                        Text(
                            "Progress Photos",
                            color = OF.muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (record != null)
                    IconButton({ deleting = true }, enabled = !busy && pendingCapture == null) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete photo record")
                    }
                else Spacer(Modifier.width(48.dp))
            }
            HorizontalDivider(color = OF.line)
            Column(
                Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visiblePositions.forEach { position ->
                        // Keep the full draft indexed by pose, not by visible slot order.
                        val index = position.ordinal
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
                            val emptyOutline = OF.muted
                            Box(
                                Modifier.fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OF.elevated)
                                    .drawBehind {
                                        if (reference.isEmpty())
                                            drawRoundRect(
                                                color = emptyOutline,
                                                cornerRadius = CornerRadius(10.dp.toPx()),
                                                style =
                                                    Stroke(
                                                        width = 2.dp.toPx(),
                                                        pathEffect =
                                                            PathEffect.dashPathEffect(
                                                                floatArrayOf(
                                                                    6.dp.toPx(),
                                                                    4.dp.toPx(),
                                                                )
                                                            ),
                                                    ),
                                            )
                                    }
                                    .clickable(enabled = !busy && pendingCapture == null) {
                                        if (reference.isEmpty()) takePhoto(index) else viewing = position
                                    }
                                    .semantics {
                                        contentDescription =
                                            if (reference.isEmpty()) "Add ${position.label} photo"
                                            else "View ${position.label} photo"
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (reference.isEmpty())
                                    Icon(
                                        Icons.Rounded.PersonOutline,
                                        null,
                                        Modifier.size(40.dp),
                                        tint = OF.muted,
                                    )
                                else
                                    PhotoImage(
                                        vm.photoStore,
                                        reference,
                                        null,
                                        Modifier.fillMaxSize(),
                                    )
                            }
                            Row {
                                IconButton({ takePhoto(index) }, enabled = !busy && pendingCapture == null) {
                                    Icon(Icons.Rounded.PhotoCamera, "Take ${position.label} photo")
                                }
                                Box {
                                    IconButton({ photoMenu = index }, enabled = !busy && pendingCapture == null) {
                                        Icon(Icons.Rounded.MoreHoriz, "${position.label} photo options")
                                    }
                                    DropdownMenu(photoMenu == index, { photoMenu = -1 }) {
                                        DropdownMenuItem(
                                            text = { Text(if (reference.isEmpty()) "Choose from library" else "Replace from library") },
                                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                                            onClick = { photoMenu = -1; attach() },
                                        )
                                        if (reference.isNotEmpty()) DropdownMenuItem(
                                            text = { Text("Remove photo") },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                            onClick = {
                                                photoMenu = -1
                                                references = references.mapIndexed { i, value -> if (i == index) "" else value }
                                                error = ""
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (visiblePositions.isNotEmpty())
                        repeat(PhotoPosition.entries.size - visiblePositions.size) {
                            Spacer(Modifier.weight(1f))
                        }
                }
                if (visiblePositions.size < PhotoPosition.entries.size)
                    Text(
                        if (visiblePositions.isEmpty())
                            "All photo slots are hidden in Data Visibility. Existing photos stay saved."
                        else "Hidden photo slots stay saved. Change their visibility in Settings.",
                        color = OF.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                OutlinedTextField(
                    notes, { if (it.length <= 4000) notes = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Notes") },
                    minLines = 2, maxLines = 4, enabled = !busy && pendingCapture == null,
                )
                if (busy)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Copying photo…")
                    }
                Text(
                    "Private originals · backups include image metadata",
                    color = OF.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BodySaveFooter(error, enabled = !busy && pendingCapture == null) {
                runCatching {
                        val photos =
                            PhotoPosition.entries
                                .mapIndexedNotNull { index, position ->
                                    references[index]
                                        .takeIf { it.isNotEmpty() }
                                        ?.let { position to it }
                                }
                                .toMap()
                        vm.saveProgressPhotos(
                            ProgressPhotoRecord(id, LocalDate.parse(date), photos, notes.trim())
                        )
                        onSaved()
                    }
                    .onFailure { error = it.message ?: "Could not save photos" }
            }
        }
    }
    if (choosingDate)
        AlertDialog(
            onDismissRequest = { choosingDate = false },
            title = { Text("Record date") },
            text = { BodyDateField(proposedDate) { proposedDate = it } },
            confirmButton = {
                TextButton(
                    {
                        date = proposedDate
                        choosingDate = false
                        error = ""
                    },
                    enabled = runCatching { LocalDate.parse(proposedDate) }.isSuccess,
                ) {
                    Text("Apply date")
                }
            },
            dismissButton = { TextButton({ choosingDate = false }) { Text("Cancel") } },
        )
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
