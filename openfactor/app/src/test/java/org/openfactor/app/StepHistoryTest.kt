package org.openfactor.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StepHistoryTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test fun missingDaysAreGapsAndMeasuredZeroCountsInAverage() {
        val history = HealthConnectSyncState(dailySteps = listOf(
            HealthConnectStepDay(today.minusDays(2), 4000), HealthConnectStepDay(today, 0),
        )).stepHistory(today, 7)
        assertEquals(7, history.days.size)
        assertNull(history.days[5].count)
        assertEquals(0L, history.days.last().count)
        assertEquals(2, history.observedDays)
        assertEquals(4000L, history.total)
        assertEquals(2000.0, history.average!!, 0.0)
        assertEquals(today.minusDays(2), history.best!!.date)
    }

    @Test fun windowExcludesOldAndFutureReadings() {
        val history = HealthConnectSyncState(dailySteps = listOf(
            HealthConnectStepDay(today.minusDays(30), 20000), HealthConnectStepDay(today.minusDays(29), 100),
            HealthConnectStepDay(today, 300), HealthConnectStepDay(today.plusDays(1), 100000),
        )).stepHistory(today, 30)
        assertEquals(today.minusDays(29), history.days.first().date)
        assertEquals(400L, history.total)
        assertEquals(200.0, history.average!!, 0.0)
    }

    @Test fun emptyProviderHistoryHasNoInventedAverageOrBestDay() {
        val history = HealthConnectSyncState().stepHistory(today, 7)
        assertTrue(history.days.all { it.count == null })
        assertEquals(0, history.observedDays)
        assertNull(history.average)
        assertNull(history.best)
    }
}
