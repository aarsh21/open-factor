package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun BodyChartScreen(
    data: AppData,
    metricKey: String,
    range: BodyChartRange,
    onMetric: (String) -> Unit,
    onRange: (BodyChartRange) -> Unit,
    onBack: () -> Unit,
    onDismissTutorial: () -> Unit = {},
    onLogWeight: (() -> Unit)? = null,
) {
    val today = rememberToday()
    val dailyWeight = data.scaleWeights.weightTrend(today)
    val factor = if (data.preferences.pounds) 2.2046226218 else 1.0
    val series = data.bodyChartSeries().map { metric ->
        if (metric.key == "weight") metric.copy(points = dailyWeight.map { BodyChartPoint(it.recordId, it.date, it.weightKg * factor) }) else metric
    }
    val metric = series.firstOrNull { it.key == metricKey } ?: series.first()
    val window = range.window(today, metric.points)
    val points = metric.inWindow(window).filter { metric.key != "weight" || !it.date.isAfter(today) }
    var selection by rememberSaveable(metricKey, range) { mutableStateOf<String?>(null) }
    val chosen = points.find { it.recordId == selection }
    var choosingMetric by remember { mutableStateOf(false) }
    var help by rememberSaveable {
        mutableStateOf(false)
    }
    val dismissHelp = {
        help = false
        onDismissTutorial()
    }
    Column(Modifier.fillMaxSize()) {
        PageHeading(
            "Body Charts",
            onBack,
            actions = {
                IconButton({ help = true }) { Icon(Icons.Rounded.HelpOutline, "About body charts") }
            },
        )
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box {
                    OutlinedButton({ choosingMetric = true }, Modifier.fillMaxWidth()) {
                        Text(metric.label, Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                    DropdownMenu(
                        choosingMetric,
                        { choosingMetric = false },
                        Modifier.heightIn(max = 360.dp),
                    ) {
                        series.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onMetric(option.key)
                                    choosingMetric = false
                                },
                                trailingIcon =
                                    if (option.key == metricKey)
                                        ({ Icon(Icons.Rounded.Check, null) })
                                    else null,
                            )
                        }
                    }
                }
            }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val measurer = rememberTextMeasurer()
                    val widestLabel =
                        BodyChartRange.entries.maxOf {
                            measurer
                                .measure(it.label, MaterialTheme.typography.labelLarge)
                                .size
                                .width
                        }
                    val chipWidth = with(LocalDensity.current) { widestLabel.toDp() } + 32.dp
                    val columns = if (maxWidth >= chipWidth * 6 + 20.dp) 6 else 3
                    Column {
                        BodyChartRange.entries.chunked(columns).forEach { options ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                options.forEach { option ->
                                    FilterChip(
                                        range == option,
                                        { onRange(option) },
                                        label = { Text(option.label, maxLines = 1) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                Text("${window.start} – ${window.endInclusive}", color = OF.muted)
            }
            item {
                if (points.isEmpty()) {
                    EmptyContent(
                        Icons.Rounded.ShowChart,
                        "No entries in this range",
                        "Choose another range or add a reading.",
                    )
                    if (metric.key == "weight" && onLogWeight != null) PrimaryAction("Log weight", onLogWeight)
                } else {
                    val latest = points.last()
                    Text("${latest.value.bodyNumber()} ${metric.unit}", style = MaterialTheme.typography.headlineMedium)
                    Text(latest.date.toString(), color = OF.muted)
                    Spacer(Modifier.height(16.dp))
                    if (metric.key == "weight") {
                        val trend = data.scaleWeights.weightTrend(today).filter { it.date in window }
                        WeightTrendPlot(trend, data.preferences.pounds, Modifier.fillMaxWidth().height(240.dp))
                    } else BodyValuePlot(metric, points, window, chosen?.recordId) { selection = it }
                }
            }
            if (chosen != null)
                item {
                    Surface(shape = OF.shape, color = OF.card) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Selected entry", color = OF.muted)
                            Text(
                                "${chosen.value.bodyNumber()} ${metric.unit}",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(chosen.date.toString())
                        }
                    }
                }
            if (points.isNotEmpty()) {
                item {
                    Text("Saved entries", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (metric.key == "weight") "Tap a row to inspect a reading." else "Tap a dot or a row to inspect a reading.",
                        color = OF.muted,
                    )
                }
                items(points.asReversed(), key = { it.recordId }) { point ->
                    ListItem(
                        headlineContent = { Text("${point.value.bodyNumber()} ${metric.unit}") },
                        supportingContent = { Text(point.date.toString()) },
                        trailingContent =
                            if (point.recordId == chosen?.recordId)
                                ({ Icon(Icons.Rounded.Check, null) })
                            else null,
                        colors =
                            ListItemDefaults.colors(
                                containerColor =
                                    if (point.recordId == chosen?.recordId) OF.card
                                    else OF.background
                            ),
                        modifier =
                            Modifier.clickable { selection = point.recordId }
                                .semantics {
                                    contentDescription =
                                        "Select ${metric.label} ${point.date} ${point.value.bodyNumber()} ${metric.unit}"
                                    selected = point.recordId == chosen?.recordId
                                },
                    )
                }
            }
        }
    }
    if (help)
        AlertDialog(
            onDismissRequest = dismissHelp,
            title = { Text("About body charts") },
            text = {
                Text(
                    "Weight uses the newest manual reading for each date, otherwise the newest imported reading. Other metrics show saved readings individually. Blank fields are omitted; 0% body fat is a recorded value.\n\n" +
                        "1W shows Monday–Sunday. Other ranges show calendar months ending with the current month; All spans the saved dates. Future-dated entries appear only inside the displayed dates.\n\n" +
                        "Weight charts show your recorded values and a trailing seven-calendar-day average of available daily readings. Missing days are never counted as zero. Body fat and measurements are recorded values, not estimates.",
                    Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(dismissHelp) { Text("Got it") } },
        )
}

@Composable
private fun BodyValuePlot(
    series: BodyChartSeries,
    points: List<BodyChartPoint>,
    window: ClosedRange<LocalDate>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val minimum = points.minOf { it.value }
    val maximum = points.maxOf { it.value }
    val margin = maxOf((maximum - minimum) * .1, .1)
    val low = maxOf(0.0, minimum - margin)
    val high = maximum + margin
    val days = ChronoUnit.DAYS.between(window.start, window.endInclusive)
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val select by rememberUpdatedState(onSelect)
    fun position(point: BodyChartPoint, width: Float, height: Float): Offset {
        val x =
            if (days == 0L) .5
            else ChronoUnit.DAYS.between(window.start, point.date).toDouble() / days
        val y = (high - point.value) / (high - low)
        return Offset(
            inset + (x * (width - 2 * inset)).toFloat(),
            inset + (y * (height - 2 * inset)).toFloat(),
        )
    }
    val foreground = MaterialTheme.colorScheme.primary
    val line = OF.line
    Row(Modifier.fillMaxWidth().height(240.dp)) {
        Column(
            Modifier.fillMaxHeight().widthIn(min = 48.dp).padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(high.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
            Text(
                ((high + low) / 2).bodyNumber(),
                color = OF.muted,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(low.bodyNumber(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        }
        Canvas(
            Modifier.weight(1f)
                .fillMaxHeight()
                .semantics {
                    contentDescription = "${series.label} chart, ${points.size} saved entries"
                }
                .pointerInput(points, window) {
                    detectTapGestures { tap ->
                        points
                            .minByOrNull {
                                (position(it, size.width.toFloat(), size.height.toFloat()) - tap)
                                    .getDistanceSquared()
                            }
                            ?.let { select(it.recordId) }
                    }
                }
        ) {
            listOf(inset, size.height / 2, size.height - inset).forEach { y ->
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            points.forEach { point ->
                val center = position(point, size.width, size.height)
                drawCircle(foreground, 4.dp.toPx(), center)
                if (point.recordId == selectedId)
                    drawCircle(foreground, 9.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(window.start.toString(), color = OF.muted, style = MaterialTheme.typography.labelSmall)
        Text(
            window.endInclusive.toString(),
            color = OF.muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
