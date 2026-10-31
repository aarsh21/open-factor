package org.openfactor.app

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Swaps through the shipped menu, sheet, scope dialog and snackbar; the store is the oracle. */
@RunWith(AndroidJUnit4::class)
class ExerciseSwapJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bench = "19f5c6f170d8808bb424e98de4472a7e"
    private val row = "1a15c6f170d8802d969ae139f42062fa"

    private fun awaitText(text: String, substring: Boolean = false) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun awaitGone(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
    }

    private fun awaitTag(tag: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun selectedTabDescription(): String = compose.onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    ).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    /** Opens the swap sheet for the bench, picks the top suggestion and returns its snackbar title. */
    private fun swapBenchFor(choice: String): String {
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithContentDescription("Exercise options for Barbell Bench Press").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithContentDescription("Exercise options for Barbell Bench Press").performClick()
        compose.onNodeWithText("Swap exercise…").performClick()
        awaitText("Replacing Barbell Bench Press")
        awaitTag("swap-suggestion")
        compose.onAllNodesWithTag("swap-suggestion").onFirst().performClick()
        awaitText(choice)
        compose.onNodeWithText("“Upper”", substring = true).assertExists()
        compose.onNodeWithText(choice).performClick()
        awaitText("Swapped for", substring = true)
        return compose.onNodeWithText("Swapped for", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text.removePrefix("Swapped for ")
    }

    @Test fun swapJustTodayAndForTemplateThenUndoRestoresBoth() {
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
            }
            val routine = Routine(id = "swap-template", name = "Upper", exerciseIds = listOf(bench, row), prescriptions = listOf(
                TemplateExercise(bench, 3, 5, 8, 180), TemplateExercise(row, 3, 8, 12, 120),
            ))
            val workout = routine.toWorkout(System.currentTimeMillis()).copy(routineId = routine.id)
            store.save(AppData(onboarding = OnboardingState(completed = true), routines = listOf(routine), active = workout,
                preferences = Preferences(pounds = false, autoRest = false)))
            activity = ActivityScenario.launch(MainActivity::class.java)

            // Just today: the session changes, the template does not.
            val today = swapBenchFor("Just today")
            compose.runOnIdle {
                val saved = store.load()
                val active = saved.active!!
                assertEquals(listOf(routine), saved.routines)
                assertEquals(2, active.exercises.size)
                assertNotEquals(bench, active.exercises[0].exerciseId)
                assertEquals(row, active.exercises[1].exerciseId)
                assertEquals(active.exercises[0].id, active.selectedExerciseId)
                assertEquals(3, active.exercises[0].sets.size)
                assertTrue(active.exercises[0].sets.all { it.targetRepsMin == 5 && it.targetRepsMax == 8 && it.weightKg == null })
                assertEquals(180, active.exercises[0].restSeconds)
            }
            assertTrue(selectedTabDescription().startsWith("Select $today, 1 of 2"))
            compose.onNodeWithContentDescription("Exercise options for $today").assertExists()

            compose.onNodeWithText("Undo").performClick()
            awaitGone("Swapped for $today")
            compose.runOnIdle {
                assertEquals(workout, store.load().active)
                assertEquals(listOf(routine), store.load().routines)
            }
            assertTrue(selectedTabDescription().startsWith("Select Barbell Bench Press"))

            // Also update template: order and prescriptions stay, only the exercise changes.
            val forGood = swapBenchFor("Also update template")
            compose.runOnIdle {
                val saved = store.load()
                val swappedId = saved.active!!.exercises[0].exerciseId
                assertNotEquals(bench, swappedId)
                assertEquals(listOf(swappedId, row), saved.routines.single().exerciseIds)
                assertEquals(listOf(TemplateExercise(swappedId, 3, 5, 8, 180), TemplateExercise(row, 3, 8, 12, 120)),
                    saved.routines.single().prescriptions)
            }
            assertTrue(selectedTabDescription().startsWith("Select $forGood"))

            compose.onNodeWithText("Undo").performClick()
            awaitGone("Swapped for $forGood")
            compose.runOnIdle {
                assertEquals(workout, store.load().active)
                assertEquals(listOf(routine), store.load().routines)
            }
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
