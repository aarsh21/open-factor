package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.nio.ByteBuffer
import java.time.LocalDate

val upperMuscleGroups =
    listOf(
        "Chest",
        "Lats",
        "Upper Back",
        "Upper Traps",
        "Front Delts",
        "Side Delts",
        "Rear Delts",
        "Biceps",
        "Triceps",
        "Forearms",
        "Abs",
        "Obliques",
        "Serratus",
        "Lower Back",
        "Neck",
    )
val lowerMuscleGroups =
    listOf("Quads", "Hamstrings", "Glutes", "Adductors", "Abductors", "Calves", "Tibs")

data class MuscleContribution(val exercise: Exercise, val sets: Int)

fun muscleContributions(
    history: List<Workout>,
    exercises: List<Exercise>,
    days: Int,
    muscle: String,
): List<MuscleContribution> {
    val today = LocalDate.now()
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
            val count =
                sets[e.id].orEmpty().sumOf { se -> se.sets.count { it.completed && !it.warmup } }
            if (count > 0) MuscleContribution(e, count) else null
        }
        .sortedByDescending { it.sets }
}

@Composable
fun LevelsScreen(
    data: AppData,
    exercises: List<Exercise>,
    onBack: (() -> Unit)? = null,
    onExercise: (String) -> Unit,
) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var back by rememberSaveable { mutableStateOf(false) }
    var periods by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val totals =
        remember(data.active, data.history, exercises, days) {
            (upperMuscleGroups + lowerMuscleGroups).associateWith { m ->
                muscleContributions(data.trainingWorkouts(), exercises, days, m).sumOf { it.sets } /
                    (days / 7.0)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                        Canvas(Modifier.size(8.dp)) { drawCircle(OF.blue) }
                        Text("  Average weekly sets over ")
                        Text(
                            if (days == 7) "1-week" else if (days == 30) "1-month" else "3-months",
                            textDecoration = TextDecoration.Underline,
                        )
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(periods, { periods = false }) {
                        listOf(7 to "1-week", 30 to "1-month", 90 to "3-months").forEach {
                            (n, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    days = n
                                    periods = false
                                },
                            )
                        }
                    }
                }
            }
        }
        listOf("Upper Body Levels" to upperMuscleGroups, "Lower Body Levels" to lowerMuscleGroups)
            .forEach { (heading, muscles) ->
                item { Column(Modifier.padding(horizontal = 18.dp)) { SectionTitle(heading) } }
                muscles.forEach { muscle ->
                    item {
                        Surface(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .clickable { selected = muscle },
                            color = OF.card,
                            shape = OF.shape,
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                MuscleImage(muscle, Modifier.size(74.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(muscle, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${"%.1f".format(java.util.Locale.US, totals.getValue(muscle))} sets",
                                        color = OF.muted,
                                    )
                                    LinearProgressIndicator(
                                        progress = {
                                            (totals.getValue(muscle) / 20)
                                                .toFloat()
                                                .coerceIn(0f, 1f)
                                        },
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(50)),
                                        color = OF.blue,
                                        trackColor = OF.line,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = OF.muted)
                            }
                        }
                    }
                }
            }
    }
    selected?.let { m ->
        val contributions = muscleContributions(data.trainingWorkouts(), exercises, days, m)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(m) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Average weekly sets over ${if(days == 7) "1-week" else if(days == 30) "1-month" else "3-months"}",
                        color = OF.muted,
                    )
                    if (contributions.isEmpty()) Text("No logged sets in this period.")
                    contributions.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = null
                                onExercise(c.exercise.id)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.exercise.title, Modifier.weight(1f))
                            Text("${c.sets} sets", color = OF.muted)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ selected = null }) { Text("Done") } },
        )
    }
}
