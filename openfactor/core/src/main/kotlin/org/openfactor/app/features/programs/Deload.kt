package org.openfactor.app

import kotlin.math.floor
import kotlin.math.roundToInt

/** Source period keys, separate from the normal 0..<cycleCount target indexes. */
enum class ProgramDeload(val sourceCycle: Int) {
    FIRST(-1),
    LAST(999);

    fun cycleKeys(normalCount: Int): List<Int> {
        require(normalCount >= 0) { "Normal cycle count cannot be negative" }
        val normal = (0 until normalCount).toList()
        return if (this == FIRST) listOf(sourceCycle) + normal else normal + sourceCycle
    }
}

/**
 * Recovered standard-set deload transform, not history-based load progression.
 *
 * Source b78148 trims the end of the set list using floor, while b7874c rounds each rep bound.
 * Only RIR depends on the exercise's reps metric. Generated targets are not skipped.
 * Rest overrides and local set IDs are retained; the input target is never edited.
 * Failure sets become standard; other supported kinds are retained.
 */
fun CycleTarget.deloadTarget(isReps: Boolean): CycleTarget =
    copy(
        sets =
            sets.take(maxOf(1, floor(sets.size * 0.8).toInt())).map { set ->
                set.copy(
                    repsMin = set.repsMin?.let { (it * 0.8).roundToInt() },
                    repsMax = set.repsMax?.let { (it * 0.8).roundToInt() },
                    rir = if (isReps) (set.rir ?: 0) + 3 else set.rir,
                    kind = if (set.kind == SetKind.FAILURE) SetKind.STANDARD else set.kind,
                )
            },
        skip = false,
    )

/**
 * Explicit deload targets win unchanged; otherwise use the adjacent normal cycle.
 * An absent normal target falls back to a blank standard set, without applying the transform.
 * This does not append a period to PlannedExercise.cycles or change persistence/session indexes.
 */
fun List<CycleTarget>.targetForDeload(
    placement: ProgramDeload,
    isReps: Boolean,
    overrideTarget: CycleTarget? = null,
): CycleTarget =
    overrideTarget
        ?: (if (placement == ProgramDeload.FIRST) firstOrNull() else lastOrNull())
            ?.deloadTarget(isReps)
        ?: CycleTarget()

/** Missing metric facts cannot be replaced by loggingMetrics' legacy Weight/Reps default. */
fun PlannedExercise.targetForDeload(placement: ProgramDeload, exercise: Exercise?): CycleTarget? =
    deloadTargets[placement] ?: run {
        if (exercise?.properties?.get("Metrics") == null) return null
        cycles.targetForDeload(placement, exercise.loggingMetrics().any { it.isReps })
    }
