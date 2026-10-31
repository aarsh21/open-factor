package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class SetRowSwipeTest {
    private val width = 1000f
    private val flick = 300f
    private val minFlick = 30f
    private val reach = 100f

    private fun outcome(travel: Float, velocity: Float, canLog: Boolean = true) =
        setSwipeOutcome(travel, velocity, width, flick, minFlick, canLog)

    @Test fun slowShortDragsSpringBackAndDistanceOrFlickCommits() {
        assertEquals(SetSwipeOutcome.REST, outcome(0f, 0f))
        assertEquals(SetSwipeOutcome.REST, outcome(399f, 0f))
        assertEquals(SetSwipeOutcome.REST, outcome(-399f, 0f))
        assertEquals(SetSwipeOutcome.LOG, outcome(400f, 0f))
        assertEquals(SetSwipeOutcome.DELETE, outcome(-400f, 0f))
        // A flick commits regardless of distance, once it is clearly more than jitter.
        assertEquals(SetSwipeOutcome.LOG, outcome(40f, 300f))
        assertEquals(SetSwipeOutcome.DELETE, outcome(-40f, -300f))
        assertEquals(SetSwipeOutcome.REST, outcome(20f, 2000f))
        assertEquals(SetSwipeOutcome.REST, outcome(40f, 299f))
    }

    @Test fun flicksOnlyCommitTowardTheSideTheRowIsOnAndFlickingBackCancels() {
        // On the delete side, flicking toward log just returns the row.
        assertEquals(SetSwipeOutcome.REST, outcome(-100f, 2000f))
        assertEquals(SetSwipeOutcome.REST, outcome(100f, -2000f))
        // Past the commit point, a flick back cancels the commit.
        assertEquals(SetSwipeOutcome.REST, outcome(600f, -300f))
        assertEquals(SetSwipeOutcome.REST, outcome(-600f, 300f))
        assertEquals(SetSwipeOutcome.LOG, outcome(600f, -299f))
    }

    @Test fun aSetThatCannotBeLoggedRefusesInsteadOfCommitting() {
        assertEquals(SetSwipeOutcome.REFUSE, outcome(600f, 0f, canLog = false))
        assertEquals(SetSwipeOutcome.REFUSE, outcome(60f, 900f, canLog = false))
        assertEquals(SetSwipeOutcome.REST, outcome(60f, 0f, canLog = false))
        // Delete never depends on the values.
        assertEquals(SetSwipeOutcome.DELETE, outcome(-600f, 0f, canLog = false))
    }

    @Test fun rowTracksOneToOneThenRubberBandsWithoutPassingTheEdge() {
        assertEquals(250f, setSwipeShown(250f, width, true, reach), 0f)
        assertEquals(-400f, setSwipeShown(-400f, width, true, reach), 0f)
        val past = setSwipeShown(600f, width, true, reach)
        assertTrue("resists past the commit point ($past)", past in 400f..600f)
        assertTrue(setSwipeShown(700f, width, true, reach) > past)
        assertTrue(setSwipeShown(100_000f, width, true, reach) < width)
        assertTrue(setSwipeShown(-100_000f, width, true, reach) > -width)
        // A refused log only gives a short pull.
        assertTrue(setSwipeShown(30f, width, false, reach) < 30f)
        assertTrue(setSwipeShown(5_000f, width, false, reach) < reach)
        listOf(-900f, -450f, -120f, 0f, 120f, 450f, 900f).forEach { travel ->
            assertEquals(travel, setSwipeTravel(setSwipeShown(travel, width, true, reach), width, true, reach), .5f)
        }
        listOf(10f, 80f, 300f).forEach { travel ->
            assertEquals(travel, setSwipeTravel(setSwipeShown(travel, width, false, reach), width, false, reach), .5f)
        }
    }

    @Test fun commitPointArmsEachSideAndNeverArmsARefusedLog() {
        assertEquals(0, setSwipeArmed(399f, width, true))
        assertEquals(1, setSwipeArmed(400f, width, true))
        assertEquals(0, setSwipeArmed(900f, width, false))
        assertEquals(-1, setSwipeArmed(-400f, width, false))
        assertEquals(0, setSwipeArmed(-399f, width, true))
    }

    private fun splitMyoWorkout(): Workout {
        val first = LoggedSet(id = "first", weightKg = 20.0, reps = 9, completed = true)
        val second = LoggedSet(id = "second", weightKg = 22.5, reps = 8)
        val entry = SessionExercise(id = "row", exerciseId = "cable-row", sets = listOf(first, second),
            reviewedEffortSetIds = setOf("first"))
            .withSetKind(second.id, SetKind.MYO).withSplitSides()
        return Workout(id = "w", name = "Split", exercises = listOf(entry, SessionExercise(id = "other", exerciseId = "curl")),
            selectedExerciseId = "row", restUntil = 42)
    }

    @Test fun removingASideRemovesItsPairAndUndoRestoresTheExactSetsAtTheirPosition() {
        val data = AppData(active = splitMyoWorkout())
        val entry = data.active!!.exercises.first()
        val rightOfFirst = entry.sets.first { it.pairId == "first" }
        val removed = data.withRemovedSet("row", rightOfFirst.id)
        val left = removed.active!!.exercises.first()
        assertTrue(left.sets.none { it.logicalRootId == "first" })
        assertEquals(entry.sets.filter { it.logicalRootId == "second" }, left.sets)
        left.validateSetLinks()

        val undo = data.setRemovalUndo(removed)!!
        assertTrue(removed.canUndoSetRemoval(undo))
        assertEquals(data.active, removed.withoutSetRemoval(undo).active)
    }

    @Test fun removingAMyoComponentKeepsTheMainSetAndBothSidesConsistent() {
        val data = AppData(active = splitMyoWorkout())
        val entry = data.active!!.exercises.first()
        val component = entry.sets.first { it.segmentOf == "second" && it.side == SetSide.LEFT }
        val removed = data.withRemovedSet("row", component.id)
        val sets = removed.active!!.exercises.first().sets
        assertTrue(sets.none { it.segmentOf == "second" })
        assertEquals(2, sets.count { it.logicalRootId == "second" })
        removed.active!!.exercises.first().validateSetLinks()
        assertEquals(data.active, removed.withoutSetRemoval(data.setRemovalUndo(removed)!!).active)
    }

    @Test fun anyLaterEditWithdrawsUndoButMovingBetweenExercisesKeepsIt() {
        val data = AppData(active = splitMyoWorkout())
        val removed = data.withRemovedSet("row", "first")
        val undo = data.setRemovalUndo(removed)!!

        val typed = removed.copy(active = removed.active!!.copy(exercises = removed.active!!.exercises.map { e ->
            e.copy(sets = e.sets.map { if (it.id == "second") it.copy(reps = 12) else it })
        }))
        assertFalse(typed.canUndoSetRemoval(undo))
        assertSame(typed, typed.withoutSetRemoval(undo))
        // Tapping rest Skip/+30s or pausing is not an edit: the offer survives, and Undo keeps the timer.
        val rested = removed.copy(active = removed.active!!.copy(restUntil = 99, pausedAt = 5))
        assertTrue(rested.canUndoSetRemoval(undo))
        assertEquals(99L, rested.withoutSetRemoval(undo).active!!.restUntil)
        assertEquals(data.active!!.exercises, rested.withoutSetRemoval(undo).active!!.exercises)
        assertFalse(removed.copy(active = null).canUndoSetRemoval(undo))

        val moved = removed.copy(active = removed.active!!.copy(selectedExerciseId = "other"))
        assertTrue(moved.canUndoSetRemoval(undo))
        val restored = moved.withoutSetRemoval(undo).active!!
        assertEquals(data.active!!.exercises, restored.exercises)
        assertEquals("other", restored.selectedExerciseId)
    }

    @Test fun removingNothingOffersNoUndo() {
        val data = AppData(active = splitMyoWorkout())
        assertNull(data.setRemovalUndo(data.withRemovedSet("row", "missing")))
        assertNull(data.setRemovalUndo(data.withRemovedSet("missing", "first")))
        assertNull(AppData().setRemovalUndo(AppData().withRemovedSet("row", "first")))
    }
}
