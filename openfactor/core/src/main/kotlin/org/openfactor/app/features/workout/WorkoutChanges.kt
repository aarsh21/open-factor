package org.openfactor.app

/** True when an edit changed only typed metric values of one set, never its completion or kind. */
fun AppData.onlyTypedSetValuesChanged(next: AppData, exerciseId: String, setId: String): Boolean {
    val before = active?.exercises?.find { it.id == exerciseId }?.sets?.find { it.id == setId } ?: return false
    val after = next.active?.takeIf { it.id == active?.id }?.exercises?.find { it.id == exerciseId }?.sets?.find { it.id == setId } ?: return false
    return after.copy(weightKg = before.weightKg, reps = before.reps, assistanceKg = before.assistanceKg,
        durationSeconds = before.durationSeconds, distanceMeters = before.distanceMeters) == before
}

// Compiled once: titles are derived in list rows and set rows during composition.
private val titleWordStart = Regex("(^|[\\s(\\-/])([\\p{L}])")

val Exercise.title: String
    get() =
        name.replace(titleWordStart) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }

/** The one default for unknown/removed exercises, shared by effort eligibility and the session UI. */
fun loggingMetricsOf(exercise: Exercise?): List<SetMetric> = exercise?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS)

/**
 * Undo offers end with the first real edit. Navigation (selection and the effort prompts it records),
 * the rest timer and pausing are not edits, so they never withdraw an offer.
 */
fun Workout.sameWorkAs(other: Workout): Boolean = copy(
    selectedExerciseId = other.selectedExerciseId, effortPrompts = other.effortPrompts,
    restUntil = other.restUntil, pausedAt = other.pausedAt, pausedDurationMs = other.pausedDurationMs,
) == other
