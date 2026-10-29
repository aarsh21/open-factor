package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
internal fun TrainingWeekPanel(week: TrainingWeek, offset: Int, onOffset: (Int) -> Unit) {
    val today = rememberToday()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (offset == 0) "This week" else "Training week", style = MaterialTheme.typography.titleLarge)
                Text("${week.first.format(DateTimeFormatter.ofPattern("d MMM"))} – ${week.last.format(DateTimeFormatter.ofPattern("d MMM"))}", color = OF.muted)
            }
            IconButton({ onOffset(offset - 1) }, enabled = offset > -520) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous training week")
            }
            IconButton({ onOffset(offset + 1) }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next training week")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            TrainingCircle("Muscles", week.trainedMuscles, Modifier.weight(1f))
            TrainingCircle("Sets", week.workingSets, Modifier.weight(1.5f), prominent = true)
            TrainingCircle("Exercises", week.exercises, Modifier.weight(1f))
        }
        if (week.workingSets > 0) {
            val max = week.days.maxOfOrNull { it.sets }?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().height(146.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                week.days.forEach { day ->
                    Column(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                        contentDescription = "${day.date}: ${day.sets} completed working sets"
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (day.sets > 0) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.sets.toString(), style = MaterialTheme.typography.labelSmall, color = OF.muted)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.widthIn(max = 34.dp).fillMaxWidth().height((100f * day.sets / max).dp)
                                    .background(if (day.date == today) MaterialTheme.colorScheme.primary else OF.blue, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                            }
                        }
                        HorizontalDivider(color = OF.line)
                        Text(day.date.dayOfWeek.name.take(1), Modifier.padding(top = 9.dp), style = MaterialTheme.typography.labelMedium, color = OF.muted)
                    }
                }
            }
        }
        if (week.includesActive) Text("Includes completed sets in your active workout", color = OF.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrainingCircle(label: String, count: Int, modifier: Modifier, prominent: Boolean = false) {
    val line = OF.line
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "$count $label this training week"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = if (prominent) 3.dp.toPx() else 2.dp.toPx()
                drawCircle(line, radius = (size.minDimension - width) / 2, style = Stroke(width))
            }
            Text(count.toString(), fontSize = if (prominent) 34.sp else 26.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = OF.muted, maxLines = 1)
    }
}

internal fun muscleWorkloadColor(sets: Double): String = when {
    sets >= 10 -> "#3982FF"
    sets >= 5 -> "#79ACFF"
    sets > 0 -> "#B9D3FF"
    else -> "#555555"
}

@Composable
internal fun TrainingMuscleMap(totals: Map<String, Double>, modifier: Modifier = Modifier, back: Boolean = false) {
    val context = LocalContext.current
    val art by produceState<ByteBuffer?>(null, context, totals, back) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val xml = context.assets.open("reference/body/avatar_male_${if (back) "back" else "front"}.svg").bufferedReader().use { it.readText() }
                val themed = xml.replace("#DCDCDC", "#353535").replace("#EEEEEE", "#555555")
                val colored = Regex("<path[^>]*>").replace(themed) { match ->
                    val id = Regex("id=\"([^\"]+)\"").find(match.value)?.groupValues?.get(1)
                    val entry = totals.entries.find { it.key.replace(" ", "").equals(id, true) }
                    if (entry != null) match.value.replace("#555555", muscleWorkloadColor(entry.value)) else match.value
                }
                ByteBuffer.wrap(colored.toByteArray())
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (art != null) AsyncImage(art, "${if (back) "Back" else "Front"} muscle workload map", Modifier.fillMaxSize())
        else Icon(Icons.Rounded.AccessibilityNew, "Muscle workload map unavailable", Modifier.size(80.dp), tint = OF.muted)
    }
}

@Composable
internal fun TrainingMusclePanel(week: TrainingWeek, onDetails: () -> Unit, onMuscle: (String) -> Unit) {
    var back by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Muscle workload", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onDetails) { Text("All muscles"); Icon(Icons.Rounded.ChevronRight, null) }
        }
        if (week.trainedMuscles == 0) {
            Text("Complete a working set to see your muscle workload.", color = OF.muted)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(.42f).height(280.dp)) {
                    TrainingMuscleMap(week.muscles.associate { it.muscle to it.weightedSets }, Modifier.fillMaxSize(), back)
                    IconButton({ back = !back }, Modifier.align(Alignment.BottomStart).background(OF.top, RoundedCornerShape(50))) {
                        Icon(Icons.Rounded.Sync, "Show ${if (back) "front" else "back"} muscle map")
                    }
                }
                Column(Modifier.weight(.58f)) {
                    week.muscles.filter { it.weightedSets > 0 }.take(5).forEach { muscle ->
                        Column(Modifier.fillMaxWidth().clickable { onMuscle(muscle.muscle) }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(muscle.muscle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text(muscle.weightedSets.bodyNumber(), style = MaterialTheme.typography.titleMedium)
                            }
                            val relative = (muscle.weightedSets / week.muscles.maxOf { it.weightedSets }).toFloat()
                            val track = OF.elevated
                            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                                val corners = CornerRadius(2.dp.toPx())
                                drawRoundRect(track, cornerRadius = corners)
                                drawRoundRect(OF.blue, size = size.copy(width = size.width * relative), cornerRadius = corners)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weighted sets", style = MaterialTheme.typography.labelMedium, color = OF.muted)
                Text("Primary 1 · secondary ½", style = MaterialTheme.typography.labelSmall, color = OF.muted)
            }
        }
    }
}

