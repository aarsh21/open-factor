package org.openfactor.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DashboardCustomizationTest {
    @get:Rule val compose = createComposeRule()
    private val exercises = listOf(
        Exercise("bench", "Bench press", "Chest", "Barbell"),
        Exercise("squat", "Squat", "Quads", "Barbell"),
    )

    @Test fun selectingExerciseStaysDraftUntilSaveAndSurvivesRecreation() {
        var saved: DashboardPreferences? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { OpenFactorTheme { DashboardCustomization(AppData(), exercises, {}, { saved = it }) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Edit Exercises"))
        compose.onNodeWithContentDescription("Edit Exercises").performClick()
        compose.onNodeWithText("Search exercises").performTextInput("bench")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bench Press").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bench Press").performClick()
        assertNull(saved)
        compose.onNodeWithText("Done").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("bench"), saved!!.exerciseIds) }
    }

    @Test fun cancelDoesNotSaveAndWholeRowControlsWeeklyOverview() {
        var saved: DashboardPreferences? = null
        var closed = false
        compose.setContent { OpenFactorTheme { DashboardCustomization(AppData(), exercises, { closed = true }, { saved = it }) } }
        compose.onNodeWithText("Weekly overview").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(closed); assertNull(saved) }
    }

    @Test fun weeklyCardKeepsItsValuesAndHistoryActionAtLargeTypeInLightTheme() {
        var opened = false
        val summary = AppData().trainingWeek(exercises, today = LocalDate.of(2026, 10, 29))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.4f)) {
                OpenFactorTheme(AppTheme.LIGHT) { WeeklyWorkoutCard(summary, { opened = true }) }
            }
        }
        compose.onNodeWithText("This week", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("working sets", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("This week: 0 working sets, 0 sessions, 0 muscles. Open workout history").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
