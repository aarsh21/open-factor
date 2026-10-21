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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
) {
    var customize by remember { mutableStateOf(false) }
    val allWorkouts = data.preferences.dashboardAllWorkouts
    val week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val scopedHistory =
        if (allWorkouts) data.history
        else
            data.history.filter {
                data.activeProgramId != null && it.programId == data.activeProgramId
            }
    val workouts = scopedHistory.filter { !it.localDate().isBefore(week) }
    val sets = workouts.sumOf { it.completedSets() }
    val exerciseIds =
        workouts
            .flatMap { it.exercises }
            .filter { it.sets.any { s -> s.completed } }
            .map { it.exerciseId }
            .toSet()
    val muscles = exercises.filter { it.id in exerciseIds }.flatMap { it.muscles }.toSet().size
    val targetSets =
        if (data.activeProgramId != null) data.routines.sumOf { it.exerciseIds.size * 3 } else 0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
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
        if (data.preferences.showWeeklyWorkouts)
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(OF.top)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 26.dp)
                ) {
                    SectionTitle("Weekly Workouts")
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        WeeklyRing(muscles, 20, "Muscles", Modifier.weight(1f), small = true)
                        WeeklyRing(sets, targetSets, "Sets", Modifier.weight(1.65f), small = false)
                        WeeklyRing(
                            exerciseIds.size,
                            if (data.activeProgramId != null)
                                data.routines.sumOf { it.exerciseIds.size }
                            else 0,
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
                                colors =
                                    ButtonDefaults.textButtonColors(
                                        containerColor =
                                            if (allWorkouts == all) Color.White
                                            else Color.Transparent,
                                        contentColor =
                                            if (allWorkouts == all) Color.Black else Color.White,
                                    ),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        if (data.preferences.showInsights)
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    SectionTitle("Insights & Analytics", "History", onHistory)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightCard(
                            "Workouts",
                            "Last 7 Workouts",
                            scopedHistory.take(7).sumOf { it.completedSets() }.toString(),
                            "sets",
                            Modifier.weight(1f),
                            onHistory,
                        )
                        InsightCard(
                            "Volume",
                            "This Week",
                            workouts.sumOf { it.volumeKg() }.displayWeight(data.preferences.pounds),
                            if (data.preferences.pounds) "lb" else "kg",
                            Modifier.weight(1f),
                            onHistory,
                        )
                    }
                    TextButton(onLevels) {
                        Icon(Icons.Rounded.AccessibilityNew, null)
                        Text(" Muscle Levels")
                    }
                }
            }
        if (data.preferences.showHabits)
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    SectionTitle("Habits")
                    Surface(shape = OF.shape, color = OF.card, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Workouts", style = MaterialTheme.typography.titleMedium)
                                Text("${workouts.size} completed this week", color = OF.muted)
                            }
                            IconButton(onStart) { Icon(Icons.Rounded.Add, "Start workout") }
                        }
                    }
                }
            }
        item {
            Box(Modifier.padding(18.dp)) {
                OutlinedButton(onWorkout, Modifier.fillMaxWidth()) { Text("Workout Library") }
            }
        }
    }
    if (customize)
        AlertDialog(
            onDismissRequest = { customize = false },
            title = { Text("Customize Dashboard") },
            text = {
                Column {
                    listOf(
                            "Weekly Workouts" to data.preferences.showWeeklyWorkouts,
                            "Insights & Analytics" to data.preferences.showInsights,
                            "Habits" to data.preferences.showHabits,
                        )
                        .forEachIndexed { i, (label, checked) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f))
                                Switch(
                                    checked,
                                    { value ->
                                        vm.settings {
                                            when (i) {
                                                0 -> it.copy(showWeeklyWorkouts = value)
                                                1 -> it.copy(showInsights = value)
                                                else -> it.copy(showHabits = value)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                }
            },
            confirmButton = { TextButton({ customize = false }) { Text("Done") } },
        )
}

@Composable
private fun WeeklyRing(value: Int, target: Int, label: String, modifier: Modifier, small: Boolean) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(2.dp)) {
                drawCircle(OF.line, style = Stroke(4.dp.toPx()))
                if (target > 0 && value > 0)
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
                Text(
                    if (target > 0) "${(target-value).coerceAtLeast(0)} left" else "No target",
                    fontSize = if (small) 12.sp else 14.sp,
                    color = OF.muted,
                )
            }
        }
        Text(
            label,
            Modifier.padding(top = 20.dp),
            fontSize = if (small) 16.sp else 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
        )
        Text(
            if (target > 0) "$target target" else "Set a routine",
            color = OF.muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun InsightCard(
    title: String,
    subtitle: String,
    value: String,
    unit: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier.height(166.dp).clickable(onClick = onClick),
        shape = OF.shape,
        color = OF.card,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = OF.muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
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
) {
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var delete by remember { mutableStateOf<Routine?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
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
                SectionTitle("Active Program", "New Workout", onNewRoutine)
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
