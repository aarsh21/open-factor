package org.openfactor.app

import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

/** A durable, isolated edit. The saved record and active workout remain untouched until Save. */
data class PastWorkoutEdit(val original: Workout, val edited: Workout = original)

fun AppData.beginPastWorkoutEdit(id: String): AppData {
    if (pastWorkoutEdit?.original?.id == id) return this
    val workout = history.find { it.id == id } ?: return this
    // A different unfinished edit must be resolved explicitly by the caller.
    if (pastWorkoutEdit != null) return this
    return copy(pastWorkoutEdit = PastWorkoutEdit(workout))
}

fun Workout.onDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Workout {
    val start = Instant.ofEpochMilli(startedAt).atZone(zone)
    val next = start.with(date).toInstant().toEpochMilli()
    return copy(startedAt = next, finishedAt = finishedAt?.plus(next - startedAt))
}

internal fun Workout.pastEditError(catalog: List<Exercise>, today: LocalDate = LocalDate.now()): String? {
    if (name.isBlank()) return "Enter a workout name."
    if (finishedAt == null || finishedAt < startedAt) return "The workout end must follow its start."
    if (localDate() > today) return "Choose today or an earlier date."
    if (completedSets() == 0) return "Keep at least one completed set, or delete the workout instead."
    if (exercises.map { it.id }.distinct().size != exercises.size) return "Exercise entries must have unique identities."
    val known = catalog.associateBy { it.id }
    for (entry in exercises) {
        try { entry.validateSetLinks() } catch (_: IllegalArgumentException) { return "A linked set is incomplete. Restore or remove its group." }
        val metrics = known[entry.exerciseId]?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS)
        if (entry.sets.any { it.completed && !it.canComplete(metrics) }) return "Check the highlighted values in completed sets."
    }
    return null
}

fun AppData.commitPastWorkoutEdit(catalog: List<Exercise>): AppData {
    val edit = requireNotNull(pastWorkoutEdit) { "This edit is no longer available." }
    val saved = history.find { it.id == edit.original.id }
    require(saved == edit.original) { "This workout changed while you were editing. Discard this edit and reopen the saved workout." }
    val draft = edit.edited
    require(draft.id == saved.id && draft.programId == saved.programId && draft.programDayId == saved.programDayId &&
        draft.programCycle == saved.programCycle && draft.gymId == saved.gymId &&
        draft.pausedAt == saved.pausedAt && draft.pausedDurationMs == saved.pausedDurationMs) { "Workout identity cannot change." }
    require(draft.pastEditError(catalog) == null) { draft.pastEditError(catalog).orEmpty() }
    val corrected = draft.copy(name = draft.name.trim()).normalizeSupersets()
    return copy(history = history.map { if (it.id == saved.id) corrected else it }, pastWorkoutEdit = null)
}
