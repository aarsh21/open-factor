package org.openfactor.app

import java.time.LocalDate

/** A missing provider aggregate remains null; a measured zero remains an observed day. */
internal data class StepHistoryDay(val date: LocalDate, val count: Long?)
internal data class StepHistory(val days: List<StepHistoryDay>) {
    val observedDays: Int get() = days.count { it.count != null }
    val total: Long get() = days.sumOf { it.count ?: 0L }
    val average: Double? get() = if (observedDays == 0) null else total.toDouble() / observedDays
    val best: StepHistoryDay? get() = days.filter { it.count != null }.maxByOrNull { it.count!! }
}

internal fun HealthConnectSyncState.stepHistory(today: LocalDate, length: Int): StepHistory {
    require(length in 1..30)
    val byDate = dailySteps.filter { it.count >= 0 && !it.date.isAfter(today) }.associate { it.date to it.count }
    return StepHistory((0 until length).map { index ->
        val date = today.minusDays((length - index - 1).toLong())
        StepHistoryDay(date, byDate[date])
    })
}
