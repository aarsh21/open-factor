package org.openfactor.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
class WorkoutSummaryLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortAndLongExerciseCardsHaveEqualFullWidthAtLargeFontAndRepeatStillWorks() {
        val long = Exercise("long", "45° Incline Barbell Bench Press", "Chest", "Barbell")
        val short = Exercise("short", "Leg Press", "Quads", "Machine")
        val first = SessionExercise(exerciseId = long.id, sets = listOf(LoggedSet(weightKg = 66.0, reps = 99, completed = true)))
        val second = SessionExercise(exerciseId = short.id, sets = listOf(LoggedSet(weightKg = 55.0, reps = 99, warmup = true, completed = true)))
        val workout = Workout(name = "Upper and lower", startedAt = 1000L, finishedAt = 121000L, exercises = listOf(first, second))
        var repeated = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.35f)) {
                OpenFactorTheme {
                    Box(Modifier.width(320.dp).height(600.dp)) {
                        WorkoutSummary(workout, listOf(long, short), Preferences(), {}, { repeated = true }, {})
                    }
                }
            }
        }
        // SemanticsNode.boundsInRoot is clipped by the scrolling viewport (and may be Rect.Zero
        // for a prefetched/offscreen item). Width is a layout invariant, so measure the actual
        // card bounds, then separately require its data row to be reachable and displayed.
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasTestTag("summary-exercise-${first.id}"))
        val firstCard = compose.onNodeWithTag("summary-exercise-${first.id}").assertIsDisplayed().getUnclippedBoundsInRoot()
        list.performScrollToNode(hasTestTag("summary-exercise-${second.id}"))
        val secondCard = compose.onNodeWithTag("summary-exercise-${second.id}").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(firstCard.left.value, secondCard.left.value, .5f)
        assertEquals(firstCard.right.value, secondCard.right.value, .5f)
        assertEquals(284f, (firstCard.right - firstCard.left).value, .5f) //320dp viewport minus18dp on each side.
        assertEquals(284f, (secondCard.right - secondCard.left).value, .5f)
        compose.onNodeWithContentDescription("Leg Press, Warm-up, 55 kg · 99 Reps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Repeat workout").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(repeated) }
    }

    @Test fun splitMyoRowsKeepSideComponentStatusAndActualMetrics() {
        val exercise = Exercise("split", "Split press", "Chest", "Dumbbell")
        val root = LoggedSet(id = "root", weightKg = 20.0, reps = 10, completed = true, kind = SetKind.MYO, side = SetSide.LEFT)
        val right = root.copy(id = "right", pairId = root.id, side = SetSide.RIGHT, completed = false)
        val extra = LoggedSet(id = "extra", weightKg = 18.0, reps = 5, rir = 2, completed = true, kind = SetKind.MYO, side = SetSide.LEFT, segmentOf = root.id)
        val extraRight = extra.copy(id = "extra-right", pairId = extra.id, side = SetSide.RIGHT)
        val entry = SessionExercise(exerciseId = exercise.id, sets = listOf(root, right, extra, extraRight))
        val workout = Workout(name = "Myo", exercises = listOf(entry))
        compose.setContent { OpenFactorTheme { WorkoutSummary(workout, listOf(exercise), Preferences(), {}, {}, {}) } }
        compose.onNodeWithTag("summary-exercise-${entry.id}").performScrollTo()
        compose.onNodeWithContentDescription("Split Press, Set 1 · Myo, Left, 20 kg · 10 Reps").assertExists()
        compose.onNodeWithContentDescription("Split Press, Set 1 · Myo, Right, Not completed").assertExists()
        compose.onNodeWithContentDescription("Split Press, Set 1 · Myo, Myo 1 · Left, 18 kg · 5 Reps · 2 RIR").assertExists()
        compose.onNodeWithContentDescription("Split Press, Set 1 · Myo, Myo 1 · Right, 18 kg · 5 Reps · 2 RIR").assertExists()
    }
}
