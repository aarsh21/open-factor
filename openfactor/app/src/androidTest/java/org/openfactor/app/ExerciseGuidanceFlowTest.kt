package org.openfactor.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseGuidanceFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun originalGuideAndAnatomyKeepTheCurrentSetAndDoNotAutoplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pack = context.assets.open("media-pack.json").bufferedReader().use { JSONObject(it.readText()) }
        assumeTrue("Original-media guide requires the extracted static or full pack", pack.getString("mode") != "source")
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        }
        val store = WorkoutStore(context)
        val before = store.load()
        val entry = SessionExercise(exerciseId = "19f5c6f170d8808bb424e98de4472a7e", sets = listOf(LoggedSet(weightKg = 47.5, reps = 8)))
        val active = Workout(name = "Guidance continuity", exercises = listOf(entry), selectedExerciseId = entry.id)
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(active = active, onboarding = OnboardingState(completed = true), preferences = Preferences(showRir = false)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Guide").fetchSemanticsNodes().isNotEmpty() }
            val rxBefore = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            compose.onNodeWithText("Guide").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Play exercise video").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Muscles").performClick()
            compose.onNodeWithContentDescription("Female anatomy figure").performClick()
            compose.onNodeWithContentDescription("Female anatomy figure").assertIsSelected()
            compose.onAllNodesWithText("Chest").onFirst().assertExists()
            compose.onNodeWithContentDescription("Close Info").performClick()
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 weight (kg)").assertTextContains("47.5")
            compose.onNodeWithContentDescription("Barbell Bench Press set 1 reps (Reps)").assertTextContains("8")
            assertEquals(active, store.load().active)
            val rxAfter = TrafficStats.getUidRxBytes(android.os.Process.myUid())
            if (rxBefore >= 0 && rxAfter >= 0) assertEquals("Opening a guide must not stream a video", rxBefore, rxAfter)
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
