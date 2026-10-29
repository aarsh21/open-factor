package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

/** Prescriptions are targets only; actual performance is always entered in the workout. */
data class TemplateExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val repsMin: Int? = 8,
    val repsMax: Int? = 12,
    val restSeconds: Int? = 120,
) {
    fun validationError(): String? = when {
        exerciseId.isBlank() -> "Choose an exercise."
        sets !in 1..30 -> "Use 1–30 sets per exercise."
        repsMin != null && repsMin !in 1..9999 -> "Enter a valid minimum rep target."
        repsMax != null && repsMax !in 1..9999 -> "Enter a valid maximum rep target."
        repsMin != null && repsMax != null && repsMin > repsMax -> "Minimum reps cannot exceed maximum reps."
        restSeconds != null && restSeconds !in 0..1800 -> "Rest must be 0–1800 seconds."
        else -> null
    }
}

/** Seven slots, in order. Null is an intentional rest day, never a missing workout. */
data class TemplateSplit(val id: String = newId(), val name: String, val days: List<String?>)
data class StarterTemplateDay(val name: String, val prescriptions: List<TemplateExercise>)
data class StarterTemplateSplit(val id: String, val name: String, val days: List<StarterTemplateDay?>)
data class MaterializedTemplateSplit(val routines: List<Routine>, val split: TemplateSplit)

fun Routine.effectivePrescriptions(): List<TemplateExercise> = if (prescriptions.isNotEmpty()) prescriptions
else exerciseIds.map { TemplateExercise(it, sets = 1, repsMin = null, repsMax = null, restSeconds = null) }

fun Routine.validationError(catalog: List<Exercise>? = null): String? = when {
    id.isBlank() -> "This template needs an identity."
    name.trim().isEmpty() -> "Name your template."
    name.trim().length > 80 -> "Keep the template name under 81 characters."
    exerciseIds.isEmpty() -> "Add an exercise to your template."
    exerciseIds.size > 100 -> "Use at most 100 exercises in a template."
    exerciseIds.any { it.isBlank() } || (prescriptions.isNotEmpty() && exerciseIds.distinct().size != exerciseIds.size) -> "Choose each exercise once."
    prescriptions.isNotEmpty() && prescriptions.map { it.exerciseId } != exerciseIds -> "The template exercises changed. Review them before saving."
    prescriptions.any { it.validationError() != null } -> prescriptions.firstNotNullOf { it.validationError() }
    catalog != null && exerciseIds.any { id -> catalog.none { it.id == id } } -> "An exercise is unavailable. Replace it before saving."
    else -> null
}

fun Routine.toWorkout(
    now: Long,
    defaults: Map<String, ExerciseDefaults> = emptyMap(),
    catalog: List<Exercise>? = null,
): Workout {
    require(validationError(catalog) == null) { validationError(catalog).orEmpty() }
    val entries = effectivePrescriptions().map { target ->
        val supportsReps = catalog?.find { it.id == target.exerciseId }?.loggingMetrics()?.any { it.isReps } != false
        newSessionExercise(target.exerciseId, defaults[target.exerciseId],
            sets = List(target.sets) { LoggedSet(
                targetRepsMin = target.repsMin.takeIf { supportsReps },
                targetRepsMax = target.repsMax.takeIf { supportsReps },
            ) }, restSeconds = target.restSeconds)
    }
    return Workout(name = name.trim(), startedAt = now, exercises = entries, selectedExerciseId = entries.first().id)
}

fun StarterTemplateSplit.materialize(catalog: List<Exercise>): MaterializedTemplateSplit {
    require(days.size == 7 && days.any { it != null }) { "This split needs seven days and at least one workout." }
    val routines = days.filterNotNull().map { day ->
        Routine(name = day.name, exerciseIds = day.prescriptions.map { it.exerciseId }, prescriptions = day.prescriptions.toList())
            .also { require(it.validationError(catalog) == null) { it.validationError(catalog).orEmpty() } }
    }
    var index = 0
    val split = TemplateSplit(name = name, days = days.map { day -> if (day == null) null else routines[index++].id })
    return MaterializedTemplateSplit(routines, split)
}

fun AppData.withSavedRoutine(routine: Routine, catalog: List<Exercise>): AppData {
    require(routine.validationError(catalog) == null) { routine.validationError(catalog).orEmpty() }
    val saved = routine.copy(name = routine.name.trim())
    return copy(routines = if (routines.any { it.id == saved.id }) routines.map { if (it.id == saved.id) saved else it } else routines + saved)
}

fun AppData.withStarterSplit(preset: StarterTemplateSplit, catalog: List<Exercise>): AppData {
    val materialized = preset.materialize(catalog)
    return copy(routines = routines + materialized.routines, templateSplits = templateSplits + materialized.split)
}

