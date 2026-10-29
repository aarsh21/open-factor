package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    @Test fun profileMeasurementsUnitsAndTrainingChoicesSurviveRecreationAndApplyOnce() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        val history = Workout(name = "Keep my training", startedAt = System.currentTimeMillis() - 90_000,
            finishedAt = System.currentTimeMillis() - 30_000,
            exercises = listOf(SessionExercise(exerciseId = "keep", sets = listOf(LoggedSet(weightKg = 20.0, reps = 8, completed = true)))))
        try {
            store.save(AppData(history = listOf(history)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            await("Make it yours")
            compose.onNodeWithText("Continue").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Profile name").performTextInput("Taylor")
            compose.onNodeWithContentDescription("Gender Woman").performScrollTo().performClick()
            compose.onNodeWithText("Continue").assertIsEnabled().performClick()
            await("Your starting point")
            compose.onNodeWithText("Health Connect").assertExists()
            compose.onNodeWithContentDescription("Profile weight").performScrollTo().performTextReplacement("wrong")
            compose.onNodeWithText("Continue").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Profile weight").performTextReplacement("75.5")
            compose.onNodeWithContentDescription("Profile height").performScrollTo().performTextReplacement("180")
            activity.recreate()
            await("Your starting point")
            compose.onNodeWithContentDescription("Profile weight").performScrollTo().assertTextContains("75.5")
            compose.onNodeWithContentDescription("Profile height").performScrollTo().assertTextContains("180")
            compose.onNodeWithText("Pounds").performScrollTo().performClick()
            compose.onNodeWithText("Feet and inches").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Profile height feet").assertTextContains("5")
            compose.onNodeWithContentDescription("Profile height inches").assertTextContains("10.866")
            compose.onNodeWithText("Continue").assertIsEnabled().performClick()
            await("Ready, set, log.")
            compose.onNodeWithContentDescription("More workouts per week").performClick()
            compose.onNodeWithContentDescription("Weekly workout goal: 4").assertIsDisplayed()
            compose.onNodeWithText("Automatic rest timer").performScrollTo().performClick()
            compose.onNodeWithText("Start training").performClick()
            await("Start workout")
            compose.runOnIdle {
                val data = store.load()
                assertTrue(data.onboarding.completed)
                assertFalse(data.onboarding.deferred)
                assertNull(data.onboarding.profileDraft)
                assertEquals("Taylor", data.preferences.displayName)
                assertEquals(ProfileGender.WOMAN, data.onboarding.gender)
                assertTrue(data.preferences.pounds)
                assertTrue(data.preferences.heightInFeet)
                assertEquals(4, data.preferences.weeklyWorkoutGoal)
                assertTrue(data.preferences.rateEffortAfterExercise)
                assertFalse(data.preferences.autoRest)
                assertEquals(180.0, data.onboarding.heightCm.toDouble(), .01)
                assertEquals(75.5, data.scaleWeights.single().weightKg, .001)
                assertEquals(LocalDate.now(), data.scaleWeights.single().date)
                assertEquals(listOf(history), data.history)
            }
            activity.recreate()
            await("Start workout")
            compose.onNodeWithText("Make it yours").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, store.load().scaleWeights.size) }
        } finally { activity?.close(); store.save(before) }
    }

    @Test fun skipKeepsUnfinishedInputsAndReviewSetupResumesThem() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData())
            activity = ActivityScenario.launch(MainActivity::class.java)
            await("Make it yours")
            compose.onNodeWithContentDescription("Profile name").performTextInput("Alex")
            compose.onNodeWithText("Continue").performClick()
            await("Your starting point")
            compose.onNodeWithContentDescription("Profile weight").performScrollTo().performTextReplacement("80")
            compose.onNodeWithText("Skip for now").performClick()
            await("Start workout")
            compose.runOnIdle {
                val data = store.load()
                assertTrue(data.onboarding.deferred)
                assertFalse(data.onboarding.completed)
                assertEquals("80", data.onboarding.profileDraft!!.weightText)
                assertTrue(data.scaleWeights.isEmpty())
            }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Profile").performScrollTo().performClick()
            await("Review setup")
            compose.onNodeWithText("Review setup").performScrollTo().performClick()
            await("Your starting point")
            compose.onNodeWithContentDescription("Profile weight").performScrollTo().assertTextContains("80")
            compose.runOnIdle {
                val draft = store.load().onboarding.profileDraft!!
                assertEquals("Alex", draft.name)
                assertEquals(1, draft.stage)
                assertFalse(store.load().onboarding.deferred)
            }
        } finally { activity?.close(); store.save(before) }
    }
}
