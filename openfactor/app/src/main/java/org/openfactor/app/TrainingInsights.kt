package org.openfactor.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class MuscleWorkload(val muscle: String, val directSets: Int = 0, val secondarySets: Int = 0) {
    val weightedSets: Double get() = directSets + secondarySets * .5
}

data class TrainingDay(val date: LocalDate, val sets: Int, val volumeKg: Double)

data class TrainingWeek(
    val first: LocalDate,
    val last: LocalDate,
    val days: List<TrainingDay>,
    val sessions: Int,
    val workingSets: Int,
    val volumeKg: Double,
    val muscles: List<MuscleWorkload>,
    val includesActive: Boolean,
) {
    val trainedMuscles: Int get() = muscles.count { it.weightedSets > 0 }
}

/** Actual completed logical work only. A side pair or Myo segment never adds a second set. */
fun AppData.trainingWeek(
    catalog: List<Exercise>,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
): TrainingWeek {
    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
    val last = first.plusDays(6)
    val workouts = trainingWorkouts().filter { it.localDate() in first..last && !it.localDate().isAfter(today) }
    val muscles = muscleWorkloads(workouts, catalog)
    val days = (0..6).map { day ->
        val date = first.plusDays(day.toLong())
        val saved = workouts.filter { it.localDate() == date }
        TrainingDay(date, saved.sumOf { it.completedWorkingSets() }, saved.sumOf { it.volumeKg() })
    }
    return TrainingWeek(first, last, days, workouts.count { it.completedWorkingSets() > 0 },
        days.sumOf { it.sets }, days.sumOf { it.volumeKg }, muscles,
        active?.let { a -> workouts.any { it.id == a.id } && a.completedWorkingSets() > 0 } == true)
}

/** One contribution per muscle per logical set. A primary target never also counts as secondary. */
fun muscleWorkloads(workouts: List<Workout>, catalog: List<Exercise>): List<MuscleWorkload> {
    val byId = catalog.associateBy { it.id }
    val direct = mutableMapOf<String, Int>()
    val secondary = mutableMapOf<String, Int>()
    workouts.distinctBy { it.id }.flatMap { it.exercises }.forEach { entry ->
        val count = entry.completedWorkingSets()
        val exercise = byId[entry.exerciseId] ?: return@forEach
        val primary = exercise.muscles.ifEmpty { listOf(exercise.muscle) }.filter(String::isNotBlank).toSet()
        primary.forEach { direct[it] = direct.getOrDefault(it, 0) + count }
        exercise.secondary.filter { it.isNotBlank() && it !in primary }.distinct().forEach {
            secondary[it] = secondary.getOrDefault(it, 0) + count
        }
    }
    val known = upperMuscleGroups + lowerMuscleGroups
    val muscleNames = (known + direct.keys + secondary.keys).distinct()
    return muscleNames.map { MuscleWorkload(it, direct.getOrDefault(it, 0), secondary.getOrDefault(it, 0)) }
        .sortedWith(compareByDescending<MuscleWorkload> { it.weightedSets }.thenBy { it.muscle })
}

data class WeightTrendReading(val recordId: String, val date: LocalDate, val weightKg: Double, val averageKg: Double)

/** Seven calendar days of available readings; no zero filling, future data or interpolated weigh-ins. */
fun List<ScaleWeightRecord>.weightTrend(today: LocalDate = LocalDate.now()): List<WeightTrendReading> {
    val readings = filter { !it.date.isAfter(today) && it.weightKg.isFinite() && it.weightKg > 0 }
        .groupBy { it.date }
        .map { (_, entries) -> entries.firstOrNull { it.healthConnectOrigin == null } ?: entries.first() }
        .sortedBy { it.date }
    return readings.map { reading ->
        val from = reading.date.minusDays(6)
        WeightTrendReading(reading.id, reading.date, reading.weightKg,
            readings.filter { it.date in from..reading.date }.map { it.weightKg }.average())
    }
}
