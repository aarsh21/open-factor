package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExerciseLibrary(
    exercises: List<Exercise>,
    favorites: Set<String>,
    onFavorite: (String) -> Unit,
    onDetail: (String) -> Unit,
    onPick: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onCustom: () -> Unit = {},
    picking: Boolean = false,
    gyms: List<GymProfile> = emptyList(),
    equipmentCatalog: EquipmentCatalog? = null,
) {
    var gymId by rememberSaveable { mutableStateOf("") }
    var gymMenu by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf("All types") }
    var laterality by rememberSaveable { mutableStateOf("All lateralities") }
    val gym = gyms.find { it.id == gymId } ?: gyms.firstOrNull()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("All muscles") }
    var equipment by rememberSaveable { mutableStateOf("All equipment") }
    var expandedGroups by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf(false) }
    val filtered =
        remember(
            exercises,
            query,
            muscle,
            equipment,
            onlyFavorites,
            favorites,
            gym,
            type,
            laterality,
        ) {
            exercises
                .filter { e ->
                    (equipmentCatalog?.available(e, gym) != false) &&
                        (type == "All types" || e.properties["Type"] == type) &&
                        (laterality == "All lateralities" ||
                            e.properties["Laterality"] == laterality) &&
                        (query.isBlank() ||
                            e.name.contains(query, true) ||
                            e.aliases.any { it.contains(query, true) }) &&
                        (muscle == "All muscles" || muscle in e.muscles) &&
                        (equipment == "All equipment" || equipment == e.equipment) &&
                        (!onlyFavorites || e.id in favorites)
                }
                .sortedWith(
                    compareByDescending<Exercise> { it.searchBoost }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.id }
                )
        }
    val groups =
        remember(filtered) {
            val order =
                listOf("Bodyweight & Personal", "Free Weights", "Machines", "Cardio", "Other")
            filtered
                .flatMap { e ->
                    e.browseCategories
                        .ifEmpty {
                            listOf(
                                when (e.equipment) {
                                    "Bodyweight" -> "Bodyweight & Personal"
                                    "Dumbbell",
                                    "Barbell",
                                    "Kettlebell" -> "Free Weights"
                                    "Cable",
                                    "Machine",
                                    "Smith Machine" -> "Machines"
                                    else -> "Other"
                                }
                            )
                        }
                        .map { it to e }
                }
                .groupBy({ it.first }, { it.second })
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, List<Exercise>>> {
                            it.second.take(5).sumOf { e -> e.searchBoost }
                        }
                        .thenBy { order.indexOf(it.first) }
                )
        }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            if (picking) "Add Exercise" else "Exercises",
            onBack,
            actions = { RoundAction(Icons.Rounded.Add, "Create custom exercise", onCustom) },
        )
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                FilterChip(
                    true,
                    { gymMenu = true },
                    label = { Text(gym?.name ?: "Everything Gym") },
                    leadingIcon = { Icon(Icons.Rounded.Warehouse, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
                )
                DropdownMenu(gymMenu, { gymMenu = false }) {
                    gyms.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.name) },
                            onClick = {
                                gymId = g.id
                                gymMenu = false
                            },
                        )
                    }
                }
            }
            FilterChooser(
                "Type",
                listOf("All types") +
                    exercises.mapNotNull { it.properties["Type"] }.distinct().sorted(),
                type,
            ) {
                type = it
            }
            FilterChooser(
                "Laterality",
                listOf("All lateralities") +
                    exercises.mapNotNull { it.properties["Laterality"] }.distinct().sorted(),
                laterality,
            ) {
                laterality = it
            }
            FilterChip(
                filter || equipment != "All equipment",
                { filter = !filter },
                label = { Text("Filters") },
                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                onlyFavorites,
                { onlyFavorites = !onlyFavorites },
                label = { Text("Favorites") },
                leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) },
            )
            if (muscle != "All muscles")
                InputChip(
                    true,
                    { muscle = "All muscles" },
                    label = { Text("Clear $muscle") },
                    trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(14.dp)) },
                )
        }
        if (filter)
            Column(Modifier.padding(horizontal = 18.dp)) {
                FilterChooser(
                    "Muscle group",
                    listOf("All muscles") + exercises.flatMap { it.muscles }.distinct().sorted(),
                    muscle,
                ) {
                    muscle = it
                }
                FilterChooser(
                    "Equipment",
                    listOf("All equipment") + exercises.map { it.equipment }.distinct().sorted(),
                    equipment,
                ) {
                    equipment = it
                }
            }
        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val muscles =
                listOf(
                    "Chest",
                    "Quads",
                    "Upper Back",
                    "Glutes",
                    "Lats",
                    "Biceps",
                    "Triceps",
                    "Hamstrings",
                    "Abs",
                    "Calves",
                    "Side Delts",
                )
            muscles.forEach { m ->
                Column(
                    Modifier.width(72.dp).clickable {
                        muscle = if (muscle == m) "All muscles" else m
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.border(
                            if (muscle == m) 2.dp else 1.dp,
                            if (muscle == m) Color.White else OF.line,
                            RoundedCornerShape(10.dp),
                        )
                    ) {
                        MuscleImage(m, Modifier.size(72.dp))
                    }
                    Text(
                        m,
                        Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = if (muscle == m) Color.White else OF.muted,
                    )
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 2.dp,
                    bottom = if (onBack == null) 104.dp else 18.dp,
                ),
        ) {
            if (filtered.isEmpty())
                item {
                    EmptyContent(
                        Icons.Rounded.SearchOff,
                        "No exercises found",
                        "Try a different name or clear your filters.",
                        "Clear Filters",
                        {
                            query = ""
                            muscle = "All muscles"
                            equipment = "All equipment"
                            onlyFavorites = false
                            type = "All types"
                            laterality = "All lateralities"
                        },
                    )
                }
            groups.forEach { (group, entries) ->
                item(key = "group-$group") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            group,
                            Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (entries.size > 8)
                            TextButton({
                                expandedGroups =
                                    if (group in expandedGroups) expandedGroups - group
                                    else expandedGroups + group
                            }) {
                                Text(
                                    if (group in expandedGroups) "See Less"
                                    else "See ${entries.size - 5} More",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                    }
                }
                items(
                    if (entries.size > 8 && group !in expandedGroups) entries.take(5) else entries,
                    key = { "$group-${it.id}" },
                ) { e ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onDetail(e.id) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ExerciseImage(e, Modifier.width(46.dp).height(62.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                e.muscles.joinToString(", ") +
                                    if (e.secondary.isNotEmpty())
                                        " • " + e.secondary.joinToString(", ")
                                    else "",
                                color = OF.muted,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        RoundAction(
                            if (picking) Icons.Rounded.Add
                            else if (e.id in favorites) Icons.Rounded.Star
                            else Icons.Rounded.StarOutline,
                            if (picking) "Add ${e.name}" else "Favorite ${e.name}",
                        ) {
                            if (picking && onPick != null) onPick(e.id) else onFavorite(e.id)
                        }
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 60.dp),
                        color = OF.line.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .padding(bottom = if (onBack == null) 88.dp else 0.dp)
                .background(OF.top)
                .imePadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f),
                placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty())
                        IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
            )
            if (picking && onBack != null)
                Button(onBack, shape = RoundedCornerShape(50)) { Text("Done") }
        }
    }
}

