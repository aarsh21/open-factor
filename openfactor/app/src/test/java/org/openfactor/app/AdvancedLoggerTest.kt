package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class AdvancedLoggerTest {
    @Test fun sidesAndComponentsRetainIndependentActualsAndOneIdentityAcrossBackup() {
        val root = LoggedSet(id = "root", weightKg = 23.0, reps = 7, rir = 3)
        val entry = SessionExercise(id = "exercise", exerciseId = "row", sets = listOf(root))
            .withSetKind(root.id, SetKind.MYO).withSplitSides()
        assertEquals(listOf(root.id), entry.logicalSets().map { it.root.id })
        assertEquals(2, entry.logicalSets().single().components.size)
        assertEquals(listOf(23.0, 23.0, null, null), entry.sets.map { it.weightKg })
        assertEquals(listOf(7, 7, null, null), entry.sets.map { it.reps })
        val right = entry.sets[1]
        val changed = entry.copy(sets = entry.sets.map {
            when (it.id) {
                root.id -> it.copy(completed = true)
                right.id -> it.copy(weightKg = 19.0, reps = 5, completed = true)
                else -> it
            }
        })
        val workout = Workout(name = "Asymmetric", exercises = listOf(changed))
        val data = AppData(active = workout, history = listOf(workout.copy(id = "saved")),
            workoutDraft = WorkoutDraft(exercises = listOf(changed)),
            preferences = Preferences(restBetweenSidesPercent = 37, supersetAutoNext = false))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertEquals(1, workout.completedSets())
        assertEquals(256.0, workout.volumeKg(), 0.0) // 23×7 + 19×5, not an average.
        assertFalse(changed.logicalSets().single().completed)
        assertEquals(23.0, changed.sets.first().weightKg!!, 0.0)
        val failure = changed.withSetKind(root.id, SetKind.FAILURE)
        assertEquals(2, failure.sets.size)
        assertTrue(failure.sets.all { it.kind == SetKind.FAILURE && it.rir == 0 })
        assertEquals(listOf(root.id, right.id), failure.sets.map { it.id })
        assertEquals(listOf(23.0, 19.0), failure.sets.map { it.weightKg })
    }

    @Test fun myoAndSideRestApplyAtTheirOwnBoundariesWithoutAdvancingEarly() {
        val main = LoggedSet(id = "main", weightKg = 20.0, reps = 9)
        val entry = SessionExercise(id = "a", exerciseId = "row", sets = listOf(main), restSeconds = 80)
            .withSetKind(main.id, SetKind.MYO).withSplitSides().let { exercise ->
                exercise.copy(sets = exercise.sets.map { it.copy(weightKg = 20.0, reps = if (it.segmentOf == null) 9 else 4) })
            }
        var workout = Workout(name = "Myo", selectedExerciseId = "a", restUntil = 123,
            exercises = listOf(entry, SessionExercise(id = "b", exerciseId = "pull"))).withSuperset(setOf("a", "b"))
        val prefs = Preferences(restBetweenSidesPercent = 40, exerciseAutoNext = false)
        workout = workout.toggleSetCompletion("a", entry.sets[0].id, prefs, 1000)
        assertEquals(7000, workout.restUntil) // 40% of the 15s Myo rest.
        assertEquals("a", workout.selectedExerciseId)
        workout = workout.toggleSetCompletion("a", entry.sets[1].id, prefs, 2000)
        assertEquals(17000, workout.restUntil)
        workout = workout.toggleSetCompletion("a", entry.sets[2].id, prefs, 3000)
        assertEquals(35000, workout.restUntil) // 40% of 80s on the final component's first side.
        assertEquals("a", workout.selectedExerciseId)
        val suppressed = workout.toggleSetCompletion("a", entry.sets[3].id, prefs.copy(restBetweenExercises = false), 4000)
        assertEquals(35000, suppressed.restUntil)
        assertEquals("b", suppressed.selectedExerciseId) // Group control is independent of ordinary auto-next.
        workout = workout.toggleSetCompletion("a", entry.sets[3].id, prefs, 4000)
        assertEquals(84000, workout.restUntil)
        assertEquals("b", workout.selectedExerciseId)
        val undo = workout.toggleSetCompletion("a", entry.sets[0].id, prefs, 5000)
        assertEquals(84000, undo.restUntil)
        assertFalse(undo.exercises.first().logicalSets().single().completed)
    }

    @Test fun supersetOrderUsesFirstUnfinishedOrdinalNotCompletedCountAndSurvivesReordering() {
        fun entry(id: String, done: List<Boolean>) = SessionExercise(id = id, exerciseId = id,
            sets = listOf(LoggedSet(warmup = true, completed = false)) + done.map { LoggedSet(weightKg = 1.0, reps = 1, completed = it) })
        val workout = Workout(name = "Round", exercises = listOf(
            entry("a", listOf(true, false, false)), entry("outside", listOf(false)),
            entry("b", listOf(false, true, true)), entry("c", listOf(true, false)),
        )).withSuperset(setOf("a", "b", "c"))
        assertEquals("b", workout.nextSupersetExercise("c")) // B has more complete logs but an earlier unfinished ordinal.
        val reordered = workout.copy(exercises = workout.exercises.reversed())
        assertEquals("b", reordered.nextSupersetExercise("c"))
        val b = workout.exercises[2]
        val advanced = workout.copy(exercises = workout.exercises.map { if (it.id == "b") it.copy(sets = b.sets.map { s -> s.copy(completed = true) }) else it })
        assertEquals("a", advanced.nextSupersetExercise("c")) // Wrap; completed B and unrelated exercise excluded.
        assertNull(workout.nextSupersetExercise("outside"))
        val regrouped = workout.withSuperset(setOf("b", "outside"))
        assertEquals(workout.exercises[0].supersetId, regrouped.exercises[0].supersetId)
        assertEquals(regrouped.exercises[1].supersetId, regrouped.exercises[2].supersetId)
        val removed = workout.copy(exercises = workout.exercises.filter { it.id == "a" || it.id == "outside" }).normalizeSupersets()
        assertTrue(removed.exercises.all { it.supersetId == null })
    }

    @Test fun removalsAndKindChangesNeverLeaveDanglingLinksOrAlterOtherSets() {
        val entry = SessionExercise(exerciseId = "row", sets = listOf(LoggedSet(id = "root"), LoggedSet(id = "other")))
            .withSetKind("root", SetKind.MYO).withMyoComponent("root").withSplitSides()
        val extraRight = entry.logicalSets().first().components[1].last()
        val removedComponent = entry.withoutSet(extraRight.id).validateSetLinks()
        assertEquals(2, removedComponent.logicalSets().first().components.size)
        assertEquals(entry.logicalSets()[1], removedComponent.logicalSets()[1])
        val removedRoot = entry.withoutSet(entry.sets[1].id).validateSetLinks()
        assertEquals(listOf("other"), removedRoot.logicalSets().map { it.root.id })
        val warmup = entry.withSetKind("root", SetKind.STANDARD, warmup = true).validateSetLinks()
        assertEquals(1, warmup.logicalSets().first().components.size)
        assertTrue(warmup.logicalSets().first().logs.all { it.warmup })
        val newWarmup = entry.withNewSet(warmup = true).validateSetLinks()
        assertTrue(newWarmup.sets.take(2).all { it.warmup })
        assertEquals(2, newWarmup.logicalSets().first().logs.size)
        val merged = entry.withLeftSideOnly().validateSetLinks()
        assertTrue(merged.sets.all { it.side == null && it.pairId == null })
        assertEquals(entry.logicalSets().map { it.root.id }, merged.logicalSets().map { it.root.id })
    }

    @Test fun addedWorkingSetsReuseActualsWithoutCompletionEffortOrMyoLinks() {
        val source = LoggedSet(id = "main", weightKg = 42.5, reps = 8, rir = 1, completed = true,
            assistanceKg = 6.0, durationSeconds = 30.0, distanceMeters = 100.0,
            targetRepsMin = 6, targetRepsMax = 10, targetRir = 2)
        val exercise = SessionExercise(exerciseId = "row", sets = listOf(source))
            .withSetKind(source.id, SetKind.MYO)
        val changed = exercise.withNewSet().validateSetLinks()
        val added = changed.logicalSets().last().root
        assertEquals(exercise.sets, changed.sets.take(exercise.sets.size))
        assertNotEquals(source.id, added.id)
        assertEquals(42.5, added.weightKg!!, 0.0)
        assertEquals(8, added.reps)
        assertEquals(6.0, added.assistanceKg!!, 0.0)
        assertEquals(30.0, added.durationSeconds!!, 0.0)
        assertEquals(100.0, added.distanceMeters!!, 0.0)
        assertEquals(6, added.targetRepsMin)
        assertEquals(10, added.targetRepsMax)
        assertEquals(2, added.targetRir)
        assertNull(added.rir)
        assertFalse(added.completed)
        assertFalse(added.warmup)
        assertNull(added.segmentOf)
        assertNull(added.pairId)
        assertEquals(SetKind.STANDARD, added.kind)
        val warmup = changed.withNewSet(warmup = true).validateSetLinks().sets.first()
        assertTrue(warmup.warmup)
        assertNull(warmup.weightKg)
        assertNull(warmup.reps)
        assertNull(warmup.targetRepsMin)
        assertFalse(warmup.completed)
    }

    @Test fun addedSplitSetsKeepIndependentLeftAndRightActuals() {
        val exercise = SessionExercise(exerciseId = "row", sets = listOf(
            LoggedSet(id = "left", weightKg = 20.0, reps = 8, completed = true, side = SetSide.LEFT),
            LoggedSet(id = "right", weightKg = 17.5, reps = 7, completed = true, side = SetSide.RIGHT, pairId = "left"),
        ))
        val changed = exercise.withNewSet().validateSetLinks()
        val added = changed.logicalSets().last().logs
        assertEquals(listOf(20.0, 17.5), added.map { it.weightKg })
        assertEquals(listOf(8, 7), added.map { it.reps })
        assertTrue(added.none { it.completed })
        assertEquals(added.first().id, added.last().pairId)
        assertNotEquals("left", added.first().id)
        assertEquals(exercise.sets, changed.sets.take(2))
    }

    @Test fun backupRejectsCrossExercisePairsCyclesAndMissingRoots() {
        fun rejected(vararg rows: LoggedSet) = assertThrows(IllegalArgumentException::class.java) {
            DataCodec.decode(DataCodec.encode(AppData(active = Workout(name = "Invalid", exercises = listOf(
                SessionExercise(exerciseId = "row", sets = rows.toList()),
                SessionExercise(exerciseId = "other", sets = listOf(LoggedSet(id = "external", side = SetSide.LEFT),
                    LoggedSet(id = "external-right", side = SetSide.RIGHT, pairId = "external"))),
            )))))
        }
        rejected(LoggedSet(id = "x", side = SetSide.RIGHT, pairId = "external"))
        rejected(LoggedSet(id = "x", kind = SetKind.MYO, segmentOf = "x"))
        rejected(LoggedSet(id = "x", kind = SetKind.MYO, segmentOf = "y"), LoggedSet(id = "y", kind = SetKind.MYO, segmentOf = "x"))
        rejected(LoggedSet(id = "x", side = SetSide.LEFT))
        rejected(LoggedSet(id = "x"), LoggedSet(id = "x"))
        val old = DataCodec.decode("""{"schemaVersion":1,"active":{"id":"w","name":"Old","startedAt":1,"exercises":[{"id":"e","exerciseId":"row","sets":[{"id":"s","warmup":true}]}]}}""")
        assertEquals(1, old.active!!.exercises.single().logicalSets().size)
        assertTrue(old.active.exercises.single().sets.single().warmup)
        assertNull(old.active.exercises.single().sets.single().side)
        for (invalid in listOf("-1", "101", "4294967296", "37.5", "\"37\"")) {
            assertThrows(IllegalArgumentException::class.java) {
                DataCodec.decode("""{"schemaVersion":1,"preferences":{"restBetweenSidesPercent":$invalid}}""")
            }
        }
    }
}
