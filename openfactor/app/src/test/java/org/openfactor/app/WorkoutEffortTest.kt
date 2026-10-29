package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutEffortTest {
    @Test fun bulkCompletionStartsNoIntermediateRestWhenExerciseRestIsDisabled() {
        val se = SessionExercise(exerciseId = "press", sets = listOf(
            LoggedSet(weightKg = 40.0, reps = 8), LoggedSet(weightKg = 40.0, reps = 8)))
        val workout = Workout(name = "Upper", exercises = listOf(se))
        val completed = workout.withAllSetsCompleted(se.id, Preferences(autoRest = true, restBetweenExercises = false), 1000)
        assertTrue(completed.exercises.single().sets.all { it.completed })
        assertEquals(0L, completed.restUntil)
        val rest = workout.withAllSetsCompleted(se.id, Preferences(autoRest = true, restSeconds = 90), 1000)
        assertEquals(91000L, rest.restUntil)
    }
    private val metrics = listOf(SetMetric.WEIGHT, SetMetric.REPS)
    private val first = LoggedSet(id = "first", weightKg = 47.5, reps = 8, rir = 3, completed = true)
    private val last = LoggedSet(id = "last", weightKg = 45.0, reps = 9)
    private fun entry(vararg sets: LoggedSet) = SessionExercise(id = "press", exerciseId = "bench", sets = sets.toList())

    @Test fun bulkLoggingCompletesOnlyPendingRowsAndPreservesRecordedValues() {
        val exercise = entry(first, last)
        val next = SessionExercise(id = "next", exerciseId = "row")
        val initial = Workout(name = "Upper", exercises = listOf(exercise, next), selectedExerciseId = exercise.id)
        val logged = initial.withAllSetsCompleted(exercise.id, Preferences(), 1_000, metrics)
        assertEquals(first, logged.exercises.first().sets.first())
        assertEquals(last.copy(completed = true), logged.exercises.first().sets.last())
        assertEquals("next", logged.selectedExerciseId)
        assertEquals(121_000L, logged.restUntil)
        assertEquals(next, logged.exercises.last())
        assertEquals(logged, logged.withAllSetsCompleted(exercise.id, Preferences(), 5_000, metrics))
    }

    @Test fun oneInvalidPendingSetPreventsEveryBulkChange() {
        for (invalid in listOf(last.copy(reps = null), last.copy(weightKg = null), last.copy(reps = 0), last.copy(weightKg = Double.NaN))) {
            val exercise = entry(last.copy(id = "ready"), invalid)
            val workout = Workout(name = "Upper", exercises = listOf(exercise), restUntil = 5_000)
            assertFalse(exercise.canLogAllSets(metrics))
            assertEquals(workout, workout.withAllSetsCompleted(exercise.id, Preferences(), 1_000, metrics))
        }
        assertFalse(entry().canLogAllSets(metrics))
    }

    @Test fun sideAndMyoRowsAllCompleteBeforeOneLastLogPrompt() {
        val exercise = entry(last.copy(kind = SetKind.MYO)).withMyoComponent(last.id).withSplitSides()
        val ready = exercise.copy(sets = exercise.sets.map { it.copy(weightKg = 30.0, reps = 8) })
        assertNull(ready.pendingEffortSet(metrics))
        val workout = Workout(name = "Upper", exercises = listOf(ready))
            .withAllSetsCompleted(ready.id, Preferences(), 10_000, metrics)
        val logged = workout.exercises.single()
        assertTrue(logged.sets.all { it.completed })
        assertEquals(1, logged.logicalSets().size)
        assertEquals(4, logged.sets.size)
        assertEquals(logged.sets.last().id, logged.pendingEffortSet(metrics)?.id)
        assertEquals(SetSide.RIGHT, logged.pendingEffortSet(metrics)?.side)
        assertNotNull(logged.pendingEffortSet(metrics)?.segmentOf)
    }

    @Test fun ratingUpdatesLastLogOnlyAndNeverRewritesEarlierEffort() {
        val exercise = entry(first, last.copy(completed = true))
        val rated = exercise.withReviewedEffort(last.id, 2)
        assertEquals(first, rated.sets.first())
        assertEquals(2, rated.sets.last().rir)
        assertEquals(setOf(last.id), rated.reviewedEffortSetIds)
        assertNull(rated.pendingEffortSet(metrics))
        assertEquals(rated, rated.withReviewedEffort(last.id, -1))
        assertEquals(rated, rated.withReviewedEffort(last.id, 11))
        assertEquals(rated, rated.withReviewedEffort("missing", 2))
    }

    @Test fun skipSurvivesBackupAndUndoRecompletionWithoutDuplicatePrompt() {
        val skipped = entry(first, last.copy(completed = true)).withReviewedEffort(last.id, null)
        assertNull(skipped.sets.last().rir)
        val restored = DataCodec.decode(DataCodec.encode(AppData(active = Workout(name = "Upper", exercises = listOf(skipped))))).active!!
        val undone = restored.toggleSetCompletion(skipped.id, last.id, Preferences(), 1_000, metrics)
        assertNull(undone.exercises.single().pendingEffortSet(metrics))
        val complete = undone.toggleSetCompletion(skipped.id, last.id, Preferences(), 2_000, metrics)
        assertNull(complete.exercises.single().pendingEffortSet(metrics))
        assertEquals(skipped, complete.exercises.single())
    }

    @Test fun addedWorkingSetCanAskAgainAfterItsCompletion() {
        val rated = entry(first, last.copy(completed = true)).withReviewedEffort(last.id, 1)
        val added = rated.withNewSet()
        assertNull(added.pendingEffortSet(metrics))
        val completed = added.copy(sets = added.sets.map { it.copy(completed = true) })
        assertEquals(added.sets.last().id, completed.pendingEffortSet(metrics)?.id)
        assertEquals(1, completed.sets[1].rir)
    }

    @Test fun warmupsDurationAndExistingActualRatingsDoNotAskForEffort() {
        assertNull(entry(last.copy(warmup = true, completed = true)).pendingEffortSet(metrics))
        assertNull(entry(last.copy(completed = true, rir = 0)).pendingEffortSet(metrics))
        assertNull(entry(LoggedSet(durationSeconds = 30.0, completed = true)).pendingEffortSet(listOf(SetMetric.DURATION)))
        val warmupPending = entry(last.copy(id = "warmup", warmup = true), first)
        assertNull(warmupPending.pendingEffortSet(metrics))
    }
}
