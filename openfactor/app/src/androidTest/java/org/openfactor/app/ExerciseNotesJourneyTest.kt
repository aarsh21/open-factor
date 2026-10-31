package org.openfactor.app

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

/** Template, pinned and last-time notes through the shipped activity and its public controls. */
@RunWith(AndroidJUnit4::class)
class ExerciseNotesJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val bench = "19f5c6f170d8808bb424e98de4472a7e"
    private val lastNote = "Elbows tucked, pause on chest"

    private fun exists(text: String, substring: Boolean = false) =
        runCatching { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun awaitText(text: String, substring: Boolean = false) = compose.waitUntil(15_000) { exists(text, substring) }
    private fun awaitGone(text: String, substring: Boolean = false) = compose.waitUntil(15_000) { !exists(text, substring) }

    private fun lazyNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun seeded(): AppData {
        val past = Workout(id = "notes-past", name = "Push", startedAt = System.currentTimeMillis() - 3 * 86_400_000L,
            finishedAt = System.currentTimeMillis() - 3 * 86_400_000L + 3_600_000L,
            exercises = listOf(SessionExercise(exerciseId = bench, notes = lastNote,
                sets = listOf(LoggedSet(weightKg = 60.0, reps = 8, completed = true)))))
        return AppData(onboarding = OnboardingState(completed = true), preferences = Preferences(pounds = false, autoRest = false),
            routines = listOf(Routine("notes-upper", "Upper A", listOf(bench), listOf(TemplateExercise(bench)))),
            history = listOf(past))
    }

    private fun grantNotifications() {
        // A permission dialog would cover the session controls on Android 13+.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
            ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        }
    }

    @Test fun templateNoteFollowsItsTemplateAndLastTimeNoteDismissesDurably() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            grantNotifications()
            store.save(seeded())
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Start workout")
            lazyNode(hasContentDescription("Manage Upper A")).performClick()
            compose.onNodeWithText("Edit template").performClick()
            awaitText("Template name")
            lazyNode(hasContentDescription("Edit targets for Barbell Bench Press")).performClick()
            lazyNode(hasSetTextAction() and hasText("Template note")).performTextReplacement("Go heavy on Upper A")
            compose.onNodeWithText("Save template").assertIsEnabled().performClick()
            awaitText("Start Upper A")
            compose.runOnIdle {
                assertEquals("Go heavy on Upper A", store.load().routines.single().prescriptions.single().note)
            }
            compose.onNodeWithText("Start Upper A").performScrollTo().performClick()
            awaitText("Warmup")
            compose.onNodeWithText("Template · Upper A").assertIsDisplayed()
            compose.onNodeWithText("Go heavy on Upper A").assertIsDisplayed()
            // The session has no note of its own yet, so last time's note shows as a labelled placeholder.
            compose.onNodeWithText("Last time ·", substring = true).assertIsDisplayed()
            compose.onNodeWithText(lastNote).assertIsDisplayed()
            compose.runOnIdle { assertEquals("notes-upper", store.load().active!!.routineId) }

            // Editing from the session writes back to the template.
            compose.onNodeWithContentDescription("Exercise options for Barbell Bench Press").performClick()
            compose.onNodeWithText("Template note").performClick()
            field("Template note").performTextReplacement("Go heavy, then back off")
            compose.onNodeWithText("Save").performClick()
            awaitGone("Saved to Upper A", substring = true)
            compose.onNodeWithText("Go heavy, then back off").assertIsDisplayed()
            compose.runOnIdle {
                val saved = store.load()
                assertEquals("Go heavy, then back off", saved.routines.single().prescriptions.single().note)
                assertEquals("", saved.active!!.exercises.single().notes)
            }
            activity.close()

            // An empty workout with the same exercise is not this template: no template note.
            val templated = store.load()
            store.save(templated.copy(active = Workout(name = "Empty", exercises = listOf(SessionExercise(exerciseId = bench)))))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            awaitText(lastNote)
            assertFalse(exists("Go heavy, then back off"))
            assertFalse(exists("Template · Upper A"))
            compose.onNodeWithText("Last time ·", substring = true).assertIsDisplayed()

            compose.onNodeWithContentDescription("Dismiss last time note").performClick()
            awaitGone(lastNote)
            compose.runOnIdle {
                val saved = store.load()
                assertTrue(saved.active!!.exercises.single().lastNoteDismissed)
                assertEquals(lastNote, saved.history.single().exercises.single().notes)
            }
            activity.recreate()
            awaitText("Warmup")
            compose.waitForIdle()
            assertFalse(exists(lastNote))
            assertFalse(exists("Last time ·", substring = true))
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun swipeDismissesOnAFlickButASlowShortDragSettlesBack() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            grantNotifications()
            store.save(seeded().copy(active = Workout(name = "Empty", exercises = listOf(SessionExercise(exerciseId = bench)))))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            awaitText(lastNote)
            val forward = if (compose.onRoot().fetchSemanticsNode().layoutInfo.layoutDirection ==
                androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
            // ~0.07 dp/ms over a fifth of the width: below both the flick and the 40% distance rule.
            compose.onNodeWithText(lastNote).performTouchInput {
                swipe(center, center + androidx.compose.ui.geometry.Offset(forward * width * .2f, 0f), 1_000)
            }
            compose.waitForIdle()
            compose.onNodeWithText(lastNote).assertIsDisplayed()
            compose.runOnIdle { assertFalse(store.load().active!!.exercises.single().lastNoteDismissed) }
            // A short, quick flick commits even though it travels less than the slow drag.
            compose.onNodeWithText(lastNote).performTouchInput {
                swipe(center, center + androidx.compose.ui.geometry.Offset(forward * width * .15f, 0f), 50)
            }
            awaitGone(lastNote)
            compose.runOnIdle { assertTrue(store.load().active!!.exercises.single().lastNoteDismissed) }
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun writingANoteReplacesTheLastTimePlaceholderWithoutCopyingIt() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            grantNotifications()
            store.save(seeded().copy(active = Workout(name = "Empty", exercises = listOf(SessionExercise(exerciseId = bench)))))
            activity = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Warmup")
            awaitText(lastNote)
            compose.onNodeWithText(lastNote).performClick()
            awaitText("Exercise notes")
            // The editor starts empty; last time's words are only a placeholder.
            val editor = field("Exercise notes")
            assertEquals("", editor.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            editor.performTextInput("Felt strong 🔥")
            compose.onNodeWithText("Save").performClick()
            awaitText("Felt strong 🔥")
            awaitGone(lastNote)
            compose.onNodeWithText("This workout").assertIsDisplayed()
            assertFalse(exists("Last time ·", substring = true))
            compose.runOnIdle {
                val saved = store.load()
                assertEquals("Felt strong 🔥", saved.active!!.exercises.single().notes)
                assertFalse(saved.active!!.exercises.single().lastNoteDismissed)
                assertEquals(lastNote, saved.history.single().exercises.single().notes)
            }
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
