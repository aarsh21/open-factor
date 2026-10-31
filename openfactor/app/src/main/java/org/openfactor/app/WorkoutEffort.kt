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

/** When the optional end-of-exercise effort question is asked. */
enum class EffortPromptTiming(val label: String) {
    NEXT_EXERCISE("When moving to the next exercise"),
    LAST_SET("Right after the last set"),
}

val Preferences.asksEffortOnLeave: Boolean
    get() = rateEffortAfterExercise && effortPromptTiming == EffortPromptTiming.NEXT_EXERCISE

/**
 * An effort question owed for an exercise the lifter has left. [afterRest] marks an automatic
 * advance (auto-next, superset rotation): the app moved on, not the lifter, so the question waits
 * out the running rest timer instead of covering it right after the hardest set.
 */
data class EffortPrompt(val exerciseId: String, val afterRest: Boolean = false)

/** The exercise the logger shows: the selection, or the first exercise when it is unset or gone. */
fun Workout.currentExerciseId(): String? =
    exercises.find { it.id == selectedExerciseId }?.id ?: exercises.firstOrNull()?.id

/**
 * Applied to every change of the active workout, whatever caused it. Leaving an exercise whose last
 * working log is unrated owes one question about that exercise; returning to it before answering
 * withdraws the question. Done/Skip (reviewedEffortSetIds) or a changed last log retires it.
 */
fun Workout.withEffortPromptTransition(
    before: Workout?,
    preferences: Preferences,
    metrics: (SessionExercise) -> List<SetMetric>,
): Workout {
    if (before == null || before.id != id) return this
    val left = before.currentExerciseId()
    val arrived = currentExerciseId()
    val moved = left != arrived
    if (!moved && effortPrompts.isEmpty()) return this
    fun pending(entryId: String) = exercises.find { it.id == entryId }?.let { it.pendingEffortSet(metrics(it)) } != null
    var prompts = effortPrompts.filter { pending(it.exerciseId) && !(moved && it.exerciseId == arrived) }
    if (moved && left != null && preferences.asksEffortOnLeave && prompts.none { it.exerciseId == left } && pending(left)) {
        // A set completed in the same change means the logger advanced on its own.
        prompts = prompts + EffortPrompt(left, afterRest = completedSetSince(before))
    }
    return if (prompts == effortPrompts) this else copy(effortPrompts = prompts)
}

private fun Workout.completedSetSince(before: Workout): Boolean {
    val done = before.exercises.flatMapTo(HashSet()) { entry -> entry.sets.filter { it.completed }.map { it.id } }
    return exercises.any { entry -> entry.sets.any { it.completed && it.id !in done } }
}

/** Finishing leaves the current exercise too: everything still owed is asked now, rest or not. */
fun Workout.withEffortPromptsBeforeFinish(preferences: Preferences, metrics: (SessionExercise) -> List<SetMetric>): Workout {
    if (!preferences.asksEffortOnLeave) return this
    fun pending(entryId: String) = exercises.find { it.id == entryId }?.let { it.pendingEffortSet(metrics(it)) } != null
    val owed = effortPrompts.filter { pending(it.exerciseId) }.map { it.copy(afterRest = false) }
    val current = currentExerciseId()?.takeIf { id -> owed.none { it.exerciseId == id } && pending(id) }
    val prompts = owed + listOfNotNull(current?.let { EffortPrompt(it) })
    return if (prompts == effortPrompts) this else copy(effortPrompts = prompts)
}
