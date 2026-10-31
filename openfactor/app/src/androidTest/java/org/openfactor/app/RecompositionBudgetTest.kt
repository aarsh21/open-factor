package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Counts recompositions of the shipped activity through the debug-only [RecompositionTrace] hooks.
 * These prove which scopes a tick, a keystroke or a scroll invalidates; they are not frame timings.
 */
@RunWith(AndroidJUnit4::class)
class RecompositionBudgetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun count(scope: String, id: String = "") = RecompositionTrace.count(scope, id)

    private fun startTracing() {
        compose.waitForIdle()
        RecompositionTrace.reset()
        RecompositionTrace.enabled = true
    }

    private class Session(val store: WorkoutStore, val activity: ActivityScenario<MainActivity>, val sets: List<LoggedSet>)

    private fun withStore(seed: AppData, block: (WorkoutStore, () -> ActivityScenario<MainActivity>) -> Unit) {
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(seed)
            block(store) { ActivityScenario.launch(MainActivity::class.java).also { activity = it } }
        } finally {
            RecompositionTrace.enabled = false
            RecompositionTrace.reset()
            compose.mainClock.autoAdvance = true
            activity?.close()
            store.save(before)
            WorkoutNotifications.sync(context, before, forceRefresh = true)
        }
    }

    private fun withSession(block: (Session) -> Unit) {
        val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
        val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
        val sets = List(4) { LoggedSet(id = "budget-set-$it", weightKg = 60.0, reps = 8) }
        val entry = SessionExercise(id = "budget-entry", exerciseId = bench.id, sets = sets)
        val seed = AppData(
            onboarding = OnboardingState(completed = true),
            preferences = Preferences(autoRest = false, rateEffortAfterExercise = false, showWorkoutTimer = true),
            // A running rest countdown ticks alongside the elapsed timer.
            active = Workout(name = "Recomposition budget", exercises = listOf(entry), selectedExerciseId = entry.id,
                restUntil = System.currentTimeMillis() + 10 * 60_000),
        )
        withStore(seed) { store, launch ->
            val activity = launch()
            awaitText("Warmup")
            awaitText("Add set")
            block(Session(store, activity, sets))
        }
    }

    @Test fun workoutClockTicksRecomposeOnlyTheClock() = withSession { session ->
        startTracing()
        compose.mainClock.autoAdvance = false
        repeat(4) {
            // Effect delays follow the test clock; wall time must also move for a new clock value.
            Thread.sleep(1_000)
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.runOnIdle {
            assertTrue("The elapsed clock must keep ticking (${count("SessionClock")})", count("SessionClock") >= 2)
            assertEquals("A tick must not recompose the session body", 0, count("WorkoutSession"))
            session.sets.forEach { assertEquals("A tick must not recompose set row ${it.id}", 0, count("SetRow", it.id)) }
            assertEquals("A tick must not recompose the app shell", 0, count("OpenFactorApp"))
        }
    }

    @Test fun typingInOneSetRecomposesOnlyThatRowAndIsSavedWithoutBlocking() = withSession { session ->
        val reps = compose.onNodeWithContentDescription("Barbell Bench Press set 2 reps (Reps)").performScrollTo()
        startTracing()
        reps.performTextReplacement("9")
        compose.runOnIdle {
            assertTrue("The edited row recomposes", count("SetRow", session.sets[1].id) >= 1)
            assertTrue("The session receives the edit", count("WorkoutSession") >= 1)
            listOf(0, 2, 3).forEach { index ->
                assertEquals("Typing must not recompose unedited row $index", 0, count("SetRow", session.sets[index].id))
            }
            assertEquals("Typing must not recompose the app shell", 0, count("OpenFactorApp"))
        }
        compose.onNodeWithContentDescription("Barbell Bench Press set 2 reps (Reps)").assertTextContains("9")
        // Typed values are written off the main thread within the coalescing window.
        compose.waitUntil(5_000) { session.store.load().active!!.exercises.single().sets[1].reps == 9 }
    }

    @Test fun typedValuesAreDurableAsSoonAsTheActivityPauses() = withSession { session ->
        compose.onNodeWithContentDescription("Barbell Bench Press set 3 reps (Reps)").performScrollTo().performTextReplacement("11")
        compose.waitForIdle()
        session.activity.moveToState(Lifecycle.State.STARTED)
        assertEquals(11, session.store.load().active!!.exercises.single().sets[2].reps)
        session.activity.moveToState(Lifecycle.State.RESUMED)
        // Completion is never deferred: it is durable before the UI publishes it.
        compose.onNodeWithContentDescription("Complete set 3 for Barbell Bench Press").performClick()
        compose.runOnIdle { assertTrue(session.store.load().active!!.exercises.single().sets[2].completed) }
    }

    @Test fun scrollingATabDoesNotRecomposeTheShell() {
        // Scroll energy only accumulates while system animations are on.
        assumeTrue("Requires animator duration scale > 0", android.animation.ValueAnimator.areAnimatorsEnabled())
        withStore(AppData(onboarding = OnboardingState(completed = true))) { _, launch ->
            launch()
            awaitText("Workout")
            compose.onAllNodesWithText("Exercises").filter(isSelectable()).onFirst().performClick()
            awaitText("Search exercises")
            val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
            startTracing()
            repeat(3) { list.performTouchInput { swipeUp() } }
            compose.runOnIdle { assertEquals("Scrolling must not recompose the app shell", 0, count("OpenFactorApp")) }
        }
    }
}
