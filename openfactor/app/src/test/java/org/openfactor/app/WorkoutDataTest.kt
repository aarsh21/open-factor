package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test
    fun pauseAndSelectionSurviveBackupWithoutCountingPausedTime() {
        val workout =
            Workout(
                name = "Training",
                startedAt = 1000,
                selectedExerciseId = "selected",
                pausedDurationMs = 2000,
            )
        val paused = workout.withPause(true, 13000)
        assertEquals(10L, paused.elapsedSeconds(60000))
        val restored = DataCodec.decode(DataCodec.encode(AppData(active = paused))).active!!
        assertEquals(paused, restored)
        assertEquals("selected", restored.selectedExerciseId)
        val resumed = restored.withPause(false, 18000)
        assertNull(resumed.pausedAt)
        assertEquals(7000L, resumed.pausedDurationMs)
        assertEquals(12L, resumed.elapsedSeconds(20000))
        assertEquals(resumed, resumed.withPause(false, 21000))
        assertEquals(12L, resumed.copy(finishedAt = 20000).elapsedSeconds(30000))
    }

    @Test
    fun savedDurationExcludesLongPauseIncludingFinishWhilePaused() {
        val active = Workout(name = "Training", startedAt = 1000)
        val resumed = active.withPause(true, 61000).withPause(false, 361000)
        val finished = resumed.copy(finishedAt = 421000)
        val finishWhilePaused =
            active.withPause(true, 121000).withPause(false, 421000).copy(finishedAt = 421000)
        for (workout in listOf(finished, finishWhilePaused)) {
            val restored =
                DataCodec.decode(DataCodec.encode(AppData(history = listOf(workout))))
                    .history
                    .single()
            assertEquals(120L, restored.elapsedSeconds(900000))
            assertEquals(2L, restored.elapsedSeconds(900000) / 60)
        }
    }

    @Test
    fun finalSetAdvancesAndStartsRestInOnePersistableChange() {
        val first =
            SessionExercise(
                id = "first",
                exerciseId = "chin-up",
                sets = listOf(LoggedSet(id = "a"), LoggedSet(id = "b", completed = true)),
            )
        val second =
            SessionExercise(id = "second", exerciseId = "dip", sets = listOf(LoggedSet(id = "c")))
        val workout =
            Workout(
                name = "Training",
                exercises = listOf(first, second),
                selectedExerciseId = "second",
            )
        val advanced = workout.toggleSetCompletion("second", "c", Preferences(), 1000)
        assertEquals("first", advanced.selectedExerciseId)
        assertEquals(121000L, advanced.restUntil)
        assertTrue(advanced.exercises[1].sets.single().completed)
        assertEquals(
            advanced,
            DataCodec.decode(DataCodec.encode(AppData(active = advanced))).active,
        )
        val finished = advanced.toggleSetCompletion("first", "a", Preferences(), 2000)
        assertEquals("first", finished.selectedExerciseId)
        val unchecked = advanced.toggleSetCompletion("second", "c", Preferences(), 3000)
        assertEquals("first", unchecked.selectedExerciseId)
        assertEquals(121000L, unchecked.restUntil)
    }

    @Test
    fun autoNextCanBeDisabledAndPartialExerciseDoesNotAdvance() {
        val first =
            SessionExercise(
                id = "first",
                exerciseId = "chin-up",
                sets = listOf(LoggedSet(id = "a"), LoggedSet(id = "b")),
            )
        val second = SessionExercise(id = "second", exerciseId = "dip")
        val workout =
            Workout(
                name = "Training",
                exercises = listOf(first, second),
                selectedExerciseId = "first",
            )
        val partial = workout.toggleSetCompletion("first", "a", Preferences(), 1000)
        assertEquals("first", partial.selectedExerciseId)
        val prefs = Preferences(exerciseAutoNext = false, autoRest = false)
        val completed = partial.toggleSetCompletion("first", "b", prefs, 2000)
        assertEquals("first", completed.selectedExerciseId)
        assertEquals(partial.restUntil, completed.restUntil)
        assertEquals(
            prefs,
            DataCodec.decode(DataCodec.encode(AppData(preferences = prefs))).preferences,
        )
        assertTrue(DataCodec.decode("{\"schemaVersion\":1}").preferences.exerciseAutoNext)
        assertEquals(workout, workout.toggleSetCompletion("missing", "a", Preferences(), 1000))
    }

    @Test
    fun volumeCountsOnlyCompletedWorkingSets() {
        val workout =
            Workout(
                name = "Training",
                exercises =
                    listOf(
                        SessionExercise(
                            exerciseId = "squat",
                            sets =
                                listOf(
                                    LoggedSet(
                                        weightKg = 20.0,
                                        reps = 10,
                                        completed = true,
                                        warmup = true,
                                    ),
                                    LoggedSet(weightKg = 100.0, reps = 5, completed = true),
                                    LoggedSet(weightKg = 100.0, reps = 8, completed = false),
                                ),
                        )
                    ),
            )
        assertEquals(500.0, workout.volumeKg(), 0.0001)
        assertEquals(2, workout.completedSets())
    }

    @Test
    fun poundsRoundTripWithoutChangingStoredKilograms() {
        assertEquals(100.0, "220.46226218".toWeightKg(true)!!, 0.00001)
        assertEquals("220.5", 100.0.displayWeight(true))
        assertEquals("100", 100.0.displayWeight(false))
        assertNull("NaN".toWeightKg(false))
        assertNull("-20".toWeightKg(false))
        assertEquals(12.5, "12,5".toWeightKg(false)!!, 0.0)
    }

    @Test
    fun backupPreservesSessionHistoryAndPreferences() {
        val set = LoggedSet(weightKg = 42.5, reps = 9, rir = 2, completed = true)
        val workout =
            Workout(
                name = "Upper",
                exercises =
                    listOf(
                        SessionExercise(exerciseId = "press", sets = listOf(set), notes = "Seat 3")
                    ),
            )
        val source =
            AppData(
                active = workout,
                history = listOf(workout.copy(id = "history", finishedAt = 123456)),
                routines = listOf(Routine(name = "Upper", exerciseIds = listOf("press"))),
                favorites = setOf("press"),
                customExercises =
                    listOf(Exercise("press", "My Press", "Chest", "Dumbbell", custom = true)),
                preferences =
                    Preferences(
                        pounds = true,
                        restSeconds = 90,
                        autoRest = false,
                        displayName = "Athlete",
                    ),
            )
        assertEquals(source, DataCodec.decode(DataCodec.encode(source)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedBackupRejected() {
        DataCodec.decode("{\"schemaVersion\":999}")
    }

    @Test
    fun restDeadlineAndDashboardChoicesSurviveBackup() {
        val deadline = System.currentTimeMillis() + 120000
        val source =
            AppData(
                active = Workout(name = "Training", restUntil = deadline),
                preferences =
                    Preferences(
                        dashboardAllWorkouts = true,
                        showWeeklyWorkouts = false,
                        showInsights = false,
                        showHabits = false,
                    ),
            )
        val restored = DataCodec.decode(DataCodec.encode(source))
        assertEquals(deadline, restored.active!!.restUntil)
        assertEquals(source, restored)
    }

    @Test
    fun levelsCountCompletedWorkingSetsInPrimaryAndSecondaryGroups() {
        val exercise = Exercise("press", "Press", "Chest", "Barbell", secondary = listOf("Triceps"))
        val workout =
            Workout(
                name = "Training",
                exercises =
                    listOf(
                        SessionExercise(
                            exerciseId = "press",
                            sets =
                                listOf(
                                    LoggedSet(completed = true),
                                    LoggedSet(completed = true, warmup = true),
                                    LoggedSet(completed = false),
                                ),
                        )
                    ),
            )
        assertEquals(
            1,
            muscleContributions(listOf(workout), listOf(exercise), 7, "Chest").single().sets,
        )
        assertEquals(
            1,
            muscleContributions(listOf(workout), listOf(exercise), 7, "Triceps").single().sets,
        )
        assertTrue(muscleContributions(listOf(workout), listOf(exercise), 7, "Quads").isEmpty())
    }

    @Test
    fun gymSelectionsAndWeightsSurviveBackup() {
        val gym =
            GymProfile(
                name = "Garage",
                icon = "garage",
                equipmentIds = setOf("bar", "plates"),
                weightsKg = mapOf("plates" to listOf(1.25, 2.5, 5.0)),
            )
        val source = AppData(gyms = listOf(gym))
        assertEquals(source, DataCodec.decode(DataCodec.encode(source)))
        val oldBackup = DataCodec.decode("{\"schemaVersion\":1}")
        assertTrue(oldBackup.gyms.isEmpty())
    }

    @Test
    fun gymAvailabilityRequiresAllMembersOfAnAlternativeGroup() {
        val resistance = listOf(setOf("bar", "plates"), setOf("dumbbells"))
        val support = listOf(setOf("bench", "rack"), setOf("station"))
        assertFalse(equipmentRequirementsMet(resistance, support, setOf("bar", "station")))
        assertFalse(equipmentRequirementsMet(resistance, support, setOf("bar", "plates", "bench")))
        assertTrue(equipmentRequirementsMet(resistance, support, setOf("bar", "plates", "station")))
        assertTrue(
            equipmentRequirementsMet(resistance, support, setOf("dumbbells", "bench", "rack"))
        )
        assertTrue(equipmentRequirementsMet(emptyList(), emptyList(), emptySet()))
    }

    @Test
    fun equipmentReferencesMayNameAnEntityOrAnEquipmentGroup() {
        val index =
            org.json.JSONObject(
                """{"bar":{"type":"equipment"},"plates":{"type":"equipment"},"bar-and-plates":{"type":"resistanceEquipmentGroup","equipment":["bar","plates"]}}"""
            )
        assertEquals(setOf("bar"), resolveEquipmentRequirement(index, "bar"))
        assertEquals(setOf("bar", "plates"), resolveEquipmentRequirement(index, "bar-and-plates"))
        assertFalse(
            equipmentRequirementsMet(
                listOf(resolveEquipmentRequirement(index, "bar")),
                emptyList(),
                emptySet(),
            )
        )
    }
}
