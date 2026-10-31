package org.openfactor.app

/** Why a suggestion is listed; the first labels shown are the strongest reasons. */
enum class SwapReason(val label: String) {
    SAME_MUSCLES("Same muscles"),
    SAME_MOVEMENT("Same movement"),
    AT_GYM("At your gym"),
    FAVORITE("Favorite"),
    RECENT("Recent"),
}

data class SwapSuggestion(val exercise: Exercise, val reasons: List<SwapReason>)

enum class SwapScope { TODAY, TEMPLATE, PROGRAM }

/** The saved plan the active workout came from, when it still contains the original exercise. */
data class SwapPlan(val scope: SwapScope, val name: String, val alreadyContainsReplacement: Boolean)

/** Exact pre-swap values; plans are null when the swap did not change them. */
data class ExerciseSwapUndo(val workout: Workout, val routine: Routine? = null, val program: TrainingProgram? = null)

private fun Exercise.primaryMuscles(): Set<String> =
    muscles.ifEmpty { listOf(muscle) }.filterNot { it.equals("Other", true) }.mapTo(HashSet()) { it.lowercase() }

private fun Exercise.listProperty(key: String): Set<String> =
    properties[key].orEmpty().split(',').map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it != "none" }.toSet()

private fun jaccard(a: Set<String>, b: Set<String>): Double =
    if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

/**
 * Muscle fit decides eligibility and leads the order; movement, joint action and secondary
 * muscles refine it; favorites and recent use only break near-ties. [catalog] order (the
 * shared search index's default order) is the final deterministic tiebreak.
 */
fun rankSwapAlternatives(
    original: Exercise,
    catalog: List<Exercise>,
    excludedIds: Set<String>,
    favorites: Set<String> = emptySet(),
    recentIds: List<String> = emptyList(),
    doNotRecommend: Set<String> = emptySet(),
    available: (Exercise) -> Boolean = { true },
    limit: Int = 8,
): List<SwapSuggestion> {
    val primary = original.primaryMuscles()
    val patterns = original.listProperty("Movement Patterns")
    val joints = original.listProperty("Primary Joint Action")
    val secondary = original.secondary.mapTo(HashSet()) { it.lowercase() }
    val type = original.properties["Type"]
    val recency = recentIds.distinct().take(10).withIndex().associate { (index, id) -> id to index }
    var gymExcluded = false
    data class Scored(val suggestion: SwapSuggestion, val score: Double, val position: Int)
    val scored = catalog.withIndex().mapNotNull { (position, candidate) ->
        if (candidate.id == original.id || candidate.id in excludedIds || candidate.archived ||
            candidate.id in doNotRecommend) return@mapNotNull null
        val sameMuscle = original.muscle.lowercase() in primary && candidate.muscle.equals(original.muscle, true)
        val muscleFit = jaccard(primary, candidate.primaryMuscles())
        val movementFit = jaccard(patterns, candidate.listProperty("Movement Patterns"))
        // Exercises without real muscle data can still match on movement pattern.
        if (muscleFit == 0.0 && !(primary.isEmpty() && movementFit > 0.0)) return@mapNotNull null
        if (!available(candidate)) { gymExcluded = true; return@mapNotNull null }
        val favorite = candidate.id in favorites
        val recent = recency[candidate.id]
        val score = (if (sameMuscle) 1000.0 else 0.0) + 400 * muscleFit + 250 * movementFit +
            150 * jaccard(joints, candidate.listProperty("Primary Joint Action")) +
            100 * jaccard(secondary, candidate.secondary.mapTo(HashSet()) { it.lowercase() }) +
            (if (type != null && candidate.properties["Type"] == type) 60.0 else 0.0) +
            (if (favorite) 150.0 else 0.0) + (recent?.let { 100.0 - 8 * it } ?: 0.0)
        val reasons = listOfNotNull(
            SwapReason.SAME_MUSCLES.takeIf { sameMuscle },
            SwapReason.SAME_MOVEMENT.takeIf { movementFit > 0.0 && !sameMuscle },
            SwapReason.FAVORITE.takeIf { favorite },
            SwapReason.RECENT.takeIf { recent != null && !favorite },
        )
        Scored(SwapSuggestion(candidate, reasons), score, position)
    }
    return scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.position })
        .take(limit)
        .map { if (gymExcluded) it.suggestion.copy(reasons = it.suggestion.reasons + SwapReason.AT_GYM) else it.suggestion }
}

/** Newest first, so the last few sessions decide what counts as recently performed. */
fun List<Workout>.recentExerciseIds(workouts: Int = 20): List<String> =
    sortedByDescending { it.startedAt }.take(workouts)
        .flatMap { w -> w.exercises.filter { e -> e.sets.any { it.completed } }.map { it.exerciseId } }
        .distinct()

/**
 * Logical sets with any completed log stay with the original exercise; untouched sets move to
 * the replacement as fresh logs with the same kinds, sides, warm-ups and targets but no actuals.
 * If nothing was completed the replacement takes the original's place.
 */
