package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test
    fun compositionSurvivesBackupWithoutStartingAndBeginsAtExplicitStartTime() {
        val first = SessionExercise(exerciseId = "dip", sets = listOf(
            LoggedSet(reps = 0, targetRepsMin = 6, targetRepsMax = 9, targetRir = 2),
        ), restSeconds = 73, notes = "Use neutral grip")
        val second = SessionExercise(exerciseId = "press", sets = listOf(LoggedSet(reps = 0)))
        val draft = WorkoutDraft("Upper", listOf(second, first), "gym", "Keep this composition")
        val prior = Workout(name = "Previous", startedAt = 2000, finishedAt = 62000)
        val source = AppData(
            workoutDraft = draft,
            history = listOf(prior),
            scaleWeights = listOf(ScaleWeightRecord(weightKg = 79.3)),
        )
        val restored = DataCodec.decode(DataCodec.encode(source))
        assertEquals(source, restored)
        assertNull(restored.active)
        val started = restored.startWorkoutDraft(800000)
        assertNull(started.workoutDraft)
        assertEquals(source.history, started.history)
        assertEquals(source.scaleWeights, started.scaleWeights)
        val workout = started.active!!
        assertEquals(draft.exercises, workout.exercises)
        assertEquals(draft.name, workout.name)
        assertEquals(draft.gymId, workout.gymId)
        assertEquals(draft.notes, workout.notes)
        assertEquals(second.id, workout.selectedExerciseId)
        assertEquals(800000L, workout.startedAt)
        assertEquals(7L, workout.elapsedSeconds(807000))
        assertEquals(started, started.startWorkoutDraft(900000))
        assertEquals(started, DataCodec.decode(DataCodec.encode(started)))
        assertNull(DataCodec.decode("{\"schemaVersion\":1}").workoutDraft)
    }

    @Test
    fun startingACompositionCannotReplaceAnActiveWorkoutOrConsumeItsDraft() {
        val active = Workout(name = "In progress", startedAt = 5000, notes = "Do not replace")
        val source = AppData(active = active, workoutDraft = WorkoutDraft("Later"))
        assertEquals(source, source.startWorkoutDraft(800000))
        assertEquals(source, DataCodec.decode(DataCodec.encode(source)))
    }

    @Test
    fun sessionTargetsPreserveActualsOtherExerciseHistoryAndProgramAcrossBackup() {
        val first = LoggedSet(id = "a", weightKg = 72.5, reps = 11, rir = 4, completed = true)
        val second = LoggedSet(id = "b", weightKg = 61.0, reps = 7, rir = 1)
        val warmup = LoggedSet(id = "warm", reps = 3, warmup = true)
        val exercise = SessionExercise(id = "exercise", exerciseId = "dip", sets = listOf(warmup, first, second))
        val other = SessionExercise(exerciseId = "press", restSeconds = 83)
        val workout = Workout(name = "Training", exercises = listOf(exercise, other), restUntil = 12345)
        val program = TrainingProgram(name = "Plan", days = listOf(ProgramDay(exercises = listOf(
            PlannedExercise(exerciseId = "dip", cycles = listOf(CycleTarget(sets = listOf(SetTarget(repsMin = 5, repsMax = 9, rir = 3))))),
        ))), cycleCount = 1)
        val source = AppData(active = workout, history = listOf(workout.copy(id = "past", finishedAt = 99)), programs = listOf(program), programDraft = program)
        val targets = mapOf("a" to WorkingSetTarget(6, 10, 2), "b" to WorkingSetTarget(12, 17, 0), "warm" to WorkingSetTarget(8, 8, 1))
        val changed = source.copy(active = source.active!!.withExerciseTargets(exercise.id, targets, 137))
        val restored = DataCodec.decode(DataCodec.encode(changed))
        assertEquals(changed, restored)
        assertEquals(source.history, restored.history)
        assertEquals(source.programs, restored.programs)
        assertEquals(source.programDraft, restored.programDraft)
        val active = restored.active!!
        assertEquals(other, active.exercises[1])
        assertEquals(workout.restUntil, active.restUntil)
        assertEquals(137, active.exercises[0].restSeconds)
        assertEquals(listOf(warmup, first.copy(targetRepsMin = 6, targetRepsMax = 10, targetRir = 2), second.copy(targetRepsMin = 12, targetRepsMax = 17, targetRir = 0)), active.exercises[0].sets)
        val cleared = active.withExerciseTargets(exercise.id, mapOf("a" to WorkingSetTarget()), null)
        assertEquals(first, cleared.exercises[0].sets[1])
        assertNull(cleared.exercises[0].restSeconds)
    }

    @Test
    fun invalidTargetsAndRestAreRejectedAndOptionalBoundsAreAccepted() {
        val exercise = SessionExercise(exerciseId = "dip")
        val workout = Workout(name = "Training", exercises = listOf(exercise))
        for (target in listOf(WorkingSetTarget(0), WorkingSetTarget(-1), WorkingSetTarget(9, 4), WorkingSetTarget(rir = -1), WorkingSetTarget(rir = 11))) {
            assertTrue(runCatching { workout.withExerciseTargets(exercise.id, mapOf(exercise.sets[0].id to target), null) }.exceptionOrNull() is IllegalArgumentException)
        }
        for (rest in listOf(-1, 1801)) {
            assertTrue(runCatching { workout.withExerciseTargets(exercise.id, emptyMap(), rest) }.exceptionOrNull() is IllegalArgumentException)
        }
        for (target in listOf(WorkingSetTarget(), WorkingSetTarget(repsMin = 2), WorkingSetTarget(repsMax = 5), WorkingSetTarget(1, 1, 10))) {
            assertTrue(target.isValid())
        }
        for (rest in listOf(0, 1800)) {
            assertEquals(rest, workout.withExerciseTargets(exercise.id, emptyMap(), rest).exercises[0].restSeconds)
        }
    }

    @Test
    fun emptyWarmupsInsertBeforeWorkingRowsWithoutChangingExistingSets() {
        val warmup = LoggedSet(warmup = true, reps = 4)
        val working = LoggedSet(weightKg = 50.0, reps = 9, completed = true, targetRepsMin = 6)
        val exercise = SessionExercise(id = "exercise", exerciseId = "dip", sets = listOf(warmup, working))
        val other = SessionExercise(exerciseId = "press")
        val workout = Workout(name = "Training", exercises = listOf(exercise, other))
        val changed = workout.withEmptyWarmupSet(exercise.id).withEmptyWarmupSet(exercise.id)
        val sets = changed.exercises[0].sets
        assertEquals(warmup, sets.first())
        assertEquals(working, sets.last())
        assertEquals(4, sets.map { it.id }.distinct().size)
        sets.subList(1, 3).forEach {
            assertTrue(it.warmup)
            assertEquals(0, it.reps)
            assertEquals(0.0, it.weightKg, 0.0)
            assertFalse(it.completed)
            assertNull(it.targetRepsMin)
        }
        assertEquals(other, changed.exercises[1])
        assertEquals(changed, DataCodec.decode(DataCodec.encode(AppData(active = changed))).active)
        assertEquals(1, Workout(name = "Empty", exercises = listOf(exercise.copy(sets = emptyList()))).withEmptyWarmupSet(exercise.id).exercises[0].sets.size)
    }

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
