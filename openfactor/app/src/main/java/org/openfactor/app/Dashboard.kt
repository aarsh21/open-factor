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
) {
    var customize by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var detailParent by rememberSaveable { mutableStateOf<String?>(null) }
    var chart by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf(BodyChartRange.MONTH) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val detailState = rememberSaveableStateHolder()
    val selection = data.preferences.dashboard
    var focus by
        rememberSaveable(selection.focuses) { mutableStateOf(selection.focuses.firstOrNull()) }
    fun closeDetail() {
        detail = detailParent
        detailParent = null
    }
    androidx.activity.compose.BackHandler(customize || detail != null || chart != null) {
        customize = false
        if (detail != null) closeDetail()
        chart = null
    }
    if (customize) {
        DashboardCustomization(data, exercises, { customize = false }) { selection ->
            vm.settings {
                it.copy(
                    dashboard = selection,
                    showWeeklyWorkouts = true,
                    showInsights = true,
                    showHabits = true,
                )
            }
            customize = false
        }
        return
    }
    chart?.let { key ->
        BodyChartScreen(data, key, range, { chart = it }, { range = it }, { chart = null })
        return
    }
    detail?.let { key ->
        detailState.SaveableStateProvider(key) {
            DashboardDetail(
                data,
                exercises,
                key,
                ::closeDetail,
                {
                    detailParent = detail
                    detail = it
                },
            )
        }
        return
    }
    val metrics = data.dashboardMetrics(exercises)
    val allWorkouts = metrics.allWorkouts
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 108.dp),
    ) {
        item {
            PageHeading(
                "Dashboard",
                actions = {
                    RoundAction(Icons.Rounded.Tune, "Customize Dashboard", { customize = true })
                },
            )
        }
        if (data.active != null)
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(OF.top)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    PrimaryAction(
                        "Resume ${data.active.name}",
                        onResume,
                        icon = Icons.Rounded.PlayArrow,
                    )
                }
            }
        if (data.preferences.showWeeklyWorkouts && focus == "records")
            item {
                Column(Modifier.fillMaxWidth().background(OF.top).padding(18.dp)) {
                    SectionTitle("Recent Records", "History", onHistory)
                    Text(
                        if (metrics.history.isEmpty()) "No Records Yet"
                        else
                            "Record detection is not available. Your saved workouts remain in History.",
                        color = OF.muted,
                    )
                    if ("weekly" in selection.focuses)
                        TextButton({ focus = "weekly" }) {
                            Text("Weekly Workouts")
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                }
            }
        if (data.preferences.showWeeklyWorkouts && focus == "weekly")
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(OF.top)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 26.dp)
                ) {
                    SectionTitle("Weekly Workouts")
                    Text(
                        if (metrics.includesActive)
                            "Completed working sets · includes current workout"
                        else "Completed working sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = OF.muted,
                    )
                    metrics.planCycle?.let { cycle ->
                        Text(
                            "Compared with Cycle ${cycle + 1} plan",
                            style = MaterialTheme.typography.bodySmall,
                            color = OF.muted,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        WeeklyRing(
                            metrics.totals.muscles,
                            metrics.plan?.muscles,
                            "Muscles",
                            Modifier.weight(1f),
                            small = true,
                        )
                        WeeklyRing(
                            metrics.totals.sets,
                            metrics.plan?.sets,
                            "Sets",
                            Modifier.weight(1.65f),
                            small = false,
                        )
                        WeeklyRing(
                            metrics.totals.exercises,
                            metrics.plan?.exercises,
                            "Exercises",
                            Modifier.weight(1f),
                            small = true,
                        )
                    }
                    Row(
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(OF.elevated)
                    ) {
                        listOf(false to "Active Program", true to "All Workouts").forEach {
                            (all, label) ->
                            TextButton(
                                { vm.settings { it.copy(dashboardAllWorkouts = all) } },
                                enabled =
                                    all || data.programs.any { it.id == data.activeProgramId },
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        containerColor =
                                            if (allWorkouts == all)
                                                MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                        contentColor =
                                            if (allWorkouts == all)
                                                MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Text(label)
                            }
                        }
                    }
                    if ("records" in selection.focuses)
                        TextButton(
                            { focus = "records" },
                            Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Recent Records")
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                }
            }
        items(selection.sections, key = { it.name }) { section ->
            DashboardTiles(
                data,
                exercises,
                section,
                { detail = it },
                { chart = it },
                onHistory,
                onBodyData,
                onStart,
            )
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp)) {
                TextButton(onLevels) {
                    Icon(Icons.Rounded.AccessibilityNew, null)
                    Text(" Muscle Levels")
                }
            }
        }
        item {
            Box(Modifier.padding(18.dp)) {
                OutlinedButton(onWorkout, Modifier.fillMaxWidth()) { Text("Workout Library") }
            }
        }
    }
}

@Composable
private fun WeeklyRing(
    value: Int,
    target: Int?,
    label: String,
    modifier: Modifier,
    small: Boolean,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription =
                "$label: $value completed this week" +
                    if (target != null && target > 0) ", $target in cycle plan" else ""
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)) {
                drawCircle(OF.line, style = Stroke(4.dp.toPx()))
                if (target != null && target > 0 && value > 0)
                    drawArc(
                        OF.blue,
                        -90f,
                        360f * (value.toFloat() / target).coerceIn(0f, 1f),
                        false,
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value.toString(),
                    fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                    fontSize = if (small) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("this week", fontSize = if (small) 12.sp else 14.sp, color = OF.muted)
            }
        }
        Text(
            label,
            Modifier.padding(top = 20.dp),
            fontSize = if (small) 18.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
        )
        Text(
            if (target != null) "$target planned" else "No plan",
            color = OF.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun InsightCard(
    title: String,
    subtitle: String,
    value: String,
    unit: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier.heightIn(min = 166.dp).clickable(onClick = onClick),
        shape = OF.shape,
        color = OF.card,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = OF.muted, fontSize = 12.sp)
            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = OF.line)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(" $unit", color = OF.muted)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
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
) {
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var delete by remember { mutableStateOf<Routine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp)) {
        item {
            PageHeading(
                "Workout",
                actions = {
                    RoundAction(Icons.Rounded.History, "Workout history", onHistory)
                    RoundAction(Icons.Rounded.Add, "New workout", onStart)
                },
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .background(OF.top)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val monday =
                    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (0..6).forEach { i ->
                    val day = monday.plusDays(i.toLong())
                    Column(
                        Modifier.width(44.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(50))
                            .border(
                                if (day == selectedDay) 2.dp else 1.dp,
                                if (day == selectedDay) Color.White else OF.line,
                                RoundedCornerShape(50),
                            )
                            .clickable { selectedDay = day },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(day.dayOfMonth.toString())
                        Text(
                            day.format(DateTimeFormatter.ofPattern("EE")),
                            color = OF.muted,
                            fontSize = 12.sp,
                        )
                        if (day == LocalDate.now()) Text("•", fontSize = 10.sp)
                    }
                }
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
                    Text(program.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${program.days.size} days · ${program.cycleCount} cycles",
                        color = OF.muted,
                    )
                    PrimaryAction("Open Program", onPrograms)
                } else Text("No active program", color = OF.muted)
                SectionTitle("Workout Library", "New Workout", onNewRoutine)
            }
        }
        if (data.routines.isEmpty())
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
        items(data.routines, key = { it.id }) { r ->
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
                        IconButton({ delete = r }) {
                            Icon(Icons.Rounded.MoreVert, "Manage ${r.name}")
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
