package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseLibrary(
    exercises: List<Exercise>,
    favorites: Set<String>,
    onFavorite: (String) -> Unit,
    onDetail: (String) -> Unit,
    onPick: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onCustom: () -> Unit,
    picking: Boolean = false,
    gyms: List<GymProfile> = emptyList(),
    equipmentCatalog: EquipmentCatalog? = null,
    performedIds: Set<String> = emptySet(),
    deprioritizedIds: Set<String> = emptySet(),
    modifiedIds: Set<String> = emptySet(),
) {
    var gymId by rememberSaveable { mutableStateOf("") }
    var gymMenu by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf("All types") }
    var laterality by rememberSaveable { mutableStateOf("All lateralities") }
    val gym = gyms.find { it.id == gymId }
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("All muscles") }
    var resistanceId by rememberSaveable { mutableStateOf("") }
    var resistanceSheet by rememberSaveable { mutableStateOf(false) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf(false) }
    var pickInFlight by remember { mutableStateOf(false) }
    var scope by rememberSaveable { mutableStateOf("All") }
    val listState = rememberLazyListState()
    val filters = listOf(query, muscle, resistanceId, gym?.id.orEmpty(), type, laterality, onlyFavorites.toString(), scope)
    var previousFilters by rememberSaveable { mutableStateOf(filters) }
    LaunchedEffect(filters) {
        if (filters != previousFilters) {
            listState.scrollToItem(0)
            previousFilters = filters
        }
    }
    val resistanceItems = remember(exercises, equipmentCatalog) {
        equipmentCatalog?.resistanceItems(exercises).orEmpty()
    }
    // Equipment availability and dropdown values depend only on the catalog and gym, not on the
    // query or filters, so they stay out of the per-keystroke path.
    val availableIds = remember(exercises, gym, equipmentCatalog) {
        exercises.filter { equipmentCatalog?.available(it, gym) != false }.mapTo(HashSet()) { it.id }
    }
    val types = remember(exercises) {
        listOf("All types") + exercises.mapNotNull { it.properties["Type"] }.distinct().sorted()
    }
    val lateralities = remember(exercises) {
        listOf("All lateralities") + exercises.mapNotNull { it.properties["Laterality"] }.distinct().sorted()
    }
    val muscleGroups = remember(exercises) {
        listOf("All muscles") + exercises.flatMap { it.muscles }.distinct().sorted()
    }
    val searchIndex = remember(exercises) { ExerciseSearchIndex(exercises) }
    val filtered =
        remember(
            exercises,
            query,
            muscle,
            resistanceId,
            onlyFavorites,
            favorites,
            gym,
            type,
            laterality,
            picking,
            scope,
            performedIds,
            deprioritizedIds,
            modifiedIds,
            availableIds,
        ) {
            searchIndex.search(query)
                .filter { e ->
                    (if (scope == "Archived" && !picking) e.archived else !e.archived) &&
                    (scope == "All" || (scope == "Performed" && e.id in performedIds) ||
                        (scope == "Deprioritized" && e.id in deprioritizedIds) ||
                        (scope == "Modified" && e.id in modifiedIds) || scope == "Archived") &&
                    (e.id in availableIds) &&
                        (type == "All types" || e.properties["Type"] == type) &&
                        (laterality == "All lateralities" ||
                            e.properties["Laterality"] == laterality) &&
                        (muscle == "All muscles" || muscle in e.muscles) &&
                        (resistanceId.isEmpty() || equipmentCatalog?.usesResistance(e, resistanceId) == true) &&
                        (!onlyFavorites || e.id in favorites)
                }
        }
    val hasActiveFilters = gym != null || muscle != "All muscles" || resistanceId.isNotEmpty() ||
        onlyFavorites || type != "All types" || laterality != "All lateralities" || scope != "All"
    fun clearFilters() {
        gymId = ""
        muscle = "All muscles"
        resistanceId = ""
        onlyFavorites = false
        type = "All types"
        laterality = "All lateralities"
        scope = "All"
    }
    val searchField: @Composable (Modifier) -> Unit = { searchModifier ->
        Row(
            searchModifier
                .fillMaxWidth()
                .then(if (onBack != null) Modifier.background(OF.top) else Modifier)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.weight(1f).background(OF.top, RoundedCornerShape(50)),
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
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            PageHeading(
                if (picking) "Add Exercise" else "Exercises",
                onBack,
                actions = { RoundAction(Icons.Rounded.Add, "Create custom exercise", onCustom) },
            )
            searchField(Modifier)
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!picking) FilterChooser("Scope", listOf("All", "Performed", "Deprioritized", "Modified", "Archived"), scope) { scope = it }
                if (!picking || filter) Box {
                    FilterChip(
                        gym != null,
                        { gymMenu = true },
                        label = { Text(gym?.name ?: "All equipment") },
                        leadingIcon = { Icon(Icons.Rounded.Warehouse, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
                    )
                    DropdownMenu(gymMenu, { gymMenu = false }) {
                        DropdownMenuItem({ Text("All equipment") }, { gymId = ""; gymMenu = false })
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
                if (filter) {
                    FilterChooser("Type", types, type) { type = it }
                    FilterChooser("Laterality", lateralities, laterality) { laterality = it }
                }
                if (equipmentCatalog != null) FilterChip(
                    resistanceId.isNotEmpty(),
                    { resistanceSheet = true },
                    label = { Text(resistanceItems.find { it.id == resistanceId }?.name ?: "Resistance") },
                    trailingIcon = { Icon(Icons.Rounded.ExpandMore, null) },
                    modifier = Modifier.semantics { contentDescription = "Resistance filter" },
                )
                FilterChip(
                    filter,
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
                    FilterChooser("Muscle group", muscleGroups, muscle) { muscle = it }
                }
            if (!picking && query.isBlank() && !WindowInsets.isImeVisible) Row(
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
                        Modifier.width(72.dp).selectable(muscle == m, role = Role.Button) {
                            muscle = if (muscle == m) "All muscles" else m
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.border(
                                if (muscle == m) 2.dp else 1.dp,
                                if (muscle == m) MaterialTheme.colorScheme.onSurface else OF.line,
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
                            color = if (muscle == m) MaterialTheme.colorScheme.onSurface else OF.muted,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${filtered.size} ${if (filtered.size == 1) "exercise" else "exercises"}",
                    Modifier.weight(1f), color = OF.muted, style = MaterialTheme.typography.labelLarge)
                if (hasActiveFilters && filtered.isNotEmpty()) TextButton({ clearFilters() }) { Text("Clear filters") }
            }
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp,
                    bottom = if (onBack == null) 112.dp else 18.dp),
            ) {
                if (filtered.isEmpty()) item {
                    EmptyContent(
                        Icons.Rounded.SearchOff,
                        if (hasActiveFilters) "No matches with these filters" else "No exercises found",
                        if (hasActiveFilters) "Clear filters to search the full catalog."
                        else "Try a movement, equipment name, or abbreviation such as DB or RDL.",
                        if (hasActiveFilters) "Clear filters" else "Clear search",
                        { if (hasActiveFilters) clearFilters() else query = "" },
                    )
                    TextButton(onCustom, Modifier.fillMaxWidth()) { Text("Create custom exercise") }
                }
                items(filtered, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onDetail(e.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ExerciseImage(e, Modifier.width(46.dp).height(62.dp))
                        Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        RoundAction(
                            if (picking) Icons.Rounded.Add
                            else if (e.id in favorites) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            if (picking) "Add ${e.name}" else "Favorite ${e.name}",
                        ) {
                            if (picking && onPick != null) {
                                if (!pickInFlight) { pickInFlight = true; onPick(e.id) }
                            } else onFavorite(e.id)
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = OF.line.copy(alpha = 0.5f))
                }
            }
        }
    }
    if (resistanceSheet) ResistanceFilterSheet(
        resistanceItems, resistanceId, { resistanceSheet = false },
    ) { resistanceId = it; resistanceSheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResistanceFilterSheet(equipment: List<EquipmentItem>, selected: String, dismiss: () -> Unit, select: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val order = listOf("Personal", "Free Weights", "Loadable Bars", "Fixed Weight Bars", "Bands & Ropes", "Cable Machines", "Cardio", "Plate Loaded Machines", "Pin Loaded Machines")
    val groups = remember(equipment, query) {
        equipment.filter { it.name.contains(query, true) }.groupBy { it.category }
            .toList().sortedBy { order.indexOf(it.first).takeIf { index -> index >= 0 } ?: order.size }
    }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(dismiss) { Icon(Icons.Rounded.Close, "Close Resistance") }
                Text("Resistance", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            key(query) {
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        ListItem(
                            headlineContent = { Text("All Resistance") },
                            trailingContent = { RadioButton(selected.isEmpty(), null) },
                            modifier = Modifier.clickable { select("") },
                        )
                    }
                    if (groups.isEmpty()) item { Text("No matching equipment", Modifier.padding(18.dp), color = OF.muted) }
                    groups.forEach { (category, items) ->
                        item(key = "category-$category") {
                            Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle(if (category == "Personal") "Bodyweight" else category) }
                        }
                        items(items, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(if (item.name.equals("Bodyweight only", true)) "Bodyweight Only" else item.name) },
                                leadingContent = { SubcomposeAsyncImage("file:///android_asset/reference/equipment/${item.id}.png", null, Modifier.size(48.dp),
                                    error = { Icon(Icons.Rounded.FitnessCenter, null, tint = OF.muted) }) },
                                trailingContent = { RadioButton(item.id == selected, null) },
                                modifier = Modifier.clickable { select(item.id) },
                            )
                            HorizontalDivider(color = OF.line)
                        }
                    }
                }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Filter equipment by name") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear equipment search") } },
                singleLine = true, shape = RoundedCornerShape(50))
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
    onAdd: (() -> Unit)?,
    closeable: Boolean = false,
    onSettings: (() -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collapsedSections by rememberSaveable(e.id) { mutableStateOf(emptyList<Int>()) }
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
            if (closeable) null else onBack,
            displayTitle = false,
            actions = {
                if (closeable) IconButton(onBack) { Icon(Icons.Rounded.Close, "Close Info") }
                if (onSettings != null) IconButton(onSettings) { Icon(Icons.Rounded.Tune, "Exercise settings") }
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
            listOf("Guide", "Muscles", "History").forEachIndexed { i, s ->
                Tab(tab == i, { tab = i }, text = { Text(s) })
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val mediaViewportHeight = maxHeight
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = if (tab == 0) PaddingValues(bottom = 18.dp) else PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (tab == 0) {
                    item { ExerciseMedia(e, mediaViewportHeight) }
                    if (instructions.isNotBlank())
                        markdownItems(instructions).forEachIndexed { index, (heading, text) ->
                            item {
                                Column(
                                    Modifier.padding(horizontal = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (heading != null)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(heading, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                            val collapsed = index in collapsedSections
                                            TextButton(
                                                onClick = {
                                                    collapsedSections = if (collapsed) collapsedSections - index else collapsedSections + index
                                                },
                                                modifier = Modifier.semantics {
                                                    contentDescription = "${if (collapsed) "Expand" else "Collapse"} $heading"
                                                },
                                            ) { Text(if (collapsed) "Expand" else "Collapse") }
                                        }
                                    if (index !in collapsedSections)
                                        Text(text, style = MaterialTheme.typography.bodyLarge, color = OF.muted)
                                }
                            }
                        }
                } else if (tab == 1) {
                    item { ExerciseAnatomy(e) }
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
                            e.equipmentOptions.ifEmpty { listOf(e.equipment) }.joinToString(if (e.custom) " or " else ", "),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (e.custom && e.supportEquipment.isNotEmpty()) item {
                        SectionTitle("Support Equipment")
                        val names = vm.equipmentCatalog.items.associate { it.id to it.name }
                        Text(e.supportEquipment.joinToString(" or ") { group ->
                            group.map { names[it] ?: it }.sorted().joinToString(" + ")
                        })
                    }
                    item {
                        SectionTitle("Details")
                        if (e.aliases.isNotEmpty()) {
                            Text("Alternative Names", color = OF.muted)
                            Text(e.aliases.joinToString(", "), Modifier.padding(bottom = 12.dp))
                        }
                        val type = e.infoTypeLabel
                        e.properties
                            .filterKeys { it !in listOf("Range of Motion", "Stability", "Movement Patterns") && (it != "Region" || type == null) }
                            .forEach { (label, value) ->
                                Text(label, color = OF.muted)
                                Text(if (label == "Type") type ?: value else value, Modifier.padding(bottom = 12.dp))
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
                                    .flatMap { it.logicalSets() }
                                    .filter { it.logs.any { log -> log.completed } }
                                    .forEachIndexed { index, group ->
                                        Text("Set ${index + 1} · ${if (group.root.warmup) "Warm-up" else group.root.kind.label}", Modifier.padding(top = 8.dp), color = OF.muted)
                                        group.components.forEachIndexed { componentIndex, logs -> logs.forEach { s ->
                                            Text(
                                                listOfNotNull(s.side?.label, if (componentIndex > 0) "Myo $componentIndex" else null).joinToString(" · ")
                                                    .let { if (it.isEmpty()) "" else "$it: " } +
                                                    if (s.completed) s.describe(e.loggingMetrics(), data.preferences.pounds, data.preferences.distanceImperial) else "Not completed",
                                                Modifier.padding(top = 4.dp),
                                            )
                                        } }
                                    }
                            }
                        }
                    }
                }
            }
        }
        if (onAdd != null) Box(Modifier.padding(18.dp)) {
            PrimaryAction(
                "Add Exercise",
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
                lines.first().trimStart('#', ' ') to cleanMarkdown(lines.drop(1).joinToString("\n")).trim()
            else null to cleanMarkdown(part)
        }
        .filter { it.first != null || it.second.isNotBlank() }

@Composable
fun RoutineBuilder(
    exercises: List<Exercise>,
    onBack: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var leaving by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var editNameRequest by remember { mutableIntStateOf(0) }
    val nameFocus = remember { FocusRequester() }
    val searchIndex = remember(exercises) { ExerciseSearchIndex(exercises) }
    val matches = remember(searchIndex, query) { searchIndex.search(query).filterNot { it.archived } }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    LaunchedEffect(editNameRequest) { if (editNameRequest > 0) nameFocus.requestFocus() }
    fun back() {
        if (name.isNotBlank() || selected.isNotEmpty()) leaving = true else onBack()
    }
    androidx.activity.compose.BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize().imePadding()) {
        // Search is a working mode: retain the name as an editable header and give the list room
        // above the keyboard. Never collapse the field while the person is typing its name.
        Row(Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(if (searching && name.isNotBlank()) name else "Create template", Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (searching) IconButton({ searching = false; editNameRequest++ }) {
                Icon(Icons.Rounded.Edit, "Edit template name")
            }
        }
        if (!searching) OutlinedTextField(
            name,
            { name = it },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).focusRequester(nameFocus),
            label = { Text("Template name") },
            singleLine = true,
        )
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).onFocusChanged { searching = it.isFocused },
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
            singleLine = true,
        )
        Text("${selected.size} selected", Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, color = OF.muted)
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 18.dp)) {
            items(if (searching) emptyList() else selected, key = { "selected-$it" }) { id ->
                val exercise = exercises.find { it.id == id }
                val index = selected.indexOf(id)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. ${exercise?.title ?: "Exercise"}", Modifier.weight(1f))
                    listOf(-1 to Icons.Rounded.ArrowUpward, 1 to Icons.Rounded.ArrowDownward)
                        .forEach { (delta, icon) ->
                            IconButton(
                                onClick = {
                                    selected =
                                        selected.toMutableList().apply {
                                            java.util.Collections.swap(this, index, index + delta)
                                        }
                                },
                                enabled = index + delta in selected.indices,
                            ) {
                                Icon(
                                    icon,
                                    "Move ${exercise?.title ?: "exercise"} ${if (delta < 0) "up" else "down"}",
                                )
                            }
                        }
                    IconButton({ selected = selected - id }) {
                        Icon(Icons.Rounded.Close, "Remove ${exercise?.title ?: "exercise"}")
                    }
                }
            }
            if (selected.isNotEmpty() && !searching)
                item { HorizontalDivider(Modifier.padding(vertical = 12.dp), color = OF.line) }
            if (matches.isEmpty()) item {
                EmptyContent(Icons.Rounded.Search, "No exercises found", "Try a name, muscle or equipment.", "Clear search", { query = "" })
            }
            items(
                matches,
                key = { it.id },
            ) { e ->
                ListItem(
                    headlineContent = { Text(e.title) },
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
        Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            PrimaryAction(
                "Save template",
                { onSave(name, selected) },
                enabled = name.isNotBlank() && selected.isNotEmpty(),
            )
        }
    }
    if (leaving)
        ConfirmDialog(
            "Discard unsaved template?",
            "This template has not been saved to your library.",
            "Discard",
            { leaving = false },
            {
                leaving = false
                onBack()
            },
        )
}
