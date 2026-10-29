package org.openfactor.app

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class TrainingPeriod(val label: String) { WEEK("Week"), MONTH("Month") }

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
    val exercises: Int,
) {
    val trainedMuscles: Int get() = muscles.count { it.weightedSets > 0 }
}

/** Actual completed logical work only. A side pair or Myo segment never adds a second set. */
fun AppData.trainingWeek(
    catalog: List<Exercise>,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
): TrainingWeek {
    return trainingPeriod(catalog, TrainingPeriod.WEEK, offset, today)
}

/** Calendar periods, bounded to the present. Future-dated entries never inflate recorded totals. */
fun AppData.trainingPeriod(
    catalog: List<Exercise>,
    period: TrainingPeriod,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
): TrainingWeek {
    val boundedOffset = offset.coerceIn(-520, 0).toLong()
    val first = when (period) {
        TrainingPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(boundedOffset)
        TrainingPeriod.MONTH -> today.withDayOfMonth(1).plusMonths(boundedOffset)
    }
    val last = when (period) {
        TrainingPeriod.WEEK -> first.plusDays(6)
        TrainingPeriod.MONTH -> first.with(TemporalAdjusters.lastDayOfMonth())
    }
    val workouts = trainingWorkouts().filter { it.localDate() in first..minOf(last, today) }
    val muscles = muscleWorkloads(workouts, catalog)
    val byDate = workouts.groupBy { it.localDate() }.mapValues { (date, saved) ->
        TrainingDay(date, saved.sumOf { it.completedWorkingSets() }, saved.sumOf { it.volumeKg() })
    }
    val days = (0L..java.time.temporal.ChronoUnit.DAYS.between(first, last)).map { day ->
        val date = first.plusDays(day)
        byDate[date] ?: TrainingDay(date, 0, 0.0)
    }
    return TrainingWeek(first, last, days, workouts.count { it.completedWorkingSets() > 0 },
        days.sumOf { it.sets }, days.sumOf { it.volumeKg }, muscles,
        active?.let { a -> workouts.any { it.id == a.id } && a.completedWorkingSets() > 0 } == true,
        workouts.flatMap { it.exercises }.filter { it.completedWorkingSets() > 0 }.map { it.exerciseId }.distinct().size)
}

/** One contribution per muscle per logical set. A primary target never also counts as secondary. */
fun muscleWorkloads(workouts: List<Workout>, catalog: List<Exercise>): List<MuscleWorkload> =
    muscleWorkloads(workouts, catalog.associateBy { it.id })

internal fun muscleWorkloads(workouts: List<Workout>, byId: Map<String, Exercise>): List<MuscleWorkload> {
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
    var first = 0
    var total = 0.0
    return readings.mapIndexed { index, reading ->
        total += reading.weightKg
        val from = reading.date.minusDays(6)
        while (readings[first].date < from) total -= readings[first++].weightKg
        WeightTrendReading(reading.id, reading.date, reading.weightKg, total / (index - first + 1))
    }
}
