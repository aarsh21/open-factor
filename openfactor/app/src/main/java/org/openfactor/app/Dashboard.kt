package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun DashboardScreen(
    data: AppData,
    exercises: List<Exercise>,
    vm: WorkoutViewModel,
    onWorkout: () -> Unit,
    onHistory: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onLevels: () -> Unit,
    onBodyData: () -> Unit,
    onLogWeight: (() -> Unit)? = null,
    onHealthConnect: (() -> Unit)? = null,
) {
    val today = rememberToday()
    var customize by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var detailParent by rememberSaveable { mutableStateOf<String?>(null) }
    var chart by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf(BodyChartRange.MONTH) }
    var trainingPeriod by rememberSaveable { mutableStateOf(TrainingPeriod.WEEK) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    val training = remember(data.active, data.history, exercises, trainingPeriod, periodOffset, today) {
        data.trainingPeriod(exercises, trainingPeriod, periodOffset, today)
    }
    val detailState = rememberSaveableStateHolder()
    val selection = data.preferences.dashboard
    fun closeDetail() { detail = detailParent; detailParent = null }
    androidx.activity.compose.BackHandler(customize || detail != null || chart != null) {
        customize = false
        if (detail != null) closeDetail()
        chart = null
    }
    if (customize) {
        DashboardCustomization(data, exercises, { customize = false }) { selected ->
            vm.settings { it.copy(dashboard = selected, showWeeklyWorkouts = true, showInsights = true, showHabits = true) }
            customize = false
        }
        return
    }
    chart?.let { key ->
        BodyChartScreen(data, key, range, { chart = it }, { range = it }, { chart = null },
            onDismissTutorial = { vm.settings { it.copy(dismissedTutorials = it.dismissedTutorials + "bodyCharts") } }, onLogWeight = onLogWeight)
        return
    }
    if (detail == "workload" || detail?.startsWith("muscle:") == true) {
        LevelsScreen(data, exercises, { detail = null }, { detail = "exercise:$it" },
            referenceDate = minOf(today, training.last), referenceStart = training.first,
            initialMuscle = detail?.takeIf { it.startsWith("muscle:") }?.removePrefix("muscle:"))
        return
    }
    detail?.let { key ->
        detailState.SaveableStateProvider(key) {
            DashboardDetail(data.copy(preferences = data.preferences.copy(dashboardAllWorkouts = true)), exercises, key,
                ::closeDetail, { detailParent = detail; detail = it },
                today = minOf(today, training.last))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { PageHeading("Dashboard", actions = { RoundAction(Icons.Rounded.Tune, "Customize Dashboard", { customize = true }) }) }
        if (data.active != null) item {
            Box(Modifier.padding(horizontal = 18.dp)) { PrimaryAction("Resume ${data.active.name}", onResume, icon = Icons.Rounded.PlayArrow) }
        }
        if (data.preferences.showWeeklyWorkouts && "weekly" in selection.focuses) item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TrainingPeriodPanel(training, trainingPeriod, periodOffset,
                    onPeriod = { trainingPeriod = it; periodOffset = 0 },
                    onOffset = { periodOffset = it.coerceIn(-520, 0) })
                if (training.workingSets == 0 && periodOffset == 0) PrimaryAction("Start workout", onStart, icon = Icons.Rounded.Add)
            }
        }
        item { Box(Modifier.padding(horizontal = 18.dp)) { TrainingMusclePanel(training, { detail = "workload" }) { detail = "muscle:$it" } } }
        items(selection.sections.filter { data.hasVisibleDashboardSection(it, today) }, key = { it.name }) { section ->
            DashboardTiles(data, exercises, section, { detail = it }, { chart = it }, onHistory, onBodyData, onStart, onHealthConnect, onLogWeight)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onWorkout, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Templates") }
                OutlinedButton(onHistory, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("History") }
            }
        }
    }
}

