package org.openfactor.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class BodyChartPoint(val recordId: String, val date: LocalDate, val value: Double)

data class BodyChartSeries(
    val key: String,
    val label: String,
    val unit: String,
    val points: List<BodyChartPoint>,
)

/** Raw saved values only. Missing percentages and measurements are not zeroes. */
fun AppData.bodyChartSeries(): List<BodyChartSeries> =
    listOf(
        BodyChartSeries(
            "weight",
            "Scale Weight",
            if (preferences.pounds) "lb" else "kg",
            scaleWeights.map {
                BodyChartPoint(
                    it.id,
                    it.date,
                    it.weightKg * if (preferences.pounds) 2.2046226218 else 1.0,
                )
            },
        ),
        BodyChartSeries(
            "bodyFat",
            "Scale Body Fat",
            "%",
            scaleWeights.mapNotNull { record ->
                record.bodyFatPercent?.let { BodyChartPoint(record.id, record.date, it) }
            },
        ),
        BodyChartSeries(
            "visualBodyFat",
            "Visual Body Fat",
            "%",
            bodyMeasurements.mapNotNull { record ->
                record.visualBodyFatPercent?.let { BodyChartPoint(record.id, record.date, it) }
            },
        ),
    ) +
        BodyMeasurement.entries.map { field ->
            BodyChartSeries(
                field.key,
                field.label,
                "cm",
                bodyMeasurements.mapNotNull { record ->
                    record.centimeters[field]?.let { BodyChartPoint(record.id, record.date, it) }
                },
            )
        }

enum class BodyChartRange(val label: String, private val months: Long = 0) {
    WEEK("1W"),
    MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    YEAR("1Y", 12),
    ALL("All");

    /** Local calendar-window policy, not a recovered source aggregation or smoothing rule. */
    fun window(today: LocalDate, points: List<BodyChartPoint>): ClosedRange<LocalDate> =
        when (this) {
            WEEK ->
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let {
                    it..it.plusDays(6)
                }
            ALL ->
                (points.minOfOrNull { it.date } ?: today)..(points.maxOfOrNull { it.date } ?: today)
            else ->
                today.withDayOfMonth(1).minusMonths(months - 1)..today.with(
                        TemporalAdjusters.lastDayOfMonth()
                    )
        }
}

fun BodyChartSeries.inWindow(window: ClosedRange<LocalDate>): List<BodyChartPoint> =
    points.filter { it.date in window }.sortedBy { it.date }
