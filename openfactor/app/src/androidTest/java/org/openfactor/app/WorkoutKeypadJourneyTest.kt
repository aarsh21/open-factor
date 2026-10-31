package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutKeypadJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun key(description: String) = compose.onNode(hasContentDescription(description) and hasClickAction())
    private fun keypad() = compose.onNodeWithTag("Workout keypad")
    private fun field(set: Int, metric: String) = compose.onNodeWithContentDescription("Barbell Bench Press set $set $metric")

    @Test fun keypadTypesStepsAdvancesLogsAndYieldsToBackAndTheSystemKeyboardSetting() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
            val entry = SessionExercise(
                id = "keypad-entry", exerciseId = bench.id,
                sets = listOf(
                    LoggedSet(id = "keypad-first", weightKg = 60.0, reps = 8),
                    LoggedSet(id = "keypad-second", weightKg = 60.0, reps = 8),
                ),
            )
            store.save(AppData(
                onboarding = OnboardingState(completed = true),
                preferences = Preferences(showRir = false, autoRest = false, rateEffortAfterExercise = false),
                active = Workout(name = "Keypad test", exercises = listOf(entry), selectedExerciseId = entry.id),
            ))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            keypad().assertDoesNotExist()

            field(1, "weight (kg)").performScrollTo().performClick()
            keypad().assertIsDisplayed()
            field(1, "weight (kg)").assertIsFocused()
            activity.onActivity {
                val insets = ViewCompat.getRootWindowInsets(it.window.decorView)
                assertFalse(insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            }

            // The first key replaces the prefilled 60; later keys append.
            listOf("7", "2", "Decimal point", "5").forEach { key(it).performClick() }
            field(1, "weight (kg)").assertTextEquals("72.5")
            key("Increase by 2.5 kg").performClick()
            field(1, "weight (kg)").assertTextEquals("75")
            key("Decrease by 2.5 kg").performClick()
            key("Decrease by 2.5 kg").performClick()
            field(1, "weight (kg)").assertTextEquals("70")

            key("Next field").performClick()
            field(1, "reps (Reps)").assertIsFocused()
            keypad().assertIsDisplayed()
            key("Decimal point").assertIsNotEnabled()
            key("1").performClick()
            key("0").performClick()
            field(1, "reps (Reps)").assertTextEquals("10")

            // Logging a set goes straight to the next unfinished set; the keypad stays up between sets.
            key("Log set").performClick()
            keypad().assertIsDisplayed()
            field(2, "weight (kg)").assertIsFocused()
            compose.onNodeWithContentDescription("Mark set incomplete 1 for Barbell Bench Press").assertExists()
            compose.runOnIdle {
                val first = store.load().active!!.exercises.single().sets.first()
                assertTrue(first.completed)
                assertEquals(70.0, first.weightKg!!, 0.0001)
                assertEquals(10, first.reps)
            }

            // Accessibility and test automation still set text directly while the keypad is on.
            field(2, "reps (Reps)").performScrollTo().performTextReplacement("6")
            keypad().assertIsDisplayed()
            field(2, "reps (Reps)").assertTextEquals("6")

            // Back hides the keypad before it can leave the session.
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            keypad().assertDoesNotExist()
            compose.onNodeWithText("Keypad test").assertExists()
            field(2, "weight (kg)").assertExists()
            // Typed values reach storage through the coalesced background save.
            compose.waitUntil(5_000) { store.load().active!!.exercises.single().sets.last().reps == 6 }

            compose.onNodeWithContentDescription("Workout options").performClick()
            compose.onNodeWithText("Workout settings").performClick()
            compose.onNodeWithText("Workout keypad").performScrollTo().performClick()
            compose.onNodeWithText("Done").performClick()
            compose.runOnIdle { assertFalse(store.load().preferences.workoutKeypad) }

            field(2, "weight (kg)").performScrollTo().performClick()
            field(2, "weight (kg)").assertIsFocused()
            keypad().assertDoesNotExist()
            field(2, "weight (kg)").performTextReplacement("65")
            field(2, "weight (kg)").assertTextEquals("65")
            compose.waitUntil(5_000) { store.load().active!!.exercises.single().sets.last().weightKg == 65.0 }
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
