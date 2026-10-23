package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val programColors =
    listOf(0xFFFF2F4A, 0xFFFF642B, 0xFFFFB500, 0xFF3C9D6A, 0xFF286CF5, 0xFF965FF8, 0xFF0099AA)

// Public vector equivalents; the private source icon font is not redistributed.
private val programIcons =
    linkedMapOf(
        "rocket" to Icons.Rounded.RocketLaunch,
        "apartment" to Icons.Rounded.Apartment,
        "diamond" to Icons.Rounded.Diamond,
        "castle" to Icons.Rounded.Castle,
        "fort" to Icons.Rounded.Fort,
        "trophy" to Icons.Rounded.EmojiEvents,
        "crown" to Icons.Rounded.WorkspacePremium,
        "cake" to Icons.Rounded.Cake,
        "gift" to Icons.Rounded.CardGiftcard,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "tractor" to Icons.Rounded.Agriculture,
        "fan" to Icons.Rounded.Air,
        "glove" to Icons.Rounded.SportsMma,
        "fitness" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "pig" to Icons.Rounded.Savings,
        "horse" to Icons.Rounded.Sports,
        "globe" to Icons.Rounded.Public,
        "plant" to Icons.Rounded.Yard,
        "shoe" to Icons.Rounded.IceSkating,
        "tree" to Icons.Rounded.Park,
        "panda" to Icons.Rounded.Pets,
        "bike" to Icons.Rounded.DirectionsBike,
        "moon" to Icons.Rounded.DarkMode,
        "flower" to Icons.Rounded.LocalFlorist,
        "shirt" to Icons.Rounded.Checkroom,
        "food" to Icons.Rounded.Restaurant,
        "palette" to Icons.Rounded.Palette,
        "hiking" to Icons.Rounded.Hiking,
        "mask" to Icons.Rounded.TheaterComedy,
        "mountain" to Icons.Rounded.Terrain,
        "sailing" to Icons.Rounded.Sailing,
        "soccer" to Icons.Rounded.SportsSoccer,
        "star" to Icons.Rounded.Star,
        "bolt" to Icons.Rounded.Bolt,
        "flag" to Icons.Rounded.Flag,
    )

fun programIcon(id: String) = programIcons[id] ?: Icons.Rounded.RocketLaunch

