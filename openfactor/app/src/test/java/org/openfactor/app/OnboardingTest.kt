package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {
    @Test
    fun setupProgressAndPartialInputSurviveBackup() {
        val setup =
            OnboardingState(
                step = 3,
                name = "Synthetic Athlete",
                heightCm = "18.",
                weightKg = "75",
                pounds = true,
                deferred = true,
            )
        val program = TrainingProgram(name = "Keep my plan")
        val data = AppData(programs = listOf(program), onboarding = setup)
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertFalse(OnboardingState().completed)
    }

    @Test
    fun legacyInstallationsDoNotGetForcedThroughSetup() {
        val old =
            JSONObject(
                    DataCodec.encode(
                        AppData(preferences = Preferences(displayName = "Existing athlete"))
                    )
                )
                .apply { remove("onboarding") }
        val migrated = DataCodec.decode(old.toString())
        assertTrue(migrated.onboarding.completed)
        assertEquals("Existing athlete", migrated.preferences.displayName)
        assertEquals(7, OnboardingState.decode(JSONObject().put("step", 999)).step)
    }

    @Test
    fun optionalProfileValuesHaveRecoverableValidation() {
        val basic = OnboardingState(name = "Athlete")
        assertTrue(basic.validBasics())
        assertFalse(basic.copy(heightCm = "0").validBasics())
        assertFalse(basic.copy(weightKg = "NaN").validBasics())
        assertFalse(basic.copy(heightCm = "bad").validBasics())
        assertTrue(basic.copy(heightCm = "180", weightKg = "75.5").validBasics())
    }
}
