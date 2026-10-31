package org.openfactor.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        var saved: Routine? = null
        val equipment = EquipmentCatalog(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            OpenFactorTheme {
                // Less space than an ordinary phone with its keyboard open. The previous fixed
                // name/header/footer exhausted this viewport and hid every exercise result.
                // This height already represents the space remaining ABOVE the keyboard.
                // Consume the real test-host IME so the editor/picker cannot subtract it again.
                Box(Modifier.height(340.dp).consumeWindowInsets(WindowInsets.ime)) {
                    TemplateEditor(null, listOf(flat, incline), {}, { saved = it; null },
                        equipmentCatalog = equipment)
                }
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Template name")).performTextInput("Upper A")
        compose.onNodeWithContentDescription("Add exercises").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Resistance filter").assertIsDisplayed().performClick()
        compose.onNodeWithText("All Resistance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Resistance").performClick()
        compose.onNodeWithText("Filters").performScrollTo().performClick()
        compose.onNodeWithText("Muscle group").assertIsDisplayed().performClick()
        compose.onNodeWithText("Chest").performClick()
        compose.onNodeWithText("Filters").performScrollTo().performClick()
        // "Moderate" exists only in the alias; the tokens are deliberately reordered.
        compose.onNode(hasSetTextAction() and hasText("Search exercises")).performTextInput("bench moderate incline")
        compose.onNodeWithText(incline.title).assertIsDisplayed()
        compose.onNodeWithText(flat.title).assertDoesNotExist()
        compose.onNodeWithText("No matches with these filters").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add ${incline.name}").performClick()
        compose.onNodeWithContentDescription("Remove ${incline.name} from selection").assertExists()
        compose.onNodeWithText("Done (1)").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction() and hasText("Upper A")).performScrollTo().assertExists()
        compose.onNodeWithText("Save template").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("Upper A", saved?.name)
            assertEquals(listOf("incline"), saved?.exerciseIds)
        }
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
        // The strip identifies exercises by photo and progress ring; its tabs carry the names.
        compose.onNodeWithContentDescription("Select ${incline.title}, 2 of 2", substring = true).performClick().assertIsSelected()
        compose.runOnIdle {
            assertEquals(second.id, current.selectedExerciseId)
            assertEquals(initial.exercises, current.exercises)
        }
        compose.onNodeWithContentDescription("Select ${flat.title}, 1 of 2", substring = true).performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Add exercise").performClick()
        compose.runOnIdle { assertEquals(1, additions); assertEquals(initial, current) }
    }
}