@Composable
private fun FilterChooser(
    title: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }) {
            Text(if (selected.startsWith("All ")) title else selected)
            Icon(Icons.Rounded.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v) },
                    onClick = {
                        expanded = false
                        onSelect(v)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetail(
    e: Exercise,
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val instructions =
        remember(e.id) {
            if (e.instructions.startsWith("reference/"))
                runCatching {
                        context.assets.open(e.instructions).bufferedReader().use { it.readText() }
                    }
                    .getOrDefault("")
            else e.instructions
        }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            e.title,
            onBack,
            actions = {
                IconButton({ vm.favorite(e.id) }) {
                    Icon(
                        if (e.id in data.favorites) Icons.Rounded.Star
                        else Icons.Rounded.StarOutline,
                        "Toggle favorite",
                    )
                }
            },
        )
        SecondaryTabRow(tab, containerColor = OF.top) {
            listOf("Instructions", "Details", "History").forEachIndexed { i, s ->
                Tab(tab == i, { tab = i }, text = { Text(s) })
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (tab == 0) {
                item { ExerciseMedia(e) }
                if (instructions.isNotBlank())
                    markdownItems(instructions).forEach { (heading, text) ->
                        item {
                            if (heading != null)
                                Text(heading, style = MaterialTheme.typography.titleLarge)
                            else
                                Text(
                                    text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OF.muted,
                                )
                        }
                    }
            } else if (tab == 1) {
                item { SectionTitle("Target Muscles") }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        (e.muscles + e.secondary).distinct().forEach { m ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MuscleImage(m, Modifier.size(110.dp))
                                Text(m, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (m in e.muscles) "Primary" else "Secondary",
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
                if (e.properties.isNotEmpty())
                    item {
                        SectionTitle("Properties")
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            listOf("Range of Motion", "Stability").forEach { p ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(p, color = OF.muted)
                                    Text(
                                        "${e.properties[p] ?: "–"}/5",
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        }
                    }
                item {
                    SectionTitle("Resistance Equipment")
                    Text(
                        e.equipmentOptions.ifEmpty { listOf(e.equipment) }.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                item {
                    SectionTitle("Details")
                    if (e.aliases.isNotEmpty()) {
                        Text("Alternative Names", color = OF.muted)
                        Text(e.aliases.joinToString(", "), Modifier.padding(bottom = 12.dp))
                    }
                    e.properties
                        .filterKeys { it !in listOf("Range of Motion", "Stability") }
                        .forEach { (label, value) ->
                            Text(label, color = OF.muted)
                            Text(value, Modifier.padding(bottom = 12.dp))
                        }
                    Text("Body Weight Contribution", color = OF.muted)
                    Text("${(e.bodyweight*100).toInt()}%")
                }
            } else {
                val history = data.history.filter { w -> w.exercises.any { it.exerciseId == e.id } }
                if (history.isEmpty())
                    item {
                        EmptyContent(
                            Icons.Rounded.History,
                            "No exercise history",
                            "Completed sets will appear here after your first workout.",
                        )
                    }
                items(history, key = { it.id }) { w ->
                    Surface(shape = OF.shape, color = OF.card) {
                        Column(Modifier.padding(16.dp)) {
                            Text(w.name, style = MaterialTheme.typography.titleMedium)
                            Text(w.localDate().toString(), color = OF.muted)
                            w.exercises
                                .filter { it.exerciseId == e.id }
                                .flatMap { it.sets }
                                .filter { it.completed }
                                .forEach { s ->
                                    Text(
                                        "${s.weightKg.displayWeight(data.preferences.pounds)} ${if(data.preferences.pounds)"lb" else "kg"} × ${s.reps}${s.rir?.let {" · $it RIR"} ?: ""}",
                                        Modifier.padding(top = 8.dp),
                                    )
                                }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryAction(
                if (data.active == null) "Start Workout with Exercise" else "Add to Workout",
                onAdd,
                icon = Icons.Rounded.Add,
            )
        }
    }
}

private fun cleanMarkdown(text: String) =
    text.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1").replace("**", "").replace("__", "")

private fun descriptionSection(text: String) =
    cleanMarkdown(
            text
                .substringAfter("## Description", text.substringBefore("## Exercise technique"))
                .trim()
        )
        .take(2400)

private fun markdownItems(text: String): List<Pair<String?, String>> =
    text
        .split(Regex("\\n(?=##? )"))
        .map { part ->
            val lines = part.trim().lines()
            if (lines.firstOrNull()?.startsWith("#") == true)
                listOf(
                    lines.first().trimStart('#', ' ') to "",
                    null to cleanMarkdown(lines.drop(1).joinToString("\n")).trim(),
                )
            else listOf(null to cleanMarkdown(part))
        }
        .flatten()
        .filter { it.first != null || it.second.isNotBlank() }

@Composable
fun CustomExercise(onBack: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("Other") }
    var equipment by rememberSaveable { mutableStateOf("Bodyweight") }
    var instructions by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeading("New Exercise", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                label = { Text("Exercise name") },
                singleLine = true,
            )
            OutlinedTextField(
                muscle,
                { muscle = it },
                Modifier.fillMaxWidth(),
                label = { Text("Primary muscle") },
                singleLine = true,
            )
            OutlinedTextField(
                equipment,
                { equipment = it },
                Modifier.fillMaxWidth(),
                label = { Text("Equipment") },
                singleLine = true,
            )
            OutlinedTextField(
                instructions,
                { instructions = it },
                Modifier.fillMaxWidth().heightIn(min = 160.dp),
                label = { Text("Instructions and technique notes") },
            )
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryAction(
                "Create Exercise",
                {
                    onSave(
                        name,
                        muscle.ifBlank { "Other" },
                        equipment.ifBlank { "Other" },
                        instructions,
                    )
                },
                enabled = name.isNotBlank(),
            )
        }
    }
}

@Composable
fun RoutineBuilder(
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Create Workout", onBack)
        OutlinedTextField(
            name,
            { name = it },
            Modifier.fillMaxWidth().padding(18.dp),
            label = { Text("Workout name") },
            singleLine = true,
        )
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
        )
        Text("${selected.size} selected", Modifier.padding(18.dp), color = OF.muted)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp)) {
            items(
                exercises.filter { it.name.contains(query, true) }.sortedBy { it.name },
                key = { it.id },
            ) { e ->
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = { Text("${e.muscle} · ${e.equipment}") },
                    leadingContent = { ExerciseImage(e, Modifier.size(48.dp)) },
                    trailingContent = {
                        Checkbox(
                            e.id in selected,
                            {
                                selected =
                                    if (e.id in selected) selected - e.id else selected + e.id
                            },
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            selected = if (e.id in selected) selected - e.id else selected + e.id
                        },
                    colors = ListItemDefaults.colors(containerColor = OF.background),
                )
            }
        }
        Box(Modifier.padding(18.dp)) {
            PrimaryAction(
                "Save Workout",
                { onSave(name, selected) },
                enabled = name.isNotBlank() && selected.isNotEmpty(),
            )
        }
    }
}
