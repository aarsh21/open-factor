package org.openfactor.app

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the shipped activity with a frozen Compose clock, so "it animates" is measured as
 * positions and coexisting screens at a known moment rather than judged from a video.
 */
@RunWith(AndroidJUnit4::class)
class NavigationMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var store: WorkoutStore
    private lateinit var before: AppData
    private var activity: ActivityScenario<MainActivity>? = null
    private var animatorScale = "1"

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString().trim() }
    }

    @Before fun seed() {
        store = WorkoutStore(context)
        before = store.load()
        animatorScale = shell("settings get global animator_duration_scale").ifBlank { "1" }
        shell("settings put global animator_duration_scale 1")
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    }

    @After fun restore() {
        activity?.close()
        store.save(before)
        shell("settings put global animator_duration_scale ${if (animatorScale == "null") "1" else animatorScale}")
    }

    private fun launch(data: AppData) {
        store.save(data)
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Warmup").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    // Unique to Home: the empty History screen has its own Start workout action.
    private val HOME = "Browse splits"
    private fun exists(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun left(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.left
    private fun top(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.top

    /** Kept as evidence: frames pulled from the device after the run. */
    private fun frame(name: String) {
        val dir = java.io.File(context.getExternalFilesDir(null), "motion-proof").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun back(block: ComponentActivity.() -> Unit) = activity!!.onActivity { it.block() }

    @Test fun pushSlidesInFromTheRightAndPopRevealsFromTheLeft() {
        launch(AppData(onboarding = OnboardingState(completed = true)))
        val homeLeft = left(HOME)
        frame("01-home")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Workout history").performClick()
        // Early in the 220ms push: travel is a fraction of the width, so narrow screens move fewer pixels.
        compose.mainClock.advanceTimeBy(48)
        frame("02-push-mid")
        assertTrue("Both screens share the frame mid-push", exists(HOME) && exists("Workout History"))
        val headingMid = left("Workout History")
        compose.mainClock.advanceTimeBy(600)
        frame("03-history")
        assertFalse("Home leaves once the push settles", exists(HOME))
        val headingRest = left("Workout History")
        val minTravel = compose.onRoot().fetchSemanticsNode().size.width / 200f
        assertTrue("A push arrives from the right ($headingMid > $headingRest)", headingMid > headingRest + minTravel)
        assertFalse("The floating navigation sinks away under a pushed screen", exists("Dashboard"))

        back { onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(48)
        frame("04-pop-mid")
        assertTrue("Both screens share the frame mid-pop", exists(HOME) && exists("Workout History"))
        val homeMid = left(HOME)
        assertTrue("A pop reveals from the left ($homeMid < $homeLeft)", homeMid < homeLeft - minTravel)
        compose.mainClock.advanceTimeBy(600)
        frame("05-home-again")
        assertFalse(exists("Workout History"))
        assertEquals(homeLeft, left(HOME), .5f)
        assertTrue("The navigation returns with the root screen", exists("Dashboard"))
    }

    @Test fun predictiveBackScrubsWithTheGestureAndCancellingRestoresTheScreen() {
        launch(AppData(onboarding = OnboardingState(completed = true)))
        compose.onNodeWithContentDescription("Workout history").performClick()
        compose.waitForIdle()
        val restingLeft = left("Workout History")
        compose.mainClock.autoAdvance = false
        back { onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 1000f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        back { onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 1000f, .2f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        val atFifth = left("Workout History")
        back { onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(700f, 1000f, .5f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeBy(32)
        frame("06-predictive-back-half")
        val atHalf = left("Workout History")
        assertTrue("The gesture reveals Home beneath History", exists(HOME) && exists("Workout History"))
        assertTrue("History follows the finger ($restingLeft < $atFifth < $atHalf)", restingLeft < atFifth && atFifth < atHalf)
        val travel = compose.onRoot().fetchSemanticsNode().size.width / 12f
        assertEquals("Half a swipe moves History half its travel", travel / 2, atHalf - restingLeft, travel * .1f)
        // Holding still: nothing commits on its own while the finger is down.
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(atHalf, left("Workout History"), .5f)

        back { onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(800)
        frame("07-predictive-back-cancelled")
        assertFalse("Cancelling puts Home away again", exists(HOME))
        assertEquals("History settles back where it was", restingLeft, left("Workout History"), .5f)

        back {
            onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 1000f, 0f, BackEventCompat.EDGE_LEFT))
            onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(700f, 1000f, .6f, BackEventCompat.EDGE_LEFT))
        }
        compose.mainClock.advanceTimeBy(48)
        back { onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(800)
        frame("08-predictive-back-committed")
        assertFalse("Releasing commits the pop", exists("Workout History"))
        assertTrue(exists(HOME))
    }

    @Test fun tabsFadeThroughInPlaceWithoutSliding() {
        launch(AppData(onboarding = OnboardingState(completed = true)))
        val resting = left(HOME)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Settings").performClick()
        compose.mainClock.advanceTimeBy(48)
        frame("09-tab-fade-mid")
        assertTrue("The outgoing tab is still fading", exists(HOME))
        assertEquals("Peers never slide sideways", resting, left(HOME), .5f)
        compose.mainClock.advanceTimeBy(600)
        assertFalse(exists(HOME))
        assertTrue("Navigation stays put across tab switches", exists("Dashboard"))
    }

    @Test fun androidRemoveAnimationsSwapsScreensWithoutStackingThem() {
        shell("settings put global animator_duration_scale 0")
        launch(AppData(onboarding = OnboardingState(completed = true)))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Workout history").performClick()
        // One frame for the tap to commit state, one for the transition to take the target.
        compose.mainClock.advanceTimeBy(32)
        frame("10-reduced-motion-next-frame")
        assertFalse("No exit animation with Remove animations", exists(HOME))
        assertTrue(exists("Workout History"))
    }

    @Test fun addedSetSettlesInWhileExistingSetsStayStill() {
        val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
        val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
        val entry = SessionExercise(id = "motion-entry", exerciseId = bench.id,
            sets = listOf(LoggedSet(id = "motion-first", weightKg = 60.0, reps = 8)))
        launch(AppData(
            onboarding = OnboardingState(completed = true),
            preferences = Preferences(autoRest = false, rateEffortAfterExercise = false),
            active = Workout(name = "Motion test", exercises = listOf(entry), selectedExerciseId = entry.id),
        ))
        val first = "Set 1 options for Barbell Bench Press"
        val firstTop = top(first)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Add set").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(48)
        frame("11-set-arriving")
        val arriving = top("Set 2 options for Barbell Bench Press")
        compose.mainClock.advanceTimeBy(600)
        frame("12-set-settled")
        val settled = top("Set 2 options for Barbell Bench Press")
        assertTrue("The new set drops into place from just above ($arriving < $settled)", arriving < settled - 1)
        assertEquals("Existing sets never replay an entrance", firstTop, top(first), .5f)
    }

    @Test fun restControlsAppearInPlaceWithoutMovingTheSets() {
        val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
        val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
        val entry = SessionExercise(id = "rest-entry", exerciseId = bench.id,
            sets = listOf(LoggedSet(id = "rest-first", weightKg = 60.0, reps = 8), LoggedSet(id = "rest-second", weightKg = 60.0, reps = 8)))
        launch(AppData(
            onboarding = OnboardingState(completed = true),
            preferences = Preferences(autoRest = true, rateEffortAfterExercise = false),
            active = Workout(name = "Rest test", exercises = listOf(entry), selectedExerciseId = entry.id),
        ))
        val anchor = "Set 2 options for Barbell Bench Press"
        val before = top(anchor)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Complete set 1 for Barbell Bench Press").performClick()
        compose.mainClock.advanceTimeBy(64)
        frame("13-rest-opening")
        val during = top(anchor)
        compose.mainClock.advanceTimeBy(600)
        frame("14-rest-open")
        assertTrue("Rest controls are shown", exists("Skip") && exists("+30s"))
        // Completing a set happens every set: the next set must stay exactly under the thumb.
        assertEquals("Sets stay put while rest starts", before, during, .5f)
        assertEquals("Sets stay put once rest is running", before, top(anchor), .5f)
    }
}
