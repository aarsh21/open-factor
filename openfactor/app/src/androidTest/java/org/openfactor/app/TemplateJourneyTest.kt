package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the shipped activity and public controls; no ViewModel calls stand in for navigation. */
@RunWith(AndroidJUnit4::class)
class TemplateJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun awaitDescription(description: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun field(label: String): SemanticsNodeInteraction {
        val matcher = hasSetTextAction() and hasText(label)
        // Recreation can restore the list below the name row; locate lazy items before querying.
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }

    @Test fun browseSplitRecreatesRawDraftSavesOrDiscardsAndStartsEditedDayWithBlankActuals() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            // A permission dialog would cover the session controls on Android 13+.
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
                ).use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
            }
            store.save(AppData(onboarding = OnboardingState(completed = true),
                preferences = Preferences(pounds = false, autoRest = false)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Start workout")
            compose.onNodeWithText("Browse splits").performScrollTo()
            // Clear the floating navigation before a real touch on compact CI viewports.
            compose.onRoot().performTouchInput { swipeUp(startY = height * .65f, endY = height * .35f) }
            compose.onNodeWithText("Browse splits").assertIsDisplayed().performClick()
            awaitText("Starter splits")
            compose.onNodeWithText("Upper / Lower").performScrollTo().performClick()
            awaitText("Use split")
            compose.onNodeWithText("Day 1").assertExists()
            compose.onNodeWithContentDescription("Day 3, Rest").performClick()
            compose.onNodeWithText("Day 3").assertExists()
            compose.onNodeWithText("Barbell Bench Press").assertDoesNotExist()
            compose.onNodeWithText("Use split").assertIsDisplayed()
            compose.onNodeWithContentDescription("Day 1, Upper A").performClick()
            compose.onNodeWithContentDescription("Edit Upper A").assertDoesNotExist()
            compose.runOnIdle { assertTrue(store.load().routines.isEmpty()) }
            compose.onNodeWithText("Use split").assertIsDisplayed().performClick()
            awaitDescription("Edit Upper A")
            compose.onNodeWithText("Use split").assertDoesNotExist()
            val installed = store.load()
            assertEquals(4, installed.routines.size)
            assertEquals("Upper / Lower", installed.templateSplits.single().name)

            compose.onNodeWithContentDescription("Edit Upper A").performScrollTo().performClick()
            awaitText("Edit template")
            field("Template name").performTextReplacement("My upper")
            compose.onNodeWithContentDescription("Edit targets for Barbell Bench Press")
                .performScrollTo().performClick()
            field("Sets").performScrollTo().performTextReplacement("2")
            field("Rest (seconds)").performTextReplacement("invalid")
            compose.onNodeWithText("Save template").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(installed, store.load()) }

            // Exercise the actual route and rememberSaveable draft through activity recreation.
            // Raw invalid input must survive without being coerced into a persisted prescription.
            activity.recreate()
            awaitText("Edit template")
            field("Template name").performScrollTo().assertTextContains("My upper")
            field("Sets").performScrollTo().assertTextContains("2")
            field("Rest (seconds)").assertTextContains("invalid")
            compose.onNodeWithText("Save template").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(installed, store.load()) }
            field("Rest (seconds)").performTextReplacement("150")
            field("Min reps").performScrollTo().performTextReplacement("5")
            field("Max reps").performTextReplacement("8")
            compose.onNodeWithText("Save template").assertIsEnabled().performClick()
            awaitDescription("Edit My upper")
            val saved = store.load()
            val routine = saved.routines.single { it.name == "My upper" }
            assertEquals(TemplateExercise(routine.exerciseIds.first(), 2, 5, 8, 150), routine.prescriptions.first())
            assertEquals(installed.templateSplits, saved.templateSplits)

            // Cancel keeps the draft open; Discard returns to the saved split/day unchanged.
            compose.onNodeWithContentDescription("Edit My upper").performScrollTo().performClick()
            awaitText("Edit template")
            field("Template name").performTextReplacement("Discarded upper")
            compose.onNodeWithContentDescription("Back").performClick()
            awaitText("Discard template changes?")
            compose.onNodeWithText("Cancel").performClick()
            field("Template name").assertTextContains("Discarded upper")
            compose.onNodeWithContentDescription("Back").performClick()
            awaitText("Discard template changes?")
            compose.onNodeWithText("Discard").performClick()
            awaitDescription("Edit My upper")
            compose.runOnIdle { assertEquals(saved, store.load()) }

            compose.onNodeWithText("Start My upper").assertIsDisplayed().performClick()
            awaitText("Warmup")
            compose.onAllNodesWithText("5–8 reps").onFirst().performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 weight (kg)")
                .performScrollTo().assertTextEquals("")
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 reps (Reps)")
                .assertTextEquals("")
            compose.runOnIdle {
                val active = store.load().active!!
                assertEquals("My upper", active.name)
                assertEquals(routine.exerciseIds, active.exercises.map { it.exerciseId })
                val bench = active.exercises.first()
                assertEquals(2, bench.sets.size)
                assertEquals(150, bench.restSeconds)
                assertTrue(bench.sets.all { it.targetRepsMin == 5 && it.targetRepsMax == 8 })
                assertTrue(active.exercises.flatMap { it.sets }.all {
                    it.weightKg == null && it.reps == null && !it.completed
                })
                assertEquals(saved.routines, store.load().routines)
            }
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
