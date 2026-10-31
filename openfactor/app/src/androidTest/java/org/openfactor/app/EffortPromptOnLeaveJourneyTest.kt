package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The default effort timing asks about an exercise when the lifter moves on, not at its last set. */
@RunWith(AndroidJUnit4::class)
class EffortPromptOnLeaveJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog by lazy { context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) } }
    private val bench by lazy { catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) } }
    private val other by lazy { catalog.first { it.id != bench.id && it.loggingMetrics() == bench.loggingMetrics() && !it.archived } }

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    private fun awaitGone(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
    }
    private fun keypad() = compose.onNodeWithTag("Workout keypad")

    private fun grantNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
            ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        }
    }

    /** Bench with one logged set and an unfinished last set, then an unfinished second exercise. */
    private fun session(preferences: Preferences, withNext: Boolean = true) = AppData(
        onboarding = OnboardingState(completed = true),
        preferences = preferences,
        active = Workout(
            name = "Effort timing",
            exercises = listOfNotNull(
                SessionExercise(
                    id = "leave-a", exerciseId = bench.id,
                    sets = listOf(
                        LoggedSet(id = "leave-a1", weightKg = 47.5, reps = 8, completed = true),
                        LoggedSet(id = "leave-a2", weightKg = 47.5, reps = 9),
                    ),
                ),
                if (withNext) SessionExercise(id = "leave-b", exerciseId = other.id, sets = listOf(LoggedSet(id = "leave-b1", weightKg = 20.0, reps = 10))) else null,
            ),
            selectedExerciseId = "leave-a",
        ),
    )

    private fun ActivityScenario<MainActivity>.select(id: String) =
        onActivity { ViewModelProvider(it)[WorkoutViewModel::class.java].selectExercise(id) }

    private fun benchLast(store: WorkoutStore) = store.load().active!!.exercises.first { it.id == "leave-a" }.sets.last()

    @Test fun movingOnAsksAboutTheExerciseJustLeftAndTheQuestionSurvivesRecreation() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(session(Preferences(showRir = false, autoRest = false, exerciseAutoNext = false)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            compose.onNodeWithContentDescription("Complete set 2 for Barbell Bench Press").performScrollTo().performClick()
            compose.waitUntil(5_000) { benchLast(store).completed }
            // The last set no longer interrupts: the lifter may still add a set.
            compose.waitForIdle()
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()

            activity.select("leave-b")
            awaitText("Rate your last set")
            // The sheet asks about the exercise just left and sits over the one arrived at.
            compose.onNode(hasText(bench.title) and hasAnySibling(hasText("Rate your last set"))).assertIsDisplayed()
            compose.runOnIdle {
                val active = store.load().active!!
                assertEquals("leave-b", active.selectedExerciseId)
                assertEquals(listOf(EffortPrompt("leave-a")), active.effortPrompts)
            }

            activity.recreate()
            awaitText("Rate your last set")
            compose.onNodeWithContentDescription("2 reps left").performClick().assertIsSelected()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            awaitGone("Rate your last set")
            compose.runOnIdle {
                val active = store.load().active!!
                assertEquals(2, benchLast(store).rir)
                assertTrue(active.effortPrompts.isEmpty())
                assertEquals("leave-b", active.selectedExerciseId)
            }

            // Hopping back and forth without new completions never asks again.
            activity.select("leave-a")
            activity.select("leave-b")
            compose.waitForIdle()
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun automaticAdvanceWaitsOutRestAndNeverInterruptsTypingOnTheKeypad() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            grantNotifications()
            store.save(session(Preferences(showRir = false, autoRest = true, restSeconds = 120)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            compose.onNodeWithContentDescription("Complete set 2 for Barbell Bench Press").performScrollTo().performClick()
            compose.waitUntil(5_000) { store.load().active!!.selectedExerciseId == "leave-b" }
            compose.waitForIdle()
            // Auto-advance moved on for the lifter; the rest right after the hardest set stays clear.
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
            assertEquals(listOf(EffortPrompt("leave-a", afterRest = true)), store.load().active!!.effortPrompts)

            compose.onNodeWithContentDescription("${other.title} set 1 weight (kg)").performScrollTo().performClick()
            keypad().assertIsDisplayed()
            compose.onNodeWithText("Skip").performClick()
            compose.waitForIdle()
            // Rest is over, but someone typing the next set keeps the keypad; the question waits for it.
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()
            keypad().assertIsDisplayed()
            compose.onNodeWithContentDescription("Hide keypad").performClick()
            awaitText("Rate your last set")
            compose.onNodeWithContentDescription("4 or more reps left").performClick()
            compose.onNodeWithText("Done").performScrollTo().performClick()
            awaitGone("Rate your last set")
            compose.runOnIdle {
                assertEquals(4, benchLast(store).rir)
                assertTrue(store.load().active!!.effortPrompts.isEmpty())
            }
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun finishWithTheCurrentExerciseOwedAsksFirstThenFinishes() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(session(Preferences(showRir = false, autoRest = false), withNext = false))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            compose.onNodeWithContentDescription("Complete set 2 for Barbell Bench Press").performScrollTo().performClick()
            compose.waitUntil(5_000) { benchLast(store).completed }
            compose.waitForIdle()
            compose.onNodeWithText("Rate your last set").assertDoesNotExist()

            compose.onNodeWithText("Finish").performClick()
            awaitText("Rate your last set")
            compose.onNodeWithText("Finish Workout?").assertDoesNotExist()
            compose.onNodeWithContentDescription("Skip effort rating").performScrollTo().performClick()
            // Skipping still counts as reviewed, and the finish intent carries on.
            awaitText("Finish Workout?")
            compose.runOnIdle {
                val logged = store.load().active!!.exercises.single()
                assertNull(logged.sets.last().rir)
                assertEquals(setOf("leave-a2"), logged.reviewedEffortSetIds)
            }
            compose.onAllNodesWithText("Finish").onLast().performClick()
            awaitText("Workout Complete")
            compose.runOnIdle {
                val data = store.load()
                assertNull(data.active)
                assertTrue(data.history.first().effortPrompts.isEmpty())
                assertEquals(setOf("leave-a2"), data.history.first().exercises.single().reviewedEffortSetIds)
            }
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun workoutSettingsOfferTheTimingOnlyWhileEffortRatingIsOn() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            // On a fresh install the system notification prompt would cover the workout.
            grantNotifications()
            store.save(session(Preferences(showRir = false, autoRest = false)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            compose.onNodeWithContentDescription("Workout options").performClick()
            compose.onNodeWithText("Workout settings").performClick()
            compose.onNodeWithText("When moving to the next exercise").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Right after the last set").performScrollTo().assertIsNotSelected().performClick()
            compose.waitUntil(5_000) { store.load().preferences.effortPromptTiming == EffortPromptTiming.LAST_SET }
            compose.onNodeWithText("Rate effort after exercise").performClick()
            compose.waitUntil(5_000) { !store.load().preferences.rateEffortAfterExercise }
            compose.onNodeWithText("Right after the last set").assertDoesNotExist()
            compose.onNodeWithText("Done").performClick()
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
