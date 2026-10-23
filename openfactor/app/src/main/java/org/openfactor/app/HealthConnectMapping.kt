package org.openfactor.app

import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** Source calendar labels survive UTC-day normalization; these are not raw UTC timestamps. */
fun healthConnectScaleWeights(
    weights: List<WeightRecord>,
    bodyFat: List<BodyFatRecord>,
    zone: ZoneId,
    ownPackage: String,
): List<ScaleWeightRecord> {
    val fats = bodyFat.filter { it.metadata.dataOrigin.packageName != ownPackage }
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, records) -> records.maxBy { it.time } }
    return weights.filter { it.metadata.dataOrigin.packageName != ownPackage }
        .groupBy { it.time.atZone(zone).toLocalDate() }
        .map { (date, records) ->
            // maxBy keeps the first record on equal timestamps, as the source's strict > does.
            val weight = records.maxBy { it.time }
            val fat = fats[date]?.percentage?.value?.let {
                BigDecimal.valueOf(it).setScale(1, RoundingMode.HALF_UP).toDouble()
            }?.takeIf { it in 0.0..100.0 }
            ScaleWeightRecord(
                date = date,
                weightKg = weight.weight.inKilograms,
                bodyFatPercent = fat,
                healthConnectOrigin = weight.metadata.dataOrigin.packageName,
            ).also { it.validate() }
        }
}

/** Manual dates win; a repeated import updates only the imported reading and retains its ID. */
fun AppData.withHealthConnectWeights(incoming: List<ScaleWeightRecord>): AppData {
    require(incoming.all { it.healthConnectOrigin != null })
    incoming.forEach { it.validate() }
    var records = scaleWeights
    for (record in incoming) {
        val day = records.filter { it.date == record.date }
        if (day.any { it.healthConnectOrigin == null }) continue
        val existing = day.firstOrNull()
        val replacement = record.copy(id = existing?.id ?: record.id)
        records = records.filterNot { it.date == record.date } + replacement
    }
    return copy(scaleWeights = records)
}

/** Local IDs use the platform's client-ID upsert contract, without deleting a health-store range. */
fun healthConnectScaleExport(
    records: List<ScaleWeightRecord>,
    zone: ZoneId,
    version: Long,
    includeWeight: Boolean,
    includeBodyFat: Boolean,
): List<Record> = records.filter { it.healthConnectOrigin == null }.flatMap { record ->
    record.validate()
    val at = record.date.atStartOfDay(zone)
    buildList {
        if (includeWeight) add(WeightRecord(
            time = at.toInstant(), zoneOffset = at.offset,
            weight = Mass.kilograms(record.weightKg),
            metadata = Metadata.manualEntry("open-factor:weight:${record.id}", version),
        ))
        if (includeBodyFat && record.bodyFatPercent != null) add(BodyFatRecord(
            time = at.toInstant(), zoneOffset = at.offset,
            percentage = Percentage(record.bodyFatPercent),
            metadata = Metadata.manualEntry("open-factor:fat:${record.id}", version),
        ))
    }
}

/**
 * Ten-second sequential WORKOUT segments and max(elapsed, count * 10s) follow the source.
 * Local eligibility uses completed actuals, not source persisted skipped flags. One segment represents
 * one logical set; repetitions sum its completed side/component logs (a local export policy).
 */
fun healthConnectWorkoutExport(workout: Workout, zone: ZoneId, version: Long): ExerciseSessionRecord? {
    val finished = workout.finishedAt ?: return null
    val sets = workout.exercises.flatMap { it.logicalSets() }.filterNot { it.root.warmup }.map { group -> group.logs.filter {
        it.completed && !it.warmup && (
            (it.reps ?: 0) > 0 || (it.weightKg ?: 0.0) > 0 ||
                (it.durationSeconds ?: 0.0) > 0 || (it.distanceMeters ?: 0.0) > 0
            )
    } }.filter { it.isNotEmpty() }
    val elapsed = (finished - workout.startedAt - workout.pausedDurationMs).coerceAtLeast(0)
    val duration = maxOf(elapsed, sets.size * 10_000L)
    // Android rejects equal endpoints. Do not invent a time for a zero-set, zero-duration session.
    if (duration == 0L) return null
    val start = Instant.ofEpochMilli(workout.startedAt)
    val end = start.plusMillis(duration)
    val segments = sets.mapIndexed { index, set ->
        ExerciseSegment(
            startTime = start.plusSeconds(index * 10L),
            endTime = start.plusSeconds((index + 1) * 10L),
            segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            repetitions = set.sumOf { it.reps ?: 0 },
        )
    }
    return ExerciseSessionRecord(
        startTime = start, startZoneOffset = zone.rules.getOffset(start),
        endTime = end, endZoneOffset = zone.rules.getOffset(end),
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
        title = workout.name, notes = workout.notes.takeIf { it.isNotBlank() },
        segments = segments,
        metadata = Metadata.manualEntry("open-factor:workout:${workout.id}", version),
    )
}
