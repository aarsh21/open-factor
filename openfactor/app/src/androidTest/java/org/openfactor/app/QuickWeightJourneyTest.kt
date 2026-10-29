package org.openfactor.app

import android.Manifest
import android.os.Build
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
class QuickWeightJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun plusWeighInSavesAndReturnsWithoutChangingActiveWorkout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val store = WorkoutStore(context)
        val before = store.load()
        val entry = SessionExercise(exerciseId = "19f5c6f170d8808bb424e98de4472a7e", sets = listOf(LoggedSet(weightKg = 47.5, reps = 8)))
        val workout = Workout(name = "Weigh-in continuity", exercises = listOf(entry), selectedExerciseId = entry.id)
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(active = workout, onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Minimize workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Minimize workout").performClick()
            compose.onNodeWithContentDescription("Quick actions").performClick()
            compose.onNodeWithText("Log Weight").performClick()
            compose.onNode(hasSetTextAction() and hasText("Weight")).performTextReplacement("72.4")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(5_000) { store.load().scaleWeights.size == 1 }
            assertEquals(72.4, store.load().scaleWeights.single().weightKg, .00001)
            assertEquals(workout, store.load().active)
            compose.onNodeWithText("Resume workout").assertExists()
            compose.onNodeWithContentDescription("Quick actions").performClick()
            compose.onNodeWithText("Log Weight").performClick()
            compose.onNodeWithContentDescription("Back").performClick()
            assertEquals(1, store.load().scaleWeights.size)
            assertEquals(workout, store.load().active)
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
