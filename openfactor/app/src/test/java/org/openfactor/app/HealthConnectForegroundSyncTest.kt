package org.openfactor.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HealthConnectForegroundSyncTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun state(at: Long = 1000) = HealthConnectSyncState(
        lastSyncedAtMillis = at, firstDate = today.minusDays(29), lastDate = today,
        stepsAccess = true,
    )

    @Test fun neverConnectsImplicitlyOrCompetesWithOpenHealthPanel() {
        val gate = HealthConnectForegroundGate()
        assertFalse(gate.begin(HealthConnectSyncState(), true))
        assertFalse(gate.begin(state(), false))
        assertTrue(gate.begin(state(), true))
    }

    @Test fun everyForegroundVisitRefreshesEvenFreshlySyncedData() {
        val gate = HealthConnectForegroundGate()
        assertTrue(gate.begin(state(), true))
        assertFalse(gate.begin(state(), true))
        gate.leaveForeground()
        assertTrue(gate.begin(state(at = 1001), true))
    }

    @Test fun repeatedResumeCallbacksAndChangedSnapshotsCannotStartDuplicateReads() {
        val gate = HealthConnectForegroundGate()
        assertTrue(gate.begin(state(), true))
        assertFalse(gate.begin(state(at = 1002), true))
        assertFalse(gate.begin(state(at = 999), true))
        gate.leaveForeground()
        assertTrue(gate.begin(state(at = 999), true))
    }
}
