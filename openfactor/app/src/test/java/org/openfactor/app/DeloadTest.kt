package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class DeloadTest {
    @Test
    fun capturedPressAndRowDeloadsMatchNativeTransform() {
        val press =
            CycleTarget(
                listOf(
                    SetTarget("press-1", 7, 9, 3),
                    SetTarget("press-2", 7, 9, 2),
                    SetTarget("press-3", 7, 9, 1),
                ),
                restSeconds = 0,
            )
        val row =
            CycleTarget(
                listOf(
                    SetTarget("row-1", 9, 11, 2),
                    SetTarget("row-2", 9, 11, 1),
                    SetTarget("row-3", 9, 11, 0),
                ),
                restSeconds = 75,
            )
        assertEquals(
            CycleTarget(listOf(SetTarget("press-1", 6, 7, 6), SetTarget("press-2", 6, 7, 5)), restSeconds = 0),
            press.deloadTarget(isReps = true),
        )
        assertEquals(
            CycleTarget(listOf(SetTarget("row-1", 7, 9, 5), SetTarget("row-2", 7, 9, 4)), restSeconds = 75),
            row.deloadTarget(isReps = true),
        )
        assertEquals(listOf(3, 2, 1), press.sets.map { it.rir })
        assertEquals(listOf(2, 1, 0), row.sets.map { it.rir })
    }

    @Test
    fun setCountsFloorButAsymmetricRepBoundsRoundAndRirIsNotClamped() {
        val expectedCounts = listOf(0, 1, 1, 2, 3, 4, 4)
        expectedCounts.forEachIndexed { count, expected ->
            val target = CycleTarget(List(count) { SetTarget("set-$it", 3, 8, 10) }, skip = true)
            val deload = target.deloadTarget(isReps = true)
            assertEquals("Input set count $count", expected, deload.sets.size)
            assertEquals((0 until expected).map { "set-$it" }, deload.sets.map { it.id })
            deload.sets.forEach {
                assertEquals(2, it.repsMin)
                assertEquals(6, it.repsMax)
                assertEquals(13, it.rir)
            }
            assertFalse(deload.skip)
            assertTrue(target.skip)
            assertEquals(count, target.sets.size)
        }
    }

    @Test
    fun nullRepBoundsStayNullAndOnlyRepExercisesGainRir() {
        val missing = CycleTarget(listOf(SetTarget("unknown")))
        assertEquals(SetTarget("unknown", rir = 3), missing.deloadTarget(isReps = true).sets.single())
        assertEquals(missing, missing.deloadTarget(isReps = false))
        // The native metric predicate gates RIR, not the transformation of present rep bounds.
        val nonRep = CycleTarget(listOf(SetTarget("timed", null, 7, 1)), restSeconds = 0)
        assertEquals(
            CycleTarget(listOf(SetTarget("timed", null, 6, 1)), restSeconds = 0),
            nonRep.deloadTarget(isReps = false),
        )
    }

    @Test
    fun sourcePeriodKeysStaySeparateAndExplicitOverridesBypassGeneration() {
        val first = CycleTarget(listOf(SetTarget("first", 3, 8, null)), restSeconds = 0)
        val last = CycleTarget(listOf(SetTarget("last", 9, 11, 1)), restSeconds = 65)
        val cycles = listOf(first, last)
        assertEquals(listOf(-1, 0, 1), ProgramDeload.FIRST.cycleKeys(2))
        assertEquals(listOf(0, 1, 999), ProgramDeload.LAST.cycleKeys(2))
        assertEquals(
            CycleTarget(listOf(SetTarget("first", 2, 6, 3)), restSeconds = 0),
            cycles.targetForDeload(ProgramDeload.FIRST, isReps = true),
        )
        assertEquals(
            CycleTarget(listOf(SetTarget("last", 7, 9, 4)), restSeconds = 65),
            cycles.targetForDeload(ProgramDeload.LAST, isReps = true),
        )
        val explicit = CycleTarget(listOf(SetTarget("explicit", 17, 23, 0)), skip = true, restSeconds = 0)
        assertSame(explicit, cycles.targetForDeload(ProgramDeload.FIRST, true, explicit))
        assertSame(explicit, emptyList<CycleTarget>().targetForDeload(ProgramDeload.LAST, true, explicit))
        val fallback = emptyList<CycleTarget>().targetForDeload(ProgramDeload.FIRST, isReps = true)
        assertEquals(1, fallback.sets.size)
        assertNull(fallback.sets.single().rir)
        assertFalse(fallback.skip)
        assertEquals(listOf(first, last), cycles)
    }
}
