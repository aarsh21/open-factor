package org.openfactor.app

import kotlin.math.abs
import kotlin.math.roundToLong

/** Resolved source splits, not recommendations for a missing split preference. */
enum class ProgramSplit(val trainingDays: IntRange, internal val upperTimeFraction: Double) {
    FULL_BODY(2..6, 0.65),
    UPPER_LOWER(2..2, 0.5),
    PUSH_PULL(2..2, 0.65),
    UPPER_LOWER_FULL(3..3, 0.5),
    PUSH_PULL_LEGS(3..3, 0.65),
    UPPER_LOWER_UPPER_LOWER(4..4, 0.5),
    PUSH_PULL_HINGE_SQUAT(4..4, 0.5),
    PUSH_PULL_LEGS_ACCESSORIES(4..4, 0.65),
    UPPER_LOWER_UPPER_LOWER_UPPER(5..5, 0.6),
    PUSH_PULL_HINGE_SQUAT_ACCESSORIES(5..5, 0.5),
    PUSH_PULL_LEGS_UPPER_LOWER(5..5, 0.6),
    PUSH_PULL_HINGE_SQUAT_UPPER_LOWER(6..6, 0.5),
    PUSH_PULL_LEGS_PUSH_PULL_LEGS(6..6, 0.65),
}

enum class ProgramWorkoutLength(internal val budgetMinutes: Int) {
    MAX_20(18),
    MAX_40(30),
    MAX_60(50),
    MAX_90(75),
    MAX_120(105),
    OVER_120(125),
}

enum class ProgramVolumeCategory(
    internal val setupSeconds: Int,
    internal val defaultRestSeconds: Int,
    internal val minimumSets: Int,
) {
    UPPER_COMPOUND(180, 120, 4),
    LOWER_COMPOUND(180, 180, 2),
    UPPER_ISOLATION(90, 90, 0),
    LOWER_ISOLATION(90, 90, 0),
}

/**
 * A category's time-calculation layout, before exercise selection and distribution across days.
 * These slots are NOT selected exercises, primary/secondary roles, or an editable program.
 */
data class ProgramCategoryVolume(
    val setsPerSlot: List<Int>,
    val estimatedMicros: Long,
) {
    val totalSets: Int get() = setsPerSlot.sum()
}

/**
 * Recovered weekly time/volume stage (ecf034–ecf49c).
 *
 * The source picks the closest estimate, even when it exceeds the budget. Compound ties select the
 * last candidate; isolation ties select the first. Isolation receives the actual remainder after
 * compound allocation, not the unspent fraction of the initial compound budget.
 *
 * Warm-up automation and category rest settings must be resolved by the caller. Missing rest-map
 * entries use the source category defaults; explicit zero is preserved. This does not infer any
 * equipment, focus, experience, exercise-ranking, or split-recommendation policy.
 */
fun allocateProgramVolume(
    goal: ProgramGoal,
    split: ProgramSplit,
    trainingDays: Int,
    workoutLength: ProgramWorkoutLength,
    warmUpsEnabled: Boolean,
    restSeconds: Map<ProgramVolumeCategory, Int> = emptyMap(),
): Map<ProgramVolumeCategory, ProgramCategoryVolume> {
    require(trainingDays in split.trainingDays) { "Unsupported split/frequency combination" }
    require(restSeconds.values.all { it >= 0 }) { "Rest must not be negative" }
    val weeklyMicros = workoutLength.budgetMinutes * 60_000_000L * trainingDays
    val compoundFraction = if (goal == ProgramGoal.STRENGTH) 0.6 else 0.5
    val result = linkedMapOf<ProgramVolumeCategory, ProgramCategoryVolume>()
    for ((compound, isolation, fraction) in
        listOf(
            Triple(
                ProgramVolumeCategory.UPPER_COMPOUND,
                ProgramVolumeCategory.UPPER_ISOLATION,
                split.upperTimeFraction,
            ),
            Triple(
                ProgramVolumeCategory.LOWER_COMPOUND,
                ProgramVolumeCategory.LOWER_ISOLATION,
                1.0 - split.upperTimeFraction,
            ),
        )) {
        val regionMicros = (weeklyMicros * fraction).roundToLong()
        val compoundVolume =
            closestVolume(
                compound,
                (regionMicros * compoundFraction).roundToLong(),
                warmUpsEnabled,
                restSeconds[compound] ?: compound.defaultRestSeconds,
            )
        result[compound] = compoundVolume
        result[isolation] =
            closestVolume(
                isolation,
                regionMicros - compoundVolume.estimatedMicros,
                warmUpsEnabled,
                restSeconds[isolation] ?: isolation.defaultRestSeconds,
            )
    }
    return result
}

private fun closestVolume(
    category: ProgramVolumeCategory,
    budgetMicros: Long,
    warmUpsEnabled: Boolean,
    restSeconds: Int,
): ProgramCategoryVolume {
    val compound = category.minimumSets > 0
    val options = if (compound) compoundVolumeOptions else isolationVolumeOptions
    var best: ProgramCategoryVolume? = null
    var bestDistance = Long.MAX_VALUE
    for (sets in options) {
        if (sets.sum() < category.minimumSets) continue
        // ed31ac: setup + warm-up per slot, 30 seconds per set, rest only between its sets.
        val micros =
            sets.sumOf { count ->
                (category.setupSeconds.toLong() + (if (warmUpsEnabled) 120L else 0L) +
                    count * 30L + (count - 1) * restSeconds.toLong()) * 1_000_000L
            }
        // edb428–edb444 truncates to whole milliseconds BEFORE taking the absolute value.
        val distance = abs((budgetMicros - micros) / 1000)
        if (distance < bestDistance || (compound && distance == bestDistance)) {
            best = ProgramCategoryVolume(sets, micros)
            bestDistance = distance
        }
    }
    return checkNotNull(best)
}

// The order is significant for ties. These are the two source sync* enumerators, not a search over
// all possible exercise/set combinations. TimeCalcAllocation stores two count × set-count groups.
private val compoundVolumeOptions: List<List<Int>> = buildList {
    // 12a0ae4–12a0bb0
    for (count in 1..4) for (sets in 2..(if (count == 1) 2 else 3)) add(List(count) { sets })
    // 12a0bb4–12a0c6c
    for (count in 1..4) for (sets in 2..3) add(List(4) { 3 } + List(count) { sets })
    add(List(8) { 4 })
    for (sets in 3..4) add(List(4) { 4 } + List(5) { sets })
    for (count in 6..8) add(List(4 + count) { 4 })
    for (sets in 4..5) add(List(4) { 5 } + List(8) { sets })
    for (count in 9..16) add(List(4 + count) { 5 })
}

private val isolationVolumeOptions: List<List<Int>> = buildList {
    // 12a097c–12a0a78: the inclusive/exclusive boundaries differ at counts 20 and 30.
    for (count in 1..42) {
        val minimum = when { count <= 20 -> 2; count <= 30 -> 3; else -> 4 }
        val maximum = when { count < 20 -> 3; count < 30 -> 4; else -> 5 }
        for (sets in minimum..maximum) add(List(count) { sets })
    }
}
