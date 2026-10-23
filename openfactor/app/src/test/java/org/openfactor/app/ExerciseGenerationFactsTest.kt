package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExerciseGenerationFactsTest {
    @Test
    fun olderCatalogIsEnrichedByIdWithoutOverwritingPresentNullEmptyOrZero() {
        val bundled = DataCodec.catalog("""[
            {"id":"a","name":"Bundled A","properties":{"Region":"Upper body","Movement Patterns":"Horizontal Push","Metrics":"Reps"},
             "generationFacts":{"exerciseClassificationStrength":["primary"],"exerciseClassificationHypertrophy":["secondary"],
               "strengthPrimaryCompoundRecommendationLevel":2.5,"strengthAccessoryRecommendationLevel":4.0,
               "hypertrophyAccessoryRecommendationLevel":3.5}},
            {"id":"b","name":"Bundled B","generationFacts":{"exerciseClassificationStrength":[],"strengthPrimaryCompoundRecommendationLevel":null}}
        ]""")
        val imported = DataCodec.catalog("""[
            {"id":"b","name":"Imported B","frames":["private.png"]},
            {"id":"a","name":"Imported A","properties":{"Region":"Lower body","Movement Patterns":"None"},
             "generationFacts":{"exerciseClassificationStrength":null,"exerciseClassificationHypertrophy":[],
               "strengthPrimaryCompoundRecommendationLevel":null,"strengthAccessoryRecommendationLevel":0}},
            {"id":"unknown","name":"Unknown"}
        ]""", bundled)
        assertEquals(listOf("b", "a", "unknown"), imported.map { it.id })
        assertEquals("Imported B", imported[0].name)
        assertEquals(listOf("private.png"), imported[0].frames)
        assertEquals(bundled[1].generationFacts, imported[0].generationFacts)
        val facts = imported[1].generationFacts
        assertEquals(mapOf("exerciseClassificationStrength" to null, "exerciseClassificationHypertrophy" to emptyList<String>()), facts.descriptorLists)
        assertEquals(mapOf("strengthPrimaryCompoundRecommendationLevel" to null, "strengthAccessoryRecommendationLevel" to 0.0,
            "hypertrophyAccessoryRecommendationLevel" to 3.5), facts.recommendationLevels)
        assertEquals(mapOf("Region" to "Lower body", "Movement Patterns" to "None"), imported[1].properties)
        assertEquals(ExerciseGenerationFacts(), imported[2].generationFacts)
        val legacy = DataCodec.catalog("""[{"id":"a","name":"Legacy"}]""", bundled).single()
        assertEquals(mapOf("Region" to "Upper body", "Movement Patterns" to "Horizontal Push"), legacy.properties)
        assertEquals(bundled[0].generationFacts, legacy.generationFacts)

        val grouped = DataCodec.catalog("""[{"id":"a","name":"Bundled",
            "generationFacts":{"exclusionGroupings":["group-a","group-b"]}}]""")
        for ((field, expected) in listOf(
            "" to listOf("group-a", "group-b"),
            """"exclusionGroupings":null""" to null,
            """"exclusionGroupings":[]""" to emptyList(),
            """"exclusionGroupings":["group-c","group-a"]""" to listOf("group-c", "group-a"),
        )) {
            val result = DataCodec.catalog("""[{"id":"a","name":"Imported","generationFacts":{$field}}]""", grouped).single()
            assertEquals(mapOf("exclusionGroupings" to expected), result.generationFacts.descriptorLists)
        }
        val missing = DataCodec.catalog("""[{"id":"a","name":"No facts"}]""").single()
        assertFalse(missing.generationFacts.descriptorLists.containsKey("exclusionGroupings"))
    }

    @Test
    fun explicitFactsSurviveBackupButNewCustomExerciseGetsNoInferredFacts() {
        val custom = CustomExerciseDraft(name = "Custom", metric1 = SetMetric.REPS, bodyPercent = "0")
            .toExercise(emptyList(), "custom")
        assertEquals(ExerciseGenerationFacts(), custom.generationFacts)
        val absentJson = JSONObject(DataCodec.encode(AppData(customExercises = listOf(custom))))
        assertFalse(absentJson.getJSONArray("customExercises").getJSONObject(0).has("generationFacts"))
        val explicit = custom.copy(generationFacts = ExerciseGenerationFacts(
            mapOf("exerciseClassificationStrength" to null, "exerciseClassificationHypertrophy" to emptyList(),
                "exclusionGroupings" to listOf("group-z", "group-b")),
            mapOf("strengthPrimaryCompoundRecommendationLevel" to 1.5, "hypertrophyAccessoryRecommendationLevel" to null),
        ))
        val saved = AppData(customExercises = listOf(explicit))
        assertEquals(saved, DataCodec.decode(DataCodec.encode(saved)))
    }

    @Test
    fun invalidGenerationFactsAreRejectedRatherThanTreatedAsMissing() {
        for (facts in listOf(
            "null", "[]", "true", """{"searchBoost":9}""",
            """{"strengthPrimaryCompoundRecommendationLevel":"2.5"}""",
            """{"strengthPrimaryCompoundRecommendationLevel":true}""",
            """{"strengthPrimaryCompoundRecommendationLevel":1e9999}""",
            """{"exerciseClassificationStrength":"primary"}""",
            """{"exerciseClassificationHypertrophy":[1]}""",
            """{"exerciseClassificationStrength":[null]}""",
            """{"exclusionGroupings":[false]}""",
            """{"exerciseGroup":["not-exclusion-groupings"]}""",
        )) {
            assertThrows("Invalid $facts", RuntimeException::class.java) {
                DataCodec.catalog("""[{"id":"invalid","name":"Invalid","generationFacts":$facts}]""")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseGenerationFacts(recommendationLevels = mapOf("strengthPrimaryCompoundRecommendationLevel" to Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseGenerationFacts(descriptorLists = mapOf("invented" to emptyList()))
        }
    }
}