@Composable
fun WorkoutScreen(
    data: AppData,
    exercises: List<Exercise>,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onRoutine: (Routine) -> Unit,
    onNewRoutine: () -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onHistory: () -> Unit,
    onPrograms: () -> Unit,
    onReorderRoutines: (List<String>) -> Unit,
) {
    var selectedEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val selectedDay = LocalDate.ofEpochDay(selectedEpoch)
    var calendarExpanded by rememberSaveable { mutableStateOf(false) }
    var programExpanded by rememberSaveable { mutableStateOf(true) }
    var libraryExpanded by rememberSaveable { mutableStateOf(true) }
    var delete by remember { mutableStateOf<Routine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp)) {
        item {
            PageHeading(
                "Workout",
                actions = {
                    RoundAction(
                        Icons.Rounded.CalendarMonth,
                        if (calendarExpanded) "Collapse calendar" else "Expand calendar",
                        { calendarExpanded = !calendarExpanded },
                    )
                    RoundAction(Icons.Rounded.History, "Workout history", onHistory)
                    RoundAction(Icons.Rounded.Add, "New workout", onStart)
                },
            )
        }
        item {
            WorkoutCalendar(
                selectedDay,
                calendarExpanded,
                data.history.map { it.localDate() }.toSet(),
            ) {
                selectedEpoch = it.toEpochDay()
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                if (data.active != null) {
                    SectionTitle("In Progress")
                    PrimaryAction("Resume ${data.active.name}", onResume)
                }
                SectionTitle("Active Program", "Programs", onPrograms)
                val program = data.programs.find { it.id == data.activeProgramId }
                if (program != null) {
                    ListItem(
                        headlineContent = { Text(program.name) },
                        supportingContent = {
                            Text(
                                "${program.days.count { it.exercises.isNotEmpty() }} workouts · ${program.cycleCount} ${program.cycleUnit.plural.lowercase()}"
                            )
                        },
                        leadingContent = {
                            Icon(programIcon(program.icon), null, tint = Color(program.color))
                        },
                        trailingContent = {
                            Icon(
                                if (programExpanded) Icons.Rounded.ExpandLess
                                else Icons.Rounded.ExpandMore,
                                null,
                            )
                        },
                        modifier =
                            Modifier.clickable { programExpanded = !programExpanded }
                                .semantics {
                                    contentDescription =
                                        "${if (programExpanded) "Collapse" else "Expand"} ${program.name}"
                                },
                        colors = ListItemDefaults.colors(containerColor = OF.card),
                    )
                    if (programExpanded)
                        program.days.forEach { day ->
                            ListItem(
                                headlineContent = {
                                    Text(if (day.exercises.isEmpty()) "Rest Day" else day.name)
                                },
                                supportingContent =
                                    if (day.exercises.isEmpty()) null
                                    else
                                        ({
                                            Text(
                                                day.exercises
                                                    .mapNotNull { p ->
                                                        exercises
                                                            .find { it.id == p.exerciseId }
                                                            ?.title
                                                    }
                                                    .joinToString(", "),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }),
                                modifier = Modifier.clickable(onClick = onPrograms),
                                colors = ListItemDefaults.colors(containerColor = OF.background),
                            )
                        }
                    TextButton(onPrograms) { Text("Open Program") }
                } else Text("No active program", color = OF.muted)
                SectionTitle(
                    "Workout Library",
                    if (libraryExpanded) "Collapse" else "Expand",
                    { libraryExpanded = !libraryExpanded },
                )
                if (libraryExpanded) TextButton(onNewRoutine) { Text("New Workout") }
            }
        }
        if (libraryExpanded && data.routines.isEmpty())
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    EmptyContent(
                        Icons.Rounded.FitnessCenter,
                        "Your workout library",
                        "Create a workout with your exercises, or start an empty session.",
                        "Create Workout",
                        onNewRoutine,
                    )
                    PrimaryAction("Start Empty Workout", onStart)
                }
            }
        items(if (libraryExpanded) data.routines else emptyList(), key = { it.id }) { r ->
            var menu by remember { mutableStateOf(false) }
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                shape = OF.shape,
                color = OF.card,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleLarge)
                            Text("${r.exerciseIds.size} exercises", color = OF.muted)
                        }
                        Box {
                            IconButton({ menu = true }) {
                                Icon(Icons.Rounded.MoreVert, "Manage ${r.name}")
                            }
                            DropdownMenu(menu, { menu = false }) {
                                val index = data.routines.indexOfFirst { it.id == r.id }
                                listOf(-1 to "Move Up", 1 to "Move Down").forEach { (delta, label)
                                    ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        enabled = index + delta in data.routines.indices,
                                        onClick = {
                                            val ids = data.routines.map { it.id }.toMutableList()
                                            java.util.Collections.swap(ids, index, index + delta)
                                            onReorderRoutines(ids)
                                            menu = false
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete Workout") },
                                    onClick = {
                                        menu = false
                                        delete = r
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        r.exerciseIds
                            .mapNotNull { id -> exercises.find { it.id == id }?.name }
                            .joinToString(", "),
                        Modifier.padding(vertical = 12.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = OF.muted,
                    )
                    PrimaryAction("Start Workout", { onRoutine(r) })
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                SectionTitle(selectedDay.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
                val dayWorkouts = data.history.filter { it.localDate() == selectedDay }
                if (dayWorkouts.isEmpty()) Text("No workouts recorded", color = OF.muted)
                else
                    dayWorkouts.forEach { w ->
                        ListItem(
                            headlineContent = { Text(w.name) },
                            supportingContent = {
                                Text(
                                    "${w.completedSets()} sets · ${w.volumeKg().displayWeight(data.preferences.pounds)} ${if(data.preferences.pounds)"lb" else "kg"}"
                                )
                            },
                            modifier = Modifier.clickable(onClick = onHistory),
                            colors = ListItemDefaults.colors(containerColor = OF.background),
                        )
                    }
            }
        }
    }
    delete?.let { r ->
        ConfirmDialog(
            "Delete ${r.name}?",
            "This removes the workout template. Your history stays saved.",
            "Delete",
            { delete = null },
            {
                onDeleteRoutine(r.id)
                delete = null
            },
        )
    }
}

@Composable
private fun WorkoutCalendar(
    selected: LocalDate,
    expanded: Boolean,
    recorded: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    var anchorEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorEpoch)
    val first = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Column(
        Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ anchorEpoch = anchor.minusWeeks(1).toEpochDay() }) {
                Icon(Icons.Rounded.ChevronLeft, "Previous week")
            }
            Text(
                anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton({ anchorEpoch = anchor.plusWeeks(1).toEpochDay() }) {
                Icon(Icons.Rounded.ChevronRight, "Next week")
            }
        }
        // The source expands into consecutive weeks, crossing month boundaries.
        // Eight visible weeks and arrow navigation are a bounded local browsing policy.
        repeat(if (expanded) 8 else 1) { week ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { index ->
                    val day = first.plusDays((week * 7 + index).toLong())
                    Column(
                        Modifier.weight(1f)
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(50))
                            .border(
                                if (day == selected) 2.dp else 1.dp,
                                if (day == selected) Color.White else OF.line,
                                RoundedCornerShape(50),
                            )
                            .clickable { onSelect(day) }
                            .semantics {
                                contentDescription =
                                    "${day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))}${if (day in recorded) ", workout recorded" else ""}"
                                this.selected = day == selected
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (expanded && day.dayOfMonth == 1)
                            Text(
                                day.format(DateTimeFormatter.ofPattern("MMM")),
                                color = OF.muted,
                                fontSize = 10.sp,
                            )
                        Text(day.dayOfMonth.toString())
                        Text(
                            day.format(DateTimeFormatter.ofPattern("EE")),
                            color = OF.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            if (day in recorded) "•" else if (day == LocalDate.now()) "·" else "",
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
        if (selected != LocalDate.now() || anchor != LocalDate.now())
            TextButton(
                {
                    anchorEpoch = LocalDate.now().toEpochDay()
                    onSelect(LocalDate.now())
                },
                Modifier.align(Alignment.End),
            ) {
                Text("Today")
            }
    }
}
