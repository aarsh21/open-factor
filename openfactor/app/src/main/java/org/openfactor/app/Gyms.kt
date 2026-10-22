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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

data class GymProfile(
    val id: String = newId(),
    val name: String,
    val icon: String = "gym",
    val equipmentIds: Set<String> = emptySet(),
    val weightsKg: Map<String, List<Double>> = emptyMap(),
    val weightSelectionsKg: Map<String, List<EquipmentWeight>> = emptyMap(),
    val weightSelectionsLb: Map<String, List<EquipmentWeight>> = emptyMap(),
) {
    fun weights(item: EquipmentItem, pounds: Boolean): List<EquipmentWeight> =
        (if (pounds) weightSelectionsLb else weightSelectionsKg)[item.id]
            ?: weightsKg[item.id]?.distinct()?.map {
                EquipmentWeight(if (pounds) it * 2.2046226218 else it)
            }
            ?: (if (pounds) item.selectionLb else item.selectionKg).map { EquipmentWeight(it) }

    fun selectCategory(items: List<EquipmentItem>, selected: Boolean): GymProfile {
        val ids = items.map { it.id }.toSet()
        return copy(equipmentIds = if (selected) equipmentIds + ids else equipmentIds - ids)
    }
}

/** Quantity is inventory metadata; no source-backed loading rule has been recovered. */
data class EquipmentWeight(val value: Double, val enabled: Boolean = true, val quantity: Int? = null) {
    init {
        require(value.isFinite() && value >= 0.0) { "Invalid equipment weight" }
        require(quantity == null || quantity > 0) { "Invalid equipment quantity" }
    }
}

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
            .put("weightSelectionsKg", encodeSelections(g.weightSelectionsKg))
            .put("weightSelectionsLb", encodeSelections(g.weightSelectionsLb))

    private fun encodeSelections(selections: Map<String, List<EquipmentWeight>>) =
        JSONObject().also { j -> selections.forEach { (id, rows) -> j.put(id, encodeWeights(rows)) } }

    fun encodeWeights(rows: List<EquipmentWeight>): JSONArray = JSONArray(rows.map {
        JSONObject().put("value", it.value).put("enabled", it.enabled)
            .put("quantity", it.quantity ?: JSONObject.NULL)
    })

    fun decodeWeights(a: JSONArray): List<EquipmentWeight> = (0 until a.length()).map { i ->
        val row = a.getJSONObject(i)
        val quantity = if (row.isNull("quantity")) null else {
            val number = row.getDouble("quantity")
            require(number.isFinite() && number >= 1 && number <= Int.MAX_VALUE && number % 1 == 0.0) {
                "Invalid equipment quantity"
            }
            number.toInt()
        }
        EquipmentWeight(row.getDouble("value"), row.getBoolean("enabled"), quantity)
    }.also { rows -> require(rows.map { it.value }.distinct().size == rows.size) { "Duplicate equipment weights" } }

    private fun decodeSelections(j: JSONObject?) = j?.keys()?.asSequence()?.associateWith {
        decodeWeights(j.getJSONArray(it))
    }.orEmpty()

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
            decodeSelections(if (j.has("weightSelectionsKg")) j.getJSONObject("weightSelectionsKg") else null),
            decodeSelections(if (j.has("weightSelectionsLb")) j.getJSONObject("weightSelectionsLb") else null),
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
            .getOrElse {
                runCatching {
                        context.assets.open("equipment.json").bufferedReader().use {
                            JSONObject(it.readText())
                        }
                    }
                    .getOrNull()
            }
    private val index = source?.optJSONObject("uuidIndex") ?: JSONObject()
    val jointActions: List<String> = index.keys().asSequence().mapNotNull { id ->
        index.getJSONObject(id).takeIf { it.optString("type") == "jointAction" }?.getString("name")
    }.distinct().sorted().toList().ifEmpty {
        context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            .flatMap { exercise -> listOf("Primary Joint Action", "Secondary Joint Action").flatMap {
                exercise.properties[it].orEmpty().split(',').map(String::trim)
            } }.filter { it.isNotBlank() && it != "None" }.distinct().sorted()
    }
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
                        selection(item, "weightSelectKg").ifEmpty { observedProfileKg[id].orEmpty() },
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

    fun requirementsFor(e: Exercise): Pair<List<Set<String>>, List<Set<String>>> =
        if (e.custom) e.resistanceEquipment to e.supportEquipment
        else exerciseRequirements[e.id]?.let { (resistance, support) ->
            resistance.ifEmpty { listOf(emptySet()) } to support
        } ?: (e.resistanceEquipment to e.supportEquipment)

    fun available(e: Exercise, gym: GymProfile?): Boolean {
        if (gym == null) return true
        val (resistance, support) = if (e.custom) e.resistanceEquipment to e.supportEquipment
            else exerciseRequirements[e.id] ?: return true
        return equipmentRequirementsMet(resistance, support, gym.equipmentIds)
    }

    fun usesResistance(e: Exercise, equipmentId: String): Boolean {
        val item = items.find { it.id == equipmentId } ?: return false
        val resistance = if (e.custom) e.resistanceEquipment else exerciseRequirements[e.id]?.first.orEmpty()
        if (resistance.isNotEmpty()) return resistance.any {
            equipmentId in it || (it.isEmpty() && item.name.equals("Bodyweight only", true))
        }
        return e.equipmentOptions.ifEmpty { listOf(e.equipment) }.any {
            it.equals(item.name, true) || (it.equals("Bodyweight", true) && item.name.equals("Bodyweight only", true))
        }
    }

    fun prefill(key: String): Set<String> =
        if (key == "everything") items.map { it.id }.toSet()
        else items.filter { key in it.presets }.map { it.id }.toSet()

    companion object {
        // The retained Everything Gym profile showed these lists. These are local starter
        // selections, not a recovered factory generator; pounds are not inferred from them.
        private val observedProfileKg = mapOf(
            "25a5c6f170d8805b8c95e7e5b4e8a989" to listOf(5.0, 10.0, 15.0, 20.0),
            "19e5c6f170d8808593d8ecf408c4f749" to listOf(
                1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 12.5,
                15.0, 17.5, 20.0, 22.5, 25.0, 27.5, 30.0, 32.5, 35.0, 37.5, 40.0, 42.5,
                45.0, 47.5, 50.0, 52.5, 55.0, 57.5, 60.0, 62.5, 65.0, 67.5, 70.0, 72.5, 75.0,
            ),
            "25a5c6f170d880478d81c880d6ee06a0" to listOf(
                4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 26.0,
                28.0, 30.0, 32.0, 36.0, 40.0, 44.0, 48.0, 52.0, 56.0, 60.0, 64.0, 68.0,
                72.0, 76.0, 80.0, 84.0, 88.0, 92.0,
            ),
            "19e5c6f170d8802a89e5c8d58b54c757" to (1..14).map { it.toDouble() },
            "25a5c6f170d880c1a55ac08cf52671ab" to listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0),
        )

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
        "apartment" to Icons.Rounded.Apartment,
        "city" to Icons.Rounded.LocationCity,
        "cottage" to Icons.Rounded.Cottage,
        "castle" to Icons.Rounded.Castle,
        "cabin" to Icons.Rounded.Cabin,
        "house" to Icons.Rounded.House,
        "holiday-home" to Icons.Rounded.HolidayVillage,
        "bungalow" to Icons.Rounded.Bungalow,
        "chalet" to Icons.Rounded.Chalet,
        "villa" to Icons.Rounded.Villa,
        "roof" to Icons.Rounded.Roofing,
        "foundation" to Icons.Rounded.Foundation,
        "store" to Icons.Rounded.Storefront,
        "corporate" to Icons.Rounded.CorporateFare,
        "other-house" to Icons.Rounded.OtherHouses,
        "door" to Icons.Rounded.DoorFront,
        "domain" to Icons.Rounded.Domain,
        "home-work" to Icons.Rounded.HomeWork,
    )

