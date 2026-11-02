package org.openfactor.app

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectForegroundSyncControllerTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val instant = Instant.parse("2026-10-29T12:00:00Z")

    private fun snapshot(at: Long = 1000, steps: Long = 10) = HealthConnectSyncState(
        lastSyncedAtMillis = at,
        firstDate = today.minusDays(29), lastDate = today,
        dailySteps = listOf(HealthConnectStepDay(today, steps)), stepsAccess = true,
    )

    private fun data(request: HealthConnectAutomaticSyncRequest) = HealthConnectSyncData(
        weights = emptyList(),
        state = HealthConnectSyncState(
            lastSyncedAtMillis = request.syncedAtMillis,
            firstDate = request.firstDate, lastDate = request.lastDate,
            dailySteps = listOf(HealthConnectStepDay(request.lastDate, 42)), stepsAccess = true,
        ),
    )

    private data class Harness(
        val controller: HealthConnectForegroundSyncController,
        val requests: MutableList<HealthConnectAutomaticSyncRequest>,
        val commits: MutableList<HealthConnectSyncData>,
    )

    private fun TestScope.harness(
        initial: HealthConnectSyncState = snapshot(),
        enabled: Boolean = true,
        clock: Clock = Clock.fixed(instant, ZoneOffset.UTC),
        zoneId: () -> ZoneId = { ZoneOffset.UTC },
        generation: () -> Long = { 0 },
        commit: (HealthConnectSyncData, Long) -> Boolean = { _, _ -> true },
        read: suspend (HealthConnectAutomaticSyncRequest) -> HealthConnectSyncData? = { data(it) },
    ): Harness {
        val requests = mutableListOf<HealthConnectAutomaticSyncRequest>()
        val commits = mutableListOf<HealthConnectSyncData>()
        return Harness(
            HealthConnectForegroundSyncController(
                scope = backgroundScope,
                readDispatcher = StandardTestDispatcher(testScheduler, "provider"),
                read = { request -> requests += request; read(request) },
                onCommit = { result, expectedGeneration ->
                    commit(result, expectedGeneration).also { accepted -> if (accepted) commits += result }
                },
                generation = generation,
                clock = clock, zoneId = zoneId, initialState = initial, enabled = enabled,
            ),
            requests, commits,
        )
    }

    @Test fun onlyReadsPreviouslyConnectedDataWhileForegroundAndEnabled() = runTest {
        val h = harness(initial = HealthConnectSyncState())
        runCurrent()
        assertTrue(h.requests.isEmpty())
        h.controller.setForeground(true)
        runCurrent()
        assertTrue(h.requests.isEmpty())

        // The first explicit connection is itself a sync, not a reason to duplicate that sync.
        h.controller.updateInputs(snapshot())
        runCurrent()
        assertTrue(h.requests.isEmpty())
        h.controller.setForeground(false)
        h.controller.updateInputs(snapshot(), enabled = false)
        h.controller.setForeground(true)
        runCurrent()
        assertTrue(h.requests.isEmpty())
        h.controller.updateInputs(snapshot(), enabled = true)
        runCurrent()
        assertTrue(h.requests.isEmpty())
        h.controller.setForeground(false)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        assertEquals(1, h.commits.size)
    }

    @Test fun readsOncePerVisitRegardlessOfSnapshotAgeOrRepeatedCallbacks() = runTest {
        val h = harness()
        h.controller.setForeground(true)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        assertEquals(h.commits.single().state, h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.isSyncing)

        h.controller.updateInputs(snapshot(at = 999))
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        h.controller.setForeground(false)
        h.controller.setForeground(false)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(2, h.requests.size)
        assertEquals(2, h.commits.size)
    }

    @Test fun explicitPanelSyncDoesNotTriggerAnImmediateAutomaticDuplicate() = runTest {
        val h = harness(enabled = false)
        h.controller.setForeground(true)
        runCurrent()
        val explicitSnapshot = snapshot(at = 2000)
        h.controller.updateInputs(explicitSnapshot, enabled = false)
        h.controller.updateInputs(explicitSnapshot, enabled = true)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(explicitSnapshot, h.controller.state.value.snapshot)
        assertTrue(h.requests.isEmpty())
        h.controller.setForeground(false)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
    }

    @Test fun providerFailuresRetainPriorSnapshotAndDoNotRetryWithinVisit() = runTest {
        var attempts = 0
        val prior = snapshot()
        val h = harness(initial = prior, read = {
            if (attempts++ == 0) null else throw IllegalStateException("provider failed")
        })
        repeat(2) {
            h.controller.setForeground(true)
            runCurrent()
            assertEquals(prior, h.controller.state.value.snapshot)
            assertTrue(h.commits.isEmpty())
            assertFalse(h.controller.state.value.isSyncing)
            h.controller.setForeground(true)
            runCurrent()
            assertEquals(it + 1, attempts)
            h.controller.setForeground(false)
        }
    }

    @Test fun unsuccessfulDurableCommitRetainsPriorSnapshot() = runTest {
        val prior = snapshot()
        val h = harness(initial = prior, commit = { _, _ -> throw IllegalStateException("disk full") })
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        assertTrue(h.commits.isEmpty())
        assertEquals(prior, h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.isSyncing)
    }

    @Test fun rejectedCommitRetainsPriorSnapshotAndDoesNotRetryWithinVisit() = runTest {
        var commitAttempts = 0
        val prior = snapshot()
        val h = harness(initial = prior, commit = { _, _ -> commitAttempts++; false })
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, commitAttempts)
        assertTrue(h.commits.isEmpty())
        assertEquals(prior, h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.isSyncing)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        assertEquals(1, commitAttempts)
    }

    @Test fun capturesGenerationBeforeDispatchAndPassesItUnchangedToCommit() = runTest {
        var generation = 7L
        var generationReads = 0
        val commitGenerations = mutableListOf<Long>()
        val h = harness(
            generation = { generationReads++; generation },
            commit = { _, expected -> commitGenerations += expected; true },
        )
        h.controller.setForeground(true)
        assertEquals(1, generationReads)
        assertTrue(h.requests.isEmpty())
        generation = 8L
        runCurrent()
        assertEquals(7L, h.requests.single().expectedGeneration)
        assertEquals(listOf(7L), commitGenerations)
        assertEquals(1, generationReads)

        h.controller.setForeground(false)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(8L, h.requests.last().expectedGeneration)
        assertEquals(listOf(7L, 8L), commitGenerations)
        assertEquals(2, generationReads)
    }

    @Test fun pauseCancelsReadAndNextResumeCanStartWithoutWaitingForOldRead() = runTest {
        var cancellations = 0
        val h = harness(read = {
            try { awaitCancellation() } finally { cancellations++ }
        })
        h.controller.setForeground(true)
        runCurrent()
        assertTrue(h.controller.state.value.isSyncing)
        h.controller.setForeground(false)
        assertFalse(h.controller.state.value.isSyncing)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, cancellations)
        assertEquals(2, h.requests.size)
        assertTrue(h.controller.state.value.isSyncing)
        h.controller.close()
        runCurrent()
        assertEquals(2, cancellations)
        assertTrue(h.commits.isEmpty())
        assertTrue(backgroundScope.isActive)
    }

    @Test fun disablingCancelsReadAndReenablingDoesNotRestartInSameVisit() = runTest {
        var cancelled = false
        val h = harness(read = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        h.controller.setForeground(true)
        runCurrent()
        h.controller.updateInputs(snapshot(), enabled = false)
        runCurrent()
        assertTrue(cancelled)
        assertFalse(h.controller.state.value.isSyncing)
        h.controller.updateInputs(snapshot(), enabled = true)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(1, h.requests.size)
        assertTrue(h.commits.isEmpty())
        h.controller.close()
    }

    @Test fun oldNonCooperativeResultCannotCommitOrClearNewVisitPendingState() = runTest {
        val oldResult = CompletableDeferred<HealthConnectSyncData>()
        val newResult = CompletableDeferred<HealthConnectSyncData>()
        var reads = 0
        val h = harness(read = {
            val response = if (reads++ == 0) oldResult else newResult
            withContext(NonCancellable) { response.await() }
        })
        h.controller.setForeground(true)
        runCurrent()
        h.controller.setForeground(false)
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(2, h.requests.size)
        oldResult.complete(data(h.requests[0]))
        runCurrent()
        assertTrue(h.commits.isEmpty())
        assertEquals(snapshot(), h.controller.state.value.snapshot)
        assertTrue(h.controller.state.value.isSyncing)
        newResult.complete(data(h.requests[1]))
        runCurrent()
        assertEquals(listOf(data(h.requests[1])), h.commits)
        assertFalse(h.controller.state.value.isSyncing)
    }

    @Test fun nonCooperativeResultAfterDisableAndReenableIsDiscarded() = runTest {
        val response = CompletableDeferred<HealthConnectSyncData>()
        val h = harness(read = { withContext(NonCancellable) { response.await() } })
        h.controller.setForeground(true)
        runCurrent()
        h.controller.updateInputs(snapshot(), enabled = false)
        h.controller.updateInputs(snapshot(), enabled = true)
        response.complete(data(h.requests.single()))
        runCurrent()
        assertTrue(h.commits.isEmpty())
        assertEquals(snapshot(), h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.isSyncing)
        assertEquals(1, h.requests.size)
    }

    @Test fun externalSnapshotSupersedesPendingReadEvenWithSameTimestamp() = runTest {
        val response = CompletableDeferred<HealthConnectSyncData>()
        val h = harness(read = { withContext(NonCancellable) { response.await() } })
        h.controller.setForeground(true)
        runCurrent()
        val restored = snapshot(steps = 99)
        h.controller.updateInputs(restored)
        response.complete(data(h.requests.single()))
        runCurrent()
        assertTrue(h.commits.isEmpty())
        assertEquals(restored, h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.isSyncing)
    }

    @Test fun closingDiscardsLateResultAndDoesNotCancelTheHostScope() = runTest {
        val response = CompletableDeferred<HealthConnectSyncData>()
        val h = harness(read = { withContext(NonCancellable) { response.await() } })
        h.controller.setForeground(true)
        runCurrent()
        h.controller.close()
        h.controller.close()
        h.controller.setForeground(true)
        h.controller.updateInputs(snapshot(at = 2000))
        response.complete(data(h.requests.single()))
        runCurrent()
        assertTrue(h.commits.isEmpty())
        assertEquals(snapshot(), h.controller.state.value.snapshot)
        assertFalse(h.controller.state.value.foreground)
        assertFalse(h.controller.state.value.isSyncing)
        assertTrue(backgroundScope.isActive)
    }

    @Test fun providerCancellationPropagatesInsteadOfBecomingAnOrdinaryFailure() = runTest {
        lateinit var readJob: Job
        val h = harness(read = {
            readJob = currentCoroutineContext()[Job]!!
            throw CancellationException("provider cancelled")
        })
        h.controller.setForeground(true)
        runCurrent()
        assertTrue(readJob.isCancelled)
        assertFalse(h.controller.state.value.isSyncing)
        assertEquals(snapshot(), h.controller.state.value.snapshot)
        assertTrue(h.commits.isEmpty())
        assertTrue(backgroundScope.isActive)
    }

    @Test fun cancellationFromDurableCommitPropagates() = runTest {
        lateinit var commitJob: Job
        val h = harness(commit = { _, _ -> throw CancellationException("save cancelled") }, read = {
            // The controller's Job is the parent of this withContext provider Job.
            commitJob = currentCoroutineContext()[Job]!!.parent!!
            data(it)
        })
        h.controller.setForeground(true)
        runCurrent()
        assertTrue(commitJob.isCancelled)
        assertTrue(h.commits.isEmpty())
        assertEquals(snapshot(), h.controller.state.value.snapshot)
    }

    @Test fun cancellingHostScopeCancelsPendingReadWithoutCommitting() = runTest {
        var cancelled = false
        val h = harness(read = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        h.controller.setForeground(true)
        runCurrent()
        backgroundScope.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(h.commits.isEmpty())
        assertFalse(h.controller.state.value.isSyncing)
    }

    @Test fun injectedClockAndZoneDefineInclusiveThirtyDayWindowForEachVisit() = runTest {
        val clock = MutableClock(Instant.parse("2026-03-08T00:30:00Z"))
        var zone = ZoneId.of("America/Los_Angeles")
        var zoneReads = 0
        val h = harness(clock = clock, zoneId = { zoneReads++; zone })
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(HealthConnectAutomaticSyncRequest(
            LocalDate.of(2026, 2, 6), LocalDate.of(2026, 3, 7), zone, clock.millis(),
        ), h.requests.single())
        assertEquals(1, zoneReads)

        h.controller.setForeground(false)
        clock.now = Instant.parse("2026-03-09T00:30:00Z")
        zone = ZoneId.of("Asia/Tokyo")
        h.controller.setForeground(true)
        runCurrent()
        assertEquals(HealthConnectAutomaticSyncRequest(
            LocalDate.of(2026, 2, 8), LocalDate.of(2026, 3, 9), zone, clock.millis(),
        ), h.requests.last())
        assertEquals(2, zoneReads)
        assertEquals(h.requests.last().syncedAtMillis, h.controller.state.value.snapshot.lastSyncedAtMillis)
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
    }
}
