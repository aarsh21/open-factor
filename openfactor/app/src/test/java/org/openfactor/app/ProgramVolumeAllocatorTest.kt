package org.openfactor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgramVolumeAllocatorTest {
    private fun assertVolume(
        volumes: Map<ProgramVolumeCategory, ProgramCategoryVolume>,
        category: ProgramVolumeCategory,
        sets: List<Int>,
        seconds: Long,
    ) {
        assertEquals(sets, volumes.getValue(category).setsPerSlot)
        assertEquals(seconds * 1_000_000L, volumes.getValue(category).estimatedMicros)
    }

    @Test
    fun shortFullBodyUsesCategoryMinimumsAndCanExceedTheBudget() {
        val volumes = allocateProgramVolume(
            ProgramGoal.STRENGTH, ProgramSplit.FULL_BODY, 2, ProgramWorkoutLength.MAX_20, true,
        )
        // 2160 seconds weekly; upper budget 1404, compound target 842.4.
        // A single two-set slot costs 480 but fails the four-set upper minimum.
        assertVolume(volumes, ProgramVolumeCategory.UPPER_COMPOUND, listOf(2, 2), 960)
        assertVolume(volumes, ProgramVolumeCategory.UPPER_ISOLATION, listOf(3), 480)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_COMPOUND, listOf(2), 540)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_ISOLATION, listOf(2), 360)
        assertEquals(4, volumes.getValue(ProgramVolumeCategory.UPPER_COMPOUND).totalSets)
        assertEquals(2, volumes.getValue(ProgramVolumeCategory.LOWER_COMPOUND).totalSets)
    }

    @Test
    fun compoundTiesChooseLastAndIsolationTiesChooseFirstUsingActualRemainder() {
        for (goal in listOf(ProgramGoal.HYPERTROPHY, ProgramGoal.BOTH)) {
            val volumes = allocateProgramVolume(
                goal, ProgramSplit.PUSH_PULL_HINGE_SQUAT_ACCESSORIES, 5,
                ProgramWorkoutLength.MAX_20, true,
            )
            // Upper compound target 1350: [3,3] costs 1260; [2,2,2] costs 1440.
            assertVolume(volumes, ProgramVolumeCategory.UPPER_COMPOUND, listOf(2, 2, 2), 1440)
            // Actual remainder is 1260, NOT 1350. 3×2 costs 1080; 3×3/4×2 cost 1440.
            assertVolume(volumes, ProgramVolumeCategory.UPPER_ISOLATION, listOf(2, 2, 2), 1080)
        }
    }

    @Test
    fun asymmetricRestOverridesAndFiveDaySplitUseWeeklyNotPerSessionBudget() {
        val volumes = allocateProgramVolume(
            ProgramGoal.STRENGTH, ProgramSplit.PUSH_PULL_LEGS_UPPER_LOWER, 5,
            ProgramWorkoutLength.MAX_60, false,
            mapOf(
                ProgramVolumeCategory.UPPER_COMPOUND to 73,
                ProgramVolumeCategory.LOWER_COMPOUND to 211,
                ProgramVolumeCategory.UPPER_ISOLATION to 37,
                ProgramVolumeCategory.LOWER_ISOLATION to 0,
            ),
        )
        // Native enumerator layouts and ed31ac timing, not values read from allocator internals.
        assertVolume(volumes, ProgramVolumeCategory.UPPER_COMPOUND, List(10) { 4 }, 5190)
        assertVolume(volumes, ProgramVolumeCategory.UPPER_ISOLATION, List(15) { 3 }, 3810)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_COMPOUND, listOf(3, 3, 3, 3, 2, 2), 3670)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_ISOLATION, List(13) { 3 }, 2340)
    }

    @Test
    fun explicitZeroDoesNotReplaceUnspecifiedCategoryDefaults() {
        val volumes = allocateProgramVolume(
            ProgramGoal.HYPERTROPHY, ProgramSplit.PUSH_PULL_LEGS, 3,
            ProgramWorkoutLength.MAX_40, false,
            mapOf(ProgramVolumeCategory.UPPER_COMPOUND to 0),
        )
        assertVolume(volumes, ProgramVolumeCategory.UPPER_COMPOUND, listOf(3, 3, 3, 3, 2, 2, 2), 1800)
        assertVolume(volumes, ProgramVolumeCategory.UPPER_ISOLATION, List(7) { 2 }, 1680)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_COMPOUND, listOf(2, 2), 840)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_ISOLATION, listOf(3, 3, 3), 1080)
    }

    @Test
    fun largestBudgetStopsAtSourceCandidateLimitRatherThanInventingMoreSlots() {
        val volumes = allocateProgramVolume(
            ProgramGoal.STRENGTH, ProgramSplit.PUSH_PULL_LEGS_PUSH_PULL_LEGS, 6,
            ProgramWorkoutLength.OVER_120, false,
        )
        assertVolume(volumes, ProgramVolumeCategory.UPPER_COMPOUND, List(20) { 5 }, 16200)
        assertVolume(volumes, ProgramVolumeCategory.UPPER_ISOLATION, List(27) { 4 }, 12960)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_COMPOUND, List(11) { 4 }, 9240)
        assertVolume(volumes, ProgramVolumeCategory.LOWER_ISOLATION, List(18) { 3 }, 6480)
    }

    @Test
    fun rejectsUnresolvedSplitFrequencyAndNegativeRest() {
        assertThrows(IllegalArgumentException::class.java) {
            allocateProgramVolume(
                ProgramGoal.BOTH, ProgramSplit.PUSH_PULL, 3, ProgramWorkoutLength.MAX_60, false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            allocateProgramVolume(
                ProgramGoal.BOTH, ProgramSplit.FULL_BODY, 3, ProgramWorkoutLength.MAX_60, false,
                mapOf(ProgramVolumeCategory.UPPER_ISOLATION to -1),
            )
        }
    }
}
