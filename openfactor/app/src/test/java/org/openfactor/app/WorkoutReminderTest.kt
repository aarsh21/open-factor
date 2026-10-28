package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutReminderTest {
    @Test fun restAlertsRejectSkippedReplacedAndFinishedWorkouts() {
        val workout = Workout(id = "active", name = "Training", restUntil = 5_000L)
        assertTrue(restAlarmMatches(workout, "active", 5_000L, 5_000L))
        assertTrue(restAlarmMatches(workout, "active", 5_000L, 8_000L))
        assertFalse(restAlarmMatches(workout, "active", 5_000L, 4_999L))
        assertFalse(restAlarmMatches(workout, "old-workout", 5_000L, 5_001L))
        assertFalse(restAlarmMatches(workout.copy(restUntil = 0), "active", 5_000L, 5_001L))
        assertFalse(restAlarmMatches(workout.copy(restUntil = 8_000), "active", 5_000L, 9_000L))
        assertFalse(restAlarmMatches(null, "active", 5_000L, 5_001L))
        assertFalse(restAlarmMatches(workout.copy(restUntil = 0), "active", 0L, 5_001L))
    }

    @Test fun activeWorkoutRestTimerPauseAndWarmupsSurviveSerialization() {
        val active = Workout(
            name = "Recovered workout", startedAt = 10_000L, restUntil = 90_000L,
            pausedAt = 70_000L, pausedDurationMs = 5_000L,
            selectedExerciseId = "selected", notes = "Keep this",
            exercises = listOf(SessionExercise(id = "selected", exerciseId = "barbell-bench-press", restSeconds = 80,
                sets = listOf(LoggedSet(weightKg = 22.5, reps = 10, completed = true, warmup = true), LoggedSet(weightKg = 42.5, reps = 8)))),
        )
        val recovered = DataCodec.decode(DataCodec.encode(AppData(active = active))).active!!
        assertEquals(active, recovered)
        assertEquals(55L, recovered.elapsedSeconds(1_000_000L))
        assertEquals(75L, recovered.withPause(false, 80_000L).elapsedSeconds(100_000L))
        assertTrue(restAlarmMatches(recovered, active.id, active.restUntil, 90_000L))
    }
}
