package org.openfactor.consumer

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.openfactor.app.*

/** Public commands, not delayed UI observation, own the atomic stale-write boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthSyncGenerationAcceptanceTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val instant = Instant.parse("2026-10-29T12:00:00Z")

    private fun weight(kg: Double) = ScaleWeightRecord(
        id = "provider-weight", date = today, weightKg = kg, healthConnectOrigin = "provider",
    )

    private fun connected() = AppData(
        scaleWeights = listOf(weight(80.0)),
        healthConnect = HealthConnectSyncState(
            lastSyncedAtMillis = 1000,
            firstDate = today.minusDays(29), lastDate = today,
            weightReadings = 1, weightAccess = true, stepsAccess = true,
            dailySteps = listOf(HealthConnectStepDay(today, 10)),
        ),
    )

    private fun result(request: HealthConnectAutomaticSyncRequest, kg: Double = 81.0) = HealthConnectSyncData(
        weights = listOf(weight(kg).copy(date = request.lastDate)),
        state = HealthConnectSyncState(
            lastSyncedAtMillis = request.syncedAtMillis,
            firstDate = request.firstDate, lastDate = request.lastDate,
            weightReadings = 1, weightAccess = true, stepsAccess = true,
            dailySteps = listOf(HealthConnectStepDay(request.lastDate, 42)),
        ),
    )

    private class Store(var data: AppData) {
        val writes = mutableListOf<AppData>()
        fun write(next: AppData) {
            data = next
            writes += next
        }
    }

    private data class Client(
        val commands: WorkoutCommands,
        val controller: HealthConnectForegroundSyncController,
        val response: CompletableDeferred<HealthConnectSyncData>,
        val requests: MutableList<HealthConnectAutomaticSyncRequest>,
        val commitAcceptances: MutableList<Boolean>,
        val store: Store,
    )

    private fun TestScope.pendingClient(): Client {
        val initial = connected()
        val store = Store(initial)
        val commands = WorkoutCommands(initial, backgroundScope, write = store::write)
        val response = CompletableDeferred<HealthConnectSyncData>()
        val requests = mutableListOf<HealthConnectAutomaticSyncRequest>()
        val acceptances = mutableListOf<Boolean>()
        val controller = HealthConnectForegroundSyncController(
            scope = backgroundScope,
            readDispatcher = StandardTestDispatcher(testScheduler, "provider"),
            read = { requests += it; response.await() },
            generation = commands::healthSyncGeneration,
            onCommit = { sync, expected ->
                commands.importHealthConnectSyncIfCurrent(sync, expected).also { acceptances += it }
            },
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            zoneId = { ZoneOffset.UTC },
            initialState = initial.healthConnect,
        )
        controller.setForeground(true)
        runCurrent()
        assertEquals(1, requests.size)
        assertTrue(controller.state.value.isSyncing)
        return Client(commands, controller, response, requests, acceptances, store)
    }

    private fun TestScope.assertPendingReadRejected(client: Client, authoritative: AppData) {
        val writesBeforeCompletion = client.store.writes.size
        assertTrue(client.commands.healthSyncGeneration() > client.requests.single().expectedGeneration)
        // Deliberately do not call controller.updateInputs: Compose has not observed publication yet.
        assertEquals(connected().healthConnect, client.controller.state.value.snapshot)
        client.response.complete(result(client.requests.single()))
        runCurrent()
        assertEquals(listOf(false), client.commitAcceptances)
        assertEquals(authoritative, client.commands.data.value)
        assertEquals(authoritative, client.store.data)
        assertEquals(writesBeforeCompletion, client.store.writes.size)
        assertEquals(connected().healthConnect, client.controller.state.value.snapshot)
        assertFalse(client.controller.state.value.isSyncing)
    }

    @Test fun restorePublishesBeforeInputObservationAndRejectsOldAutomaticRead() = runTest {
        val client = pendingClient()
        val restored = connected().copy(
            scaleWeights = listOf(weight(90.0)),
            healthConnect = connected().healthConnect.copy(
                lastSyncedAtMillis = 2000,
                dailySteps = listOf(HealthConnectStepDay(today, 99)),
            ),
        )
        client.commands.replaceData(restored)
        assertPendingReadRejected(client, restored)
    }

    @Test fun restoreWithIdenticalHealthMetadataAndNewBodyReadingsRejectsOldRead() = runTest {
        val client = pendingClient()
        val restored = connected().copy(scaleWeights = listOf(weight(90.0)))
        assertEquals(client.controller.state.value.snapshot, restored.healthConnect)
        client.commands.replaceData(restored)
        assertPendingReadRejected(client, restored)
    }

    @Test fun successfulIdenticalReplacementStillInvalidatesOldRead() = runTest {
        val client = pendingClient()
        val restored = client.commands.data.value
        client.commands.replaceData(restored)
        assertPendingReadRejected(client, restored)
    }

    @Test fun explicitSyncPublishesBeforeInputObservationAndRejectsOldAutomaticRead() = runTest {
        val client = pendingClient()
        val explicit = result(client.requests.single(), kg = 92.0).let {
            it.copy(state = it.state.copy(lastSyncedAtMillis = instant.toEpochMilli() + 1))
        }
        client.commands.importHealthConnectSync(explicit)
        assertPendingReadRejected(client, client.commands.data.value)
    }

    @Test fun scaleWeightMutationWithUnchangedHealthMetadataInvalidatesOldRead() = runTest {
        val client = pendingClient()
        client.commands.saveScaleWeight(weight(93.0))
        assertEquals(client.controller.state.value.snapshot, client.commands.data.value.healthConnect)
        assertPendingReadRejected(client, client.commands.data.value)
    }

    @Test fun currentGenerationCommitsDurablyAndUpdatesControllerSnapshot() = runTest {
        val client = pendingClient()
        val request = client.requests.single()
        assertEquals(client.commands.healthSyncGeneration(), request.expectedGeneration)
        val sync = result(request)
        client.response.complete(sync)
        runCurrent()
        assertEquals(listOf(true), client.commitAcceptances)
        assertEquals(sync.state, client.controller.state.value.snapshot)
        assertEquals(sync.state, client.commands.data.value.healthConnect)
        assertEquals(sync.weights, client.commands.data.value.scaleWeights)
        assertEquals(client.commands.data.value, client.store.data)
        assertEquals(1, client.store.writes.size)
        assertTrue(client.commands.healthSyncGeneration() > request.expectedGeneration)
        assertFalse(client.controller.state.value.isSyncing)
    }

    @Test fun generationComparisonAndCommitSerializeWithAuthoritativeReplacement() {
        val scope = CoroutineScope(SupervisorJob())
        val store = Store(connected())
        val commands = WorkoutCommands(store.data, scope, write = store::write)
        val expectedGeneration = commands.healthSyncGeneration()
        val sync = HealthConnectSyncData(listOf(weight(81.0)), connected().healthConnect)
        val restored = connected().copy(scaleWeights = listOf(weight(99.0)))
        val ready = CountDownLatch(1)
        val acceptance = AtomicReference<Boolean?>()
        val failure = AtomicReference<Throwable?>()
        val commit = Thread {
            ready.countDown()
            try {
                acceptance.set(commands.importHealthConnectSyncIfCurrent(sync, expectedGeneration))
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            commands.withSnapshot {
                commit.start()
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (commit.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.yield()
                assertEquals("Commit must wait on the authoritative transaction", Thread.State.BLOCKED, commit.state)
                commands.replaceData(restored)
            }
            commit.join(5000)
            assertFalse(commit.isAlive)
            failure.get()?.let { throw AssertionError(it) }
            assertEquals(false, acceptance.get())
            assertEquals(restored, commands.data.value)
            assertEquals(restored, store.data)
            assertEquals(listOf(restored), store.writes)
        } finally {
            scope.cancel()
        }
    }
}
