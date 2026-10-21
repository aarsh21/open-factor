package org.openfactor.app

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

data class GymProfile(
    val id: String = newId(),
    val name: String,
    val icon: String = "gym",
    val equipmentIds: Set<String> = emptySet(),
    val weightsKg: Map<String, List<Double>> = emptyMap(),
)

data class EquipmentItem(
    val id: String,
    val name: String,
    val category: String,
    val selectionKg: List<Double> = emptyList(),
    val selectionLb: List<Double> = emptyList(),
    val presets: Set<String> = emptySet(),
)

object GymCodec {
    fun encode(g: GymProfile) =
        JSONObject()
            .put("id", g.id)
            .put("name", g.name)
            .put("icon", g.icon)
            .put("equipmentIds", JSONArray(g.equipmentIds.toList()))
            .put(
                "weightsKg",
                JSONObject().also { j -> g.weightsKg.forEach { (k, v) -> j.put(k, JSONArray(v)) } },
            )

    fun decode(j: JSONObject): GymProfile {
        val ids = j.optJSONArray("equipmentIds") ?: JSONArray()
        val weights = j.optJSONObject("weightsKg") ?: JSONObject()
        return GymProfile(
            j.getString("id"),
            j.getString("name"),
            j.optString("icon", "gym"),
            (0 until ids.length()).map { ids.getString(it) }.toSet(),
            weights.keys().asSequence().associateWith { k ->
                val a = weights.getJSONArray(k)
                (0 until a.length())
                    .map { a.getDouble(it) }
                    .also { values ->
                        require(values.all { it.isFinite() && it in 0.0..5000.0 }) {
                            "Invalid equipment weights"
                        }
                    }
            },
        )
    }
}

/**
 * Literal equipment entities/preset flags. Null and -1 source masses aren't manufactured defaults.
 */
class EquipmentCatalog(context: Context) {
    private val source =
        runCatching {
                context.assets.open("reference/source-catalog.json").bufferedReader().use {
                    JSONObject(it.readText())
                }
            }
            .getOrNull()
    private val index = source?.optJSONObject("uuidIndex") ?: JSONObject()
    val items: List<EquipmentItem> =
        index
            .keys()
            .asSequence()
            .mapNotNull { id ->
                val item = index.getJSONObject(id)
                if (item.optString("type") != "equipment") null
                else
                    EquipmentItem(
                        id,
                        item.getString("name"),
                        index.optJSONObject(item.optString("category"))?.optString("name")
                            ?: "Personal",
                        selection(item, "weightSelectKg"),
                        selection(item, "weightSelectLb"),
                        presetLabels.keys.filter { item.optInt(it) == 1 }.toSet(),
                    )
            }
            .toList()
            .sortedWith(compareBy({ it.category }, { it.name }))
            .ifEmpty {
                listOf(
                        "Bodyweight",
                        "Dumbbells",
                        "Barbell",
                        "Weight Plates",
                        "Adjustable Bench",
                        "Pull-Up Bar",
                    )
                    .map { EquipmentItem(it, it, "Equipment") }
            }
    private val exerciseRequirements: Map<String, Pair<List<Set<String>>, List<Set<String>>>> =
        source?.optJSONArray("exercises")?.let { a ->
            (0 until a.length()).associate { i ->
                val e = a.getJSONObject(i)
                e.getString("id") to
                    (requirements(e, "resistanceEquipmentGroupIds") to
                        requirements(e, "supportEquipmentGroupIds"))
            }
        } ?: emptyMap()

    private fun requirements(e: JSONObject, field: String): List<Set<String>> {
        val a = e.optJSONArray(field) ?: return emptyList()
        return (0 until a.length()).map { i -> resolveEquipmentRequirement(index, a.getString(i)) }
    }

    fun available(e: Exercise, gym: GymProfile?): Boolean {
        if (gym == null || e.custom) return true
        val (resistance, support) = exerciseRequirements[e.id] ?: return true
        return equipmentRequirementsMet(resistance, support, gym.equipmentIds)
    }

    fun prefill(key: String): Set<String> =
        if (key == "everything") items.map { it.id }.toSet()
        else items.filter { key in it.presets }.map { it.id }.toSet()

