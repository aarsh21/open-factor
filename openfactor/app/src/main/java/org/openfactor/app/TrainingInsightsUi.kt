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
import androidx.compose.ui.graphics.vector.ImageVector
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
internal fun TrainingPeriodPanel(
    summary: TrainingWeek,
    period: TrainingPeriod,
    offset: Int,
    onPeriod: (TrainingPeriod) -> Unit,
    onOffset: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TrainingPeriod.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = period == value,
                    onClick = { onPeriod(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, TrainingPeriod.entries.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(value.label) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val dateLabel = if (period == TrainingPeriod.MONTH) {
                summary.first.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            } else {
                val firstPattern = if (summary.first.year != summary.last.year) "d MMM yyyy" else "d MMM"
                "${summary.first.format(DateTimeFormatter.ofPattern(firstPattern))} – ${summary.last.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
            }
            Text(dateLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ onOffset(offset - 1) }, enabled = offset > -520) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous training ${period.label.lowercase()}")
            }
            IconButton({ onOffset(offset + 1) }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next training ${period.label.lowercase()}")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            TrainingCircle("Muscles", summary.trainedMuscles, Icons.Rounded.AccessibilityNew, Modifier.weight(1f))
            TrainingCircle("Sets", summary.workingSets, Icons.Rounded.Layers, Modifier.weight(1.4f), prominent = true)
            TrainingCircle("Exercises", summary.exercises, Icons.Rounded.FitnessCenter, Modifier.weight(1f))
        }
        if (summary.includesActive) Text("Includes completed sets in your active workout", color = OF.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrainingCircle(label: String, count: Int, icon: ImageVector, modifier: Modifier, prominent: Boolean = false) {
    val line = OF.line
    val accent = OF.accent
    val surface = OF.card
    val foreground = MaterialTheme.colorScheme.onSurface
    val number = count.toString()
    val numberSize = (if (prominent) 40f else 30f) / (number.length / 3f).coerceAtLeast(1f)
    Column(modifier.semantics(mergeDescendants = true) {
        contentDescription = "$count $label in selected training period"
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            // These are counters, not goal rings: every keyline is complete and constant.
            // The larger center medallion carries the one accent; no invented completion ratio.
            Canvas(Modifier.fillMaxSize()) {
                val width = if (prominent) 2.5.dp.toPx() else 1.5.dp.toPx()
                val radius = (size.minDimension - width) / 2
                drawCircle(if (prominent) accent.copy(alpha = .09f) else surface, radius = radius)
                drawCircle(if (prominent) accent.copy(alpha = .85f) else line, radius = radius, style = Stroke(width))
                drawCircle(if (prominent) accent.copy(alpha = .16f) else foreground.copy(alpha = .05f),
                    radius = radius - 6.dp.toPx(), style = Stroke(.75.dp.toPx()))
            }
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (prominent) 6.dp else 4.dp)) {
                Icon(icon, null, Modifier.size(if (prominent) 18.dp else 16.dp),
                    tint = if (prominent) accent else OF.muted)
                Text(number, maxLines = 1, softWrap = false, color = if (count == 0) OF.muted else foreground,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = numberSize.sp, lineHeight = (numberSize + 2).sp,
                        fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum",
                        letterSpacing = (-.5).sp,
                    ))
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (prominent) foreground else OF.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun muscleWorkloadColor(sets: Double, ramp: PaletteColors = AppPalette.CHARCOAL.colors(dark = true)): String = when {
    sets >= 10 -> ramp.highlight.svgHex()
    sets >= 5 -> ramp.highlightMid.svgHex()
    sets > 0 -> ramp.highlightSoft.svgHex()
    else -> "#555555"
}

@Composable
internal fun TrainingMuscleMap(totals: Map<String, Double>, modifier: Modifier = Modifier, back: Boolean = false) {
    val context = LocalContext.current
    val ramp = OF.palette
    val art by produceState<ByteBuffer?>(null, context, totals, back, ramp.highlight) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val xml = context.assets.open("reference/body/avatar_male_${if (back) "back" else "front"}.svg").bufferedReader().use { it.readText() }
                val themed = xml.replace("#DCDCDC", "#353535").replace("#EEEEEE", "#555555")
                val colored = Regex("<path[^>]*>").replace(themed) { match ->
                    val id = Regex("id=\"([^\"]+)\"").find(match.value)?.groupValues?.get(1)
                    val entry = totals.entries.find { it.key.replace(" ", "").equals(id, true) }
                    if (entry != null) match.value.replace("#555555", muscleWorkloadColor(entry.value, ramp)) else match.value
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
                            val accent = OF.accent
                            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                                val corners = CornerRadius(2.dp.toPx())
                                drawRoundRect(track, cornerRadius = corners)
                                drawRoundRect(accent, size = size.copy(width = size.width * relative), cornerRadius = corners)
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
    val line = OF.line
    val accent = OF.accent
    Column {
        Text(if (pounds) "lb" else "kg", Modifier.align(Alignment.End), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        Row(modifier) {
            Column(Modifier.fillMaxHeight().widthIn(min = 46.dp).padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(high.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                Text(((high + low) / 2).bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                Text(low.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
                contentDescription = "Weight chart, ${readings.size} dated readings, ${first} to ${last}. Latest ${readings.last().weightKg * factor} ${if (pounds) "pounds" else "kilograms"}. The bold line is the seven-day average."
            }) {
                val inset = 8.dp.toPx()
                fun point(date: LocalDate, kg: Double) = Offset(
                    if (readings.size == 1) size.width / 2 else inset + ((ChronoUnit.DAYS.between(first, date).toFloat() / days) * (size.width - inset * 2)),
                    inset + (((high - kg * factor) / (high - low)) * (size.height - inset * 2)).toFloat(),
                )
                listOf(inset, size.height / 2, size.height - inset).forEach { y ->
                    drawLine(line.copy(alpha = .7f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (readings.size > 1) {
                    val raw = Path().apply { readings.forEachIndexed { i, value -> val p = point(value.date, value.weightKg); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    val trend = Path().apply { readings.forEachIndexed { i, value -> val p = point(value.date, value.averageKg); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(raw, foreground.copy(alpha = .5f), style = Stroke(1.5.dp.toPx()))
                    drawPath(trend, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else drawCircle(accent, 6.dp.toPx(), point(readings.single().date, readings.single().averageKg))
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
                Canvas(Modifier.width(14.dp).height(8.dp)) { drawLine(accent, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx()) }
                Text("7-day average", color = OF.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
