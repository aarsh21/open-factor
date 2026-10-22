package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun BodyDataScreen(
    data: AppData,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    onWeight: (String) -> Unit,
    onMeasurements: (String) -> Unit,
    onPhotos: (String) -> Unit,
    photoStore: ProgressPhotoStore,
    onCharts: () -> Unit,
) {
    val measurements = tab == 1
    Column(Modifier.fillMaxSize()) {
        PageHeading("Body Data", onBack)
        TabRow(tab) {
            listOf("Scale Weight", "Measurements", "Photos").forEachIndexed { index, title ->
                Tab(tab == index, { onTab(index) }, text = { Text(title) })
            }
        }
        if (tab == 2) {
            ProgressPhotoHistory(data.progressPhotos, photoStore, onPhotos)
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PrimaryAction(
                    if (measurements) "Log Measurements" else "Log Weight",
                    { if (measurements) onMeasurements("") else onWeight("") },
                    icon = Icons.Rounded.Add,
                )
            }
            item {
                Text(
                    "Saved on this device · included in JSON backups",
                    color = OF.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                OutlinedButton(onCharts, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ShowChart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Charts")
                }
            }
            if (measurements) {
                if (data.bodyMeasurements.isEmpty())
                    item {
                        EmptyContent(
                            Icons.Rounded.Straighten,
                            "No measurements yet",
                            "Log any of the full-body measurements in centimeters. Tap a saved record to edit or delete it.",
                        )
                    }
                items(data.bodyMeasurements.sortedByDescending { it.date }, key = { it.id }) {
                    record ->
                    Surface(shape = OF.shape, color = OF.card) {
                        ListItem(
                            headlineContent = { Text(record.date.toString()) },
                            supportingContent = {
                                Column {
                                    BodyMeasurement.entries
                                        .filter { it in record.centimeters }
                                        .take(3)
                                        .forEach {
                                            Text(
                                                "${it.label}: ${record.centimeters.getValue(it).bodyNumber()} cm"
                                            )
                                        }
                                    if (record.centimeters.size > 3)
                                        Text("+${record.centimeters.size - 3} more measurements")
                                }
                            },
                            trailingContent = {
                                Icon(Icons.Rounded.Edit, "Edit measurements ${record.date}")
                            },
                            modifier = Modifier.clickable { onMeasurements(record.id) },
                            colors = ListItemDefaults.colors(containerColor = OF.card),
                        )
                    }
                }
            } else {
                if (data.scaleWeights.isEmpty())
                    item {
                        EmptyContent(
                            Icons.Rounded.MonitorWeight,
                            "No weigh-ins yet",
                            "Log your scale weight and optional body fat. Tap a saved record to edit or delete it.",
                        )
                    }
                items(data.scaleWeights.sortedByDescending { it.date }, key = { it.id }) { record ->
                    Surface(shape = OF.shape, color = OF.card) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${record.weightKg.bodyWeightNumber(data.preferences.pounds)} ${if (data.preferences.pounds) "lb" else "kg"}"
                                )
                            },
                            supportingContent = {
                                Text(
                                    record.date.toString() +
                                        (record.bodyFatPercent?.let {
                                            " · Body fat ${it.bodyNumber()}%"
                                        } ?: "")
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Rounded.Edit, "Edit weight ${record.date}")
                            },
                            modifier = Modifier.clickable { onWeight(record.id) },
                            colors = ListItemDefaults.colors(containerColor = OF.card),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScaleWeightEditor(
    record: ScaleWeightRecord?,
    pounds: Boolean,
    onBack: () -> Unit,
    onSave: (ScaleWeightRecord) -> Unit,
    onDelete: () -> Unit,
) {
    val id = rememberSaveable { record?.id ?: newId() }
    var date by rememberSaveable { mutableStateOf((record?.date ?: LocalDate.now()).toString()) }
    var inputPounds by rememberSaveable { mutableStateOf(pounds) }
    var weight by rememberSaveable {
        mutableStateOf(record?.weightKg?.bodyWeightNumber(inputPounds, 6) ?: "")
    }
    var fat by rememberSaveable { mutableStateOf(record?.bodyFatPercent?.bodyNumber(6) ?: "") }
    var error by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    val parsedWeight = weight.bodyDecimal()?.let { if (inputPounds) it / 2.2046226218 else it }
    val weightValid = parsedWeight != null && parsedWeight > 0 && parsedWeight <= 5000
    val fatValid = fat.isBlank() || fat.bodyDecimal()?.let { it in 0.0..100.0 } == true
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading(
            "Scale Weight",
            onBack,
            actions = {
                if (record != null)
                    IconButton({ deleting = true }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete weight")
                    }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BodyDateField(
                date,
                {
                    date = it
                    error = ""
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weight unit", Modifier.weight(1f))
                listOf(false to "kg", true to "lb").forEach { (usePounds, label) ->
                    FilterChip(
                        inputPounds == usePounds,
                        {
                            if (inputPounds != usePounds) {
                                if (parsedWeight != null)
                                    weight = parsedWeight.bodyWeightNumber(usePounds, 6)
                                inputPounds = usePounds
                                error = ""
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
            BodyNumberField(
                weight,
                {
                    weight = it
                    error = ""
                },
                "Weight",
                if (inputPounds) "lb" else "kg",
                weight.isNotBlank() && !weightValid,
                "Enter a positive weight (at most 5000 kg).",
            )
            BodyNumberField(
                fat,
                {
                    fat = it
                    error = ""
                },
                "Body Fat (optional)",
                "%",
                !fatValid,
                "Enter 0–100%, or leave blank.",
            )
            Text(
                "Body fat is a manually entered percentage. No estimate is calculated.",
                color = OF.muted,
            )
        }
        BodySaveFooter(error) {
            runCatching {
                    require(weightValid) { "Enter a positive weight (at most 5000 kg)." }
                    require(fatValid) { "Body fat must be 0–100%, or blank." }
                    // Preserve the exact stored value when an existing entry is saved unchanged.
                    val kg =
                        if (
                            record != null &&
                                weight == record.weightKg.bodyWeightNumber(inputPounds, 6)
                        )
                            record.weightKg
                        else requireNotNull(parsedWeight)
                    val percent =
                        if (fat.isBlank()) null
                        else if (
                            record?.bodyFatPercent != null &&
                                fat == record.bodyFatPercent.bodyNumber(6)
                        )
                            record.bodyFatPercent
                        else fat.bodyDecimal()
                    onSave(ScaleWeightRecord(id, LocalDate.parse(date), kg, percent))
                }
                .onFailure { error = it.message ?: "Could not save weight" }
        }
    }
    if (deleting)
        ConfirmDialog(
            "Delete weigh-in?",
            "This removes only this dated weight and its body fat value.",
            "Delete",
            { deleting = false },
            {
                runCatching(onDelete).onFailure { error = it.message ?: "Could not delete weight" }
                deleting = false
            },
        )
}

@Composable
fun BodyMeasurementEditor(
    record: BodyMeasurementRecord?,
    onBack: () -> Unit,
    onSave: (BodyMeasurementRecord) -> Unit,
    onDelete: () -> Unit,
) {
    val id = rememberSaveable { record?.id ?: newId() }
    var date by rememberSaveable { mutableStateOf((record?.date ?: LocalDate.now()).toString()) }
    var values by rememberSaveable {
        mutableStateOf(
            BodyMeasurement.entries.map { record?.centimeters?.get(it)?.bodyNumber(6) ?: "" }
        )
    }
    var error by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading(
            "Full Body",
            onBack,
            actions = {
                if (record != null)
                    IconButton({ deleting = true }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete measurements")
                    }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BodyDateField(
                    date,
                    {
                        date = it
                        error = ""
                    },
                )
            }
            item {
                Text(
                    "All fields are optional; enter at least one measurement. Values are stored in centimeters.",
                    color = OF.muted,
                )
            }
            BodyMeasurement.entries.forEachIndexed { index, field ->
                if (index == 0 || BodyMeasurement.entries[index - 1].group != field.group)
                    item { SectionTitle(field.group) }
                item(key = field.key) {
                    val text = values[index]
                    val valid =
                        text.isBlank() || text.bodyDecimal()?.let { it > 0 && it <= 1000 } == true
                    BodyNumberField(
                        text,
                        { next ->
                            values =
                                values.mapIndexed { i, value -> if (i == index) next else value }
                            error = ""
                        },
                        field.label,
                        "cm",
                        !valid,
                        "Enter more than 0 and at most 1000 cm, or leave blank.",
                    )
                }
            }
        }
        BodySaveFooter(error) {
            runCatching {
                    val parsed =
                        BodyMeasurement.entries
                            .mapIndexedNotNull { index, field ->
                                val text = values[index]
                                if (text.isBlank()) null
                                else {
                                    val original = record?.centimeters?.get(field)
                                    val number =
                                        if (original != null && text == original.bodyNumber(6))
                                            original
                                        else text.bodyDecimal()
                                    require(number != null && number > 0 && number <= 1000) {
                                        "${field.label}: enter more than 0 and at most 1000 cm."
                                    }
                                    field to number
                                }
                            }
                            .toMap()
                    val entry = BodyMeasurementRecord(id, LocalDate.parse(date), parsed)
                    entry.validate()
                    onSave(entry)
                }
                .onFailure { error = it.message ?: "Could not save measurements" }
        }
    }
    if (deleting)
        ConfirmDialog(
            "Delete measurements?",
            "This removes only this dated full-body record.",
            "Delete",
            { deleting = false },
            {
                runCatching(onDelete).onFailure {
                    error = it.message ?: "Could not delete measurements"
                }
                deleting = false
            },
        )
}

@Composable
private fun BodyNumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    unit: String,
    invalid: Boolean,
    message: String,
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(message) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyDateField(value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text("Date (YYYY-MM-DD)") },
        singleLine = true,
        isError = parsed == null,
        supportingText =
            if (parsed == null) ({ Text("Enter a valid date, for example 2026-10-22.") }) else null,
        trailingIcon = {
            IconButton({ picking = true }) { Icon(Icons.Rounded.CalendarMonth, "Choose date") }
        },
    )
    if (picking) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    parsed?.takeIf { it.year in 1900..2100 }?.toEpochDay()?.times(86_400_000)
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let {
                        onChange(LocalDate.ofEpochDay(it / 86_400_000).toString())
                    }
                    picking = false
                }) {
                    Text("Set date")
                }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state)
        }
    }
}

@Composable
internal fun BodySaveFooter(error: String, enabled: Boolean = true, save: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        PrimaryAction("Save", save, enabled = enabled)
    }
}