    companion object {
        val presetLabels =
            linkedMapOf(
                "commercialGym" to "Commercial Gym",
                "warehouseGym" to "Warehouse Gym",
                "localGym" to "Local Gym",
                "garageGym" to "Garage Gym",
                "homeGym" to "Home Gym",
            )

        private fun selection(j: JSONObject, key: String): List<Double> =
            j.optString(key)
                .takeIf { it.isNotBlank() && it != "null" }
                ?.split(",")
                ?.mapNotNull { it.trim().toDoubleOrNull()?.takeIf { n -> n.isFinite() && n >= 0 } }
                ?: emptyList()
    }
}

fun resolveEquipmentRequirement(index: JSONObject, id: String): Set<String> {
    val entry = index.optJSONObject(id) ?: return setOf(id)
    if (entry.optString("type") == "equipment") return setOf(id)
    val members = entry.optJSONArray("equipment") ?: return setOf(id)
    return (0 until members.length()).map { members.getString(it) }.toSet()
}

fun equipmentRequirementsMet(
    resistance: List<Set<String>>,
    support: List<Set<String>>,
    available: Set<String>,
): Boolean =
    (resistance.isEmpty() || resistance.any { available.containsAll(it) }) &&
        (support.isEmpty() || support.any { available.containsAll(it) })

private val gymIcons =
    linkedMapOf(
        "gym" to Icons.Rounded.Warehouse,
        "home" to Icons.Rounded.Home,
        "commercial" to Icons.Rounded.Business,
        "garage" to Icons.Rounded.Garage,
        "outdoors" to Icons.Rounded.Park,
    )

@Composable
fun GymProfilesScreen(
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var create by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Gym Profiles", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${data.gyms.size} ${if (data.gyms.size == 1) "Gym" else "Gyms"}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(data.gyms, key = { it.id }) { gym ->
                Surface(onClick = { onEdit(gym.id) }, shape = OF.shape, color = OF.card) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            gymIcons[gym.icon] ?: Icons.Rounded.Warehouse,
                            null,
                            Modifier.size(36.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(gym.name, style = MaterialTheme.typography.titleLarge)
                            Text("${gym.equipmentIds.size} pieces of equipment", color = OF.muted)
                        }
                        if (data.gyms.firstOrNull()?.id == gym.id) Text("Default", color = OF.green)
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }
        }
        PrimaryAction(
            "Add New Gym Profile",
            { create = true },
            Modifier.padding(18.dp),
            icon = Icons.Rounded.Add,
        )
    }
    if (create)
        NewGymDialog(vm.equipmentCatalog, { create = false }) { name, icon, ids ->
            val id = vm.saveGym(GymProfile(name = name, icon = icon, equipmentIds = ids))
            create = false
            onEdit(id)
        }
}

@Composable
private fun NewGymDialog(
    catalog: EquipmentCatalog,
    onDismiss: () -> Unit,
    onSave: (String, String, Set<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("New Gym") }
    var icon by rememberSaveable { mutableStateOf("gym") }
    var preset by rememberSaveable { mutableStateOf("everything") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Gym Profile") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GymIconChoices(icon) { icon = it }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Pre-fill Equipment?", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton({ expanded = true }) {
                        Text(
                            EquipmentCatalog.presetLabels[preset]
                                ?: if (preset == "everything") "Everything Gym" else "Blank Gym"
                        )
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        (linkedMapOf("everything" to "Everything Gym") +
                                EquipmentCatalog.presetLabels +
                                ("blank" to "Blank Gym"))
                            .forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text("$label · ${catalog.prefill(key).size}") },
                                    onClick = {
                                        preset = key
                                        expanded = false
                                    },
                                )
                            }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                { onSave(name.trim(), icon, catalog.prefill(preset)) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GymIconChoices(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gymIcons.forEach { (key, icon) ->
            FilledIconToggleButton(selected == key, { onSelect(key) }) {
                Icon(icon, "$key gym icon")
            }
        }
    }
}