@Composable
fun GymProfilesScreen(
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var create by rememberSaveable { mutableStateOf(false) }
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
    var chooseIcon by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Gym Profile") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FilledIconButton({ chooseIcon = true }) {
                    Icon(gymIcons[icon] ?: Icons.Rounded.Warehouse, "Choose gym icon")
                }
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
    if (chooseIcon) AlertDialog(
        onDismissRequest = { chooseIcon = false }, title = { Text("Gym Icon") },
        text = { GymIconChoices(icon) { icon = it; chooseIcon = false } },
        confirmButton = { TextButton({ chooseIcon = false }) { Text("Cancel") } },
    )
}

@Composable
private fun GymIconChoices(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        gymIcons.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (key, icon) ->
                    FilledIconToggleButton(selected == key, { onSelect(key) }) {
                        Icon(icon, "$key gym icon")
                    }
                }
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
    var dialog by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedOnly by rememberSaveable { mutableStateOf(true) }
    var weightItemId by rememberSaveable(gym.id) { mutableStateOf<String?>(null) }
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
                    Text(
                        if (query.isNotBlank()) "No equipment matches your search."
                        else "No equipment selected. Choose All to add equipment.",
                        color = OF.muted,
                    )
                }
            filtered
                .groupBy { it.category }
                .forEach { (category, entries) ->
                    item {
                        val categoryItems = catalog.items.filter { it.category == category }
                        val allSelected = categoryItems.all { it.id in gym.equipmentIds }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(category, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                { vm.saveGym(gym.selectCategory(categoryItems, !allSelected)) },
                                Modifier.semantics {
                                    contentDescription = "${if (allSelected) "Deselect" else "Select"} all $category equipment"
                                },
                            ) { Text(if (allSelected) "Deselect All" else "Select All") }
                        }
                    }
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
                                val values = gym.weights(item, data.preferences.pounds).filter { it.enabled }
                                Text(
                                    if (values.isEmpty()) "No enabled weight selections"
                                    else values.joinToString(", ") {
                                        "${it.value.toBigDecimal().stripTrailingZeros().toPlainString()} ${if (data.preferences.pounds) "lb" else "kg"}"
                                    },
                                    color = OF.muted,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    { weightItemId = item.id },
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
    catalog.items.find { it.id == weightItemId }?.let { item ->
        WeightSelectionDialog(
            item.name, gym.weights(item, false), gym.weights(item, true),
            data.preferences.pounds, { weightItemId = null },
        ) { kg, lb ->
            vm.saveGym(
                gym.copy(
                    weightSelectionsKg = gym.weightSelectionsKg + (item.id to kg),
                    weightSelectionsLb = gym.weightSelectionsLb + (item.id to lb),
                )
            )
            weightItemId = null
        }
    }
}