@Composable
internal fun WeightTrendCard(data: AppData, onChart: () -> Unit, onLogWeight: () -> Unit) {
    val today = rememberToday()
    val readings = remember(data.scaleWeights, today) { data.scaleWeights.weightTrend(today) }
    val latest = readings.lastOrNull()
    Surface(Modifier.fillMaxWidth(), shape = OF.shape, color = OF.card) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weight trend", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onLogWeight) { Icon(Icons.Rounded.Add, null); Text(" Log weight") }
            }
            if (latest == null) {
                Text("No weight readings yet", color = OF.muted)
                OutlinedButton(onLogWeight, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Log your first weight") }
            } else {
                val factor = if (data.preferences.pounds) 2.2046226218 else 1.0
                val unit = if (data.preferences.pounds) "lb" else "kg"
                Row(Modifier.fillMaxWidth().clickable(onClick = onChart), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${(latest.weightKg * factor).bodyNumber()} $unit", style = MaterialTheme.typography.headlineMedium)
                        Text(latest.date.format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted, style = MaterialTheme.typography.labelMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(latest.averageKg * factor).bodyNumber()} $unit", style = MaterialTheme.typography.titleLarge)
                        Text("7-day average", color = OF.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
                val first = today.minusDays(29)
                val visible = readings.filter { it.date >= first }.ifEmpty { readings.takeLast(30) }
                WeightTrendPlot(visible, data.preferences.pounds, Modifier.fillMaxWidth().height(168.dp).clickable(onClick = onChart))
                TextButton(onChart, Modifier.align(Alignment.End)) { Text("View chart"); Icon(Icons.Rounded.ChevronRight, null) }
            }
        }
    }
}

/** Exact dated readings and their trailing seven-calendar-day mean, never an estimated body value. */
@Composable
internal fun WeightTrendPlot(readings: List<WeightTrendReading>, pounds: Boolean, modifier: Modifier = Modifier) {
    if (readings.isEmpty()) return
    val factor = if (pounds) 2.2046226218 else 1.0
    val values = readings.flatMap { listOf(it.weightKg * factor, it.averageKg * factor) }
    val low = (values.min() - maxOf(.5, (values.max() - values.min()) * .15)).coerceAtLeast(0.0)
    val high = values.max() + maxOf(.5, (values.max() - values.min()) * .15)
    val first = readings.first().date
    val last = readings.last().date
    val days = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1)
    val foreground = MaterialTheme.colorScheme.onSurface
    Column {
        Text(if (pounds) "lb" else "kg", Modifier.align(Alignment.End), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        Row(modifier) {
            Column(Modifier.fillMaxHeight().widthIn(min = 46.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(high.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                Text(((high + low) / 2).bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                Text(low.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
                contentDescription = "Weight chart, ${readings.size} dated readings, ${first} to ${last}. Latest ${readings.last().weightKg * factor} ${if (pounds) "pounds" else "kilograms"}. Blue line is the seven-day average."
            }) {
                val inset = 8.dp.toPx()
                fun point(date: LocalDate, kg: Double) = Offset(
                    if (readings.size == 1) size.width / 2 else inset + ((ChronoUnit.DAYS.between(first, date).toFloat() / days) * (size.width - inset * 2)),
                    inset + (((high - kg * factor) / (high - low)) * (size.height - inset * 2)).toFloat(),
                )
                listOf(inset, size.height / 2, size.height - inset).forEach { y ->
                    drawLine(OF.line.copy(alpha = .7f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (readings.size > 1) {
                    val raw = Path().apply { readings.forEachIndexed { i, value -> val p = point(value.date, value.weightKg); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    val trend = Path().apply { readings.forEachIndexed { i, value -> val p = point(value.date, value.averageKg); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(raw, foreground.copy(alpha = .5f), style = Stroke(1.5.dp.toPx()))
                    drawPath(trend, OF.blue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else drawCircle(OF.blue, 6.dp.toPx(), point(readings.single().date, readings.single().averageKg))
                readings.forEach { value -> drawCircle(foreground, 3.dp.toPx(), point(value.date, value.weightKg)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 46.dp, top = 6.dp), horizontalArrangement = if (first == last) Arrangement.Center else Arrangement.SpaceBetween) {
            Text(first.format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            if (last != first) Text(last.format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(8.dp)) { drawCircle(foreground) }
                Text("Readings", color = OF.muted, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.width(14.dp).height(8.dp)) { drawLine(OF.blue, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx()) }
                Text("7-day average", color = OF.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
