package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExerciseSwapTest {
    private fun exercise(
        id: String,
        muscle: String,
        muscles: List<String> = listOf(muscle),
        secondary: List<String> = emptyList(),
        pattern: String = "",
        joint: String = "",
        archived: Boolean = false,
    ) = Exercise(id, id, muscle, "Equipment", secondary = secondary, muscles = muscles, archived = archived,
        properties = mapOf("Movement Patterns" to pattern, "Primary Joint Action" to joint, "Type" to "Multi-joint (compound)"))

    private val bench = exercise("bench", "Chest", listOf("Chest", "Front Delts"), listOf("Triceps"), "Horizontal Push", "Shoulder Horizontal Flexion")
    private val dbBench = exercise("db-bench", "Chest", listOf("Chest", "Front Delts"), listOf("Triceps"), "Horizontal Push", "Shoulder Horizontal Flexion")
    private val fly = exercise("fly", "Chest", listOf("Chest"), pattern = "Chest Accessory", joint = "Shoulder Horizontal Flexion")
    private val dip = exercise("dip", "Triceps", listOf("Triceps", "Chest"), pattern = "Vertical Push")
    private val ohp = exercise("ohp", "Front Delts", listOf("Front Delts"), listOf("Triceps"), "Vertical Push")
    private val squat = exercise("squat", "Quads", listOf("Quads", "Glutes"), pattern = "Squat")
    private val machine = exercise("machine", "Chest", listOf("Chest", "Front Delts"), pattern = "Horizontal Push")
    private val catalog = listOf(squat, fly, machine, dip, ohp, dbBench, bench)

    @Test fun samePrimaryMuscleLeadsAndUnrelatedMusclesAreExcluded() {
        val ranked = rankSwapAlternatives(bench, catalog, excludedIds = emptySet()).map { it.exercise.id }
        assertEquals(listOf("db-bench", "machine", "fly", "ohp", "dip"), ranked)
        assertFalse("squat" in ranked)
        val first = rankSwapAlternatives(bench, catalog, emptySet()).first()
        assertEquals(listOf(SwapReason.SAME_MUSCLES), first.reasons)
    }

    @Test fun gymEquipmentFiltersCandidatesAndLabelsTheRemainder() {
        val ranked = rankSwapAlternatives(bench, catalog, emptySet(), available = { it.id != "db-bench" && it.id != "machine" })
        assertEquals(listOf("fly", "ohp", "dip"), ranked.map { it.exercise.id })
        assertTrue(ranked.all { SwapReason.AT_GYM in it.reasons })
        // When the gym excludes nothing, the chip would be noise.
        assertTrue(rankSwapAlternatives(bench, catalog, emptySet()).none { SwapReason.AT_GYM in it.reasons })
        assertTrue(rankSwapAlternatives(bench, catalog, emptySet(), available = { false }).isEmpty())
    }

    @Test fun excludesArchivedDoNotRecommendWorkoutExercisesAndTheOriginal() {
        val archivedFly = fly.copy(archived = true)
        val ranked = rankSwapAlternatives(bench, catalog.map { if (it.id == "fly") archivedFly else it },
            excludedIds = setOf("bench", "machine"), doNotRecommend = setOf("dip")).map { it.exercise.id }
        assertEquals(listOf("db-bench", "ohp"), ranked)
    }

    @Test fun favoritesAndRecentBreakTiesWithinTheSameMuscleTier() {
        val twins = listOf("t1", "t2", "t3").map { exercise(it, "Chest", listOf("Chest", "Front Delts"), listOf("Triceps"), "Horizontal Push") }
        val ranked = rankSwapAlternatives(bench, twins, emptySet(), favorites = setOf("t3"), recentIds = listOf("t2"))
        assertEquals(listOf("t3", "t2", "t1"), ranked.map { it.exercise.id })
        assertEquals(listOf(SwapReason.SAME_MUSCLES, SwapReason.FAVORITE), ranked[0].reasons)
        assertEquals(listOf(SwapReason.SAME_MUSCLES, SwapReason.RECENT), ranked[1].reasons)
        // A favorite never beats a same-primary-muscle exercise from a different muscle tier.
        val boostedDip = rankSwapAlternatives(bench, catalog, emptySet(), favorites = setOf("dip")).map { it.exercise.id }
        assertTrue(boostedDip.indexOf("dip") > boostedDip.indexOf("fly"))
    }

    @Test fun orderIsDeterministicAndCapped() {
        val twins = (1..12).map { exercise("twin$it", "Chest", listOf("Chest", "Front Delts"), listOf("Triceps"), "Horizontal Push", "Shoulder Horizontal Flexion") }
        val first = rankSwapAlternatives(bench, twins, emptySet())
        assertEquals(8, first.size)
        // Equal scores keep the supplied (search index default) order.
        assertEquals(twins.take(8).map { it.id }, first.map { it.exercise.id })
        assertEquals(first, rankSwapAlternatives(bench, twins, emptySet()))
    }

    @Test fun recentIdsComeFromCompletedSetsNewestFirst() {
        val done = listOf(LoggedSet(completed = true))
        val history = listOf(
            Workout(name = "Old", startedAt = 1, exercises = listOf(SessionExercise(exerciseId = "a", sets = done))),
            Workout(name = "New", startedAt = 2, exercises = listOf(SessionExercise(exerciseId = "b", sets = done),
                SessionExercise(exerciseId = "skipped"))),
        )
        assertEquals(listOf("b", "a"), history.recentExerciseIds())
    }

    private fun workout(vararg entries: SessionExercise) =
        Workout(name = "W", exercises = entries.toList(), selectedExerciseId = entries.first().id, restUntil = 99)

    @Test fun nothingCompletedReplacesInPlaceWithTargetsKindsAndRestButNoActuals() {
        val sets = listOf(
            LoggedSet(id = "w", warmup = true, weightKg = 40.0, reps = 10),
            LoggedSet(id = "s1", weightKg = 100.0, reps = 8, rir = 2, targetRepsMin = 6, targetRepsMax = 8, targetRir = 2),
            LoggedSet(id = "s2", kind = SetKind.FAILURE, weightKg = 100.0, rir = 0, targetRepsMin = 6, targetRepsMax = 8),
        )
        val original = SessionExercise(id = "a", exerciseId = "bench", sets = sets, restSeconds = 150, notes = "Seat 4")
        val other = SessionExercise(id = "b", exerciseId = "row")
        val result = workout(original, other).withSwappedExercise("a", "db-bench")
        assertEquals(listOf("db-bench", "row"), result.exercises.map { it.exerciseId })
        val swapped = result.exercises.first()
        assertNotEquals("a", swapped.id)
        assertEquals(swapped.id, result.selectedExerciseId)
        assertEquals(150, swapped.restSeconds)
        // Today's note moves with the in-place swap rather than vanishing with the old entry.
        assertEquals("Seat 4", swapped.notes)
        assertEquals(listOf(true, false, false), swapped.sets.map { it.warmup })
        assertEquals(listOf(SetKind.STANDARD, SetKind.STANDARD, SetKind.FAILURE), swapped.sets.map { it.kind })
        assertEquals(listOf(null, 6, 6), swapped.sets.map { it.targetRepsMin })
        assertEquals(listOf(null, 8, 8), swapped.sets.map { it.targetRepsMax })
        assertEquals(listOf(null, 2, null), swapped.sets.map { it.targetRir })
        assertTrue(swapped.sets.all { it.weightKg == null && it.reps == null && !it.completed })
        assertEquals(listOf(null, null, 0), swapped.sets.map { it.rir })
        assertTrue(swapped.sets.none { it.id in setOf("w", "s1", "s2") })
        assertEquals(99, result.restUntil)
        // A replacement without reps never inherits rep targets.
        val timed = workout(original).withSwappedExercise("a", "plank", supportsReps = false).exercises.single()
        assertTrue(timed.sets.all { it.targetRepsMin == null && it.targetRepsMax == null })
    }

    @Test fun completedSetsStayWithOriginalAndRemainingSetsMoveToReplacementAfterIt() {
        val sets = listOf(
            LoggedSet(id = "s1", weightKg = 100.0, reps = 8, completed = true, targetRepsMin = 6),
            LoggedSet(id = "s2", weightKg = 100.0, reps = 8, targetRepsMin = 6),
            LoggedSet(id = "s3", targetRepsMin = 6),
        )
        val first = SessionExercise(id = "first", exerciseId = "squat")
        val original = SessionExercise(id = "a", exerciseId = "bench", sets = sets, restSeconds = 90, reviewedEffortSetIds = setOf("s1", "s2"))
        val result = workout(first, original).withSwappedExercise("a", "db-bench")
        assertEquals(listOf("squat", "bench", "db-bench"), result.exercises.map { it.exerciseId })
        val kept = result.exercises[1]
        assertEquals("a", kept.id)
        assertEquals(listOf(sets[0]), kept.sets)
        assertEquals(setOf("s1"), kept.reviewedEffortSetIds)
        val replacement = result.exercises[2]
        assertEquals(2, replacement.sets.size)
        assertTrue(replacement.sets.all { it.weightKg == null && it.targetRepsMin == 6 && !it.completed })
        assertEquals(90, replacement.restSeconds)
        assertEquals(replacement.id, result.selectedExerciseId)
        assertEquals(1, result.completedSets())
    }

    @Test fun splitSidesMyoAndSupersetsStayConsistent() {
        val sides = SessionExercise(id = "a", exerciseId = "curl", sets = listOf(LoggedSet(id = "l"), LoggedSet(id = "m", kind = SetKind.MYO)), supersetId = "g")
            .withSplitSides().withMyoComponent("m")
            .let { it.copy(sets = it.sets.map { s -> if (s.id == "l") s.copy(completed = true, weightKg = 10.0, reps = 10) else s }) }
        val partner = SessionExercise(id = "b", exerciseId = "pushdown", supersetId = "g")
        val result = workout(sides, partner).withSwappedExercise("a", "hammer")
        val kept = result.exercises[0]
        val replacement = result.exercises[1]
        // The left log was completed, so the whole left/right pair stays with the original.
        assertEquals(listOf(SetSide.LEFT, SetSide.RIGHT), kept.sets.map { it.side })
        assertNull(kept.supersetId)
        assertEquals("g", replacement.supersetId)
        assertEquals("g", result.exercises[2].supersetId)
        replacement.validateSetLinks()
        assertEquals(listOf(SetSide.LEFT, SetSide.RIGHT, SetSide.LEFT, SetSide.RIGHT), replacement.sets.map { it.side })
        assertTrue(replacement.sets.all { it.kind == SetKind.MYO })
        assertEquals(1, replacement.logicalSets().size)
        assertEquals(2, replacement.logicalSets().single().components.size)

        // A lone superset member left behind loses its group label.
        val pair = workout(SessionExercise(id = "x", exerciseId = "a", sets = listOf(LoggedSet(completed = true, reps = 5)), supersetId = "h"),
            SessionExercise(id = "y", exerciseId = "b", supersetId = "h"))
        val moved = pair.withSwappedExercise("x", "c")
        assertEquals(listOf(null, "h", "h"), moved.exercises.map { it.supersetId })
    }

    @Test fun fullyLoggedExerciseGivesReplacementOneFreshWorkingRow() {
        val original = SessionExercise(id = "a", exerciseId = "bench", sets = listOf(
            LoggedSet(warmup = true, completed = true, reps = 5),
            LoggedSet(completed = true, reps = 8, weightKg = 80.0, targetRepsMax = 8),
        ))
        val result = workout(original).withSwappedExercise("a", "db-bench")
        assertEquals(2, result.exercises.size)
        val fresh = result.exercises[1].sets.single()
        assertFalse(fresh.warmup || fresh.completed)
        assertEquals(8, fresh.targetRepsMax)
        assertNull(fresh.weightKg)
    }

    private val routine = Routine(id = "r", name = "Upper", exerciseIds = listOf("row", "bench", "curl"), prescriptions = listOf(
        TemplateExercise("row", 3, 8, 12, 120), TemplateExercise("bench", 4, 5, 8, 180), TemplateExercise("curl", 2, 10, 15, 60),
    ))

    @Test fun templateSwapPreservesOrderAndPrescriptions() {
        val swapped = routine.withSwappedExercise("bench", "db-bench")!!
        assertEquals(listOf("row", "db-bench", "curl"), swapped.exerciseIds)
        assertEquals(TemplateExercise("db-bench", 4, 5, 8, 180), swapped.prescriptions[1])
        assertEquals(routine.prescriptions - routine.prescriptions[1], swapped.prescriptions - swapped.prescriptions[1])
        assertNull(swapped.validationError())
        assertNull(routine.withSwappedExercise("bench", "curl"))
        assertNull(routine.withSwappedExercise("missing", "db-bench"))
        val legacy = Routine(id = "l", name = "Old", exerciseIds = listOf("bench"))
        assertEquals(listOf("db-bench"), legacy.withSwappedExercise("bench", "db-bench")!!.exerciseIds)
    }

    private fun started(): AppData {
        val data = AppData(routines = listOf(routine, routine.copy(id = "other", name = "Other")))
        return data.withStartedRoutine("r", listOf("row", "bench", "curl").map { Exercise(it, it, "Back", "Cable") }, 1_000)
    }

    @Test fun appDataSwapUpdatesOnlyTheStartedTemplateWhenAsked() {
        val data = started()
        assertEquals("r", data.active!!.routineId)
        val entry = data.active!!.exercises[1].id
        assertEquals(SwapPlan(SwapScope.TEMPLATE, "Upper", false), data.swapPlanFor(entry, "db-bench"))
        assertEquals(SwapPlan(SwapScope.TEMPLATE, "Upper", true), data.swapPlanFor(entry, "curl"))

        val today = data.withExerciseSwap(entry, "db-bench", SwapScope.TODAY, emptyList())
        assertEquals(data.routines, today.routines)
        assertEquals("db-bench", today.active!!.exercises[1].exerciseId)
        assertEquals(4, today.active!!.exercises[1].sets.size)
        assertEquals(180, today.active!!.exercises[1].restSeconds)

        val forGood = data.withExerciseSwap(entry, "db-bench", SwapScope.TEMPLATE, emptyList())
        assertEquals(listOf("row", "db-bench", "curl"), forGood.routines[0].exerciseIds)
        assertEquals(data.routines[1], forGood.routines[1])
        assertEquals(today.active!!.exercises.map { it.exerciseId }, forGood.active!!.exercises.map { it.exerciseId })

        // A free workout has no plan to update.
        val free = AppData(active = workout(SessionExercise(id = "x", exerciseId = "bench")))
        assertNull(free.swapPlanFor("x", "db-bench"))
    }

    @Test fun programDaySwapKeepsCycleTargets() {
        val planned = PlannedExercise(exerciseId = "bench", cycles = List(3) { CycleTarget(listOf(SetTarget(repsMin = 5, repsMax = 8, rir = 2)), restSeconds = 150) })
        val day = ProgramDay(id = "d", name = "Push", exercises = listOf(planned))
        val program = TrainingProgram(id = "p", name = "Block", days = listOf(day), cycleCount = 3)
        val data = AppData(programs = listOf(program), active = program.toWorkout("d", 0, 1_000))
        val entry = data.active!!.exercises.single().id
        assertEquals(SwapPlan(SwapScope.PROGRAM, "Push · Block", false), data.swapPlanFor(entry, "db-bench"))
        val next = data.withExerciseSwap(entry, "db-bench", SwapScope.PROGRAM, emptyList())
        val swapped = next.programs.single().days.single().exercises.single()
        assertEquals("db-bench", swapped.exerciseId)
        assertEquals(planned.cycles, swapped.cycles)
        assertEquals(planned.id, swapped.id)
        assertEquals(2, next.active!!.exercises.single().sets.single().targetRir)
    }

    @Test fun undoRestoresExactWorkoutAndTemplate() {
        val data = started()
        val entry = data.active!!.exercises[1].id
        val next = data.withExerciseSwap(entry, "db-bench", SwapScope.TEMPLATE, emptyList())
        val undo = data.exerciseSwapUndo(next)!!
        assertEquals(data.active, undo.workout)
        assertEquals(routine, undo.routine)
        assertNull(undo.program)
        val restored = next.withoutExerciseSwap(undo)
        assertEquals(data, restored)
        // Undo never resurrects a workout that has since been replaced.
        val later = next.copy(active = Workout(name = "Another"))
        assertEquals(later.active, later.withoutExerciseSwap(undo).active)
        assertNull(data.exerciseSwapUndo(data))
    }

    @Test fun lateUndoNeverErasesWorkLoggedAfterTheSwap() {
        val data = started()
        val entry = data.active!!.exercises[1].id
        val next = data.withExerciseSwap(entry, "db-bench", SwapScope.TEMPLATE, emptyList())
        val undo = data.exerciseSwapUndo(next)!!
        assertTrue(next.canUndoExerciseSwap(undo))
        // A set logged after the swap: Undo is withdrawn and changes nothing.
        val logged = next.copy(active = next.active!!.copy(exercises = next.active!!.exercises.map { e ->
            e.copy(sets = e.sets.mapIndexed { i, s -> if (i == 0) s.copy(weightKg = 30.0, reps = 10, completed = true) else s })
        }))
        assertFalse(logged.canUndoExerciseSwap(undo))
        assertEquals(logged, logged.withoutExerciseSwap(undo))
        // A template note saved after the swap is just as protected.
        val noted = next.copy(routines = next.routines.map { if (it.id == "r") it.copy(name = "Upper (edited)") else it })
        assertFalse(noted.canUndoExerciseSwap(undo))
        assertEquals(noted, noted.withoutExerciseSwap(undo))
    }

    @Test fun routineIdSurvivesTheCodec() {
        val data = started()
        val decoded = DataCodec.decode(DataCodec.encode(data))
        assertEquals("r", decoded.active!!.routineId)
        assertEquals(data.active, decoded.active)
        val legacy = JSONObject(DataCodec.encode(data)).apply { getJSONObject("active").remove("routineId") }
        assertNull(DataCodec.decode(legacy.toString()).active!!.routineId)
    }
}