@Composable
fun GymEditorScreen(
    gym: GymProfile,
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(true) }
    var weightItem by remember { mutableStateOf<EquipmentItem?>(null) }
    val catalog = vm.equipmentCatalog
    val filtered =
        catalog.items.filter {
            (it.name.contains(query, true)) && (!selectedOnly || it.id in gym.equipmentIds)
        }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            gym.name,
            onBack,
            actions = {
                IconButton({ dialog = "delete" }, enabled = data.gyms.size > 1) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete gym profile")
                }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton({ dialog = "icon" }) {
                        Icon(gymIcons[gym.icon] ?: Icons.Rounded.Warehouse, null)
                        Text("Edit Icon")
                    }
                    OutlinedButton({ dialog = "name" }) { Text("Rename") }
                    OutlinedButton({
                        onEdit(vm.saveGym(gym.copy(id = newId(), name = "${gym.name} Copy")))
                    }) {
                        Text("Duplicate")
                    }
                }
                if (data.gyms.firstOrNull()?.id != gym.id)
                    TextButton({ vm.defaultGym(gym.id) }) { Text("Make Default Gym") }
            }
            item {
                Text("Equipment", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedOnly, { selectedOnly = true }, label = { Text("Selected") })
                    FilterChip(!selectedOnly, { selectedOnly = false }, label = { Text("All") })
                }
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("Search equipment") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                )
            }
            if (filtered.isEmpty())
                item {
                    Text("No equipment selected. Choose All to add equipment.", color = OF.muted)
                }
            filtered
                .groupBy { it.category }
                .forEach { (category, entries) ->
                    item { SectionTitle(category) }
                    items(entries, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                item.id in gym.equipmentIds,
                                { checked ->
                                    vm.saveGym(
                                        gym.copy(
                                            equipmentIds =
                                                if (checked) gym.equipmentIds + item.id
                                                else gym.equipmentIds - item.id
                                        )
                                    )
                                },
                                Modifier.semantics { contentDescription = item.name },
                            )
                            AsyncImage(
                                "file:///android_asset/reference/equipment/${item.id}.png",
                                null,
                                Modifier.size(50.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                val values = gym.weightsKg[item.id]
                                if (values != null)
                                    Text(
                                        values.joinToString(", ") {
                                            it.displayWeight(data.preferences.pounds)
                                        } + if (data.preferences.pounds) " lb" else " kg",
                                        color = OF.muted,
                                    )
                                TextButton(
                                    { weightItem = item },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("Edit Weights")
                                }
                            }
                        }
                        HorizontalDivider(color = OF.line)
                    }
                }
        }
    }
    when (dialog) {
        "name" ->
            NameDialog("Rename Gym Profile", gym.name, { dialog = "" }) {
                vm.saveGym(gym.copy(name = it))
                dialog = ""
            }
        "icon" ->
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Gym Icon") },
                text = { GymIconChoices(gym.icon) { vm.saveGym(gym.copy(icon = it)) } },
                confirmButton = { TextButton({ dialog = "" }) { Text("Done") } },
            )
        "delete" ->
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("Delete ${gym.name}?") },
                text = { Text("This removes the gym profile and its equipment selections.") },
                confirmButton = {
                    TextButton({
                        vm.deleteGym(gym.id)
                        onBack()
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = { TextButton({ dialog = "" }) { Text("Cancel") } },
            )
    }
    weightItem?.let { item ->
        val pounds = data.preferences.pounds
        val initial =
            gym.weightsKg[item.id]?.map { if (pounds) it * 2.2046226218 else it }
                ?: if (pounds) item.selectionLb else item.selectionKg
        WeightSelectionDialog(item.name, initial, pounds, { weightItem = null }) { values ->
            vm.saveGym(
                gym.copy(
                    weightsKg =
                        gym.weightsKg +
                            (item.id to values.map { if (pounds) it / 2.2046226218 else it })
                )
            )
            weightItem = null
        }
    }
}

@Composable
private fun WeightSelectionDialog(
    name: String,
    initial: List<Double>,
    pounds: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<Double>) -> Unit,
) {
    var text by
        rememberSaveable(name) { mutableStateOf(initial.joinToString(", ") { it.toString() }) }
    val parts = text.split(",", "\n").map { it.trim() }.filter { it.isNotBlank() }
    val values =
        parts.mapNotNull { it.toDoubleOrNull()?.takeIf { n -> n.isFinite() && n in 0.0..5000.0 } }
    val valid = values.size == parts.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Available weights (${if (pounds) "lb" else "kg"}), separated by commas. Leave empty to clear the list."
                )
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("Weight selections") },
                    isError = !valid,
                    supportingText =
                        if (!valid) ({ Text("Use numbers from 0 to 5000 separated by commas") })
                        else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton({ onSave(values.distinct().sorted()) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
