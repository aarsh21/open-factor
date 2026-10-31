package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Fitbod CSV importer was removed; spreadsheet import and backups remain. */
@RunWith(AndroidJUnit4::class)
class ImportOptionsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun settingsOffersSpreadsheetImportButNoFitbodImport() {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Import spreadsheet").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Import Fitbod CSV").assertDoesNotExist()
            compose.onAllNodes(hasText("Fitbod", substring = true)).assertCountEquals(0)
        } finally {
            activity?.close()
            store.save(before)
        }
    }
}
