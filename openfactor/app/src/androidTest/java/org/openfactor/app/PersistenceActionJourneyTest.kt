package org.openfactor.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Mounted callbacks exercise the real command/saver path, not a fabricated failure StateFlow. */
class PersistenceActionJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After fun closeScope() { scope.cancel() }

    private fun verifyOnboardingFailure(stage: Int, action: String) {
        val initial = AppData(onboarding = OnboardingState(profileDraft = ProfileSetupDraft(stage = stage, name = "Taylor")))
        val failWrites = AtomicBoolean(true)
        val durable = AtomicReference(initial)
        val commands = WorkoutCommands(initial, scope, write = {
            if (failWrites.get()) throw IOException("Injected full storage")
            durable.set(it)
        })
        var ready = false
        compose.setContent {
            OpenFactorTheme {
                val data by commands.data.collectAsState()
                QuickStartScreen(data, commands, onReady = { ready = true })
            }
        }
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithText("Couldn't save setup. Free up storage and try again.").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(ready)
            assertEquals(initial, commands.data.value)
            assertEquals(initial, durable.get())
            failWrites.set(false)
        }
        compose.onNodeWithText(action).performClick()
        compose.onNodeWithText("Couldn't save setup. Free up storage and try again.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(commands.data.value, durable.get())
            if (action == "Continue") {
                assertFalse(ready)
                assertEquals(stage + 1, durable.get().onboarding.profileDraft!!.stage)
            } else {
                assertTrue(ready)
                assertTrue(durable.get().onboarding.completed || durable.get().onboarding.deferred)
            }
        }
    }

    @Test fun onboardingContinueExplainsRejectedSaveAndRetriesWithoutAdvancingEarly() = verifyOnboardingFailure(0, "Continue")

    @Test fun onboardingCompletionExplainsRejectedSaveAndRetriesBeforeContinuing() = verifyOnboardingFailure(2, "Start training")

    @Test fun onboardingSkipExplainsRejectedSaveAndRetriesBeforeLeaving() = verifyOnboardingFailure(0, "Skip for now")

    @Test fun failingClickKeepsAcceptedEditsAliveAndNoticeRetriesTheActualPendingSnapshot() {
        val initial = AppData(active = Workout(name = "Boundary fixture", exercises = listOf(
            SessionExercise(id = "entry", exerciseId = "row", sets = listOf(LoggedSet(id = "set"))),
        )))
        val failWrites = AtomicBoolean(true)
        val attempts = AtomicInteger()
        val durable = AtomicReference(initial)
        val commands = WorkoutCommands(initial, scope, write = { snapshot ->
            attempts.incrementAndGet()
            if (failWrites.get()) throw IOException("Injected full storage")
            durable.set(snapshot)
        })
        var continued = false
        compose.setContent {
            OpenFactorTheme {
                val data by commands.data.collectAsState()
                Column {
                    Button(onClick = {
                        runPersistenceAction {
                            // The typed edit is accepted before the synchronous setting is rejected.
                            commands.editSet("entry", "set") { it.copy(reps = 8) }
                            commands.settings { it.copy(pounds = true) }
                            continued = true
                        }
                    }) { Text("Edit then save setting") }
                    Text("Reps: ${data.active!!.exercises.single().sets.single().reps}")
                    PersistenceStatusNotice(
                        commands.persistence,
                        onRetry = { commands.retryPendingSaves() },
                        onDismiss = { commands.dismissPersistenceFailure() },
                    )
                }
            }
        }

        compose.onNodeWithText("Edit then save setting").performClick()
        compose.onNodeWithText("Reps: 8").assertIsDisplayed()
        compose.onNodeWithText("Your latest edits aren't saved. Free up storage and retry.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(continued)
            assertTrue(attempts.get() > 0)
            assertEquals(PersistenceState.Failed(true), commands.persistence.value)
            assertEquals(8, commands.data.value.active!!.exercises.single().sets.single().reps)
            assertFalse(commands.data.value.preferences.pounds)
            assertEquals(initial, durable.get())
            failWrites.set(false)
        }
        compose.onNodeWithText("Retry save").performClick()
        compose.onNodeWithText("Retry save").assertDoesNotExist()
        compose.onNodeWithText("Reps: 8").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(PersistenceState.Saved, commands.persistence.value)
            assertEquals(commands.data.value, durable.get())
            assertFalse(durable.get().preferences.pounds)
            assertFalse(continued)
        }
    }

    @Test fun noteFormShowsLocalFailureAndRetainsTextUntilTheRealSaveSucceeds() {
        val initial = AppData(active = Workout(name = "Note fixture", exercises = listOf(
            SessionExercise(id = "entry", exerciseId = "row"),
        )))
        val failWrites = AtomicBoolean(true)
        val commands = WorkoutCommands(initial, scope, write = {
            if (failWrites.get()) throw IOException("Injected full storage")
        })
        var shown by mutableStateOf(true)
        compose.setContent {
            OpenFactorTheme {
                if (shown) NoteDialog("Exercise notes", "", onDismiss = { shown = false }, onSave = { text ->
                    commands.exerciseNotes("entry", text)
                    shown = false
                    null
                })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Keep this draft")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Couldn't save the note. Free up storage and try again.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Keep this draft")
        compose.runOnIdle {
            assertTrue(shown)
            assertEquals(initial, commands.data.value)
            assertEquals(PersistenceState.Failed(false), commands.persistence.value)
            failWrites.set(false)
        }
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(shown)
            assertEquals("Keep this draft", commands.data.value.active!!.exercises.single().notes)
            assertEquals(PersistenceState.Saved, commands.persistence.value)
        }
    }

    @Test fun failedLibraryPickReleasesItsLatchSoTheSameActionCanBeRetried() {
        val exercise = Exercise("row", "Row fixture", "Upper Back", "Dumbbell")
        val initial = AppData(workoutDraft = WorkoutDraft())
        val failWrites = AtomicBoolean(true)
        val commands = WorkoutCommands(initial, scope, write = {
            if (failWrites.get()) throw IOException("Injected full storage")
        })
        var picks = 0
        var continued = false
        compose.setContent {
            OpenFactorTheme {
                ExerciseLibrary(listOf(exercise), emptySet(), {}, {}, onPick = { id ->
                    picks++
                    commands.addDraftExercise(id)
                    continued = true
                }, onCustom = {}, picking = true, allowCustom = false)
            }
        }
        compose.onNodeWithContentDescription("Add Row fixture").performClick()
        compose.runOnIdle {
            assertEquals(1, picks)
            assertFalse(continued)
            assertEquals(initial, commands.data.value)
            assertEquals(PersistenceState.Failed(false), commands.persistence.value)
            failWrites.set(false)
        }
        compose.onNodeWithContentDescription("Add Row fixture").performClick()
        compose.runOnIdle {
            assertEquals(2, picks)
            assertTrue(continued)
            assertEquals("row", commands.data.value.workoutDraft!!.exercises.single().exerciseId)
            assertEquals(PersistenceState.Saved, commands.persistence.value)
        }
    }

    @Test fun rejectedFavoriteKeepsScreenOpenAndOnlyExplicitDismissAcknowledgesFailure() {
        val initial = AppData()
        val attempts = AtomicInteger()
        val commands = WorkoutCommands(initial, scope, write = {
            attempts.incrementAndGet()
            throw IOException("Injected full storage")
        })
        var closed = false
        compose.setContent {
            OpenFactorTheme {
                Column {
                    Button(onClick = {
                        runPersistenceAction {
                            commands.favorite("row")
                            closed = true
                        }
                    }) { Text("Favorite exercise") }
                    PersistenceStatusNotice(
                        commands.persistence,
                        onRetry = { commands.retryPendingSaves() },
                        onDismiss = { commands.dismissPersistenceFailure() },
                    )
                }
            }
        }
        compose.onNodeWithText("Favorite exercise").performClick()
        compose.onNodeWithText("The last change couldn't be saved. Please try that action again.").assertIsDisplayed()
        compose.onNodeWithText("Retry save").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, attempts.get())
            assertEquals(initial, commands.data.value)
            assertFalse(closed)
            assertTrue(commands.retryPendingSaves())
            assertEquals(PersistenceState.Failed(false), commands.persistence.value)
        }
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Dismiss").assertDoesNotExist()
        compose.onNodeWithText("Favorite exercise").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(PersistenceState.Saved, commands.persistence.value)
            assertFalse(closed)
        }
    }
}
