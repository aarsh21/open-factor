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
        .filter { muscle in it.muscles || muscle in it.secondary }
        .mapNotNull { e ->
            val count = sets[e.id].orEmpty().sumOf { it.completedWorkingSets() }
            // Retained ARM64 helper 0xd881ac: primary feature group 1, secondary 0.5.
            val factor = if (muscle in e.muscles) 1.0 else 0.5
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
) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var back by rememberSaveable { mutableStateOf(false) }
    var periods by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var exerciseDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.activity.compose.BackHandler(selected != null || exerciseDetail != null) {
        if (exerciseDetail != null) exerciseDetail = null else selected = null
    }
    exerciseDetail?.let { id ->
        DashboardDetail(
            data.copy(preferences = data.preferences.copy(dashboardAllWorkouts = true)),
            exercises,
            "exercise:$id",
            { exerciseDetail = null },
            {},
            onExercise,
        )
        return
    }
    val totals =
        remember(data.active, data.history, exercises, days) {
            (upperMuscleGroups + lowerMuscleGroups).associateWith { m ->
                muscleContributions(data.trainingWorkouts(), exercises, days, m).sumOf {
                    it.weightedSets
                } * 7.0 / days
            }
        }
    val context = LocalContext.current
    val body =
        remember(back, totals) {
            runCatching {
                    val xml =
                        context.assets
                            .open("reference/body/avatar_male_${if(back) "back" else "front"}.svg")
                            .bufferedReader()
                            .use { it.readText() }
                    val themed = xml.replace("#DCDCDC", "#414141").replace("#EEEEEE", "#555555")
                    val colored =
                        Regex("<path[^>]*>").replace(themed) { match ->
                            val id = Regex("id=\"([^\"]+)\"").find(match.value)?.groupValues?.get(1)
                            val muscle =
                                (upperMuscleGroups + lowerMuscleGroups).find {
                                    it.replace(" ", "").equals(id, true)
                                }
                            if (muscle != null && totals.getValue(muscle) > 0)
                                match.value.replace("#555555", "#276EF1")
                            else match.value
                        }
                    ByteBuffer.wrap(colored.toByteArray())
                }
                .getOrNull()
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { PageHeading("Levels", onBack) }
        item {
            Column(Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 18.dp)) {
                Box(Modifier.fillMaxWidth().height(490.dp), contentAlignment = Alignment.Center) {
                    if (body != null)
                        AsyncImage(
                            body,
                            "${if(back) "Back" else "Front"} muscle map",
                            Modifier.fillMaxSize().padding(vertical = 14.dp),
                        )
                    else
                        Icon(
                            Icons.Rounded.AccessibilityNew,
                            null,
                            Modifier.size(180.dp),
                            tint = OF.muted,
                        )
                    if (totals.values.all { it == 0.0 })
                        Text(
                            "Complete workouts\nto see your Set Levels",
                            color = OF.muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp)) {
                        RoundAction(Icons.Rounded.Sync, "Switch muscle map view", { back = !back })
                    }
                }
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    TextButton({ periods = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(8.dp)) { drawCircle(OF.blue) }
                            Text(
                                "  Average weekly sets over ",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (days == 7) "1-week"
                                else if (days == 30) "1-month" else "3-months",
                                textDecoration = TextDecoration.Underline,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                    }
                }
                Text(
                    "Primary sets + ½ secondary sets · color scale is local",
                    Modifier.padding(bottom = 12.dp),
                    color = OF.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        listOf("Upper Body Levels" to upperMuscleGroups, "Lower Body Levels" to lowerMuscleGroups)
            .forEach { (heading, muscles) ->
                item { Column(Modifier.padding(horizontal = 18.dp)) { SectionTitle(heading) } }
                item {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        color = OF.card,
                        shape = OF.shape,
                    ) {
                        Column {
                            muscles.forEachIndexed { index, muscle ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable { selected = muscle }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    MuscleImage(muscle, Modifier.size(74.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(muscle, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (totals.getValue(muscle) == 0.0) "0 sets"
                                            else
                                                "${"%.1f".format(java.util.Locale.US, totals.getValue(muscle))} sets/week",
                                            color = OF.muted,
                                        )
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
                                }
                                if (index < muscles.lastIndex)
                                    HorizontalDivider(
                                        Modifier.padding(start = 102.dp),
                                        color = OF.line,
                                    )
                            }
                        }
                    }
                }
            }
    }
    if (periods)
        ModalBottomSheet(onDismissRequest = { periods = false }) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton({ periods = false }, Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.Close, "Close time period")
                }
                Text("Time Period", style = MaterialTheme.typography.titleLarge)
            }
            listOf(7 to "1-week", 30 to "1-month", 90 to "3-months").forEach { (n, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            days = n
                            periods = false
                        }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f))
                    RadioButton(
                        days == n,
                        {
                            days = n
                            periods = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    selected?.let { m ->
        val contributions = muscleContributions(data.trainingWorkouts(), exercises, days, m)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ selected = null }) {
                            Icon(Icons.Rounded.Close, "Close muscle levels")
                        }
                        Text(m, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Average weekly sets over ${if(days == 7) "1-week" else if(days == 30) "1-month" else "3-months"}",
                        color = OF.muted,
                    )
                    Text(
                        "Completed working sets: primary muscles count 1 set, secondary muscles ½ set. Calendar windows and side/advanced-set handling remain local policies.",
                        Modifier.padding(vertical = 12.dp),
                        color = OF.muted,
                    )
                    if (contributions.isEmpty()) Text("No logged sets in this period.")
                }
                items(contributions, key = { it.exercise.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { exerciseDetail = c.exercise.id }
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.exercise.title, Modifier.weight(1f))
                        Text(
                            "${"%.1f".format(java.util.Locale.US, c.weightedSets * 7.0 / days)} sets/week",
                            color = OF.muted,
                        )
                    }
                }
            }
        }
    }
}
