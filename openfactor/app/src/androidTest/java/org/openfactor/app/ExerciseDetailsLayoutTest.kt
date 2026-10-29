package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseDetailsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allThreeTargetsRemainVisibleTogetherAtLargeFontAndBothFiguresHaveContext() {
        val legPress = Exercise("leg", "45° Leg Press", "Quads", "Machine", muscles = listOf("Glutes", "Quads", "Adductors"))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                OpenFactorTheme {
                    Box(Modifier.width(320.dp).height(700.dp)) { ExerciseAnatomy(legPress) }
                }
            }
        }
        listOf("Glutes", "Quads", "Adductors").forEach { muscle ->
            compose.onNodeWithContentDescription("$muscle, Primary target muscle").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Front target muscles: Glutes, Quads, Adductors").assertExists()
        compose.onNodeWithContentDescription("Back target muscles: Glutes, Quads, Adductors").assertExists()
        compose.onNodeWithContentDescription("Female anatomy figure").performClick().assertIsSelected()
        listOf("Glutes", "Quads", "Adductors").forEach { muscle ->
            compose.onNodeWithContentDescription("$muscle, Primary target muscle").assertIsDisplayed()
        }
    }

    @Test fun exerciseHistoryUsesFullWidthTableAndOpensTheCorrectWorkout() {
        val exercise = Exercise("leg", "45° Leg Press", "Quads", "Machine")
        val entry = SessionExercise(exerciseId = exercise.id, sets = listOf(
            LoggedSet(weightKg = 55.0, reps = 10, warmup = true, completed = true),
            LoggedSet(weightKg = 66.0, reps = 8, rir = 2, completed = true),
        ))
        val workout = Workout(id = "history", name = "Legs", exercises = listOf(entry))
        var opened: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                OpenFactorTheme {
                    Box(Modifier.width(320.dp).padding(horizontal = 18.dp)) {
                        ExerciseHistoryCard(workout, exercise, Preferences(), { opened = it })
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("exercise-history-history").getUnclippedBoundsInRoot()
        assertEquals(284f, (bounds.right - bounds.left).value, .5f)
        compose.onNodeWithContentDescription("45° Leg Press, Warm-up, 55 kg · 10 Reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("45° Leg Press, Set 1 · Standard, 66 kg · 8 Reps · 2 RIR").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open workout Legs").performClick()
        compose.runOnIdle { assertEquals("history", opened) }
    }
}
