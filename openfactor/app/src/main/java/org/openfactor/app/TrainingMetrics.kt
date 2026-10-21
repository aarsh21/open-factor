package org.openfactor.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun averageWeeklySets(sets: Int, days: Int): Double {
    require(days > 0) { "The averaging period must be positive" }
    return sets * 7.0 / days
}

/** Dashboard/Levels count completed working sets; warm-ups remain in session history. */
fun Workout.completedWorkingSets() =
    exercises.sumOf { e -> e.sets.count { it.completed && !it.warmup } }

/** Prefer the live copy, so finishing/importing a session cannot count its identity twice. */
fun AppData.trainingWorkouts(): List<Workout> =
    (listOfNotNull(active) + history).distinctBy { it.id }

data class TrainingTotals(val sets: Int = 0, val exercises: Int = 0, val muscles: Int = 0)

private fun List<SessionExercise>.totals(catalog: List<Exercise>): TrainingTotals {
    val working = filter { e -> e.sets.any { it.completed && !it.warmup } }
    val ids = working.map { it.exerciseId }.toSet()
    val muscles =
        catalog
            .filter { it.id in ids }
            .flatMap { it.muscles + it.secondary }
            .filter { it.isNotBlank() }
            .toSet()
    return TrainingTotals(
        working.sumOf { e -> e.sets.count { it.completed && !it.warmup } },
        ids.size,
        muscles.size,
    )
}

data class DashboardMetrics(
    val allWorkouts: Boolean,
    val history: List<Workout>,
    val weekly: List<Workout>,
    val totals: TrainingTotals,
    val completedWorkouts: Int,
    val includesActive: Boolean,
    val plan: TrainingTotals?,
    val planCycle: Int?,
)

fun AppData.dashboardMetrics(
    catalog: List<Exercise>,
    today: LocalDate = LocalDate.now(),
): DashboardMetrics {
    val program = programs.find { it.id == activeProgramId }
    // No active program is a useful all-workouts dashboard, never an empty filter.
    val all = preferences.dashboardAllWorkouts || program == null
    fun inScope(w: Workout) = all || w.programId == program?.id
    val saved =
        history
            .distinctBy { it.id }
            .filter { inScope(it) && !it.localDate().isAfter(today) }
            .sortedByDescending { it.startedAt }
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekly = trainingWorkouts().filter { inScope(it) && it.localDate() in monday..today }
    val current =
        active?.takeIf { w -> weekly.any { it.id == w.id } && w.completedWorkingSets() > 0 }
    val cycle =
        if (!all && program != null) {
            trainingWorkouts()
                .filter {
                    it.programId == program.id &&
                        !it.localDate().isAfter(today) &&
                        it.programCycle in 0 until program.cycleCount
                }
                .maxByOrNull { it.startedAt }
                ?.programCycle ?: 0
        } else null
    // A literal pass through the selected/latest cycle, not an invented calendar schedule.
    val plan =
        if (program != null && cycle != null) {
            program.days
                .mapNotNull { program.toWorkout(it.id, cycle, 0) }
                .flatMap { it.exercises }
                .map { e -> e.copy(sets = e.sets.map { it.copy(completed = true) }) }
                .totals(catalog)
        } else null
    return DashboardMetrics(
        all,
        saved,
        weekly,
        weekly.flatMap { it.exercises }.totals(catalog),
        saved.count { it.localDate() in monday..today },
        current != null,
        plan,
        cycle,
    )
}
