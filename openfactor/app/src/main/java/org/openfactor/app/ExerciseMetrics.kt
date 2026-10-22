package org.openfactor.app

import java.util.Locale

/** Exact metric vocabulary retained in the exercise catalog. Per-side labels do not infer side sequencing. */
enum class SetMetric(val catalogName: String) {
    WEIGHT("Weight"),
    WEIGHT_PER_SIDE("Weight per side"),
    WEIGHT_PER_SIDE_PERSISTENT("Weight per side persistent"),
    ASSISTANCE("Assistance weight"),
    REPS("Reps"),
    REPS_PER_SIDE("Reps per side"),
    DURATION("Duration"),
    DURATION_PER_SIDE("Duration per side"),
    DISTANCE_LONG("Distance long"),
    DISTANCE_SHORT("Distance short"),
    DISTANCE_PER_SIDE("Distance short per side");

    val isReps get() = this == REPS || this == REPS_PER_SIDE
    val isLoad get() = this in listOf(WEIGHT, WEIGHT_PER_SIDE, WEIGHT_PER_SIDE_PERSISTENT, ASSISTANCE)

    fun label(pounds: Boolean): String {
        val unit = if (pounds) "lb" else "kg"
        return when (this) {
            WEIGHT -> unit
            WEIGHT_PER_SIDE, WEIGHT_PER_SIDE_PERSISTENT -> "$unit / side"
            ASSISTANCE -> "Assistance ($unit)"
            REPS -> "Reps"
            REPS_PER_SIDE -> "Reps / side"
            DURATION -> "Seconds"
            DURATION_PER_SIDE -> "Seconds / side"
            DISTANCE_LONG -> "km"
            DISTANCE_SHORT -> "m"
            DISTANCE_PER_SIDE -> "m / side"
        }
    }

    fun value(set: LoggedSet): Double? = when (this) {
        WEIGHT, WEIGHT_PER_SIDE, WEIGHT_PER_SIDE_PERSISTENT -> set.weightKg
        ASSISTANCE -> set.assistanceKg
        REPS, REPS_PER_SIDE -> set.reps?.toDouble()
        DURATION, DURATION_PER_SIDE -> set.durationSeconds
        DISTANCE_LONG, DISTANCE_SHORT, DISTANCE_PER_SIDE -> set.distanceMeters
    }

    fun valid(value: Double): Boolean = value.isFinite() && when {
        isLoad -> value in 0.0..5000.0
        isReps -> value in 1.0..9999.0 && value % 1.0 == 0.0
        else -> value > 0.0
    }

    fun parse(text: String, pounds: Boolean): Double? =
        text.replace(',', '.').toDoubleOrNull()?.let { entered ->
            when {
                isLoad && pounds -> entered / 2.2046226218
                this == DISTANCE_LONG -> entered * 1000.0
                else -> entered
            }
        }?.takeIf(::valid)

    fun display(set: LoggedSet, pounds: Boolean): String = value(set)?.let {
        val shown = when {
            isLoad && pounds -> it * 2.2046226218
            this == DISTANCE_LONG -> it / 1000.0
            else -> it
        }
        if (shown % 1.0 == 0.0) shown.toLong().toString()
        else String.format(Locale.US, "%.3f", shown).trimEnd('0').trimEnd('.')
    }.orEmpty()

    fun update(set: LoggedSet, value: Double?): LoggedSet {
        require(value == null || valid(value)) { "Invalid $catalogName actual" }
        return when (this) {
            WEIGHT, WEIGHT_PER_SIDE, WEIGHT_PER_SIDE_PERSISTENT -> set.copy(weightKg = value)
            ASSISTANCE -> set.copy(assistanceKg = value)
            REPS, REPS_PER_SIDE -> set.copy(reps = value?.toInt())
            DURATION, DURATION_PER_SIDE -> set.copy(durationSeconds = value)
            DISTANCE_LONG, DISTANCE_SHORT, DISTANCE_PER_SIDE -> set.copy(distanceMeters = value)
        }
    }
}

fun Exercise.loggingMetrics(): List<SetMetric> {
    val source = properties["Metrics"] ?: "Weight, Reps"
    if (source == "None") return emptyList()
    return source.split(',').map { token ->
        requireNotNull(SetMetric.entries.find { it.catalogName == token.trim() }) {
            "Unsupported exercise metric: $token"
        }
    }.distinct().sortedBy { it.ordinal }
}

fun LoggedSet.canComplete(metrics: List<SetMetric>): Boolean =
    metrics.all { metric -> metric.value(this)?.let(metric::valid) == true } &&
        (rir == null || rir in 0..10)

fun LoggedSet.describe(metrics: List<SetMetric>, pounds: Boolean): String =
    (metrics.mapNotNull { metric ->
        metric.display(this, pounds).takeIf { it.isNotEmpty() }?.let { "$it ${metric.label(pounds)}" }
    } + listOfNotNull(rir?.let { "$it RIR" })).joinToString(" · ").ifEmpty { "Completed" }

fun LoggedSet.targetDescription(): String = buildList {
    when {
        targetRepsMin != null && targetRepsMax != null -> add(
            if (targetRepsMin == targetRepsMax) "$targetRepsMin reps" else "$targetRepsMin–$targetRepsMax reps"
        )
        targetRepsMin != null -> add("$targetRepsMin+ reps")
        targetRepsMax != null -> add("≤$targetRepsMax reps")
    }
    targetRir?.let { add("$it RIR") }
}.joinToString(" · ").ifEmpty { "No Target" }
