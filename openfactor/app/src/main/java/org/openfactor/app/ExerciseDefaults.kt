package org.openfactor.app

import org.json.JSONObject

data class ExerciseDefaults(
    val note: String = "",
    val restSeconds: Int? = null,
    val doNotRecommend: Boolean = false,
) {
    init { require(restSeconds == null || restSeconds in 0..1800) }
}

fun newSessionExercise(
    exerciseId: String,
    defaults: ExerciseDefaults? = null,
    sets: List<LoggedSet> = listOf(LoggedSet()),
    restSeconds: Int? = null,
): SessionExercise = SessionExercise(
    exerciseId = exerciseId, sets = sets,
    restSeconds = restSeconds ?: defaults?.restSeconds,
)

object ExerciseDefaultsCodec {
    fun encode(values: Map<String, ExerciseDefaults>) = JSONObject().also { json ->
        values.forEach { (id, value) ->
            json.put(id, JSONObject().put("note", value.note).put("restSeconds", value.restSeconds)
                .put("doNotRecommend", value.doNotRecommend))
        }
    }

    fun decode(json: JSONObject?): Map<String, ExerciseDefaults> = json?.keys()?.asSequence()?.associateWith { id ->
        val value = json.getJSONObject(id)
        val rest = if (value.has("restSeconds") && !value.isNull("restSeconds")) {
            val number = value.get("restSeconds")
            require(number is Number && number.toDouble().isFinite() && number.toDouble() % 1.0 == 0.0 && number.toDouble() in 0.0..1800.0) { "Invalid exercise rest timer" }
            number.toInt()
        } else null
        ExerciseDefaults(value.optString("note"), rest, value.optBoolean("doNotRecommend"))
    } ?: emptyMap()
}
