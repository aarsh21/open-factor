package org.openfactor.app

data class WorkoutDraft(
    val name: String = "Workout",
    val exercises: List<SessionExercise> = emptyList(),
    val gymId: String? = null,
    val notes: String = "",
)

fun AppData.startWorkoutDraft(now: Long): AppData {
    val draft = workoutDraft ?: return this
    if (active != null) return this
    return copy(
        active = Workout(
            name = draft.name,
            startedAt = now,
            exercises = draft.exercises,
            selectedExerciseId = draft.exercises.firstOrNull()?.id,
            gymId = draft.gymId,
            notes = draft.notes,
        ),
        workoutDraft = null,
    )
}
