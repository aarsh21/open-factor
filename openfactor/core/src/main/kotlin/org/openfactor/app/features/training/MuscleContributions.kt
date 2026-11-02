package org.openfactor.app

import java.time.LocalDate

val upperMuscleGroups =
    listOf(
        "Chest",
        "Upper Back",
        "Lats",
        "Biceps",
        "Triceps",
        "Front Delts",
        "Side Delts",
        "Lower Back",
        "Abs",
        "Upper Traps",
        "Rear Delts",
        "Forearms",
        "Obliques",
        "Neck",
        "Serratus",
    )
val lowerMuscleGroups =
    listOf("Quads", "Hamstrings", "Glutes", "Adductors", "Abductors", "Calves", "Tibs")

data class MuscleContribution(val exercise: Exercise, val sets: Int, val weightedSets: Double)

fun muscleContributions(
    history: List<Workout>,
    exercises: List<Exercise>,
    days: Int,
    muscle: String,
    today: LocalDate = LocalDate.now(),
): List<MuscleContribution> {
    require(days > 0)
    val first = today.minusDays(days.toLong() - 1)
    val sets =
        history
            .distinctBy { it.id }
            .filter { it.localDate() in first..today }
            .flatMap { it.exercises }
            .groupBy { it.exerciseId }
    return exercises
        .filter { muscle in it.muscles.ifEmpty { listOf(it.muscle) } || muscle in it.secondary }
        .mapNotNull { e ->
            val count = sets[e.id].orEmpty().sumOf { it.completedWorkingSets() }
            // Retained ARM64 helper 0xd881ac: primary feature group 1, secondary 0.5.
            val factor = if (muscle in e.muscles.ifEmpty { listOf(e.muscle) }) 1.0 else 0.5
            if (count > 0) MuscleContribution(e, count, count * factor) else null
        }
        .sortedByDescending { it.weightedSets }
}
