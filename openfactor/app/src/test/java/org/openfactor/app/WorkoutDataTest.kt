package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class WorkoutDataTest {
    @Test
    fun setKindsSurviveBackupAndLegacyRowsRemainStandard() {
        val sets = SetKind.entries.map { LoggedSet(kind = it, weightKg = 23.0, reps = 7) } + LoggedSet(warmup = true)
        val workout = Workout(name = "Kinds", exercises = listOf(SessionExercise(exerciseId = "row", sets = sets)))
        val data = AppData(active = workout, history = listOf(workout.copy(id = "history")))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        val legacy = DataCodec.encode(data).replace(Regex(",?\\s*\"kind\"\\s*:\\s*\"[A-Z]+\"\\s*,?")) { match ->
            if (match.value.startsWith(",") && match.value.endsWith(",")) "," else ""
        }
        assertTrue(DataCodec.decode(legacy).active!!.exercises.single().sets.all { it.kind == SetKind.STANDARD })
        assertTrue(DataCodec.decode(legacy).active!!.exercises.single().sets.last().warmup)
        assertThrows(IllegalArgumentException::class.java) {
            DataCodec.decode(DataCodec.encode(data).replace("\"MYO\"", "\"UNKNOWN\""))
        }
    }

    @Test
    fun distanceUnitsConvertMilesAndYardsWithoutChangingOtherActuals() {
        assertEquals(2414.016, SetMetric.DISTANCE_LONG.parse("1.5", false, true)!!, 0.000001)
        assertEquals(1500.0, SetMetric.DISTANCE_LONG.parse("1.5", true, false)!!, 0.0)
        assertEquals(22.86, SetMetric.DISTANCE_SHORT.parse("25", true, true)!!, 0.000001)
        assertEquals(22.86, SetMetric.DISTANCE_PER_SIDE.parse("25", false, true)!!, 0.000001)
        val set = LoggedSet(distanceMeters = 2414.016, durationSeconds = 73.0, weightKg = 31.0)
        assertEquals("1.5 mi · 73 Seconds", set.describe(listOf(SetMetric.DISTANCE_LONG, SetMetric.DURATION), false, true))
        assertEquals("2.414", SetMetric.DISTANCE_LONG.display(set, false, false))
        assertEquals("31", SetMetric.WEIGHT.display(set, false, true))
        assertEquals("73", SetMetric.DURATION.display(set, true, true))
        assertEquals("yd / side", SetMetric.DISTANCE_PER_SIDE.label(false, true))
        assertNull(SetMetric.DISTANCE_SHORT.parse("0", false, true))
    }

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
        assertEquals("", fresh.notes)
        assertEquals(75, fresh.restSeconds)
        assertNull(fresh.sets.single().reps)
        assertNull(fresh.sets.single().weightKg)
        assertTrue(defaults.hasOverrides)
        assertTrue(ExerciseDefaults(restSeconds = 0).hasOverrides)
        val reset = defaults.withoutOverrides()
        assertEquals(ExerciseDefaults("Global note", doNotRecommend = true), reset)
        assertFalse(reset.hasOverrides)
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
        assertFalse(legacy.customExercises.single().archived)
        assertEquals(listOf(SetMetric.WEIGHT, SetMetric.REPS), legacy.customExercises.single().loggingMetrics())
        val catalogExercise = DataCodec.catalog("""[{"id":"air-bike","name":"Air Biking","muscle":"Other","muscles":[]}]""").single()
        assertEquals(listOf("Other"), catalogExercise.muscles)
    }

    @Test
    fun duplicateIsAnEditableNewExerciseWithoutChangingTheOriginal() {
        val aliases = listOf("Supported Hold", "Independent " + "x".repeat(487))
        assertEquals(515, aliases.joinToString(", ").length)
        val original = CustomExerciseDraft(
            name = "Hold", metric1 = SetMetric.ASSISTANCE, metric2 = SetMetric.DURATION,
            type = CustomExerciseType.ISOLATION_LOWER, laterality = "Unilateral",
            primary = listOf("Hamstrings"), secondary = listOf("Glutes"),
            resistance = listOf(setOf("machine"), setOf("bar", "plates")), support = listOf(setOf("bench", "rack")),
            bodyPercent = "7.6", rom = 2, stability = 4,
            primaryJoint = "Knee Flexion", alternateNames = "Supported Hold", instructions = "Keep still",
        ).toExercise(emptyList(), "original").copy(aliases = aliases, frames = listOf("original.png"), clips = listOf("original.mp4"), archived = true)
        val draft = original.duplicateDraft()
        assertEquals("7.6", draft.bodyPercent)
        assertEquals(CustomExerciseType.ISOLATION_LOWER, draft.type)
        val duplicate = draft.copy(name = "Edited Hold", bodyPercent = "81").toExercise(emptyList())
        assertEquals("original", original.id)
        assertEquals("Hold", original.name)
        assertEquals(.076, original.bodyweight, 0.0)
        assertEquals(aliases, original.aliases)
        assertNotEquals(original.id, duplicate.id)
        assertTrue(original.archived)
        assertFalse(duplicate.archived)
        assertEquals(original, DataCodec.decode(DataCodec.encode(AppData(customExercises = listOf(original)))).customExercises.single())
        assertEquals("Edited Hold", duplicate.name)
        assertEquals(.81, duplicate.bodyweight, 0.0)
        assertEquals(listOf(setOf("machine"), setOf("bar", "plates")), duplicate.resistanceEquipment)
        assertEquals(listOf(setOf("bench", "rack")), duplicate.supportEquipment)
        assertEquals(listOf(SetMetric.ASSISTANCE, SetMetric.DURATION), duplicate.loggingMetrics())
        assertEquals(listOf("Hamstrings"), duplicate.muscles)
        assertEquals(listOf("Glutes"), duplicate.secondary)
        assertEquals(aliases, duplicate.aliases)
        assertEquals("Keep still", duplicate.instructions)
        assertTrue(duplicate.frames.isEmpty() && duplicate.clips.isEmpty())
        assertFalse(original.copy(properties = original.properties - "Region").duplicateDraft().validBasics)
        assertFalse(draft.copy(resistance = emptyList()).validDetails)
        val extended = draft.copy(alternateNames = draft.alternateNames + ", Additional hold").toExercise(emptyList())
        assertEquals(aliases + "Additional hold", extended.aliases)
        assertNotEquals(duplicate.id, extended.id)
        assertEquals(listOf(duplicate, extended), DataCodec.decode(DataCodec.encode(AppData(customExercises = listOf(duplicate, extended)))).customExercises)
    }

    @Test
    fun infoClassificationUsesRawPatternsWithoutInferringFromNameOrMuscles() {
        val source = Exercise("classification", "Chest press", "Chest", "Other",
            properties = mapOf("Type" to "Multi-joint (compound)", "Region" to "Core", "Movement Patterns" to "Lower Back Accessory"))
        assertEquals("Lower body", source.effectiveRegion)
        assertEquals("Compound Lower", source.infoTypeLabel)
        assertEquals(CustomExerciseType.COMPOUND_LOWER, source.duplicateDraft().type)
        fun patterns(value: String) = source.copy(properties = source.properties + ("Movement Patterns" to value))
        assertEquals("Compound Upper", patterns("Triceps Accessory, Chest Accessory").infoTypeLabel)
        assertEquals("Core", patterns("Lower Back Accessory, Horizontal Pull").infoTypeLabel)
        assertEquals("Core", patterns("Lower Back Accessory, Chest Accessory").infoTypeLabel)
        assertEquals("Core", patterns("Lower Back Accessory, Ab Accessory").infoTypeLabel)
        assertEquals("Core", patterns("Lower Back Accessory, Oblique Acessory").infoTypeLabel)
        assertEquals("Core", patterns("None").infoTypeLabel)
        assertEquals(emptyList<String>(), patterns("None").movementPatterns)
        assertEquals(listOf("Horizontal Pull", "Vertical Pull"), patterns("Horizontal Pull, Vertical Pull").movementPatterns)
        val absent = source.copy(properties = source.properties - "Movement Patterns")
        assertNull(absent.movementPatterns)
        assertNull(absent.infoTypeLabel)
        assertNull(absent.duplicateDraft().type)
        assertNull(source.copy(properties = source.properties - "Region").infoTypeLabel)
        val fullBody = source.copy(properties = source.properties + ("Region" to "Full body"))
        assertEquals("Compound Lower", fullBody.infoTypeLabel)
        assertEquals(CustomExerciseType.COMPOUND_FULL, fullBody.duplicateDraft().type)
        assertEquals("Lower Isolation", source.copy(properties = source.properties + ("Type" to "Single joint (isolation)")).infoTypeLabel)
        assertEquals("Upper Isolation", patterns("Chest Accessory").copy(properties = patterns("Chest Accessory").properties + ("Type" to "Single joint (isolation)")).infoTypeLabel)
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
    fun changingActiveDeloadRestPreservesRecoveredRirTargetsAndRecordedActuals() {
        val catalog = listOf(Exercise("press", "Press", "Chest", "Barbell", properties = mapOf("Metrics" to "Weight, Reps")))
        val plan = TrainingProgram(id = "plan", name = "Deload", cycleCount = 1,
            days = listOf(ProgramDay(id = "day", exercises = listOf(PlannedExercise(
                exerciseId = "press", cycles = listOf(CycleTarget(listOf(SetTarget(repsMin = 7, repsMax = 9, rir = 10)))),
            )))))
        for (placement in ProgramDeload.entries) {
            val started = plan.copy(deloadPlacement = placement).toWorkout("day", placement.sourceCycle, 1000, catalog = catalog)!!
            val recorded = started.copy(exercises = started.exercises.map { entry ->
                entry.copy(sets = entry.sets.map { it.copy(weightKg = 40.0, reps = 8, rir = 3, completed = true) })
            })
            val entry = recorded.exercises.single()
            val targets = entry.sets.associate { it.id to WorkingSetTarget(it.targetRepsMin, it.targetRepsMax, it.targetRir) }
            assertEquals(13, entry.sets.single().targetRir)
            assertEquals(13, recorded.targetRirLimit)
            val changed = recorded.withExerciseTargets(entry.id, targets, 75)
            assertEquals(recorded.copy(exercises = listOf(entry.copy(restSeconds = 75))), changed)
            // Target editing must still work after the source program has been deleted.
            val data = AppData(active = changed, history = listOf(recorded.copy(id = "saved", finishedAt = 2000)))
            assertTrue(data.programs.isEmpty())
            assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
            assertThrows(IllegalArgumentException::class.java) {
                recorded.withExerciseTargets(entry.id, mapOf(entry.sets.single().id to WorkingSetTarget(rir = 14)), 75)
            }
        }
        val normal = plan.toWorkout("day", 0, 1000, catalog = catalog)!!
        assertEquals(10, normal.targetRirLimit)
        assertThrows(IllegalArgumentException::class.java) {
            normal.withExerciseTargets(normal.exercises.single().id,
                mapOf(normal.exercises.single().sets.single().id to WorkingSetTarget(rir = 11)), 75)
        }
        assertEquals(10, normal.copy(programId = null, programCycle = -1).targetRirLimit)
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
    fun warmupRestUsesTheLastWarmupRowAndItsOwnPolicy() {
        val entry = SessionExercise(id = "entry", exerciseId = "press", restSeconds = 73, sets = listOf(
            LoggedSet(id = "warm1", warmup = true, reps = 5),
            LoggedSet(id = "warm2", warmup = true, reps = 3),
            LoggedSet(id = "work", reps = 9),
        ))
        val workout = Workout(name = "Rest", exercises = listOf(entry), restUntil = 81234)
        val metrics = listOf(SetMetric.REPS)
        for (lastWarmupRest in listOf(false, true)) for (betweenExercises in listOf(false, true)) {
            val prefs = Preferences(restAfterLastWarmupSet = lastWarmupRest, restBetweenExercises = betweenExercises)
            val first = workout.toggleSetCompletion("entry", "warm1", prefs, 1000, metrics)
            assertEquals(81234L, first.restUntil)
            // The last warm-up is determined by row order even when logged out of order.
            val last = workout.toggleSetCompletion("entry", "warm2", prefs, 2000, metrics)
            assertEquals(if (lastWarmupRest) 75000L else 81234L, last.restUntil)
            val onlyWarmups = workout.copy(exercises = listOf(entry.copy(sets = entry.sets.filter { it.warmup })))
            assertEquals(
                if (lastWarmupRest) 75000L else 81234L,
                onlyWarmups.toggleSetCompletion("entry", "warm2", prefs, 2000, metrics).restUntil,
            )
            val uncompleted = last.toggleSetCompletion("entry", "warm2", prefs, 3000, metrics)
            assertEquals(last.restUntil, uncompleted.restUntil)
            assertFalse(uncompleted.exercises.single().sets[1].completed)
            val restored = DataCodec.decode(DataCodec.encode(AppData(active = last, preferences = prefs)))
            assertEquals(last, restored.active)
            assertEquals(prefs, restored.preferences)
        }
        assertEquals(81234L, workout.toggleSetCompletion("entry", "warm2", Preferences(autoRest = false), 2000, metrics).restUntil)
        val zero = workout.copy(exercises = listOf(entry.copy(restSeconds = 0)))
        assertEquals(81234L, zero.toggleSetCompletion("entry", "warm2", Preferences(restSeconds = 137), 2000, metrics).restUntil)
    }

    @Test
    fun betweenExerciseRestDoesNotDependOnAutomaticSelectionOrOverwriteExistingDeadlines() {
        val first = SessionExercise(id = "first", exerciseId = "press", restSeconds = 67, sets = listOf(
            LoggedSet(id = "a", reps = 13), LoggedSet(id = "b", reps = 7),
        ))
        val second = SessionExercise(id = "second", exerciseId = "curl")
        val workout = Workout(name = "Rest", exercises = listOf(first, second), selectedExerciseId = "first", restUntil = 91234)
        for (autoNext in listOf(false, true)) for (betweenExercises in listOf(false, true)) {
            val prefs = Preferences(exerciseAutoNext = autoNext, restBetweenExercises = betweenExercises, restAfterLastWarmupSet = false)
            val partial = workout.toggleSetCompletion("first", "a", prefs, 1000, listOf(SetMetric.REPS))
            assertEquals(68000L, partial.restUntil)
            val finished = partial.toggleSetCompletion("first", "b", prefs, 2000, listOf(SetMetric.REPS))
            assertEquals(if (betweenExercises) 69000L else 68000L, finished.restUntil)
            assertEquals(if (autoNext) "second" else "first", finished.selectedExerciseId)
            assertEquals(second, finished.exercises[1])
            val unchecked = finished.toggleSetCompletion("first", "b", prefs, 3000, listOf(SetMetric.REPS))
            assertEquals(finished.restUntil, unchecked.restUntil)
            assertEquals(finished.selectedExerciseId, unchecked.selectedExerciseId)
            // The last working set remains a boundary with no following exercise,
            // and a skipped warm-up must not turn it into an ordinary between-set rest.
            for (skippedWarmup in listOf(false, true)) {
                val only = workout.copy(exercises = listOf(first.copy(sets =
                    (if (skippedWarmup) listOf(LoggedSet(id = "skipped", warmup = true)) else emptyList()) +
                        first.sets.map { it.copy(completed = it.id == "a") }
                )))
                val last = only.toggleSetCompletion("first", "b", prefs, 4000, listOf(SetMetric.REPS))
                assertEquals(if (betweenExercises) 71000L else 91234L, last.restUntil)
                assertEquals("first", last.selectedExerciseId)
            }
        }
        val legacy = DataCodec.decode("""{"schemaVersion":1}""").preferences
        assertTrue(legacy.restAfterLastWarmupSet && legacy.restBetweenExercises)
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
