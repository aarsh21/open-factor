package org.openfactor.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProfileSetupTest {
    private val today = LocalDate.of(2026, 10, 29)
    @Test fun rawDraftAndOptionalIdentitySurviveBackupWithoutApplyingBeforeFinish() {
        val draft = ProfileSetupDraft(stage = 1, name = "Renée", gender = ProfileGender.NON_BINARY, weightText = "bad", heightText = "18.", weightEdited = true)
        val data = AppData(onboarding = OnboardingState(profileDraft = draft))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertEquals("Athlete", data.preferences.displayName)
        assertTrue(data.scaleWeights.isEmpty())
        assertNotNull(draft.bodyError())
        assertNull(draft.copy(weightText = "", heightText = "").validationError())
    }
    @Test fun profileSaveIsAtomicAndManualWeighInIsIdempotent() {
        val old = ScaleWeightRecord(date = today.minusDays(1), weightKg = 72.0)
        val workout = Workout(name = "Preserved")
        val data = AppData(scaleWeights = listOf(old), history = listOf(workout), active = workout.copy(id = "active"))
        val draft = ProfileSetupDraft(name = "  Sam  ", gender = ProfileGender.WOMAN, weightText = "75.25", heightText = "175", weeklyGoal = 4, weightEdited = true, rateEffort = false, autoRest = false)
        val saved = data.withProfileSetup(draft, today)
        assertEquals("Sam", saved.preferences.displayName)
        assertEquals("175", saved.onboarding.heightCm)
        assertEquals(ProfileGender.WOMAN, saved.onboarding.gender)
        assertEquals(4, saved.preferences.weeklyWorkoutGoal)
        assertFalse(saved.preferences.rateEffortAfterExercise)
        assertFalse(saved.preferences.autoRest)
        assertTrue(saved.onboarding.completed)
        assertEquals(data.active, saved.active)
        assertEquals(data.history, saved.history)
        assertEquals(2, saved.scaleWeights.size)
        assertEquals(saved, saved.withProfileSetup(draft, today))
        assertEquals(saved, DataCodec.decode(DataCodec.encode(saved)))
    }
    @Test fun importsKeepTheirDateAndCannotOverwriteTypedBaseline() {
        val imported = ScaleWeightRecord(date = today.minusDays(3), weightKg = 80.0, healthConnectOrigin = "fixture")
        val typed = ProfileSetupDraft(name = "Sam", weightText = "77", weightEdited = true)
        assertEquals(typed, typed.refreshWeight(listOf(imported), today))
        val filled = ProfileSetupDraft(name = "Sam").refreshWeight(listOf(imported), today)
        assertEquals("80", filled.weightText)
        assertEquals(imported.date.toString(), filled.weightSourceDate)
        val saved = AppData(scaleWeights = listOf(imported)).withProfileSetup(filled, today)
        assertEquals(listOf(imported), saved.scaleWeights)
        assertTrue(saved.scaleWeights.none { it.date == today })
    }
    @Test fun unitChangesPreserveMeasurementsAndRejectReinterpretingInvalidInput() {
        val original = ProfileSetupDraft(name = "Sam", weightText = "75.25", heightText = "180")
        val imperial = original.withPounds(true).withHeightInFeet(true)
        assertEquals(75.25, imperial.weightKg()!!, .001)
        assertEquals(180.0, imperial.heightCm()!!, .01)
        val metric = imperial.withPounds(false).withHeightInFeet(false)
        assertEquals(75.25, metric.weightKg()!!, .001)
        assertEquals(180.0, metric.heightCm()!!, .01)
        val invalid = original.copy(weightText = "NaN", heightText = "bad")
        assertEquals(invalid, invalid.withPounds(true))
        assertEquals(invalid, invalid.withHeightInFeet(true))
        assertNotNull(original.copy(heightInFeet = true, heightText = "5", heightInchesText = "12").bodyError())
    }
    @Test fun invalidProfileCannotReplaceRealHistoryAndLatestWeightPrefersActualDates() {
        val data = AppData(history = listOf(Workout(name = "Keep")))
        for (draft in listOf(ProfileSetupDraft(), ProfileSetupDraft(name = "Sam", weightText = "NaN"), ProfileSetupDraft(name = "Sam", weeklyGoal = 0))) {
            assertThrows(IllegalArgumentException::class.java) { data.withProfileSetup(draft, today) }
        }
        val todayManual = ScaleWeightRecord(date = today, weightKg = 81.0)
        val provider = ScaleWeightRecord(date = today, weightKg = 80.0, healthConnectOrigin = "fixture")
        val future = ScaleWeightRecord(date = today.plusDays(1), weightKg = 90.0)
        assertEquals(todayManual.id, ProfileSetupDraft().refreshWeight(listOf(provider, todayManual, future), today).weightSourceId)
    }
}
