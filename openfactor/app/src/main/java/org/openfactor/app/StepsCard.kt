package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToLong

@Composable
internal fun StepsCard(state: HealthConnectSyncState, onHealthConnect: (() -> Unit)?) {
    val today = rememberToday()
    var length by rememberSaveable { mutableIntStateOf(7) }
    var selectedEpoch by rememberSaveable(today, length) { mutableLongStateOf(today.toEpochDay()) }
    val history = remember(state.dailySteps, today, length) { state.stepHistory(today, length) }
    val index = history.days.indexOfFirst { it.date.toEpochDay() == selectedEpoch }.coerceAtLeast(0)
    val selected = history.days[index]
    val formatter = remember { NumberFormat.getIntegerInstance() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Steps", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (onHealthConnect != null) TextButton(onHealthConnect) {
                Text(if (state.lastSyncedAtMillis == null) "Connect" else "Health Connect")
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = OF.shape, color = OF.card) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (state.lastSyncedAtMillis == null) {
                    Text("Connect Health Connect to see your step history.", color = OF.muted)
                    if (onHealthConnect != null) PrimaryAction("Connect Health Connect", onHealthConnect)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7 to "7 days", 30 to "30 days").forEach { (days, label) ->
                            FilterChip(selected = length == days, onClick = { length = days }, label = { Text(label) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text(selected.count?.let(formatter::format) ?: "—", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(if (selected.date == today) "Today" else selected.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = OF.muted)
                        }
                        IconButton({ selectedEpoch = history.days[index - 1].date.toEpochDay() }, enabled = index > 0) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous step day")
                        }
                        IconButton({ selectedEpoch = history.days[index + 1].date.toEpochDay() }, enabled = index < history.days.lastIndex) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next step day")
                        }
                    }
                    StepBars(history, selected.date) { selectedEpoch = it.toEpochDay() }
                    if (selected.count == null) Text("No reading for this date", color = OF.muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = OF.line)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StepStat("Total", if (history.observedDays == 0) "—" else formatter.format(history.total), Modifier.weight(1f))
                        StepStat("Daily average", history.average?.let { formatter.format(it.roundToLong()) } ?: "—", Modifier.weight(1f))
                        StepStat("Best day", history.best?.count?.let(formatter::format) ?: "—", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${history.observedDays} of $length days recorded", color = OF.muted, style = MaterialTheme.typography.labelSmall)
                        Text(state.lastSyncedAtMillis?.let { "Synced " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) }.orEmpty(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OF.muted)
    }
}

@Composable
private fun StepBars(history: StepHistory, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val max = history.days.mapNotNull { it.count }.maxOrNull()?.coerceAtLeast(1000L) ?: 1000L
    val ceiling = (ceil(max / 1000.0) * 1000.0).coerceAtLeast(1000.0)
    val line = OF.line
    val muted = OF.muted
    val accent = OF.accent
    val foreground = MaterialTheme.colorScheme.onSurface
    val number = remember { NumberFormat.getIntegerInstance() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Steps", color = OF.muted, style = MaterialTheme.typography.labelSmall)
            Text(number.format(ceiling.toLong()), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(154.dp).semantics {
            val day = history.days.find { it.date == selected }
            contentDescription = "Daily step chart. ${history.days.first().date} to ${history.days.last().date}. Selected $selected: ${day?.count?.let { "$it steps" } ?: "no reading"}. Use Previous step day and Next step day to inspect each date. Gaps are missing readings."
        }.pointerInput(history) {
            detectTapGestures { offset ->
                val index = (offset.x / size.width * history.days.size).toInt().coerceIn(history.days.indices)
                onSelect(history.days[index].date)
            }
        }) {
            val top = 6.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val plotHeight = bottom - top
            val cell = size.width / history.days.size
            val width = (cell * if (history.days.size <= 7) .56f else .7f).coerceAtMost(24.dp.toPx())
            listOf(top, (top + bottom) / 2, bottom).forEach { y ->
                drawLine(line.copy(alpha = .65f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            history.days.forEachIndexed { index, day ->
                val x = cell * (index + .5f)
                val color = if (day.date == selected) foreground else accent
                when {
                    day.count == null -> drawLine(muted, Offset(x - width.coerceAtMost(8.dp.toPx()) / 2, bottom - 1.dp.toPx()), Offset(x + width.coerceAtMost(8.dp.toPx()) / 2, bottom - 1.dp.toPx()), 1.5.dp.toPx())
                    day.count == 0L -> drawCircle(color, 2.5.dp.toPx(), Offset(x, bottom - 3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    else -> {
                        val height = (day.count.toDouble() / ceiling * plotHeight).toFloat().coerceAtLeast(3.dp.toPx())
                        drawRoundRect(color, Offset(x - width / 2, bottom - height), Size(width, height), CornerRadius(3.dp.toPx()))
                    }
                }
                if (day.date == selected) drawCircle(foreground, 2.dp.toPx(), Offset(x, size.height - 1.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(history.days.first().date.format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            Text(history.days.last().date.format(DateTimeFormatter.ofPattern("d MMM")), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
