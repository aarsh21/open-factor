package org.openfactor.consumer

import org.openfactor.app.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A separate JVM module consumes the same public commands as Android, without app dependencies. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutClientAcceptanceTest {
    @Test fun completeLifecycleRetainsFailedEditsAndRetriesWithoutUiLogic() = runTest {
        var disk = DataCodec.encode(AppData())
        var failWrites = false
        var clock = 1000L
        val published = mutableListOf<AppData>()
        val client = WorkoutCommands(
            initial = DataCodec.decode(disk), scope = backgroundScope,
            nowMillis = { clock },
            write = { if (failWrites) error("disk full") else disk = DataCodec.encode(it) },
            onPublished = { _, next ->
                if (next.active?.exercises?.firstOrNull()?.sets?.firstOrNull()?.completed == true)
                    assertTrue(DataCodec.decode(disk).active!!.exercises.single().sets.single().completed)
                published += next
            },
        )
        client.start("Headless workout", listOf("bench"))
        assertEquals(1000L, client.data.value.active!!.startedAt)
        val workout = client.data.value.active!!
        val entry = workout.exercises.single()
        val set = entry.sets.single()
        failWrites = true
        client.editSet(entry.id, set.id) { it.copy(weightKg = 60.0, reps = 8) }
        assertEquals(PersistenceState.Pending, client.persistence.value)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(PersistenceState.Failed(true), client.persistence.value)
        assertNull(DataCodec.decode(disk).active!!.exercises.single().sets.single().weightKg)
        assertEquals(60.0, client.data.value.active!!.exercises.single().sets.single().weightKg)
        assertFalse(client.flushPendingSaves())
        failWrites = false
        assertTrue(client.retryPendingSaves())
        assertEquals(PersistenceState.Saved, client.persistence.value)
        client.toggleSetCompletion(entry.id, set.id)
        failWrites = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val finish = WorkoutFinishController(client, this, dispatcher)
        assertTrue(finish.submit(workout.id, saveTemplate = true))
        assertFalse(finish.submit(workout.id, saveTemplate = true))
        advanceUntilIdle()
        assertEquals(WorkoutFinishState.Failed(workout.id), finish.state.value)
        assertEquals(workout.id, client.data.value.active!!.id)
        assertTrue(client.data.value.history.isEmpty())
        failWrites = false
        clock = 9000L
        assertTrue(finish.submit(workout.id, saveTemplate = true))
        advanceUntilIdle()
        assertEquals(WorkoutFinishState.Finished(workout.id), finish.state.value)
        assertNull(client.data.value.active)
        assertEquals(9000L, client.data.value.history.single().finishedAt)
        assertEquals(1, client.data.value.routines.size)
        assertEquals(workout.id, finish.consumeFinished())
        assertNull(finish.consumeFinished())
        assertNull(client.finish())
        assertEquals(1, client.data.value.history.size)
        val reopened = WorkoutCommands(DataCodec.decode(disk), backgroundScope, write = {})
        assertEquals(client.data.value, reopened.data.value)
        assertTrue(published.isNotEmpty())
    }

    @Test fun replacementSupersedesQueuedTypedEdits() = runTest {
        var disk = AppData()
        val client = WorkoutCommands(disk, backgroundScope, write = { disk = it })
        client.start(ids = listOf("bench"))
        val entry = client.data.value.active!!.exercises.single()
        client.editSet(entry.id, entry.sets.single().id) { it.copy(reps = 10) }
        val restored = AppData(preferences = Preferences(displayName = "Restored"))
        client.replaceData(restored)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(restored, disk)
        assertEquals(restored, client.data.value)
        assertEquals(PersistenceState.Saved, client.persistence.value)
    }

    @Test fun failedSynchronousMutationDoesNotPublishOrReplacePendingEdits() = runTest {
        var fail = false
        var disk = AppData()
        val client = WorkoutCommands(disk, backgroundScope, write = { if (fail) error("disk full") else disk = it })
        client.start(ids = listOf("bench"))
        val entry = client.data.value.active!!.exercises.single()
        client.editSet(entry.id, entry.sets.single().id) { it.copy(reps = 12) }
        val accepted = client.data.value
        fail = true
        assertThrows(IllegalStateException::class.java) { client.renameActive("Rejected") }
        assertEquals(accepted, client.data.value)
        assertEquals(PersistenceState.Failed(true), client.persistence.value)
        fail = false
        assertTrue(client.retryPendingSaves())
        assertEquals(accepted, disk)
    }

    @Test fun cancelledOwnerDoesNotLeaveAStuckSubmissionOrCommitWork() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val client = WorkoutCommands(AppData(), backgroundScope, write = {})
        client.start(ids = listOf("bench"))
        val entry = client.data.value.active!!.exercises.single()
        client.editSet(entry.id, entry.sets.single().id) { it.copy(reps = 8, weightKg = 60.0, completed = true) }
        val controller = WorkoutFinishController(client, scope, StandardTestDispatcher(testScheduler))
        scope.cancel()
        controller.submit(client.data.value.active!!.id, false)
        advanceUntilIdle()
        assertEquals(WorkoutFinishState.Idle, controller.state.value)
        assertNotNull(client.data.value.active)
        assertTrue(client.data.value.history.isEmpty())
    }

    @Test fun staleFinishCannotCompleteAReplacementWorkout() = runTest {
        val client = WorkoutCommands(AppData(), backgroundScope, write = {})
        client.start(ids = listOf("bench"))
        val oldId = client.data.value.active!!.id
        client.discard()
        client.start(ids = listOf("bench"))
        val entry = client.data.value.active!!.exercises.single()
        client.editSet(entry.id, entry.sets.single().id) { it.copy(reps = 8, weightKg = 60.0, completed = true) }
        val replacement = client.data.value.active
        val controller = WorkoutFinishController(client, this, StandardTestDispatcher(testScheduler))
        controller.submit(oldId, false)
        advanceUntilIdle()
        assertEquals(WorkoutFinishState.Failed(oldId), controller.state.value)
        assertEquals(replacement, client.data.value.active)
        assertTrue(client.data.value.history.isEmpty())
    }

    @Test fun queuedConcurrentStartsCannotOverwriteAnActiveWorkout() {
        assertOnlyOneStart(AppData()) { client, name -> client.start(name) }
    }

    @Test fun queuedProgramStartsRecheckTheirPrerequisitesInsideTheTransaction() {
        val plan = TrainingProgram(name = "Plan", days = listOf(ProgramDay(name = "A", exercises = listOf(PlannedExercise(exerciseId = "bench")))))
        assertOnlyOneStart(AppData(programs = listOf(plan))) { client, _ ->
            client.startProgramDay(plan.id, plan.days.single().id, 0)
        }
    }

    private fun assertOnlyOneStart(initial: AppData, start: (WorkoutCommands, String) -> Unit) {
        val scope = CoroutineScope(SupervisorJob())
        var writes = 0
        val failure = AtomicReference<Throwable?>()
        val ready = CountDownLatch(2)
        val client = WorkoutCommands(initial, scope, write = { writes++ })
        val threads = listOf("one", "two").map { name -> Thread {
            try { ready.countDown(); start(client, name) } catch (error: Throwable) { failure.set(error) }
        } }
        try {
            client.withSnapshot {
                threads.forEach(Thread::start)
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (threads.any { it.state != Thread.State.BLOCKED } && System.nanoTime() < deadline) Thread.yield()
                assertTrue("Both commands must be queued on the same transaction", threads.all { it.state == Thread.State.BLOCKED })
            }
            threads.forEach { it.join(5000); assertFalse(it.isAlive) }
            failure.get()?.let { throw AssertionError(it) }
            assertNotNull(client.data.value.active)
            assertEquals(1, writes)
        } finally { scope.cancel() }
    }
}