fun Workout.withSwappedExercise(
    entryId: String,
    replacementId: String,
    replacementDefaults: ExerciseDefaults? = null,
    supportsReps: Boolean = true,
): Workout {
    val index = exercises.indexOfFirst { it.id == entryId }
    if (index < 0) return this
    val original = exercises[index]
    val groups = original.logicalSets()
    val (done, pending) = groups.partition { group -> group.logs.any { it.completed } }
    // A fully logged exercise still gives the replacement one row shaped like its last working set.
    val moving = pending.ifEmpty { listOfNotNull(groups.lastOrNull { !it.root.warmup } ?: groups.lastOrNull()) }
    val movingRoots = moving.mapTo(HashSet()) { it.root.id }
    val source = original.sets.filter { it.logicalRootId in movingRoots }
    val ids = source.associate { it.id to newId() }
    val sets = source.map { set ->
        LoggedSet(
            id = ids.getValue(set.id),
            warmup = set.warmup,
            kind = set.kind,
            side = set.side,
            pairId = set.pairId?.let(ids::getValue),
            segmentOf = set.segmentOf?.let(ids::getValue),
            rir = if (set.kind == SetKind.FAILURE && !set.warmup) 0 else null,
            targetRepsMin = set.targetRepsMin.takeIf { supportsReps },
            targetRepsMax = set.targetRepsMax.takeIf { supportsReps },
            targetRir = set.targetRir,
        )
    }.ifEmpty { listOf(LoggedSet()) }
    val replacement = SessionExercise(
        exerciseId = replacementId,
        sets = sets,
        restSeconds = original.restSeconds ?: replacementDefaults?.restSeconds,
        supersetId = original.supersetId,
    ).validateSetLinks()
    val doneRoots = done.mapTo(HashSet()) { it.root.id }
    val entries = exercises.toMutableList()
    if (done.isEmpty()) entries[index] = replacement
    else {
        val kept = original.sets.filter { it.logicalRootId in doneRoots }
        // The remaining work, and with it any superset round, now belongs to the replacement.
        entries[index] = original.copy(
            sets = kept, supersetId = null,
            reviewedEffortSetIds = original.reviewedEffortSetIds.intersect(kept.mapTo(HashSet()) { it.id }),
        )
        entries.add(index + 1, replacement)
    }
    return copy(exercises = entries, selectedExerciseId = replacement.id).normalizeSupersets()
}

/** Replaces every occurrence in order, keeping prescriptions. Null when nothing can change. */
fun Routine.withSwappedExercise(originalId: String, replacementId: String): Routine? {
    if (originalId == replacementId || originalId !in exerciseIds || replacementId in exerciseIds) return null
    return copy(
        exerciseIds = exerciseIds.map { if (it == originalId) replacementId else it },
        prescriptions = prescriptions.map { if (it.exerciseId == originalId) it.copy(exerciseId = replacementId) else it },
    )
}

/** Only the started day changes; every cycle and deload target stays with its planned slot. */
fun TrainingProgram.withSwappedExercise(dayId: String, originalId: String, replacementId: String): TrainingProgram? {
    val day = days.find { it.id == dayId } ?: return null
    if (originalId == replacementId || day.exercises.none { it.exerciseId == originalId } ||
        day.exercises.any { it.exerciseId == replacementId }) return null
    return copy(days = days.map { d ->
        if (d.id != dayId) d
        else d.copy(exercises = d.exercises.map { if (it.exerciseId == originalId) it.copy(exerciseId = replacementId) else it })
    })
}

fun AppData.swapPlanFor(entryId: String, replacementId: String): SwapPlan? {
    val workout = active ?: return null
    val original = workout.exercises.find { it.id == entryId }?.exerciseId ?: return null
    routines.find { it.id == workout.routineId }?.takeIf { original in it.exerciseIds }?.let {
        return SwapPlan(SwapScope.TEMPLATE, it.name, replacementId in it.exerciseIds)
    }
    val program = programs.find { it.id == workout.programId } ?: return null
    val day = program.days.find { it.id == workout.programDayId }?.takeIf { d -> d.exercises.any { it.exerciseId == original } }
        ?: return null
    return SwapPlan(SwapScope.PROGRAM, "${day.name} · ${program.name}", day.exercises.any { it.exerciseId == replacementId })
}

fun AppData.withExerciseSwap(entryId: String, replacementId: String, scope: SwapScope, catalog: List<Exercise>): AppData {
    val workout = active ?: return this
    val original = workout.exercises.find { it.id == entryId }?.exerciseId ?: return this
    if (original == replacementId) return this
    val supportsReps = catalog.find { it.id == replacementId }?.loggingMetrics()?.any { it.isReps } != false
    val swapped = copy(active = workout.withSwappedExercise(entryId, replacementId, exerciseDefaults[replacementId], supportsReps))
    return when (scope) {
        SwapScope.TODAY -> swapped
        SwapScope.TEMPLATE -> routines.find { it.id == workout.routineId }?.withSwappedExercise(original, replacementId)
            ?.let { routine -> swapped.copy(routines = routines.map { if (it.id == routine.id) routine else it }) } ?: swapped
        SwapScope.PROGRAM -> programs.find { it.id == workout.programId }
            ?.withSwappedExercise(workout.programDayId.orEmpty(), original, replacementId)
            ?.let { program -> swapped.copy(programs = programs.map { if (it.id == program.id) program else it }) } ?: swapped
    }
}

fun AppData.exerciseSwapUndo(next: AppData): ExerciseSwapUndo? {
    val workout = active ?: return null
    if (next.active == workout) return null
    return ExerciseSwapUndo(
        workout,
        routines.firstOrNull { r -> next.routines.any { it.id == r.id && it != r } },
        programs.firstOrNull { p -> next.programs.any { it.id == p.id && it != p } },
    )
}

/** Restores only the swapped workout and plans; other workouts and plans are never touched. */
fun AppData.withoutExerciseSwap(undo: ExerciseSwapUndo): AppData = copy(
    active = if (active?.id == undo.workout.id) undo.workout else active,
    routines = undo.routine?.let { saved -> routines.map { if (it.id == saved.id) saved else it } } ?: routines,
    programs = undo.program?.let { saved -> programs.map { if (it.id == saved.id) saved else it } } ?: programs,
)
