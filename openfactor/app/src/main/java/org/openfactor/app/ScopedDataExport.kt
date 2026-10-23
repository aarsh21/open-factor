package org.openfactor.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Names and order recovered from ExportTimeFrame in the retained source specimen. */
enum class ExportTimeFrame(val label: String) {
    SEVEN_DAYS("Last 7 Days"), FOURTEEN_DAYS("Last 14 Days"), THIRTY_DAYS("Last 30 Days"),
    THIS_MONTH("This Month"), LAST_MONTH("Last Month"), ALL_TIME("All Time");

    /** Local policy: inclusive calendar dates; this month ends today, not in the future. */
    fun dates(today: LocalDate): ClosedRange<LocalDate>? = when (this) {
        SEVEN_DAYS -> today.minusDays(6)..today
        FOURTEEN_DAYS -> today.minusDays(13)..today
        THIRTY_DAYS -> today.minusDays(29)..today
        THIS_MONTH -> today.withDayOfMonth(1)..today
        LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1)..today.withDayOfMonth(1).minusDays(1)
        ALL_TIME -> null
    }
}

enum class ExportCategory(val key: String, val label: String, val group: String, val unavailable: String? = null) {
    SCALE_WEIGHT("scaleWeight", "Scale Weight", "Metrics & Analytics"),
    BODY_METRICS("bodyMetrics", "Body Metrics", "Metrics & Analytics"),
    WEIGHT_TREND("weightTrend", "Weight Trend", "Metrics & Analytics", "No recovered trend calculation"),
    STEPS("steps", "Steps", "Metrics & Analytics", "Step records are not available"),
    MUSCLE_GROUPS("muscleGroups", "Muscle Groups", "Metrics & Analytics"),
    EXERCISES("exercises", "Exercises", "Metrics & Analytics"),
    TRAINING_PROGRAMS("trainingPrograms", "Training Programs", "Library"),
    WORKOUTS("workouts", "Workouts", "Library"),
    USER_PROFILE("userProfile", "User Profile", "Other"),
    PERIOD("period", "Period", "Other", "Period tracking is not available"),
    EXERCISE_SETTINGS("exerciseSettings", "Exercise Settings", "Other"),
    GYM_PROFILES("gymProfiles", "Gym Profiles", "Other"),
    WORKOUT_SETTINGS("workoutSettings", "Workout Settings", "Other"),
    CUSTOM_EXERCISES("customExercises", "Custom Exercises", "Other"),
    WORKOUT_LOG_NOTES("workoutLogNotes", "Workout Log Notes", "Other"),
    WORKOUT_LOG("workoutLog", "Workout Log", "Training log");
}

data class ScopedExportRequest(
    val quick: Boolean = true,
    val timeFrame: ExportTimeFrame = ExportTimeFrame.SEVEN_DAYS,
    val categories: Set<ExportCategory> = emptySet(),
) {
    fun validate() {
        require(quick || categories.isNotEmpty()) { "Select at least one category" }
        require(categories.all { it.unavailable == null }) { "A selected category is not available" }
    }
}

