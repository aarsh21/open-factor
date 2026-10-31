package org.openfactor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExerciseProgressTest {
    @Test fun warmupsAndPartiallyCompletedSidePairsDoNotInflateWorkingProgress() {
        val left = LoggedSet(id = "left", completed = true, side = SetSide.LEFT)
        val right = LoggedSet(id = "right", completed = false, side = SetSide.RIGHT, pairId = left.id)
        val exercise = SessionExercise(exerciseId = "exercise", sets = listOf(
            LoggedSet(warmup = true, completed = true), left, right, LoggedSet(completed = true),
        ))
        assertEquals(WorkoutExerciseProgress(1, 2), exercise.workoutExerciseProgress())
        assertEquals(WorkoutExerciseProgress(2, 2), exercise.copy(sets = exercise.sets.map {
            if (it.id == right.id) it.copy(completed = true) else it
        }).workoutExerciseProgress())
    }

    @Test fun aMyoGroupCountsOnceAndRequiresAllItsComponents() {
        val root = LoggedSet(id = "root", completed = true, kind = SetKind.MYO)
        val component = LoggedSet(id = "component", segmentOf = root.id)
        assertEquals(WorkoutExerciseProgress(0, 1),
            SessionExercise(exerciseId = "exercise", sets = listOf(root, component)).workoutExerciseProgress())
        assertEquals(WorkoutExerciseProgress(1, 1),
            SessionExercise(exerciseId = "exercise", sets = listOf(root, component.copy(completed = true))).workoutExerciseProgress())
    }

    private fun entry(id: String, vararg completed: Boolean, warmupDone: Boolean? = null, superset: String? = null) =
        SessionExercise(id = id, exerciseId = id, supersetId = superset, sets =
            listOfNotNull(warmupDone?.let { LoggedSet(warmup = true, completed = it) }) + completed.map { LoggedSet(completed = it) })

    @Test fun doneNeedsEveryWorkingSetButIgnoresWarmupsAndEmptyExercises() {
        assertTrue(entry("a", true, true, warmupDone = false).workoutExerciseProgress().done)
        assertFalse(entry("b", true, false).workoutExerciseProgress().done)
        assertFalse(SessionExercise(exerciseId = "c", sets = emptyList()).workoutExerciseProgress().done)
        assertFalse(entry("d", warmupDone = true).workoutExerciseProgress().done)
    }

    @Test fun upNextIsTheFollowingUnfinishedExerciseAndWrapsToSkippedOnes() {
        val workout = Workout(name = "W", exercises = listOf(
            entry("a", false, false), entry("b", true, true), entry("c", true), entry("d", false), entry("e", true),
        ))
        assertEquals("d", workout.upNextExercise("b")?.id)
        assertEquals("d", workout.upNextExercise("c")?.id)
        // Past the end it wraps to the exercise skipped earlier, never to the current one.
        assertEquals("a", workout.upNextExercise("e")?.id)
        assertEquals("a", workout.upNextExercise("d")?.id)
        assertEquals("a", workout.upNextExercise(null)?.id)
        val finished = Workout(name = "W", exercises = listOf(entry("a", true), entry("b", true)))
        assertNull(finished.upNextExercise("a"))
        assertNull(Workout(name = "W", exercises = listOf(entry("a", false))).upNextExercise("a"))
    }

    @Test fun supersetLinksJoinOnlyAdjacentMembersOfTheSameGroup() {
        val workout = Workout(name = "W", exercises = listOf(
            entry("a", false), entry("b", false, superset = "s"), entry("c", false, superset = "s"),
            entry("d", false, superset = "t"), entry("e", false), entry("f", false, superset = "t"),
        ))
        assertEquals(listOf(
            SupersetLink(false, false, false),
            SupersetLink(true, false, true),
            SupersetLink(true, true, false),
            SupersetLink(true, false, false),
            SupersetLink(false, false, false),
            SupersetLink(true, false, false),
        ), workout.supersetLinks())
    }
}
