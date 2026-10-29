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

@RunWith(AndroidJUnit4::class)
class WorkoutEffortJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    @Test fun bulkLoggingValidatesAllRowsThenRatesOnlyLastSetAndSurvivesRecreation() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
            }
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
            val entry = SessionExercise(
                id = "effort-entry", exerciseId = bench.id,
                sets = listOf(
                    LoggedSet(id = "effort-first", weightKg = 47.5, reps = 8),
                    LoggedSet(id = "effort-last", weightKg = 47.5),
                ),
            )
            store.save(AppData(
                onboarding = OnboardingState(completed = true),
                // Legacy quick-start explicitly hid inline RIR; the new end-of-exercise prompt
                // remains available through its separate, default-on setting.
                preferences = Preferences(showRir = false, autoRest = false),
                active = Workout(name = "Effort test", exercises = listOf(entry), selectedExerciseId = entry.id),
            ))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            compose.onNodeWithText("Log all sets").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
            compose.onNodeWithContentDescription("Barbell Bench Press set 2 reps (Reps)")
                .performScrollTo().performTextReplacement("9")
            compose.onNodeWithText("Log all sets").performScrollTo().assertIsEnabled().performClick()
            awaitText("Rate your last set")
            compose.runOnIdle {
                val logs = store.load().active!!.exercises.single().sets
                assertTrue(logs.all { it.completed })
                assertTrue(logs.all { it.rir == null })
            }
            compose.onNodeWithContentDescription("2 reps left").performClick().assertIsSelected()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            compose.runOnIdle {
                val logged = store.load().active!!.exercises.single()
                assertNull(logged.sets.first().rir)
                assertEquals(2, logged.sets.last().rir)
                assertEquals(setOf("effort-last"), logged.reviewedEffortSetIds)
            }
            activity.recreate()
            awaitText("Warmup")
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
            compose.onNodeWithContentDescription("Mark set incomplete 2 for Barbell Bench Press")
                .performScrollTo().performClick()
            compose.onNodeWithContentDescription("Complete set 2 for Barbell Bench Press")
                .performClick()
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
            compose.onNodeWithText("Last set · 2 RIR").performScrollTo().assertIsDisplayed()
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