/** An analysis document, deliberately not a partial backup that Restore could erase data with. */
object ScopedDataExport {
    fun document(
        data: AppData,
        catalog: List<Exercise>,
        request: ScopedExportRequest,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): JSONObject {
        request.validate()
        val dates = request.timeFrame.dates(today)
        fun includes(date: LocalDate) = dates == null || date in dates
        val workouts = data.history.filter {
            includes(Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate())
        }.sortedWith(compareBy<Workout> { it.startedAt }.thenBy { it.id })
        val chosen = if (request.quick) setOf(
            ExportCategory.MUSCLE_GROUPS, ExportCategory.TRAINING_PROGRAMS, ExportCategory.WORKOUT_LOG,
        ) else request.categories
        val exercises = (catalog + data.customExercises).associateBy { it.id }
        val payload = JSONObject()
        // Only selected categories reach the codec; unselected private state is not serialized.
        val encoded = JSONObject(DataCodec.encode(AppData(
            history = if (ExportCategory.WORKOUT_LOG in chosen || ExportCategory.EXERCISES in chosen) workouts.map { workout ->
                if (request.quick) workout else workout.copy(notes = "", exercises = workout.exercises.map { it.copy(notes = "") })
            } else emptyList(),
            scaleWeights = if (ExportCategory.SCALE_WEIGHT in chosen) data.scaleWeights.filter { includes(it.date) } else emptyList(),
            bodyMeasurements = if (ExportCategory.BODY_METRICS in chosen) data.bodyMeasurements.filter { includes(it.date) } else emptyList(),
            programs = if (ExportCategory.TRAINING_PROGRAMS in chosen)
                data.programs.filter { !request.quick || it.id == data.activeProgramId } else emptyList(),
            routines = if (ExportCategory.WORKOUTS in chosen) data.routines else emptyList(),
            customExercises = if (ExportCategory.CUSTOM_EXERCISES in chosen) data.customExercises else emptyList(),
            gyms = if (ExportCategory.GYM_PROFILES in chosen) data.gyms else emptyList(),
            exerciseDefaults = if (ExportCategory.EXERCISE_SETTINGS in chosen) data.exerciseDefaults else emptyMap(),
        )))
        chosen.forEach { category ->
            when (category) {
                ExportCategory.SCALE_WEIGHT -> payload.put(category.key, encoded.getJSONArray("scaleWeights"))
                ExportCategory.BODY_METRICS -> payload.put(category.key, encoded.getJSONArray("bodyMeasurements"))
                ExportCategory.TRAINING_PROGRAMS -> payload.put(category.key, encoded.getJSONArray("programs"))
                ExportCategory.WORKOUTS -> payload.put(category.key, encoded.getJSONArray("routines"))
                ExportCategory.WORKOUT_LOG -> payload.put(category.key, encoded.getJSONArray("history"))
                ExportCategory.GYM_PROFILES -> payload.put(category.key, encoded.getJSONArray("gyms"))
                ExportCategory.CUSTOM_EXERCISES -> payload.put(category.key, encoded.getJSONArray("customExercises"))
                ExportCategory.EXERCISE_SETTINGS -> payload.put(category.key, encoded.getJSONObject("exerciseDefaults"))
                ExportCategory.USER_PROFILE -> payload.put(category.key, JSONObject().put("displayName", data.preferences.displayName))
                ExportCategory.WORKOUT_SETTINGS -> payload.put(category.key, JSONObject()
                    .put("pounds", data.preferences.pounds).put("restSeconds", data.preferences.restSeconds)
                    .put("autoRest", data.preferences.autoRest).put("showRir", data.preferences.showRir)
                    .put("exerciseAutoNext", data.preferences.exerciseAutoNext)
                    .put("keepAlive", data.preferences.keepAlive).put("showWorkoutTimer", data.preferences.showWorkoutTimer)
                    .put("showBodyweightContribution", data.preferences.showBodyweightContribution)
                    .put("distanceImperial", data.preferences.distanceImperial)
                    .put("restAfterLastWarmupSet", data.preferences.restAfterLastWarmupSet)
                    .put("restBetweenExercises", data.preferences.restBetweenExercises)
                    .put("supersetAutoNext", data.preferences.supersetAutoNext)
                    .put("restBetweenSidesPercent", data.preferences.restBetweenSidesPercent))
                ExportCategory.WORKOUT_LOG_NOTES -> payload.put(category.key, JSONArray(workouts.map { workout ->
                    JSONObject().put("workoutId", workout.id).put("notes", workout.notes)
                        .put("exercises", JSONArray(workout.exercises.map {
                            JSONObject().put("entryId", it.id).put("exerciseId", it.exerciseId).put("notes", it.notes)
                        }))
                }))
                ExportCategory.EXERCISES, ExportCategory.MUSCLE_GROUPS -> payload.put(category.key,
                    JSONArray(workouts.flatMapIndexed { workoutIndex, workout -> workout.exercises.mapIndexed { entryIndex, entry ->
                        val exercise = exercises[entry.exerciseId]
                        val row = JSONObject().put("workoutId", workout.id).put("entryId", entry.id)
                            .put("date", Instant.ofEpochMilli(workout.startedAt).atZone(zone).toLocalDate().toString())
                            .put("exerciseId", entry.exerciseId).put("exerciseName", exercise?.name ?: entry.exerciseId)
                        if (category == ExportCategory.MUSCLE_GROUPS) row
                            .put("primaryMuscles", JSONArray(exercise?.muscles ?: emptyList<String>()))
                            .put("secondaryMuscles", JSONArray(exercise?.secondary ?: emptyList<String>()))
                            .put("completedWorkingSetIds", JSONArray(entry.logicalSets().filter { !it.root.warmup && it.logs.any { log -> log.completed } }.map { it.root.id }))
                        else row.put("sets", encoded.getJSONArray("history").getJSONObject(workoutIndex)
                            .getJSONArray("exercises").getJSONObject(entryIndex).getJSONArray("sets"))
                        row
                    } }),
                )
                else -> error("Category is not available")
            }
        }
        return JSONObject()
            .put("format", "open-factor-scoped-export").put("formatVersion", 1)
            .put("mode", if (request.quick) "quick" else "granular")
            .put("timeFrame", request.timeFrame.name).put("asOfDate", today.toString())
            .put("timeZone", zone.id).put("fromDate", dates?.start?.toString() ?: JSONObject.NULL)
            .put("throughDate", dates?.endInclusive?.toString() ?: JSONObject.NULL)
            .put("policy", "Calendar dates are inclusive. Workouts use their start date in timeZone. Time limits apply to dated records only. Libraries and settings are current snapshots. Visibility does not filter exports. No photos, active sessions, drafts or reference media. Not a restorable backup.")
            .put("data", payload)
    }
}
