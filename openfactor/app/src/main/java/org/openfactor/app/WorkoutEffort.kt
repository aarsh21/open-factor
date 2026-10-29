package org.openfactor.app

/** Bulk logging is atomic: one missing or invalid actual leaves every pending row unchanged. */
fun SessionExercise.canLogAllSets(metrics: List<SetMetric>): Boolean =
    sets.any { !it.completed } && sets.filterNot { it.completed }.all { it.canComplete(metrics) }

fun Workout.withAllSetsCompleted(
    exerciseId: String,
    preferences: Preferences,
    now: Long,
    metrics: List<SetMetric> = listOf(SetMetric.WEIGHT, SetMetric.REPS),
): Workout {
    val entry = exercises.find { it.id == exerciseId } ?: return this
    if (!entry.canLogAllSets(metrics)) return this
    // Only the final boundary can start rest. Intermediate rows were logged together, so their
    // individual timers must not leak through when rest after the exercise is disabled.
    val pending = entry.sets.filterNot { it.completed }
    return pending.foldIndexed(this) { index, workout, set ->
        val policy = if (index == pending.lastIndex) preferences else preferences.copy(autoRest = false)
        workout.toggleSetCompletion(exerciseId, set.id, policy, now, metrics)
    }
}

/** A rating describes the last actual log only; it is never copied across earlier sets or sides. */
fun SessionExercise.lastEffortSet(metrics: List<SetMetric>): LoggedSet? {
    if (metrics.none { it.isReps } || sets.isEmpty() || sets.any { !it.completed }) return null
    return sets.lastOrNull { !it.warmup && it.canComplete(metrics) }
}

fun SessionExercise.pendingEffortSet(metrics: List<SetMetric>): LoggedSet? =
    lastEffortSet(metrics)?.takeIf { it.rir == null && it.id !in reviewedEffortSetIds }

/** A skipped prompt is durable too. Undo/recomplete does not ask again for the same last log. */
fun SessionExercise.withReviewedEffort(setId: String, rir: Int?): SessionExercise {
    if (rir != null && rir !in 0..10) return this
    val set = sets.find { it.id == setId && !it.warmup && it.completed } ?: return this
    return copy(
        sets = sets.map { if (it.id == set.id) it.copy(rir = rir) else it },
        reviewedEffortSetIds = reviewedEffortSetIds + set.id,
    )
}
