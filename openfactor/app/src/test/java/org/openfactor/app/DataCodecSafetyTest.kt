package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataCodecSafetyTest {
    @Test fun schemaVersionRejectsFractionsStringsBooleansAndOverflowInsteadOfCoercing() {
        val invalid = listOf("1.5", "1.0", "1e0", "\"1\"", "\"1.5\"", "true", "null",
            "4294967297", "-4294967295", "1.0000001", "999999999999999999999999999999")
        invalid.forEach { version ->
            assertThrows("Unsupported version $version must reject the whole document", IllegalArgumentException::class.java) {
                DataCodec.decode("{\"schemaVersion\":$version}")
            }
        }
    }

    @Test fun schemaVersionAcceptsBothIntegerRepresentationsUsedByJsonReaders() {
        listOf<Number>(1, 1L).forEach { version ->
            DataCodec.requireSupportedVersion(JSONObject().put("schemaVersion", version))
        }
        assertEquals(AppData(), DataCodec.decode(DataCodec.encode(AppData())))
    }

    @Test fun malformedPresentArraysRejectTheWholeBackupInsteadOfBecomingEmpty() {
        val fields = listOf("history", "programs", "routines", "favorites", "customExercises", "gyms", "templateSplits")
        for (field in fields) for (invalid in listOf(JSONObject(), "damaged", 1, JSONObject.NULL)) {
            val json = JSONObject().put("schemaVersion", 1).put(field, invalid)
            assertThrows("Malformed $field must not become an empty collection", Exception::class.java) {
                DataCodec.decode(json.toString())
            }
        }
    }

    @Test fun malformedPresentObjectsRejectTheWholeBackupInsteadOfBecomingDefaults() {
        val fields = listOf("active", "preferences", "programDraft", "workoutDraft", "onboarding",
            "exerciseDefaults", "healthConnect", "pastWorkoutEdit")
        for (field in fields) for (invalid in listOf(JSONArray(), "damaged", 1)) {
            val json = JSONObject().put("schemaVersion", 1).put(field, invalid)
            assertThrows("Malformed $field must not become a missing object", Exception::class.java) {
                DataCodec.decode(json.toString())
            }
        }
        for (field in listOf("shortcuts", "dashboard")) {
            val json = JSONObject().put("schemaVersion", 1)
                .put("preferences", JSONObject().put(field, JSONArray()))
            assertThrows(Exception::class.java) { DataCodec.decode(json.toString()) }
        }
    }

    @Test fun nestedWorkoutContainersCannotSilentlyDropRecordedWork() {
        fun document() = DataCodec.document(AppData(active = Workout(name = "Keep sets",
            exercises = listOf(SessionExercise(exerciseId = "bench", sets = listOf(
                LoggedSet(weightKg = 52.5, reps = 8, completed = true),
            ))))))
        for (field in listOf("exercises", "effortPrompts")) {
            val json = document()
            json.getJSONObject("active").put(field, JSONObject())
            assertThrows(Exception::class.java) { DataCodec.decode(json.toString()) }
        }
        for (field in listOf("sets", "reviewedEffortSetIds")) {
            val json = document()
            json.getJSONObject("active").getJSONArray("exercises").getJSONObject(0).put(field, JSONObject())
            assertThrows(Exception::class.java) { DataCodec.decode(json.toString()) }
        }
    }

    @Test fun missingLegacyFieldsAndExplicitlyAbsentOptionalObjectsKeepTheirDefaults() {
        val json = JSONObject().put("schemaVersion", 1)
        val legacyDefaults = AppData(onboarding = OnboardingState(completed = true))
        assertEquals(legacyDefaults, DataCodec.decode(json.toString()))
        for (field in listOf("active", "preferences", "programDraft", "workoutDraft", "onboarding",
            "exerciseDefaults", "healthConnect", "pastWorkoutEdit")) json.put(field, JSONObject.NULL)
        assertEquals(legacyDefaults, DataCodec.decode(json.toString()))
        assertEquals(AppData(), DataCodec.decode(DataCodec.encode(AppData())))
    }
}