@Composable
private fun WeightSelectionDialog(
    name: String,
    initialKg: List<EquipmentWeight>,
    initialLb: List<EquipmentWeight>,
    initialPounds: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<EquipmentWeight>, List<EquipmentWeight>) -> Unit,
) {
    var kgJson by rememberSaveable { mutableStateOf(GymCodec.encodeWeights(initialKg).toString()) }
    var lbJson by rememberSaveable { mutableStateOf(GymCodec.encodeWeights(initialLb).toString()) }
    var pounds by rememberSaveable { mutableStateOf(initialPounds) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newWeight by rememberSaveable { mutableStateOf("") }
    val kg = GymCodec.decodeWeights(JSONArray(kgJson))
    val lb = GymCodec.decodeWeights(JSONArray(lbJson))
    val rows = if (pounds) lb else kg
    val unit = if (pounds) "lb" else "kg"
    fun update(rows: List<EquipmentWeight>) {
        val json = GymCodec.encodeWeights(rows).toString()
        if (pounds) lbJson = json else kgJson = json
    }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = OF.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                PageHeading(name, onDismiss, actions = {
                    IconButton({ newWeight = ""; adding = true }) { Icon(Icons.Rounded.Add, "Add weight") }
                })
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Weights", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    FilterChip(!pounds, { pounds = false }, label = { Text("kg") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(pounds, { pounds = true }, label = { Text("lb") })
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp)) {
                    if (rows.isEmpty()) item {
                        Text("No weights in $unit. Add a weight to create this list.", Modifier.padding(vertical = 24.dp), color = OF.muted)
                    }
                    items(rows, key = { it.value }) { row ->
                        val label = "${row.value.toBigDecimal().stripTrailingZeros().toPlainString()} $unit"
                        Column {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    TextButton({
                                        update(rows.map { if (it == row) it.copy(quantity = if (it.quantity == null) 2 else null) else it })
                                    }, contentPadding = PaddingValues(0.dp)) {
                                        Text(if (row.quantity == null) "Set Qty" else "Remove Qty")
                                    }
                                }
                                Checkbox(row.enabled, { checked ->
                                    update(rows.map { if (it == row) it.copy(enabled = checked) else it })
                                }, Modifier.semantics { contentDescription = "Enable $label" })
                                IconButton({ update(rows - row) }) { Icon(Icons.Rounded.DeleteOutline, "Delete $label") }
                            }
                            row.quantity?.let { quantity ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton({ update(rows.map { if (it == row) it.copy(quantity = quantity - 1) else it }) }, enabled = quantity > 1) {
                                        Icon(Icons.Rounded.Remove, "Decrease $label quantity")
                                    }
                                    Text(quantity.toString(), Modifier.semantics { contentDescription = "$label quantity $quantity" })
                                    IconButton({ update(rows.map { if (it == row) it.copy(quantity = quantity + 1) else it }) }, enabled = quantity < Int.MAX_VALUE) {
                                        Icon(Icons.Rounded.Add, "Increase $label quantity")
                                    }
                                }
                            }
                            HorizontalDivider(color = OF.line)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                    Button({ onSave(kg, lb) }, Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
        if (adding) {
            val value = newWeight.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            val duplicate = value != null && rows.any { it.value == value }
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Add weight") },
                text = {
                    OutlinedTextField(newWeight, { newWeight = it }, label = { Text("Weight ($unit)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = newWeight.isNotBlank() && (value == null || duplicate),
                        supportingText = { Text(if (duplicate) "This weight already exists" else "Use a non-negative number") })
                },
                confirmButton = { TextButton({
                    update((rows + EquipmentWeight(value!!)).sortedBy { it.value })
                    adding = false
                }, enabled = value != null && !duplicate) { Text("Add") } },
                dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
            )
        }
    }
}
