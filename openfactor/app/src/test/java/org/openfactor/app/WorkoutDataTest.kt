package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test fun volumeCountsOnlyCompletedWorkingSets() {
        val workout=Workout(name="Training",exercises=listOf(SessionExercise(exerciseId="squat",sets=listOf(
            LoggedSet(weightKg=20.0,reps=10,completed=true,warmup=true),
            LoggedSet(weightKg=100.0,reps=5,completed=true),
            LoggedSet(weightKg=100.0,reps=8,completed=false)))))
        assertEquals(500.0,workout.volumeKg(),0.0001)
        assertEquals(2,workout.completedSets())
    }
    @Test fun poundsRoundTripWithoutChangingStoredKilograms() {
        assertEquals(100.0,"220.46226218".toWeightKg(true)!!,0.00001)
        assertEquals("220.5",100.0.displayWeight(true))
        assertEquals("100",100.0.displayWeight(false))
        assertNull("NaN".toWeightKg(false))
        assertNull("-20".toWeightKg(false))
        assertEquals(12.5,"12,5".toWeightKg(false)!!,0.0)
    }
    @Test fun backupPreservesSessionHistoryAndPreferences() {
        val set=LoggedSet(weightKg=42.5,reps=9,rir=2,completed=true)
        val workout=Workout(name="Upper",exercises=listOf(SessionExercise(exerciseId="press",sets=listOf(set),notes="Seat 3")))
        val source=AppData(active=workout,history=listOf(workout.copy(id="history",finishedAt=123456)),routines=listOf(Routine(name="Upper",exerciseIds=listOf("press"))),favorites=setOf("press"),customExercises=listOf(Exercise("press","My Press","Chest","Dumbbell",custom=true)),preferences=Preferences(pounds=true,restSeconds=90,autoRest=false,displayName="Athlete"))
        assertEquals(source,DataCodec.decode(DataCodec.encode(source)))
    }
    @Test(expected=IllegalArgumentException::class) fun unsupportedBackupRejected() {
        DataCodec.decode("{\"schemaVersion\":999}")
    }
}
