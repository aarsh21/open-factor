package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.nio.ByteBuffer
import java.time.LocalDate

val upperMuscleGroups =
    listOf(
        "Chest",
        "Upper Back",
        "Lats",
        "Biceps",
        "Triceps",
        "Front Delts",
        "Side Delts",
        "Lower Back",
        "Abs",
        "Upper Traps",
        "Rear Delts",
        "Forearms",
        "Obliques",
        "Neck",
        "Serratus",
    )
val lowerMuscleGroups =
    listOf("Quads", "Hamstrings", "Glutes", "Adductors", "Abductors", "Calves", "Tibs")

data class MuscleContribution(val exercise: Exercise, val sets: Int, val weightedSets: Double)

fun muscleContributions(
    history: List<Workout>,
    exercises: List<Exercise>,
    days: Int,
    muscle: String,
    today: LocalDate = LocalDate.now(),
): List<MuscleContribution> {
    require(days > 0)
    val first = today.minusDays(days.toLong() - 1)
    val sets =
        history
            .distinctBy { it.id }
            .filter { it.localDate() in first..today }
            .flatMap { it.exercises }
            .groupBy { it.exerciseId }
    return exercises
        .filter { muscle in it.muscles.ifEmpty { listOf(it.muscle) } || muscle in it.secondary }
        .mapNotNull { e ->
            val count = sets[e.id].orEmpty().sumOf { it.completedWorkingSets() }
            // Retained ARM64 helper 0xd881ac: primary feature group 1, secondary 0.5.
            val factor = if (muscle in e.muscles.ifEmpty { listOf(e.muscle) }) 1.0 else 0.5
            if (count > 0) MuscleContribution(e, count, count * factor) else null
        }
        .sortedByDescending { it.weightedSets }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelsScreen(
    data: AppData,
    exercises: List<Exercise>,
    onBack: (() -> Unit)? = null,
    onExercise: (String) -> Unit,
    referenceDate: LocalDate? = null,
) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var back by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var policy by rememberSaveable { mutableStateOf(false) }
    val currentDate = rememberToday()
    val today = minOf(referenceDate ?: currentDate, currentDate)
    val first = if (days == 7) today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) else today.minusDays(days - 1L)
    val workouts = remember(data.active, data.history, first, today) { data.trainingWorkouts().filter { it.localDate() in first..today } }
    val totals = remember(workouts, exercises) { muscleWorkloads(workouts, exercises) }
    androidx.activity.compose.BackHandler(selected != null || exerciseDetail != null) {
        if (exerciseDetail != null) exerciseDetail = null else selected = null
    }
    exerciseDetail?.let { id ->
        DashboardDetail(data.copy(preferences = data.preferences.copy(dashboardAllWorkouts = true)), exercises,
            "exercise:$id", { exerciseDetail = null }, {}, onExercise, today)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeading("Muscle workload", onBack, actions = { IconButton({ policy = true }) { Icon(Icons.Rounded.Info, "How muscle sets are counted") } }) }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "Week", 30 to "30 days", 90 to "90 days").forEach { (value, label) ->
                        FilterChip(days == value, { days = value }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text("${first.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))} – ${today.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}", color = OF.muted)
                Box(Modifier.fillMaxWidth().height(340.dp)) {
                    TrainingMuscleMap(totals.associate { it.muscle to it.weightedSets }, Modifier.fillMaxSize(), back)
                    IconButton({ back = !back }, Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Rounded.Sync, "Show ${if (back) "front" else "back"} muscle map") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Weighted sets", style = MaterialTheme.typography.labelMedium, color = OF.muted)
                    listOf("0" to 0.0, "1–4" to 1.0, "5–9" to 5.0, "10+" to 10.0).forEach { (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.size(9.dp)) { drawCircle(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(muscleWorkloadColor(value)))) }
                            Text(label, style = MaterialTheme.typography.labelSmall, color = OF.muted)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Muscle", Modifier.weight(1f), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                listOf("Direct", "Indirect", "Total").forEach { Text(it, Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, color = OF.muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
        items(totals, key = { it.muscle }) { muscle ->
            Row(Modifier.fillMaxWidth().clickable { selected = muscle.muscle }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(muscle.muscle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                listOf(muscle.directSets.toString(), muscle.secondarySets.toString(), muscle.weightedSets.bodyNumber()).forEach { value ->
                    Text(value, Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = OF.line)
        }
    }
    if (policy) AlertDialog(onDismissRequest = { policy = false }, title = { Text("Muscle set counts") },
        text = { Text("Direct sets target a primary muscle. Indirect sets involve a secondary muscle. The weighted total is direct sets + ½ indirect sets.\n\nOnly completed working sets count. Warmups are excluded. Left/right pairs and Myo segments count as one logical set. This is a training-volume view, not a recovery or strength score.") },
        confirmButton = { TextButton({ policy = false }) { Text("Done") } })
    selected?.let { muscle ->
        val contributions = muscleContributions(workouts, exercises, java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1, muscle, today)
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(muscle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton({ selected = null }) { Icon(Icons.Rounded.Close, "Close muscle workload") }
                    }
                    Text("${first} – ${today}", color = OF.muted)
                    if (contributions.isEmpty()) Text("No completed working sets in this period.", Modifier.padding(vertical = 24.dp), color = OF.muted)
                }
                items(contributions, key = { it.exercise.id }) { contribution ->
                    ListItem(headlineContent = { Text(contribution.exercise.title) },
                        supportingContent = { Text("${contribution.sets} ${if (muscle in contribution.exercise.muscles.ifEmpty { listOf(contribution.exercise.muscle) }) "direct" else "indirect"} sets") },
                        trailingContent = { Text(contribution.weightedSets.bodyNumber()) },
                        modifier = Modifier.clickable { exerciseDetail = contribution.exercise.id },
                        colors = ListItemDefaults.colors(containerColor = OF.background))
                }
            }
        }
    }
}
