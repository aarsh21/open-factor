package org.openfactor.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HealthConnectForegroundSyncTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun state(at: Long = 1000, date: LocalDate = today) = HealthConnectSyncState(
        lastSyncedAtMillis = at, firstDate = date.minusDays(29), lastDate = date,
        stepsAccess = true,
    )

    @Test fun neverConnectsImplicitlyOrCompetesWithOpenHealthPanel() {
        val gate = HealthConnectForegroundGate()
        assertFalse(gate.begin(HealthConnectSyncState(), true, 9999999, today, 1))
        assertFalse(gate.begin(state(), false, 9999999, today, 1))
        assertTrue(gate.begin(state(), true, 9999999, today, 1))
    }

    @Test fun successfulSyncFreshnessAndFailedAttemptThrottleBothUseFifteenMinutes() {
        val gate = HealthConnectForegroundGate()
        assertFalse(gate.begin(state(), true, 1000 + HEALTH_CONNECT_FOREGROUND_INTERVAL_MS - 1, today, 1))
        assertTrue(gate.begin(state(), true, 1000 + HEALTH_CONNECT_FOREGROUND_INTERVAL_MS, today, 2))
        assertFalse(gate.begin(state(), true, 9999999, today, 3))
        assertTrue(gate.begin(state(), true, 9999999, today, 2 + HEALTH_CONNECT_FOREGROUND_INTERVAL_MS))
    }

    @Test fun dayRolloverBackwardClockAndCancelledAttemptDoNotLeaveDataStuck() {
        val gate = HealthConnectForegroundGate()
        assertTrue(gate.begin(state(), true, 999, today, 100)) // Wall clock went backward.
        assertFalse(gate.begin(state(), true, 999, today, 101))
        assertTrue(gate.begin(state(), true, 1001, today.plusDays(1), 102)) // New local day needs fresh data.
        gate.cancel()
        assertTrue(gate.begin(state(), true, 1001, today.plusDays(1), 103))
    }
}
