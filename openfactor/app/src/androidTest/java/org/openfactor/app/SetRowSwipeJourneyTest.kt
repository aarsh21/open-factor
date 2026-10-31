package org.openfactor.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
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

/** Swipes the shipped set rows; the store is the oracle, frozen-clock frames are kept as design evidence. */
@RunWith(AndroidJUnit4::class)
class SetRowSwipeJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var store: WorkoutStore
    private lateinit var before: AppData
    private var activity: ActivityScenario<MainActivity>? = null
    private var animatorScale = "1"
    private val name = "Barbell Bench Press"

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

    private fun launch(): Workout {
        val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
        val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
        val entry = SessionExercise(id = "swipe-entry", exerciseId = bench.id, sets = listOf(
            LoggedSet(id = "swipe-1", weightKg = 60.0, reps = 8),
            LoggedSet(id = "swipe-2", weightKg = 62.5, reps = 6, targetRepsMin = 5, targetRepsMax = 8),
            LoggedSet(id = "swipe-3"),
        ))
        val workout = Workout(name = "Swipe test", exercises = listOf(entry), selectedExerciseId = entry.id)
        store.save(AppData(
            onboarding = OnboardingState(completed = true),
            preferences = Preferences(showRir = false, autoRest = true, restSeconds = 90, rateEffortAfterExercise = false),
            active = workout,
        ))
        activity = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Warmup")
        compose.waitForIdle()
        return workout
    }

    private fun awaitText(text: String, substring: Boolean = false) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun awaitGone(text: String, substring: Boolean = false) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isEmpty() }.getOrDefault(false)
    }

    private fun sets() = store.load().active!!.exercises.single().sets
    private fun options(n: Int) = compose.onNodeWithContentDescription("Set $n options for $name")
    private fun weight(n: Int) = compose.onNodeWithContentDescription("$name set $n weight (kg)")
    private fun reps(n: Int) = compose.onNodeWithContentDescription("$name set $n reps (Reps)")
    private fun left(n: Int) = options(n).fetchSemanticsNode().boundsInRoot.left
    private fun top(n: Int) = options(n).fetchSemanticsNode().boundsInRoot.top
    private val rowWidth get() = compose.onNodeWithContentDescription("Complete set 3 for $name", substring = true)
        .fetchSemanticsNode().boundsInRoot.right - options(3).fetchSemanticsNode().boundsInRoot.left
    private val pxPerMs get() = compose.density.density

    /** Starts on the weight field, so swiping also proves fields do not swallow horizontal drags. */
    private fun drag(n: Int, dx: Float, speedDpPerMs: Float, release: Boolean = true) = weight(n).performTouchInput {
        val step = 8L
        val perStep = speedDpPerMs * pxPerMs * step * kotlin.math.sign(dx)
        down(center)
        var moved = 0f
        while (kotlin.math.abs(moved) < kotlin.math.abs(dx)) { moveBy(Offset(perStep, 0f), step); moved += perStep }
        if (release) up()
    }

    private fun frame(name: String) {
        val dir = java.io.File(context.getExternalFilesDir(null), "design-shots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun customAction(description: String, label: String) {
        val node = compose.onNodeWithContentDescription(description).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    private fun actions(description: String) = compose.onNodeWithContentDescription(description).fetchSemanticsNode()
        .config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    @Test fun swipeLogsRefusesDeletesAndUndoesThroughTheShippedRows() {
        val workout = launch()
        val seeded = workout.exercises.single().sets
        val rest = left(1)

        // A slow, short drag springs back and changes nothing (and opens no menu).
        drag(1, 40f * pxPerMs, .03f)
        compose.waitForIdle()
        assertEquals(rest, left(1), .5f)
        compose.onNodeWithText("Mark as Warm-Up").assertDoesNotExist()
        assertEquals(seeded, sets())
        assertEquals(0L, store.load().active!!.restUntil)

        // A flick right logs a valid set through the completion path: completed, rest started.
        drag(1, 70f * pxPerMs, 1.2f)
        compose.waitUntil(5_000) { sets().first().completed }
        assertTrue(store.load().active!!.restUntil > System.currentTimeMillis())
        compose.waitForIdle()
        assertEquals("The row returns to rest while it turns completed", rest, left(1), .5f)
        compose.onNodeWithContentDescription("Mark set incomplete 1 for $name").assertExists()

        // Swiping right on a logged set never unlogs it.
        drag(1, rowWidth * .7f, .4f)
        compose.waitForIdle()
        assertTrue(sets().first().completed)

        // An empty set refuses: the row rubber-bands home and nothing completes.
        drag(3, rowWidth * .7f, .4f)
        compose.waitForIdle()
        assertEquals(rest, left(3), .5f)
        assertFalse(sets()[2].completed)

        // A slow drag past 40% to the left deletes without a dialog and offers Undo.
        val beforeDelete = store.load().active!!
        drag(2, -rowWidth * .55f, .08f)
        awaitText("Set 2 removed")
        compose.onNodeWithText("Remove set?").assertDoesNotExist()
        assertEquals(listOf("swipe-1", "swipe-3"), sets().map { it.id })
        compose.onNodeWithText("Undo").performClick()
        awaitGone("Set 2 removed")
        compose.waitUntil(5_000) { store.load().active == beforeDelete }
        assertEquals(seeded[1], sets()[1])

        // Undo is withdrawn by the next edit, so it can never erase newer work.
        drag(2, -rowWidth * .3f, 1.2f)
        awaitText("Set 2 removed")
        reps(2).performTextReplacement("5")
        awaitGone("Set 2 removed")
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.waitUntil(5_000) { sets().map { it.id } == listOf("swipe-1", "swipe-3") && sets()[1].reps == 5 }
    }

    @Test fun swipeLogFromAFocusedRowAdvancesTheKeypadLikeLogSet() {
        launch()
        weight(1).performScrollTo().performClick()
        compose.onNodeWithTag("Workout keypad").assertIsDisplayed()
        drag(1, 70f * pxPerMs, 1.2f)
        compose.waitUntil(5_000) { sets().first().completed }
        compose.onNodeWithTag("Workout keypad").assertIsDisplayed()
        weight(2).assertIsFocused()
        // Deleting closes the keypad first, so the Undo snackbar never covers its keys.
        drag(2, -rowWidth * .3f, 1.2f)
        awaitText("Set 2 removed")
        compose.onNodeWithTag("Workout keypad").assertDoesNotExist()
    }

    @Test fun talkBackGetsLogUnlogAndDeleteAsCustomActions() {
        launch()
        val check3 = "Complete set 3 for $name"
        assertEquals("An empty set offers only delete", listOf("Delete set"), actions(check3))
        weight(3).performTextReplacement("50")
        reps(3).performTextReplacement("10")
        compose.waitUntil(5_000) { sets()[2].reps == 10 }
        assertEquals(listOf("Log set", "Delete set"), actions("$name set 3 weight (kg)"))
        assertEquals(listOf("Log set", "Delete set"), actions("Set 3 options for $name"))
        customAction(check3, "Log set")
        compose.waitUntil(5_000) { sets()[2].completed }
        assertTrue(store.load().active!!.restUntil > System.currentTimeMillis())
        customAction("Mark set incomplete 3 for $name", "Mark set incomplete")
        compose.waitUntil(5_000) { !sets()[2].completed }
        customAction(check3, "Delete set")
        awaitText("Set 3 removed")
        assertEquals(listOf("swipe-1", "swipe-2"), sets().map { it.id })
    }

    @Test fun removeAnimationsKeepsSwipesWithoutTravelAndCommitsInstantly() {
        shell("settings put global animator_duration_scale 0")
        launch()
        val rest = left(1)
        compose.mainClock.autoAdvance = false
        drag(1, rowWidth * .5f, .4f, release = false)
        compose.mainClock.advanceTimeBy(32)
        frame("swipe-reduced-01-log-armed")
        assertEquals("No travel with Remove animations", rest, left(1), .5f)
        weight(1).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { sets().first().completed }

        compose.mainClock.autoAdvance = false
        drag(2, -rowWidth * .5f, .4f)
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals("Delete is instant", listOf("swipe-1", "swipe-3"), sets().map { it.id }) }
        compose.mainClock.autoAdvance = true
        awaitText("Set 2 removed")
    }

    /** Frozen-clock frames of every swipe state, pulled from the device for review. */
    @Test fun swipeFrames() {
        launch()
        val width = rowWidth
        val rest = left(1)
        frame("swipe-01-rest")
        compose.mainClock.autoAdvance = false

        drag(1, width * .25f, .3f, release = false)
        compose.mainClock.advanceTimeBy(200)
        frame("swipe-02-log-approach")
        assertTrue("The row tracks the finger", left(1) > rest + width * .2f)
        weight(1).performTouchInput { moveBy(Offset(width * .3f, 0f), 120) }
        compose.mainClock.advanceTimeBy(200)
        frame("swipe-03-log-armed")
        val armed = left(1) - rest
        assertTrue("Resists past the commit point ($armed)", armed in width * .4f..width * .55f)
        weight(1).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        frame("swipe-04-log-returning")
        compose.mainClock.advanceTimeBy(800)
        frame("swipe-05-logged")
        assertEquals(rest, left(1), .5f)

        drag(3, width * .6f, .3f, release = false)
        compose.mainClock.advanceTimeBy(200)
        frame("swipe-06-refused-pull")
        assertTrue("A refused log only gives a short pull", left(3) - rest < 48 * pxPerMs)
        weight(3).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(800)

        val secondTop = top(2)
        val thirdTop = top(3)
        drag(2, -width * .25f, .3f, release = false)
        compose.mainClock.advanceTimeBy(200)
        frame("swipe-07-delete-approach")
        weight(2).performTouchInput { moveBy(Offset(-width * .3f, 0f), 120) }
        compose.mainClock.advanceTimeBy(200)
        frame("swipe-08-delete-armed")
        weight(2).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(48)
        frame("swipe-09-delete-leaving")
        var glided = false
        repeat(40) {
            compose.mainClock.advanceTimeBy(16)
            val now = runCatching { top(3) }.getOrNull() ?: return@repeat
            if (!glided && now < thirdTop - 4 && now > secondTop + 4) { glided = true; frame("swipe-10-delete-collapsing") }
        }
        assertTrue("The row below glides up as the space closes", glided)
        compose.mainClock.advanceTimeBy(400)
        frame("swipe-11-undo-offer")
        compose.mainClock.autoAdvance = true
        awaitText("Set 2 removed")
    }
}
