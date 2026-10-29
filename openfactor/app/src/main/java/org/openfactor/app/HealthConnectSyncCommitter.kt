package org.openfactor.app

import kotlinx.coroutines.CancellationException

internal enum class HealthConnectSyncCommitOutcome { SAVED, NOT_SAVED, SAVE_FAILED }

/** Navigation follows a durable import, never permission consent or a stale connected state. */
internal class HealthConnectSyncCommitter(returnedToCaller: Boolean = false) {
    var returnedToCaller: Boolean = returnedToCaller
        private set

    fun commit(
        result: HealthConnectResult<HealthConnectSyncData>,
        save: (HealthConnectSyncData) -> Unit,
        onSuccessfulSync: (() -> Unit)?,
    ): HealthConnectSyncCommitOutcome {
        if (result !is HealthConnectResult.Success) return HealthConnectSyncCommitOutcome.NOT_SAVED
        try {
            save(result.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return HealthConnectSyncCommitOutcome.SAVE_FAILED
        }
        if (onSuccessfulSync != null && !returnedToCaller) {
            // Persist this before notifying: recreating the caller must not navigate twice.
            returnedToCaller = true
            onSuccessfulSync()
        }
        return HealthConnectSyncCommitOutcome.SAVED
    }
}
