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

/** Exercises the shipped activity through its public controls, with isolated local data. */
@RunWith(AndroidJUnit4::class)
class QuickWorkoutJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    @Test
    fun firstWorkoutLogsWarmupRecoversAndFinishesInHistory() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
                ).use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
                assertEquals(
                    android.content.pm.PackageManager.PERMISSION_GRANTED,
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS),
                )
            }
            store.save(AppData())
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Make it yours")
            compose.onNodeWithContentDescription("Profile name").performTextInput("Synthetic Athlete")
            compose.onNodeWithText("Continue").performClick()
            awaitText("Your starting point")
            compose.onNodeWithText("Kilograms").performScrollTo().performClick()
            compose.onNodeWithText("Continue").performClick()
            awaitText("Ready, set, log.")
            compose.onNodeWithText("Start training").performClick()
            awaitText("Start workout")
            assertTrue(store.load().onboarding.completed)
            assertFalse(store.load().preferences.pounds)
            compose.onNodeWithText("Start workout").performClick()
            awaitText("Search exercises")
            compose.onNode(hasSetTextAction()).performTextInput("Barbell bench press")
            compose.waitForIdle()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Add Barbell bench press").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Add Barbell bench press")
                .performScrollTo().assertIsDisplayed().performClick()
            awaitText("Warmup")
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 weight (kg)")
                .performScrollTo().performTextReplacement("47.5")
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 reps (Reps)")
                .performTextReplacement("9")
            compose.onNodeWithContentDescription("Complete set 1 for Barbell Bench Press")
                .performClick()
            awaitText("Rate your last set")
            compose.onNodeWithContentDescription("Skip effort rating").performScrollTo().performClick()
            compose.onNodeWithText("Warmup").performScrollTo().performClick()
            compose.runOnIdle {
                val session = store.load().active!!
                val sets = session.exercises.single().sets
                assertEquals(2, sets.size)
                assertTrue(sets.first().warmup)
                assertNull(sets.first().weightKg)
                assertFalse(sets.first().completed)
                assertEquals(47.5, sets.last().weightKg!!, 0.00001)
                assertEquals(9, sets.last().reps)
                assertTrue(sets.last().completed)
            }
            val saved = store.load().active!!

            // Close the activity and launch a fresh one: this creates a new ViewModel/store reader.
            // A separate external device test must verify actual operating-system process death.
            activity.close()
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            assertEquals(saved, store.load().active)
            compose.onNodeWithContentDescription("Mark set incomplete 1 for Barbell Bench Press")
                .performScrollTo().assertExists()

            compose.onNodeWithText("Finish").performClick()
            awaitText("Finish Workout?")
            compose.onAllNodesWithText("Finish").onLast().performClick()
            awaitText("Workout Complete")
            compose.onNodeWithText("Set 1 · Standard").assertExists()
            compose.runOnIdle {
                val data = store.load()
                assertNull(data.active)
                assertEquals(1, data.history.size)
                val workout = data.history.single()
                assertEquals(saved.id, workout.id)
                assertEquals(saved.exercises, workout.exercises)
                assertEquals(1, workout.completedSets())
                assertNotNull(workout.finishedAt)
            }
            compose.onNodeWithContentDescription("Back").performClick()
            awaitText("Workout History")
            compose.onNodeWithText("Barbell bench press", substring = true, ignoreCase = true)
                .assertExists()
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