fun AppData.withStartedRoutine(id: String, catalog: List<Exercise>, now: Long): AppData {
    require(active == null) { "Finish or discard the active workout first." }
    val routine = routines.find { it.id == id } ?: throw IllegalArgumentException("This template is no longer available.")
    return copy(active = routine.toWorkout(now, exerciseDefaults, catalog))
}

object TemplateCodec {
    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val number = get(key)
        require(number is Number && number.toDouble().isFinite() && number.toDouble() % 1.0 == 0.0 && number.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) { "Invalid template $key" }
        return number.toInt()
    }
    fun encodeRoutine(value: Routine): JSONObject = JSONObject().put("id", value.id).put("name", value.name)
        .put("exerciseIds", JSONArray(value.exerciseIds)).put("prescriptions", JSONArray(value.prescriptions.map {
            JSONObject().put("exerciseId", it.exerciseId).put("sets", it.sets)
                .put("repsMin", it.repsMin ?: JSONObject.NULL).put("repsMax", it.repsMax ?: JSONObject.NULL)
                .put("restSeconds", it.restSeconds ?: JSONObject.NULL)
        }))
    fun decodeRoutine(value: JSONObject): Routine {
        val ids = value.getJSONArray("exerciseIds").let { a -> (0 until a.length()).map { a.getString(it) } }
        val prescriptions = value.optJSONArray("prescriptions")?.let { a -> (0 until a.length()).map { index ->
            val p = a.getJSONObject(index)
            TemplateExercise(p.getString("exerciseId"), p.optionalInt("sets") ?: 3,
                p.optionalInt("repsMin"), p.optionalInt("repsMax"), p.optionalInt("restSeconds"))
        } }.orEmpty()
        val routine = Routine(value.getString("id"), value.getString("name"), ids, prescriptions)
        // Legacy routines may have a blank name/empty exercise list. Preserve them on import.
        require(prescriptions.isEmpty() || ids.distinct().size == ids.size) { "Duplicate template exercise" }
        require(prescriptions.isEmpty() || prescriptions.map { it.exerciseId } == ids) { "Mismatched template prescriptions" }
        require(prescriptions.all { it.validationError() == null }) { "Invalid template prescription" }
        return routine
    }
    fun encodeSplit(value: TemplateSplit): JSONObject = JSONObject().put("id", value.id).put("name", value.name)
        .put("days", JSONArray(value.days.map { it ?: JSONObject.NULL }))
    fun decodeSplits(array: JSONArray?, routines: List<Routine>): List<TemplateSplit> {
        if (array == null) return emptyList()
        val ids = routines.map { it.id }.toSet()
        return (0 until array.length()).map { index ->
            val p = array.getJSONObject(index)
            val days = p.getJSONArray("days").let { a -> (0 until a.length()).map { if (a.isNull(it)) null else a.getString(it) } }
            require(days.size == 7 && days.any { it != null }) { "Invalid template split schedule" }
            require(days.filterNotNull().all { it in ids }) { "Template split contains a missing workout" }
            val id = p.getString("id")
            val name = p.getString("name")
            require(id.isNotBlank() && name.isNotBlank() && name.length <= 80) { "Invalid template split identity" }
            TemplateSplit(id, name, days)
        }.also { splits -> require(splits.map { it.id }.distinct().size == splits.size) { "Duplicate template split" } }
    }
}

