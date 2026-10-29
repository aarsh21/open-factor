package org.openfactor.app

import org.junit.Assert.assertEquals
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
}
