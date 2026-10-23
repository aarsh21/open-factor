package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

/** Source catalog facts. Missing keys, explicit nulls, and empty descriptor lists stay distinct. */
data class ExerciseGenerationFacts(
    val descriptorLists: Map<String, List<String>?> = emptyMap(),
    val recommendationLevels: Map<String, Double?> = emptyMap(),
) {
    init {
        require(descriptorLists.keys.all { it in listFields }) { "Unknown descriptor list field" }
        require(recommendationLevels.keys.all { it in recommendationFields }) { "Unknown recommendation field" }
        require(recommendationLevels.values.all { it == null || it.isFinite() }) { "Invalid recommendation level" }
    }

    internal fun json(): JSONObject = JSONObject().apply {
        descriptorLists.forEach { (key, ids) -> put(key, ids?.let { JSONArray(it) } ?: JSONObject.NULL) }
        recommendationLevels.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
    }

    companion object {
        internal val listFields = listOf("exerciseClassificationStrength", "exerciseClassificationHypertrophy", "exclusionGroupings")
        internal val recommendationFields = listOf("strength", "hypertrophy").flatMap { goal ->
            listOf("PrimaryCompound", "SecondaryCompound", "Accessory").map { role ->
                "${goal}${role}RecommendationLevel"
            }
        }

        internal fun decode(json: JSONObject?): ExerciseGenerationFacts {
            if (json == null) return ExerciseGenerationFacts()
            require(json.keys().asSequence().all { it in listFields || it in recommendationFields }) {
                "Unknown generation fact"
            }
            return ExerciseGenerationFacts(
                descriptorLists = listFields.filter { json.has(it) }.associateWith { key ->
                    if (json.isNull(key)) null else json.getJSONArray(key).let { ids ->
                        List(ids.length()) { ids.getString(it) }
                    }
                },
                recommendationLevels = recommendationFields.filter { json.has(it) }.associateWith { key ->
                    if (json.isNull(key)) null else json.get(key).let {
                        require(it is Number) { "Invalid recommendation level type" }
                        it.toDouble()
                    }
                },
            )
        }
    }
}
