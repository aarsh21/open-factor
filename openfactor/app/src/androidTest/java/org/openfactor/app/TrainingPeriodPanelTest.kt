package org.openfactor.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrainingPeriodPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun weekAndMonthSwitchRecordedTotalsAndNavigationStopsAtCurrentPeriod() {
        val today = LocalDate.of(2026, 10, 29)
        fun workout(id: String, date: LocalDate) = Workout(
            id = id,
            name = "Training",
            startedAt = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            exercises = listOf(SessionExercise(exerciseId = "bench", sets = listOf(LoggedSet(weightKg = 50.0, reps = 8, completed = true)))),
        )
        val data = AppData(history = listOf(workout("current", today), workout("earlier", today.withDayOfMonth(15))))
        val catalog = listOf(Exercise("bench", "Bench press", "Chest", "Barbell"))
        compose.setContent {
            var period by remember { mutableStateOf(TrainingPeriod.WEEK) }
            var offset by remember { mutableIntStateOf(0) }
            OpenFactorTheme {
                Box(Modifier.width(320.dp)) {
                    TrainingPeriodPanel(data.trainingPeriod(catalog, period, offset, today), period, offset,
                        onPeriod = { period = it; offset = 0 }, onOffset = { offset = it })
                }
            }
        }
        compose.onNodeWithContentDescription("1 Sets in selected training period").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next training week").assertIsNotEnabled()
        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("October 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 Sets in selected training period").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next training month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous training month").performClick()
        compose.onNodeWithText("September 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 Sets in selected training period").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next training month").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("2 Sets in selected training period").assertIsDisplayed()
    }
}
