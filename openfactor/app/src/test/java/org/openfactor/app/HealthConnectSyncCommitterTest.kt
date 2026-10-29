package org.openfactor.app

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class HealthConnectSyncCommitterTest {
    private val date = LocalDate.of(2026, 10, 29)
    private fun sync(withReadings: Boolean = true) = HealthConnectSyncData(
        weights = if (withReadings) listOf(ScaleWeightRecord(
            id = "provider", date = date.minusDays(1), weightKg = 72.6,
            healthConnectOrigin = "scale.fixture",
        )) else emptyList(),
        state = HealthConnectSyncState(
            lastSyncedAtMillis = 100, firstDate = date.minusDays(29), lastDate = date,
            weightReadings = if (withReadings) 1 else 0, weightAccess = true,
        ),
    )

    @Test fun returnHappensAfterDurableImportAndKeepsTheProviderDate() {
        var stored = AppData()
        var returns = 0
        val committer = HealthConnectSyncCommitter()
        val outcome = committer.commit(HealthConnectResult.Success(sync()), save = {
            stored = stored.withHealthConnectSync(it)
        }, onSuccessfulSync = {
            assertEquals(72.6, stored.scaleWeights.single().weightKg, 0.0)
            assertEquals(date.minusDays(1), stored.scaleWeights.single().date)
            assertEquals(100L, stored.healthConnect.lastSyncedAtMillis)
            returns++
        })
        assertEquals(HealthConnectSyncCommitOutcome.SAVED, outcome)
        assertEquals(1, returns)
        assertTrue(committer.returnedToCaller)
    }

    @Test fun anEmptySuccessfulImportReturnsOnceIncludingAfterRecreation() {
        var saves = 0
        var returns = 0
        val original = HealthConnectSyncCommitter()
        val result = HealthConnectResult.Success(sync(withReadings = false))
        repeat(2) {
            assertEquals(HealthConnectSyncCommitOutcome.SAVED, original.commit(result,
                save = { it.state.validate(); saves++ }, onSuccessfulSync = { returns++ }))
        }
        val restored = HealthConnectSyncCommitter(original.returnedToCaller)
        restored.commit(result, save = { saves++ }, onSuccessfulSync = { returns++ })
        assertEquals(3, saves)
        assertEquals(1, returns)
    }

    @Test fun failedSaveStaysInThePanelAndCanRetryWithoutLosingTheReturn() {
        val committer = HealthConnectSyncCommitter()
        var returns = 0
        val result = HealthConnectResult.Success(sync())
        assertEquals(HealthConnectSyncCommitOutcome.SAVE_FAILED,
            committer.commit(result, save = { throw IOException("Disk full") }, onSuccessfulSync = { returns++ }))
        assertFalse(committer.returnedToCaller)
        assertEquals(0, returns)
        assertEquals(HealthConnectSyncCommitOutcome.SAVED,
            committer.commit(result, save = {}, onSuccessfulSync = { returns++ }))
        assertEquals(1, returns)
    }

    @Test fun denialRevocationUnavailableAndFailedReadsNeverSaveOrReturn() {
        val committer = HealthConnectSyncCommitter()
        val results = listOf(HealthConnectResult.PermissionRequired(emptySet()),
            HealthConnectResult.PermissionRevoked, HealthConnectResult.Failed,
            HealthConnectResult.ProviderUnavailable(HealthConnectAvailability.UNAVAILABLE))
        for (result in results) {
            assertEquals(HealthConnectSyncCommitOutcome.NOT_SAVED,
                committer.commit(result, save = { fail("No provider data to save") },
                    onSuccessfulSync = { fail("Must retain the actionable panel") }))
        }
        assertFalse(committer.returnedToCaller)
    }

    @Test fun settingsSyncWithoutReturnCallbackRemainsOpenAndDoesNotConsumeNavigation() {
        val committer = HealthConnectSyncCommitter()
        var saves = 0
        assertEquals(HealthConnectSyncCommitOutcome.SAVED,
            committer.commit(HealthConnectResult.Success(sync()), save = { saves++ }, onSuccessfulSync = null))
        assertEquals(1, saves)
        assertFalse(committer.returnedToCaller)
    }

    @Test fun cancellationDoesNotMasqueradeAsSaveFailureOrReturn() {
        val committer = HealthConnectSyncCommitter()
        assertThrows(CancellationException::class.java) {
            committer.commit(HealthConnectResult.Success(sync()), save = { throw CancellationException() },
                onSuccessfulSync = { fail("Cancelled work must not return") })
        }
        assertFalse(committer.returnedToCaller)
    }
}
