package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickPhotoJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun plusPhotosShowsPoseChoicesBeforeLaunchingAnyCamera() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithContentDescription("Quick actions").performClick()
            compose.onNodeWithText("Log Photos").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Add Side photo").fetchSemanticsNodes().isNotEmpty() }
            listOf("Front", "Side", "Back").forEach { pose ->
                compose.onNodeWithContentDescription("Add $pose photo").assertIsDisplayed()
                compose.onNodeWithContentDescription("Take $pose photo").assertIsDisplayed()
            }
            compose.onNode(hasSetTextAction()).assertIsNotFocused()
            assertEquals(Lifecycle.State.RESUMED, activity.state)
            assertTrue(store.load().progressPhotos.isEmpty())
            compose.onNodeWithContentDescription("Close photos").performClick()
            assertTrue(store.load().progressPhotos.isEmpty())
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
