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
class ProfileSettingsJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    @Test fun settingsHasRealControlsAndProfileSavesOnceOrDiscardsCleanly() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            val oldWeight = ScaleWeightRecord(id = "existing-profile-weight", date = LocalDate.now().minusDays(1), weightKg = 70.0)
            store.save(AppData(onboarding = OnboardingState(completed = true),
                preferences = Preferences(displayName = "Existing athlete", autoRest = false), scaleWeights = listOf(oldWeight)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            await("Start workout")
            compose.onNodeWithText("Settings").performClick()
            await("Profile")
            compose.onNodeWithText("Language").assertDoesNotExist()
            compose.onNodeWithText("Set up Open Factor").assertDoesNotExist()
            compose.onNodeWithText("Workout logging").performScrollTo().performClick()
            compose.onNodeWithText("Bodyweight Contribution").assertDoesNotExist()
            compose.onNodeWithText("Keep screen on").assertIsDisplayed()
            compose.onNodeWithText("Done").performClick()
            compose.onNodeWithText("Units").performScrollTo()
            // BringIntoView only knows the scroll viewport, not the floating navigation overlay.
            // Scroll the destination above that overlay before exercising its real touch target.
            compose.onRoot().performTouchInput { swipeUp(startY = height * .65f, endY = height * .35f) }
            compose.onNodeWithText("Units").assertIsDisplayed().performClick()
            await("WEIGHT UNITS")
            compose.onNodeWithText("ENERGY UNITS").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Profile").performScrollTo().performClick()
            await("Review setup")
            compose.onNodeWithContentDescription("Profile name").performScrollTo().performTextReplacement("Updated athlete")
            compose.onNodeWithContentDescription("Gender Woman").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Profile weight").performScrollTo().performTextReplacement("wrong")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Profile weight").performTextReplacement("71.5")
            compose.onNodeWithContentDescription("Profile height").performScrollTo().performTextReplacement("181")
            compose.onNodeWithContentDescription("More workouts per week").performScrollTo().performClick()
            activity.recreate()
            await("Review setup")
            compose.onNodeWithContentDescription("Profile name").performScrollTo().assertTextContains("Updated athlete")
            compose.onNodeWithText("Save").assertIsEnabled().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Profile name").fetchSemanticsNodes().isEmpty()
            }
            compose.runOnIdle {
                val saved = store.load()
                assertEquals("Updated athlete", saved.preferences.displayName)
                assertEquals(ProfileGender.WOMAN, saved.onboarding.gender)
                assertEquals("181", saved.onboarding.heightCm)
                assertEquals(4, saved.preferences.weeklyWorkoutGoal)
                assertFalse(saved.preferences.autoRest)
                assertEquals(2, saved.scaleWeights.size)
                assertEquals(oldWeight, saved.scaleWeights.single { it.id == oldWeight.id })
                assertEquals(71.5, saved.scaleWeights.single { it.date == LocalDate.now() }.weightKg, .001)
            }
            compose.onNodeWithText("Profile").performScrollTo().performClick()
            await("Review setup")
            compose.onNodeWithContentDescription("Profile name").performScrollTo().performTextReplacement("Discarded name")
            compose.onNodeWithContentDescription("Back").performClick()
            await("Discard profile changes?")
            compose.onNodeWithText("Discard").performClick()
            compose.runOnIdle {
                assertEquals("Updated athlete", store.load().preferences.displayName)
                assertEquals(2, store.load().scaleWeights.size)
            }
        } finally { activity?.close(); store.save(before) }
    }
}
