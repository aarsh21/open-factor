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
class BackupExportJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun completeExportDisclosesPrivateOriginalsBeforeTheDocumentPickerAndCancelChangesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Settings").performClick()
            // Expose the export row above the floating navigation, not just at the viewport edge.
            compose.onNodeWithText("Restore Backup").performScrollTo()
            compose.onRoot().performTouchInput { swipeUp(startY = height * .65f, endY = height * .4f) }
            val unchanged = store.load()
            compose.onNodeWithText("Export Backup").performClick()
            compose.onNodeWithText("Export complete backup?").assertIsDisplayed()
            compose.onNodeWithText("original progress photos", substring = true).assertIsDisplayed()
            compose.onNodeWithText("not encrypted", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Choose file").assertIsEnabled()
            activity.recreate()
            compose.onNodeWithText("Export complete backup?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Export complete backup?").assertDoesNotExist()
            compose.onNodeWithText("Export Backup").assertExists()
            assertEquals(unchanged, store.load())
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
