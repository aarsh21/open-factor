package org.openfactor.app

/** The active workout exactly before and after one set removal. */
data class SetRemovalUndo(val before: Workout, val after: Workout)

/** Same rule as the menu's Remove Set: a side removes its pair, a Myo component only itself (with its side). */
fun AppData.withRemovedSet(exerciseId: String, setId: String): AppData {
    val workout = active ?: return this
    return copy(active = workout.copy(exercises = workout.exercises.map {
        if (it.id == exerciseId) it.withoutSet(setId).validateSetLinks() else it
    }))
}

fun AppData.setRemovalUndo(next: AppData): SetRemovalUndo? {
    val before = active ?: return null
    val after = next.active?.takeIf { it.id == before.id && it != before } ?: return null
    return SetRemovalUndo(before, after)
}

/**
 * Undo can arrive late (a long snackbar); any edit after the removal withdraws it, so it can never
 * erase new work. Moving between exercises is not an edit and keeps the offer.
 */
fun AppData.canUndoSetRemoval(undo: SetRemovalUndo): Boolean = active?.sameWorkAs(undo.after) == true

/** Restores the set with its ids, values, completion, kind and side/Myo links, at its old position. */
fun AppData.withoutSetRemoval(undo: SetRemovalUndo): AppData =
    if (!canUndoSetRemoval(undo)) this
    else copy(active = active?.let { now ->
        undo.before.copy(selectedExerciseId = now.selectedExerciseId, effortPrompts = now.effortPrompts,
            restUntil = now.restUntil, pausedAt = now.pausedAt, pausedDurationMs = now.pausedDurationMs)
    })
