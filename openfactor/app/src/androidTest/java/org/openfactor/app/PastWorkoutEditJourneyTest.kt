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
class PastWorkoutEditJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    @Test fun correctSavedValuesPersistDraftAndCancelWithoutTouchingActiveWorkout() {
        val store = WorkoutStore(context)
        val before = store.load()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
            }
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", true) }
            val now = System.currentTimeMillis()
            val saved = Workout(id = "past-edit", name = "Saved upper", startedAt = now - 120000, finishedAt = now,
                exercises = listOf(SessionExercise(exerciseId = bench.id, sets = listOf(LoggedSet(weightKg = 40.0, reps = 8, completed = true)))))
            val active = Workout(id = "separate-active", name = "Active stays unchanged")
            store.save(AppData(history = listOf(saved), active = active, pastWorkoutEdit = PastWorkoutEdit(saved), onboarding = OnboardingState(completed = true)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Edit Workout")
            val weight = "Edit Barbell Bench Press Set 1 Weight"
            compose.onNodeWithContentDescription(weight).performScrollTo().performTextReplacement("wrong")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription(weight).performTextReplacement("60")
            compose.runOnIdle {
                assertEquals(40.0, store.load().history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(active, store.load().active)
                assertEquals(60.0, store.load().pastWorkoutEdit!!.edited.exercises.single().sets.single().weightKg!!, .001)
            }
            scenario.recreate()
            await("Edit Workout")
            compose.onNodeWithContentDescription(weight).performScrollTo().assertTextContains("60")
            compose.onNodeWithText("Save").assertIsEnabled().performClick()
            await("Workout Complete")
            compose.runOnIdle {
                val data = store.load()
                assertEquals(60.0, data.history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(480.0, data.history.single().volumeKg(), .001)
                assertEquals(active, data.active)
                assertNull(data.pastWorkoutEdit)
            }
            compose.onNodeWithContentDescription("Edit workout").performClick()
            await("Edit Workout")
            compose.onNodeWithContentDescription(weight).performScrollTo().performTextReplacement("70")
            compose.onNodeWithContentDescription("Back").performClick()
            await("Discard changes?")
            compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
            await("Workout Complete")
            compose.runOnIdle {
                assertEquals(60.0, store.load().history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(active, store.load().active)
                assertNull(store.load().pastWorkoutEdit)
            }
        } finally { scenario?.close(); store.save(before) }
    }
}
