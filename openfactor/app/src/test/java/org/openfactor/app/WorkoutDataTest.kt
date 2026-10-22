package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test
    fun exerciseDefaultsRoundTripWithoutChangingSavedEntries() {
        val defaults = ExerciseDefaults("Global note", 75, true)
        val session = SessionExercise(exerciseId = "chin", notes = "Session note", restSeconds = 0)
        val historical = SessionExercise(exerciseId = "chin", notes = "Old note", restSeconds = 20)
        val draft = SessionExercise(exerciseId = "chin", notes = "Draft note", restSeconds = 44)
        val data = AppData(
            exerciseDefaults = mapOf("chin" to defaults),
            active = Workout(name = "Active", exercises = listOf(session)),
            history = listOf(Workout(name = "History", exercises = listOf(historical))),
            workoutDraft = WorkoutDraft(exercises = listOf(draft)),
        )
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        val fresh = newSessionExercise("chin", defaults)
        assertEquals("Global note", fresh.notes)
        assertEquals(75, fresh.restSeconds)
        assertNull(fresh.sets.single().reps)
        assertNull(fresh.sets.single().weightKg)
        assertTrue(DataCodec.decode("""{"schemaVersion":1}""").exerciseDefaults.isEmpty())
        for (invalid in listOf("-1", "1801", "1.5", "\"30\"")) assertThrows(IllegalArgumentException::class.java) {
            DataCodec.decode("""{"schemaVersion":1,"exerciseDefaults":{"chin":{"restSeconds":$invalid}}}""")
        }
    }

    @Test
    fun customExerciseMetadataDrivesLoggingAndSurvivesBackup() {
        val draft = CustomExerciseDraft(
            name = " Assisted Hold ", metric1 = SetMetric.ASSISTANCE, metric2 = SetMetric.DURATION,
            primary = listOf("Lats", "Upper Back"), secondary = listOf("Biceps"),
            resistance = listOf(setOf("machine"), setOf("bar", "plates")),
            support = listOf(setOf("bench", "rack"), setOf("station")),
            bodyPercent = "73", rom = 2, stability = 4,
            primaryJoint = "Shoulder Adduction", alternateNames = "Supported Hang, Hang\nSupported Hang",
        )
        val custom = draft.toExercise(listOf(EquipmentItem("machine", "Assistance Machine", "Pin Loaded Machines")), "custom")
        assertEquals("Assisted Hold", custom.name)
        assertEquals(listOf("Supported Hang", "Hang"), custom.aliases)
        assertEquals(0.73, custom.bodyweight, 0.0)
        assertEquals("Multi-joint (compound)", custom.properties["Type"])
        assertEquals("Upper body", custom.properties["Region"])
        val source = AppData(customExercises = listOf(custom), workoutDraft = WorkoutDraft("Not started"), scaleWeights = listOf(ScaleWeightRecord(weightKg = 83.0)))
        val restored = DataCodec.decode(DataCodec.encode(source))
        assertEquals(source, restored)
        assertEquals(custom, restored.customExercises.single())
        assertEquals(listOf(SetMetric.ASSISTANCE, SetMetric.DURATION), restored.customExercises.single().loggingMetrics())
        assertFalse(equipmentRequirementsMet(custom.resistanceEquipment, custom.supportEquipment, setOf("bar", "station")))
        assertFalse(equipmentRequirementsMet(custom.resistanceEquipment, custom.supportEquipment, setOf("machine", "bench")))
        assertTrue(equipmentRequirementsMet(custom.resistanceEquipment, custom.supportEquipment, setOf("machine", "station")))
        assertTrue(equipmentRequirementsMet(custom.resistanceEquipment, custom.supportEquipment, setOf("bar", "plates", "bench", "rack")))
        val legacy = DataCodec.decode("""{"schemaVersion":1,"customExercises":[{"id":"old","name":"Old","muscle":"Other","equipment":"Bodyweight"}]}""")
        assertTrue(legacy.customExercises.single().custom)
        assertEquals(listOf(SetMetric.WEIGHT, SetMetric.REPS), legacy.customExercises.single().loggingMetrics())
        val catalogExercise = DataCodec.catalog("""[{"id":"air-bike","name":"Air Biking","muscle":"Other","muscles":[]}]""").single()
        assertEquals(listOf("Other"), catalogExercise.muscles)
    }

    @Test
    fun duplicateIsAnEditableNewExerciseWithoutChangingTheOriginal() {
        val original = CustomExerciseDraft(
            name = "Hold", metric1 = SetMetric.ASSISTANCE, metric2 = SetMetric.DURATION,
            type = CustomExerciseType.ISOLATION_LOWER, laterality = "Unilateral",
            primary = listOf("Hamstrings"), secondary = listOf("Glutes"),
            resistance = listOf(setOf("machine"), setOf("bar", "plates")), support = listOf(setOf("bench", "rack")),
            bodyPercent = "7.6", rom = 2, stability = 4,
            primaryJoint = "Knee Flexion", alternateNames = "Supported Hold", instructions = "Keep still",
        ).toExercise(emptyList(), "original").copy(frames = listOf("original.png"), clips = listOf("original.mp4"))
        val draft = original.duplicateDraft()
        assertEquals("7.6", draft.bodyPercent)
        assertEquals(CustomExerciseType.ISOLATION_LOWER, draft.type)
        val duplicate = draft.copy(name = "Edited Hold", bodyPercent = "81").toExercise(emptyList(), "copy")
        assertEquals("original", original.id)
        assertEquals("Hold", original.name)
        assertEquals(.076, original.bodyweight, 0.0)
        assertEquals("copy", duplicate.id)
        assertEquals("Edited Hold", duplicate.name)
        assertEquals(.81, duplicate.bodyweight, 0.0)
        assertEquals(listOf(setOf("machine"), setOf("bar", "plates")), duplicate.resistanceEquipment)
        assertEquals(listOf(setOf("bench", "rack")), duplicate.supportEquipment)
        assertEquals(listOf(SetMetric.ASSISTANCE, SetMetric.DURATION), duplicate.loggingMetrics())
        assertEquals(listOf("Hamstrings"), duplicate.muscles)
        assertEquals(listOf("Glutes"), duplicate.secondary)
        assertEquals("Keep still", duplicate.instructions)
        assertTrue(duplicate.frames.isEmpty() && duplicate.clips.isEmpty())
        assertFalse(original.copy(properties = original.properties - "Region").duplicateDraft().validBasics)
        assertFalse(draft.copy(resistance = emptyList()).validDetails)
        assertFalse(draft.copy(alternateNames = "a".repeat(301)).validDetails)
    }

    @Test
    fun customCreationRequiresExplicitMetricsAndContributionButAllowsSkippedMuscles() {
        val blank = CustomExerciseDraft(name = "Hold")
        assertFalse(blank.validBasics)
        assertFalse(blank.validDetails)
        val ready = blank.copy(metric1 = SetMetric.DURATION, bodyPercent = "0")
        assertTrue(ready.validBasics && ready.validDetails)
        val saved = ready.toExercise(emptyList())
        assertTrue(saved.muscles.isEmpty())
        assertEquals(saved, DataCodec.decode(DataCodec.encode(AppData(customExercises = listOf(saved)))).customExercises.single())
        assertTrue(equipmentRequirementsMet(saved.resistanceEquipment, saved.supportEquipment, emptySet()))
        for (percent in listOf("", "-1", "100.1", "NaN")) assertFalse(ready.copy(bodyPercent = percent).validDetails)
        assertEquals(100.0, ready.copy(bodyPercent = "100").contribution!!, 0.0)
        val maximum = ready.copy(bodyPercent = "100").toExercise(emptyList())
        assertEquals(maximum, DataCodec.decode(DataCodec.encode(AppData(customExercises = listOf(maximum)))).customExercises.single())
        assertThrows(IllegalArgumentException::class.java) {
            DataCodec.decode("""{"schemaVersion":1,"customExercises":[{"id":"invalid","name":"Hold","muscle":"Other","equipment":"Bodyweight","bodyweight":1.01}]}""")
        }
        assertFalse(ready.copy(metric2 = SetMetric.DURATION_PER_SIDE).validBasics)
        assertTrue(ready.copy(metric2 = SetMetric.DISTANCE_LONG).validBasics)
    }

    @Test
    fun newSessionRowsHaveNoPerformedValuesAndDoNotCopyTargetsIntoActuals() {
        val entry = SessionExercise(exerciseId = "chin-up")
        assertEquals(1, entry.sets.size)
        assertNull(entry.sets.single().reps)
        assertNull(entry.sets.single().weightKg)
        val program = TrainingProgram(name = "Plan", cycleCount = 1, days = listOf(
            ProgramDay(id = "day", exercises = listOf(PlannedExercise(
                exerciseId = "press", cycles = listOf(CycleTarget(sets = listOf(SetTarget(repsMin = 7, repsMax = 11)))),
            ))),
        ))
        val set = program.toWorkout("day", 0, 1000)!!.exercises.single().sets.single()
        assertNull(set.reps)
        assertEquals(7, set.targetRepsMin)
        assertEquals(11, set.targetRepsMax)
    }

    @Test
    fun metricActualsRemainDistinctAndBlankAcrossActiveHistoryAndDraftBackups() {
        val rows = listOf(
            LoggedSet(),
            LoggedSet(reps = 13, completed = true),
            LoggedSet(weightKg = 0.0, reps = 7, completed = true),
            LoggedSet(assistanceKg = 17.25, reps = 9, completed = true),
            LoggedSet(durationSeconds = 83.5, distanceMeters = 2312.0, completed = true),
        )
        val entry = SessionExercise(exerciseId = "mixed", sets = rows)
        val workout = Workout(name = "Metrics", exercises = listOf(entry))
        val source = AppData(active = workout, history = listOf(workout.copy(finishedAt = 9000)), workoutDraft = WorkoutDraft(exercises = listOf(entry)))
        val restored = DataCodec.decode(DataCodec.encode(source))
        assertEquals(source, restored)
        assertNull(restored.active!!.exercises.single().sets.first().weightKg)
        assertNull(restored.active.exercises.single().sets.first().reps)
        assertEquals(0.0, workout.volumeKg(), 0.0)
        val old = DataCodec.decode("""{"schemaVersion":1,"active":{"id":"old","name":"Old","startedAt":1000,"exercises":[{"id":"e","exerciseId":"press","sets":[{"id":"s","completed":true}]}]}}""")
        assertEquals(8, old.active!!.exercises.single().sets.single().reps)
        assertEquals(0.0, old.active.exercises.single().sets.single().weightKg!!, 0.0)
    }

    @Test
    fun completionUsesOnlyRequiredMetricsAndRejectsBlankActuals() {
        val exercise = Exercise("run", "Run", "Other", "Other", properties = mapOf("Metrics" to "Duration, Distance long"))
        val metrics = exercise.loggingMetrics()
        val entry = SessionExercise(id = "entry", exerciseId = exercise.id, sets = listOf(LoggedSet(id = "set")))
        val workout = Workout(name = "Training", exercises = listOf(entry))
        assertEquals(workout, workout.toggleSetCompletion(entry.id, "set", Preferences(), 1000, metrics))
        val seconds = SetMetric.DURATION.update(entry.sets.single(), SetMetric.DURATION.parse("83,5", false))
        assertFalse(seconds.canComplete(metrics))
        val run = SetMetric.DISTANCE_LONG.update(seconds, SetMetric.DISTANCE_LONG.parse("2.312", false))
        assertEquals(2312.0, run.distanceMeters!!, 0.00001)
        assertNull(run.reps)
        assertNull(run.weightKg)
        assertTrue(run.canComplete(metrics))
        assertEquals("83.5 Seconds · 2.312 km", run.describe(metrics, false))
        val changed = workout.copy(exercises = listOf(entry.copy(sets = listOf(run))))
        assertTrue(changed.toggleSetCompletion(entry.id, "set", Preferences(), 1000, metrics).exercises.single().sets.single().completed)
        assertTrue(LoggedSet(reps = 9).canComplete(listOf(SetMetric.REPS)))
        assertFalse(LoggedSet(reps = 9).canComplete(listOf(SetMetric.WEIGHT, SetMetric.REPS)))
        assertTrue(LoggedSet(reps = 9, weightKg = 0.0).canComplete(listOf(SetMetric.WEIGHT, SetMetric.REPS)))
        assertFalse(LoggedSet(reps = 9, weightKg = 12.0).canComplete(listOf(SetMetric.ASSISTANCE, SetMetric.REPS)))
        assertEquals(100.0, SetMetric.ASSISTANCE.parse("220.46226218", true)!!, 0.000001)
        assertEquals(5000.0, SetMetric.WEIGHT.parse("11023.113109", true)!!, 0.000001)
        for ((metric, invalid) in listOf(SetMetric.REPS to "1.5", SetMetric.REPS to "0", SetMetric.WEIGHT to "-1", SetMetric.WEIGHT to "5001", SetMetric.DURATION to "0", SetMetric.DISTANCE_LONG to "NaN")) {
            assertNull(metric.parse(invalid, false))
        }
        assertTrue(LoggedSet().canComplete(Exercise("none", "None", "Other", "Other", properties = mapOf("Metrics" to "None")).loggingMetrics()))
    }

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
            assertNull(it.reps)
            assertNull(it.weightKg)
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
                sets = listOf(LoggedSet(id = "a", reps = 7, weightKg = 0.0), LoggedSet(id = "b", reps = 9, weightKg = 0.0, completed = true)),
            )
        val second =
            SessionExercise(id = "second", exerciseId = "dip", sets = listOf(LoggedSet(id = "c", reps = 11, weightKg = 0.0)))
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
                sets = listOf(LoggedSet(id = "a", reps = 7, weightKg = 0.0), LoggedSet(id = "b", reps = 9, weightKg = 0.0)),
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
