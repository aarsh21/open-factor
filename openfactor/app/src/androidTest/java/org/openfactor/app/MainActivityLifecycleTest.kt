package org.openfactor.app

import android.Manifest
import android.content.Intent
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
class MainActivityLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun await(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun notificationOpensWorkoutOnceButRecreationKeepsTheCurrentDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val store = WorkoutStore(context)
        val before = store.load()
        val workout = Workout(name = "Notification continuity")
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(active = workout, onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(Intent(context, MainActivity::class.java).setAction(WorkoutNotifications.OPEN_WORKOUT))
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Minimize workout").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Minimize workout").performClick()
            compose.onNodeWithText("Settings").performClick()
            await("Profile")
            activity.recreate()
            await("Profile")
            compose.onNodeWithContentDescription("Minimize workout").assertDoesNotExist()

            // A genuinely new notification still opens the active session via onNewIntent.
            activity.onActivity {
                it.startActivity(Intent(it, MainActivity::class.java)
                    .setAction(WorkoutNotifications.OPEN_WORKOUT).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Minimize workout").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Add exercise").performClick()
            await("Add exercises")
            compose.onNode(hasSetTextAction()).performTextInput("bench")
            activity.recreate()
            await("Add exercises")
            compose.onNode(hasSetTextAction()).assertTextContains("bench")
            assertEquals(workout, store.load().active)
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
