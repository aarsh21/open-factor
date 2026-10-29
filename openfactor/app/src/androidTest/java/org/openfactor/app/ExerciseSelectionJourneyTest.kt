package org.openfactor.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseSelectionJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val flat = Exercise("flat", "Barbell bench press", "Chest", "Barbell")
    private val incline = Exercise("incline", "30° incline dumbbell press", "Chest", "Dumbbell",
        aliases = listOf("Moderate incline dumbbell bench press"))

    @Test fun templateSearchKeepsResultsAndSaveReachableInKeyboardSizedViewport() {
        var saved: Pair<String, List<String>>? = null
        compose.setContent {
            OpenFactorTheme {
                // Less space than an ordinary phone with its keyboard open. The previous fixed
                // name/header/footer exhausted this viewport and hid every exercise result.
                Box(Modifier.height(340.dp)) {
                    RoutineBuilder(listOf(flat, incline), {}) { name, ids -> saved = name to ids }
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Template name")).performTextInput("Upper A")
        compose.onNode(hasSetTextAction() and hasText("Search exercises")).performTextInput("bench press incline")
        compose.onNodeWithText(incline.title).assertIsDisplayed().performClick()
        compose.onNodeWithText(flat.title).assertDoesNotExist()
        compose.onNodeWithText("Save template").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit template name").performClick()
        compose.onNode(hasSetTextAction() and hasText("Upper A")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("bench press incline")).performClick()
        compose.onNodeWithText("Save template").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("Upper A" to listOf("incline"), saved) }
    }

    @Test fun horizontalSessionTabsSwitchInOneTapAndDoNotModifyEnteredSets() {
        val first = SessionExercise(exerciseId = flat.id, sets = listOf(LoggedSet(weightKg = 47.5, reps = 8)))
        val second = SessionExercise(exerciseId = incline.id, sets = listOf(LoggedSet(weightKg = 22.5, reps = 10)))
        val initial = Workout(name = "Upper", exercises = listOf(first, second), selectedExerciseId = first.id)
        var current by mutableStateOf(initial)
        var additions = 0
        compose.setContent {
            OpenFactorTheme {
                WorkoutExerciseSelector(current, listOf(flat, incline), current.exercises.first { it.id == current.selectedExerciseId },
                    onSelect = { current = current.copy(selectedExerciseId = it) }, onAdd = { additions++ })
            }
        }
        compose.onNodeWithText(incline.title).performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(second.id, current.selectedExerciseId)
            assertEquals(initial.exercises, current.exercises)
        }
        compose.onNodeWithText(flat.title).performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Add exercise").performClick()
        compose.runOnIdle { assertEquals(1, additions); assertEquals(initial, current) }
    }
}