/** Editable starting points, not individualized plans or claims about any athlete's routine. */
fun starterTemplateSplits(catalog: List<Exercise>): List<StarterTemplateSplit> {
    fun p(id: String, sets: Int = 3, min: Int = 8, max: Int = 12, rest: Int = 120) = TemplateExercise(id, sets, min, max, rest)
    val bench = "19f5c6f170d8808bb424e98de4472a7e"
    val incline = "2245c6f170d880238f4ff9500137ec89"
    val row = "1a15c6f170d8802d969ae139f42062fa"
    val cableRow = "1a15c6f170d88048b1ccfe9a74551d08"
    val pulldown = "1ca5c6f170d88029b5f6c3464d50ea03"
    val lateral = "1a15c6f170d88067875bfd4347b883ef"
    val rear = "1a15c6f170d880db8633e3bec8b7e8fe"
    val overhead = "1a15c6f170d880e8ad58cebec203e231"
    val pushdown = "1a15c6f170d880479d38c49a76cb6b10"
    val preacher = "29f5c6f170d8800f9577c4c1d85c2900"
    val curl = "1a15c6f170d880b49e09fcbcd061fbdb"
    val squat = "1a25c6f170d8803d8231d083fdd65458"
    val hack = "1a25c6f170d88008a9adde9f71881c36"
    val press = "1a25c6f170d88079a926dde776766181"
    val rdl = "1a35c6f170d880bebc64f4dce0d5f5f1"
    val hamCurl = "1a35c6f170d88029bc9ff9ded538758c"
    val extension = "1a25c6f170d880c59f17ce7802758fc3"
    val calf = "1a15c6f170d880518057cd4d484cbecb"
    val crunch = "1a35c6f170d8807c8999d24ee4aaf939"
    val shoulder = "1a05c6f170d88060bbffeeb9acaa3c80"
    val fly = "1a15c6f170d8804d834cef49a7b23f12"
    fun day(name: String, vararg entries: TemplateExercise) = StarterTemplateDay(name, entries.toList())
    val upperA = day("Upper A", p(bench, min = 6, max = 10, rest = 180), p(row, rest = 150), p(incline), p(pulldown), p(lateral, min = 12, max = 20, rest = 75), p(overhead, sets = 2, min = 10, max = 15, rest = 90), p(preacher, sets = 2, min = 10, max = 15, rest = 90))
    val upperB = day("Upper B", p(incline, rest = 150), p(pulldown, rest = 150), p(cableRow), p(shoulder, sets = 2), p(lateral, min = 12, max = 20, rest = 75), p(pushdown, sets = 2, min = 10, max = 15, rest = 90), p(curl, sets = 2, min = 10, max = 15, rest = 90))
    val lowerA = day("Lower A", p(squat, min = 6, max = 10, rest = 180), p(rdl, rest = 180), p(hamCurl, min = 10, max = 15, rest = 90), p(extension, sets = 2, min = 10, max = 15, rest = 90), p(calf, min = 10, max = 15, rest = 90), p(crunch, sets = 2, min = 10, max = 15, rest = 75))
    val lowerB = day("Lower B", p(hack, rest = 180), p(rdl, sets = 2, rest = 180), p(press), p(hamCurl, min = 10, max = 15, rest = 90), p(calf, min = 10, max = 15, rest = 90), p(crunch, sets = 2, min = 10, max = 15, rest = 75))
    val push = day("Push", p(incline, rest = 150), p(fly, min = 10, max = 15, rest = 90), p(shoulder, sets = 2), p(lateral, min = 12, max = 20, rest = 75), p(overhead, min = 10, max = 15, rest = 90))
    val pull = day("Pull", p(pulldown, rest = 150), p(row, rest = 150), p(rear, min = 12, max = 20, rest = 75), p(preacher, min = 10, max = 15, rest = 90), p(curl, sets = 2, min = 10, max = 15, rest = 90))
    val legs = day("Legs", p(hack, rest = 180), p(rdl, rest = 180), p(hamCurl, min = 10, max = 15, rest = 90), p(extension, sets = 2, min = 10, max = 15, rest = 90), p(calf, min = 10, max = 15, rest = 90))
    val chest = day("Chest", p(bench, min = 6, max = 10, rest = 180), p(incline, rest = 150), p(fly, min = 10, max = 15, rest = 90))
    val back = day("Back", p(pulldown, rest = 150), p(row, rest = 150), p(cableRow), p(rear, min = 12, max = 20, rest = 75))
    val shoulders = day("Shoulders", p(shoulder, rest = 150), p(lateral, sets = 4, min = 12, max = 20, rest = 75), p(rear, min = 12, max = 20, rest = 75))
    val arms = day("Arms", p(preacher, min = 10, max = 15, rest = 90), p(overhead, min = 10, max = 15, rest = 90), p(curl, min = 10, max = 15, rest = 90), p(pushdown, min = 10, max = 15, rest = 90))
    val fullA = day("Full body A", p(squat, min = 6, max = 10, rest = 180), p(bench, rest = 150), p(row), p(hamCurl, min = 10, max = 15, rest = 90), p(lateral, sets = 2, min = 12, max = 20, rest = 75))
    val fullB = day("Full body B", p(rdl, rest = 180), p(incline, rest = 150), p(pulldown), p(extension, min = 10, max = 15, rest = 90), p(calf, min = 10, max = 15, rest = 90))
    val fullC = day("Full body C", p(press, rest = 180), p(shoulder), p(cableRow), p(hamCurl, min = 10, max = 15, rest = 90), p(preacher, sets = 2, min = 10, max = 15, rest = 90), p(overhead, sets = 2, min = 10, max = 15, rest = 90))
    val presets = listOf(
        StarterTemplateSplit("upper-lower-ppl", "Upper / Lower / Push / Pull / Legs", listOf(upperA.copy(name = "Upper"), lowerA.copy(name = "Lower"), null, push, pull, legs, null)),
        StarterTemplateSplit("upper-lower", "Upper / Lower", listOf(upperA, lowerA, null, upperB, lowerB, null, null)),
        StarterTemplateSplit("bro-split", "Bro split", listOf(chest, back, legs, shoulders, arms, null, null)),
        StarterTemplateSplit("full-body", "Full body", listOf(fullA, null, fullB, null, fullC, null, null)),
    )
    val available = catalog.map { it.id }.toSet()
    return presets.filter { preset -> preset.days.filterNotNull().flatMap { it.prescriptions }.all { it.exerciseId in available } }
}