@Composable
fun ProgramLibrary(
    data: AppData,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onStart: (String, String, Int) -> Unit,
) {
    var deleting by remember { mutableStateOf<TrainingProgram?>(null) }
    var replaceDraft by remember { mutableStateOf<TrainingProgram?>(null) }
    val catalog = vm.exercises()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PageHeading(
                "Programs",
                onBack,
                actions = { RoundAction(Icons.Rounded.Add, "Create program", onCreate) },
            )
        }
        if (data.programDraft != null)
            item {
                Column(Modifier.padding(18.dp)) {
                    PrimaryAction("Continue ${data.programDraft.name}", onEdit)
                    TextButton(vm::discardProgramDraft) { Text("Discard Draft") }
                }
            }
        if (data.programs.isEmpty())
            item {
                Column(Modifier.padding(18.dp)) {
                    EmptyContent(
                        Icons.Rounded.ViewWeek,
                        "Your program library",
                        "Build a program from scratch with workout and rest days.",
                        "Build From Scratch",
                        onCreate,
                    )
                }
            }
        items(data.programs, key = { it.id }) { p ->
            val periods = p.deloadPlacement?.cycleKeys(p.cycleCount) ?: (0 until p.cycleCount).toList()
            var selectedPeriod by rememberSaveable(p.id) { mutableIntStateOf(periods.first()) }
            val cycle = selectedPeriod.takeIf { it in periods } ?: periods.first()
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        programIcon(p.icon),
                        null,
                        tint = Color(p.color),
                        modifier = Modifier.size(32.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${p.days.size} days · ${p.cycleCount} ${p.cycleUnit.plural.lowercase()}${if (data.activeProgramId == p.id) " · Active" else ""}",
                            color = OF.muted,
                        )
                    }
                    IconButton({
                        if (data.programDraft == null) {
                            vm.beginProgram(p)
                            onEdit()
                        } else if (data.programDraft.id == p.id) onEdit() else replaceDraft = p
                    }) {
                        Icon(Icons.Rounded.Edit, "Edit ${p.name}")
                    }
                    IconButton({ deleting = p }) {
                        Icon(Icons.Rounded.DeleteOutline, "Delete ${p.name}")
                    }
                }
                Row {
                    TextButton({
                        vm.activateProgram(if (data.activeProgramId == p.id) null else p.id)
                    }) {
                        Text(if (data.activeProgramId == p.id) "Deactivate" else "Activate Program")
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    periods.forEach { n ->
                        FilterChip(
                            cycle == n,
                            { selectedPeriod = n },
                            label = { Text(if (n in 0 until p.cycleCount) "${p.cycleUnit.singular} ${n + 1}" else "Deload") },
                        )
                    }
                }
                p.days.forEach { day ->
                    val deload = p.deloadPlacement?.takeIf { it.sourceCycle == cycle }
                    val targets = day.exercises.map { planned ->
                        if (deload == null) planned.cycles.getOrNull(cycle)
                        else planned.targetForDeload(deload, catalog.find { it.id == planned.exerciseId })
                    }
                    val missingMetrics = deload != null && targets.any { it == null }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(day.name)
                            Text(
                                if (day.exercises.isEmpty()) "Rest Day"
                                else
                                    "${day.exercises.size} exercise${if (day.exercises.size == 1) "" else "s"}",
                                color = OF.muted,
                                fontSize = 12.sp,
                            )
                        }
                        if (day.exercises.isNotEmpty())
                            TextButton(
                                { onStart(p.id, day.id, cycle) },
                                enabled = !missingMetrics && targets.any { it != null && !it.skip && it.sets.isNotEmpty() },
                            ) {
                                Text("Start ${day.name}")
                            }
                    }
                    if (missingMetrics) Text(
                        "Deload unavailable: exercise metric information is missing. Choose a normal ${p.cycleUnit.singular.lowercase()} or replace the exercise.",
                        color = OF.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider(color = OF.line)
            }
        }
    }
    replaceDraft?.let { p ->
        ConfirmDialog(
            "Replace current draft?",
            "This discards the unsaved draft before editing ${p.name}. Saved programs stay unchanged.",
            "Replace Draft",
            { replaceDraft = null },
            {
                vm.beginProgram(p)
                replaceDraft = null
                onEdit()
            },
        )
    }
    deleting?.let { p ->
        ConfirmDialog(
            "Delete ${p.name}?",
            "Saved workout history will remain on this device.",
            "Delete",
            { deleting = null },
            {
                vm.deleteProgram(p.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun ProgramHeader(title: String, onBack: () -> Unit, onSettings: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(56.dp).background(OF.top)) {
        IconButton(onBack, Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Rounded.ChevronLeft, "Back")
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            fontSize = 20.sp,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (onSettings != null)
            IconButton(onSettings, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.Tune, "Program settings")
            }
    }
}

@Composable
fun ProgramSetup(
    program: TrainingProgram,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable(program.id) { mutableStateOf(program.name) }
    BackHandler { if (step == 1) step = 0 else onBack() }
    Column(Modifier.fillMaxSize().background(OF.top).imePadding()) {
        ProgramHeader("Create Program", { if (step == 1) step = 0 else onBack() })
        LinearProgressIndicator(
            progress = { if (step == 0) .5f else .85f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = Color.White,
            trackColor = OF.line,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(
                if (step == 0) "What would you like to name this program?"
                else "What color should we use to display this program?",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(26.dp))
            if (step == 0)
                OutlinedTextField(
                    name,
                    {
                        name = it
                        vm.editProgramDraft { p -> p.copy(name = it) }
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            else {
                ProgramColorChoices(program.color, program.icon) { color ->
                    vm.editProgramDraft { it.copy(color = color) }
                }
                Spacer(Modifier.height(18.dp))
                ProgramIconChoices(program.icon) { icon ->
                    vm.editProgramDraft { it.copy(icon = icon) }
                }
            }
        }
        PrimaryAction(
            "Next",
            { if (step == 0) step = 1 else onNext() },
            Modifier.padding(18.dp).fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
    }
}

@Composable
private fun ProgramColorChoices(selected: Long, icon: String, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        programColors.forEachIndexed { index, color ->
            Surface(
                onClick = { onSelect(color) },
                shape = RoundedCornerShape(10.dp),
                color = Color(color),
                border = if (selected == color) BorderStroke(3.dp, Color.White) else null,
                modifier =
                    Modifier.size(48.dp).semantics {
                        contentDescription =
                            "${listOf("Red", "Orange", "Amber", "Green", "Blue", "Purple", "Teal")[index]} program color"
                        this.selected = selected == color
                        role = Role.RadioButton
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(programIcon(icon), null, tint = Color.Black.copy(alpha = .6f))
                }
            }
        }
    }
}

@Composable
private fun ProgramIconChoices(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        programIcons.entries.chunked(6).forEach { icons ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                icons.forEach { (id, icon) ->
                    Surface(
                        onClick = { onSelect(id) },
                        modifier =
                            Modifier.size(48.dp).semantics {
                                contentDescription = "$id program icon"
                                this.selected = selected == id
                                role = Role.RadioButton
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        border = if (selected == id) BorderStroke(1.dp, Color.White) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramDayAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick,
        Modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = OF.elevated,
                contentColor = Color.White,
            ),
        shape = RoundedCornerShape(50),
        content = content,
    )
}

@Composable
fun ProgramEditor(
    data: AppData,
    selectedDayId: String,
    onSelectDay: (String) -> Unit,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onSaved: () -> Unit,
) {
    val p = data.programDraft ?: return
    val day = p.days.find { it.id == selectedDayId } ?: p.days.first()
    var cycleMuscles by rememberSaveable { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var gymMenu by remember { mutableStateOf(false) }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var deloadTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    fun editDay(block: (ProgramDay) -> ProgramDay) =
        vm.editProgramDraft { plan ->
            plan.copy(days = plan.days.map { if (it.id == day.id) block(it) else it })
        }
    Column(Modifier.fillMaxSize().background(OF.top)) {
        ProgramHeader("Create Program", onBack, { options = true })
        ScrollableTabRow(
            p.days.indexOf(day),
            edgePadding = 0.dp,
            containerColor = OF.top,
            contentColor = Color.White,
        ) {
            p.days.forEach { d ->
                Tab(d.id == day.id, { onSelectDay(d.id) }, text = { Text(d.name) })
            }
            Tab(
                false,
                {
                    val added = ProgramDay()
                    vm.editProgramDraft { it.copy(days = it.days + added) }
                    onSelectDay(added.id)
                },
                text = { Text("Add Day  +") },
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProgramDayAction(
                {
                    vm.editProgramDraft {
                        it.copy(days = it.days.filterNot { d -> d.id == day.id })
                    }
                },
                enabled = p.days.size > 1,
            ) {
                Icon(Icons.Rounded.RemoveCircleOutline, null)
                Text(" Remove")
            }
            Box {
                ProgramDayAction({ gymMenu = true }, enabled = day.exercises.isNotEmpty()) {
                    Icon(Icons.Rounded.Warehouse, null)
                    Text(
                        " ${data.gyms.find { it.id == day.gymId }?.name ?: data.gyms.firstOrNull()?.name ?: "Gym"}"
                    )
                }
                DropdownMenu(gymMenu, { gymMenu = false }) {
                    data.gyms.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g.name) },
                            onClick = {
                                editDay { it.copy(gymId = g.id) }
                                gymMenu = false
                            },
                        )
                    }
                }
            }
            ProgramDayAction({ rename = true }, enabled = day.exercises.isNotEmpty()) {
                Icon(Icons.Rounded.Edit, null)
                Text(" Rename")
            }
            ProgramDayAction(
                {
                    vm.editProgramDraft { plan ->
                        val days = plan.days.toMutableList()
                        val index = days.indexOfFirst { it.id == day.id }
                        if (index > 0) {
                            days[index] = days[index - 1]
                            days[index - 1] = day
                        }
                        plan.copy(days = days)
                    }
                },
                enabled = p.days.indexOf(day) > 0,
            ) {
                Text("Move Left")
            }
            ProgramDayAction(
                {
                    vm.editProgramDraft { plan ->
                        val days = plan.days.toMutableList()
                        val index = days.indexOfFirst { it.id == day.id }
                        if (index < days.lastIndex) {
                            days[index] = days[index + 1]
                            days[index + 1] = day
                        }
                        plan.copy(days = days)
                    }
                },
                enabled = p.days.indexOf(day) < p.days.lastIndex,
            ) {
                Text("Move Right")
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (day.exercises.isEmpty())
                item {
                    Text("Rest Day", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = OF.shape,
                        color = OF.top,
                        border = BorderStroke(1.dp, OF.line),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.BeachAccess,
                                null,
                                Modifier.size(44.dp),
                                tint = OF.line,
                            )
                            Text(
                                "You can convert the rest day into a workout day by adding exercises below",
                                Modifier.padding(start = 16.dp),
                                color = OF.muted,
                            )
                        }
                    }
                }
            if (day.exercises.isNotEmpty())
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Target Muscles",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(OF.background)
                                .padding(3.dp)
                                .selectableGroup()
                        ) {
                            listOf("Day", p.cycleUnit.singular).forEachIndexed { index, label ->
                                val selected = cycleMuscles == (index == 1)
                                Box(
                                    Modifier.clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selected) Color.White else Color.Transparent
                                        )
                                        .selectable(
                                            selected,
                                            role = Role.Tab,
                                            onClick = { cycleMuscles = index == 1 },
                                        )
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label, color = if (selected) Color.Black else Color.White)
                                }
                            }
                        }
                    }
                    val included =
                        if (cycleMuscles) p.days.flatMap { it.exercises } else day.exercises
                    val groups =
                        included
                            .flatMap { planned ->
                                val target = planned.cycles.first()
                                if (target.skip) emptyList()
                                else
                                    exercises
                                        .find { it.id == planned.exerciseId }
                                        ?.muscles
                                        .orEmpty()
                                        .map { it to target.sets.size }
                            }
                            .groupBy({ it.first }, { it.second })
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        groups.forEach { (muscle, counts) ->
                            Surface(
                                shape = OF.shape,
                                color = OF.top,
                                border = BorderStroke(1.dp, OF.line),
                            ) {
                                Row(
                                    Modifier.width(210.dp).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MuscleImage(muscle, Modifier.size(72.dp))
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(muscle)
                                        Text(
                                            "${counts.size} exercise${if (counts.size == 1) "" else "s"}",
                                            color = OF.muted,
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            "${counts.sum()} set${if (counts.sum() == 1) "" else "s"}",
                                            color = OF.muted,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${day.exercises.size} ${if (day.exercises.size == 1) "Exercise" else "Exercises"}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton({ onPick(day.id) }) {
                        Text("Add Exercises", textDecoration = TextDecoration.Underline)
                    }
                }
            }
            items(day.exercises, key = { it.id }) { planned ->
                val e = exercises.find { it.id == planned.exerciseId }
                var exerciseMenu by remember(planned.id) { mutableStateOf(false) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e != null) ExerciseImage(e, Modifier.size(56.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                e?.title ?: "Exercise",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                (e?.muscles.orEmpty() + e?.secondary.orEmpty())
                                    .distinct()
                                    .forEach { muscle ->
                                        val primary = muscle in e?.muscles.orEmpty()
                                        Surface(
                                            color = if (primary) OF.elevated else Color.Transparent,
                                            shape = RoundedCornerShape(3.dp),
                                            border =
                                                if (primary) null else BorderStroke(1.dp, OF.line),
                                        ) {
                                            Text(
                                                muscle,
                                                Modifier.padding(
                                                    horizontal = 5.dp,
                                                    vertical = 2.dp,
                                                ),
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                            }
                        }
                        Box {
                            IconButton({ exerciseMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    "Exercise options for ${e?.name ?: "exercise"}",
                                )
                            }
                            DropdownMenu(exerciseMenu, { exerciseMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Remove Exercise") },
                                    onClick = {
                                        editDay {
                                            it.copy(
                                                exercises =
                                                    it.exercises.filterNot { item ->
                                                        item.id == planned.id
                                                    }
                                            )
                                        }
                                        exerciseMenu = false
                                    },
                                )
                                listOf(-1 to "Move Up", 1 to "Move Down").forEach { (delta, label)
                                    ->
                                    val index = day.exercises.indexOf(planned)
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        enabled = index + delta in day.exercises.indices,
                                        onClick = {
                                            editDay { d ->
                                                val list = d.exercises.toMutableList()
                                                val item = list.removeAt(index)
                                                list.add(index + delta, item)
                                                d.copy(exercises = list)
                                            }
                                            exerciseMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = { targetId = planned.id },
                        color = OF.top,
                        shape = OF.shape,
                        border = BorderStroke(1.dp, OF.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${p.cycleCount} ${p.cycleUnit.plural}")
                            val first = planned.cycles.first()
                            Text(
                                if (first.skip) "Skipped"
                                else
                                    "${first.sets.size} Standard ${if (first.sets.size == 1) "Set" else "Sets"}",
                                color = OF.muted,
                            )
                            if (
                                planned.cycles
                                    .map { it.copy(sets = it.sets.map { s -> s.copy(id = "") }) }
                                    .distinct()
                                    .size > 1
                            )
                                Text(
                                    "${p.cycleUnit.singular}-specific targets",
                                    color = OF.muted,
                                    fontSize = 12.sp,
                                )
                        }
                    }
                    p.deloadPlacement?.let { placement ->
                        val target = planned.targetForDeload(placement, e)
                        TextButton(
                            { deloadTargetId = planned.id },
                            enabled = target != null,
                        ) {
                            Text("Deload · ${if (placement in planned.deloadTargets) "Custom targets" else "Derived targets"}")
                        }
                        if (target == null) Text(
                            "Deload unavailable: exercise metric information is missing.",
                            color = OF.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.background(OF.top).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                {
                    vm.saveProgram(false)
                    onSaved()
                },
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                enabled = p.name.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = OF.elevated,
                        contentColor = Color.White,
                    ),
            ) {
                Text("Save to Library")
            }
            PrimaryAction(
                "Activate Program",
                {
                    vm.saveProgram(true)
                    onSaved()
                },
                enabled = p.name.isNotBlank(),
            )
        }
    }
    if (rename)
        ProgramNameDialog("Rename Day", day.name, { rename = false }) { name ->
            editDay { it.copy(name = name) }
            rename = false
        }
    if (options)
        ProgramOptions(p, { options = false }) { changed ->
            vm.editProgramDraft { changed }
            options = false
        }
    day.exercises
        .find { it.id == targetId }
        ?.let { planned ->
            ProgramTargets(
                planned,
                exercises.find { it.id == planned.exerciseId }?.title ?: "Exercise",
                p.cycleUnit,
                { targetId = null },
            ) { targets, scope ->
                vm.editProgramDraft { current ->
                    current.withTargets(day.id, planned.id, targets, scope, exercises)
                }
                targetId = null
            }
        }
    p.deloadPlacement?.let { placement ->
        day.exercises.find { it.id == deloadTargetId }?.let { planned ->
            val exercise = exercises.find { it.id == planned.exerciseId }
            planned.targetForDeload(placement, exercise)?.let { target ->
                ProgramTargets(
                    planned.copy(cycles = listOf(target)),
                    exercise?.title ?: "Exercise",
                    p.cycleUnit,
                    { deloadTargetId = null },
                    deload = placement,
                    onReset = if (placement !in planned.deloadTargets) null else ({
                        editDay { d -> d.copy(exercises = d.exercises.map {
                            if (it.id == planned.id) it.copy(deloadTargets = it.deloadTargets - placement) else it
                        }) }
                        deloadTargetId = null
                    }),
                ) { changed, _ ->
                    changed[0]?.let { override ->
                        editDay { d -> d.copy(exercises = d.exercises.map {
                            if (it.id == planned.id) it.copy(deloadTargets = it.deloadTargets + (placement to override)) else it
                        }) }
                    }
                    deloadTargetId = null
                }
            }
        }
    }
}

@Composable
private fun ProgramNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton({ onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramOptions(
    p: TrainingProgram,
    onDismiss: () -> Unit,
    onSave: (TrainingProgram) -> Unit,
) {
    var name by remember { mutableStateOf(p.name) }
    var color by remember { mutableLongStateOf(p.color) }
    var icon by remember { mutableStateOf(p.icon) }
    var count by remember { mutableIntStateOf(p.cycleCount) }
    var deload by rememberSaveable { mutableStateOf(p.deloadPlacement) }
    var reducing by remember { mutableStateOf(false) }
    fun save() = onSave(p.resizeCycles(count).copy(name = name.trim(), color = color, icon = icon, deloadPlacement = deload))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.top,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(18.dp)) {
            Text("Program Settings", style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Number of ${p.cycleUnit.plural}", Modifier.weight(1f))
                    IconButton({ count-- }, enabled = count > 1) {
                        Icon(Icons.Rounded.Remove, "Fewer ${p.cycleUnit.plural.lowercase()}")
                    }
                    Text("$count")
                    IconButton({ count++ }, enabled = count < 52) {
                        Icon(Icons.Rounded.Add, "More ${p.cycleUnit.plural.lowercase()}")
                    }
                }
                Text(
                    "New ${p.cycleUnit.plural.lowercase()} copy the final targets. Reducing the count removes trailing targets when saved.",
                    color = OF.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Deload", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    listOf(null, ProgramDeload.FIRST, ProgramDeload.LAST).forEach { placement ->
                        val label = when (placement) {
                            null -> "None"
                            ProgramDeload.FIRST -> "First ${p.cycleUnit.singular}"
                            ProgramDeload.LAST -> "Last ${p.cycleUnit.singular}"
                        }
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = deload == placement,
                                role = Role.RadioButton,
                                onClick = { deload = placement },
                            ).heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = deload == placement, onClick = null)
                            Text(label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                Text("Adds a separate deload without replacing normal ${p.cycleUnit.plural.lowercase()}. Changes apply to this draft when you save.", color = OF.muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Program name") },
                )
                Text("Color & Icon", style = MaterialTheme.typography.titleMedium)
                ProgramColorChoices(color, icon) { color = it }
                ProgramIconChoices(icon) { icon = it }
            }
            PrimaryAction(
                "Save",
                { if (count < p.cycleCount) reducing = true else save() },
                enabled = name.isNotBlank(),
            )
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
    if (reducing)
        ConfirmDialog(
            "Remove trailing ${p.cycleUnit.plural.lowercase()}?",
            "Targets after ${p.cycleUnit.singular.lowercase()} $count will be removed from this draft. Saved programs and workout history stay unchanged until you save the program.",
            "Remove",
            { reducing = false },
            {
                reducing = false
                save()
            },
        )
}

private data class TargetInput(
    val id: String,
    val min: String = "",
    val max: String = "",
    val rir: String = "",
    val kind: SetKind = SetKind.STANDARD,
) {
    fun valid(maxRir: Int = 10) =
        listOf(min, max).all { it.isEmpty() || (it.toIntOrNull() ?: 0) in 1..9999 } &&
            (min.isEmpty() || max.isEmpty() || min.toInt() <= max.toInt()) &&
            (rir.isEmpty() || (rir.toIntOrNull() ?: -1) in 0..maxRir)

    fun target() = SetTarget(id, min.toIntOrNull(), max.toIntOrNull(), rir.toIntOrNull(), kind)
}

private enum class TargetCycleScope {
    SELECTED,
    FOLLOWING,
    ALL,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramTargets(
    e: PlannedExercise,
    name: String,
    unit: ProgramCycleUnit,
    onDismiss: () -> Unit,
    deload: ProgramDeload? = null,
    onReset: (() -> Unit)? = null,
    onSave: (Map<Int, CycleTarget>, TargetSaveScope) -> Unit,
) {
    val maxRir = if (deload == null) 10 else 13
    var pending by remember(e.id) { mutableStateOf(e.cycles) }
    var savingOptions by remember { mutableStateOf(false) }
    var saveScope by remember { mutableStateOf(TargetSaveScope.EXERCISE) }
    var cycleScope by remember { mutableStateOf(TargetCycleScope.SELECTED) }
    var cycle by
        rememberSaveable(e.id) {
            mutableIntStateOf(if (deload == null && e.cycles.haveEquivalentTargets()) -1 else 0)
        }
    var edited by remember(cycle) { mutableStateOf(false) }
    var rows by
        remember(cycle) {
            mutableStateOf(
                pending[cycle.coerceAtLeast(0)].sets.map {
                    TargetInput(
                        it.id,
                        it.repsMin?.toString() ?: "",
                        it.repsMax?.toString() ?: "",
                        it.rir?.toString() ?: "",
                        it.kind,
                    )
                }
            )
        }
    var skip by remember(cycle) { mutableStateOf(pending[cycle.coerceAtLeast(0)].skip) }
    var rest by
        remember(cycle) {
            mutableStateOf(pending[cycle.coerceAtLeast(0)].restSeconds?.toString() ?: "")
        }
    val valid =
        rows.isNotEmpty() &&
            rows.all { it.valid(maxRir) } &&
            (rest.isEmpty() || (rest.toIntOrNull() ?: -1) in 0..1800)
    fun committed(): List<CycleTarget> {
        val changed = CycleTarget(rows.map { it.target() }, skip, rest.toIntOrNull())
        return applyCycleTargetEdit(pending, cycle, changed, edited)
    }
    fun selectCycle(next: Int) {
        if (valid) {
            pending = committed()
            cycle = next
        }
    }
    fun save(scope: TargetSaveScope, cycleScope: TargetCycleScope = TargetCycleScope.SELECTED) {
        val current = CycleTarget(rows.map { it.target() }, skip, rest.toIntOrNull())
        val result =
            committed().mapIndexed { index, old ->
                if (
                    cycleScope == TargetCycleScope.ALL ||
                        (cycleScope == TargetCycleScope.FOLLOWING &&
                            index >= cycle.coerceAtLeast(0))
                )
                    current
                else old
            }
        val targets =
            result.indices
                .filter { index ->
                    result[index] != e.cycles[index] ||
                        (scope != TargetSaveScope.EXERCISE && (cycle == -1 || cycle == index)) ||
                        cycleScope == TargetCycleScope.ALL ||
                        (cycleScope == TargetCycleScope.FOLLOWING &&
                            index >= cycle.coerceAtLeast(0))
                }
                .associateWith { result[it] }
        onSave(targets, scope)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.top,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().padding(horizontal = 18.dp)
        ) {
            Text(
                name,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                textAlign = TextAlign.Center,
            )
            Text(
                if (deload != null) "Deload"
                else if (cycle == -1) "${e.cycles.size} ${unit.plural}"
                else "${unit.singular} ${cycle + 1}",
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
                color = OF.muted,
            )
            if (deload == null) Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    cycle == -1,
                    { selectCycle(-1) },
                    enabled = valid,
                    label = { Text("${e.cycles.size} ${unit.plural}") },
                )
                e.cycles.indices.forEach { index ->
                    FilterChip(
                        cycle == index,
                        { selectCycle(index) },
                        enabled = valid,
                        label = { Text("${unit.singular} ${index + 1}") },
                    )
                }
            }
            if (cycle == -1 && !pending.haveEquivalentTargets())
                Text(
                    "${unit.plural} have different targets. Edits here apply to all ${unit.plural.lowercase()}.",
                    color = OF.muted,
                    fontSize = 12.sp,
                )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set", Modifier.width(48.dp))
                    Text("Reps Min", Modifier.weight(1f))
                    Text("Reps Max", Modifier.weight(1f))
                    Text("RIR", Modifier.width(48.dp), textAlign = TextAlign.Center)
                }
                rows.forEachIndexed { index, input ->
                    key(input.id) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    { menu = true },
                                    Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(OF.elevated),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("${index + 1}")
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Remove Set") },
                                        onClick = {
                                            edited = true
                                            rows = rows.filterNot { it.id == input.id }
                                            menu = false
                                        },
                                    )
                                }
                            }
                            fun replace(changed: TargetInput) {
                                edited = true
                                rows = rows.map { if (it.id == input.id) changed else it }
                            }
                            TextField(
                                input.min,
                                { replace(input.copy(min = it)) },
                                Modifier.weight(1f).semantics {
                                    contentDescription = "Target set ${index + 1} minimum reps"
                                },
                                placeholder = { Text("Optional", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    TextFieldDefaults.colors(
                                        focusedContainerColor = OF.elevated,
                                        unfocusedContainerColor = OF.elevated,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                singleLine = true,
                                isError = !input.valid(maxRir),
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            TextField(
                                input.max,
                                { replace(input.copy(max = it)) },
                                Modifier.weight(1f).semantics {
                                    contentDescription = "Target set ${index + 1} maximum reps"
                                },
                                placeholder = { Text("Optional", fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    TextFieldDefaults.colors(
                                        focusedContainerColor = OF.elevated,
                                        unfocusedContainerColor = OF.elevated,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                singleLine = true,
                                isError = !input.valid(maxRir),
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            var rirMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    { rirMenu = true },
                                    Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(OF.elevated)
                                        .semantics {
                                            contentDescription =
                                                "Target set ${index + 1} RIR: ${input.rir.ifEmpty { "not set" }}"
                                        },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(input.rir.ifEmpty { "–" })
                                }
                                DropdownMenu(rirMenu, { rirMenu = false }) {
                                    (listOf("") + (0..maxRir).map { it.toString() }).forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text(value.ifEmpty { "Not set" }) },
                                            onClick = {
                                                replace(input.copy(rir = value))
                                                rirMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                FilledTonalIconButton(
                    {
                        edited = true
                        rows = rows + TargetInput(newId())
                    },
                    Modifier.size(48.dp),
                ) {
                    Icon(Icons.Rounded.Add, "Add Set")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Skip Exercise", Modifier.weight(1f))
                    Switch(
                        skip,
                        {
                            edited = true
                            skip = it
                        },
                        Modifier.semantics {
                            contentDescription = "Skip exercise in selected cycles"
                        },
                    )
                }
                OutlinedTextField(
                    rest,
                    {
                        edited = true
                        rest = it
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Rest timer override (seconds)") },
                    supportingText = {
                        Text(
                            "Leave empty to use exercise defaults, then the workout rest timer. Zero disables rest."
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (!valid)
                    Text(
                        "Use reps 1–9999 with minimum ≤ maximum, RIR 0–$maxRir, and rest 0–1800 seconds. Keep at least one set.",
                        color = MaterialTheme.colorScheme.error,
                    )
            }
            if (cycle >= 0 && deload == null)
                TextButton(
                    { save(TargetSaveScope.EXERCISE, TargetCycleScope.ALL) },
                    Modifier.fillMaxWidth(),
                    enabled = valid,
                ) {
                    Text("Save To All ${unit.plural}")
                }
            PrimaryAction(
                "Save",
                { save(TargetSaveScope.EXERCISE) },
                Modifier.padding(top = 16.dp).fillMaxWidth(),
                enabled = valid,
            )
            if (deload == null) TextButton({ savingOptions = true }, Modifier.fillMaxWidth(), enabled = valid) { Text("More Options") }
            if (onReset != null) TextButton(onReset, Modifier.fillMaxWidth()) { Text("Use Derived Targets") }
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
    if (savingOptions)
        ModalBottomSheet(
            onDismissRequest = { savingOptions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OF.top,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Text("Saving Options", style = MaterialTheme.typography.titleLarge)
                Text("${rows.size} ${if (rows.size == 1) "set" else "sets"}", color = OF.muted)
                if (cycle >= 0) {
                    Spacer(Modifier.height(18.dp))
                    Text("${unit.singular} Settings", style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.selectableGroup()) {
                        listOf(
                                TargetCycleScope.SELECTED to "${unit.singular} ${cycle + 1} only",
                                TargetCycleScope.FOLLOWING to
                                    "${unit.singular} ${cycle + 1} and following ${unit.plural.lowercase()}",
                                TargetCycleScope.ALL to "All ${unit.plural.lowercase()}",
                            )
                            .forEach { (scope, label) ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            cycleScope == scope,
                                            role = Role.RadioButton,
                                            onClick = { cycleScope = scope },
                                        )
                                        .heightIn(min = 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, Modifier.weight(1f))
                                    RadioButton(cycleScope == scope, null)
                                }
                            }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Exercise Settings", style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    listOf(
                            TargetSaveScope.EXERCISE to "$name only",
                            TargetSaveScope.DAY to "All exercises for the day",
                            TargetSaveScope.PROGRAM to "All exercises in the program",
                        )
                        .forEach { (scope, label) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(
                                        saveScope == scope,
                                        role = Role.RadioButton,
                                        onClick = { saveScope = scope },
                                    )
                                    .heightIn(min = 64.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, Modifier.weight(1f))
                                RadioButton(saveScope == scope, null)
                            }
                        }
                }
                Text(
                    "Replaces sets, skip and rest targets in the selected ${unit.plural.lowercase()}. Other ${unit.plural.lowercase()} and saved workout history stay unchanged.",
                    color = OF.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                PrimaryAction(
                    "Save",
                    { save(saveScope, cycleScope) },
                    Modifier.padding(top = 18.dp),
                )
                TextButton({ savingOptions = false }, Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
}
