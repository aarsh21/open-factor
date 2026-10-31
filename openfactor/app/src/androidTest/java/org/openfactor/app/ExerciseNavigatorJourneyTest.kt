package org.openfactor.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Drives the session's photo/progress navigator through the shipped activity; the store is the oracle. */
@RunWith(AndroidJUnit4::class)
class ExerciseNavigatorJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var store: WorkoutStore
    private lateinit var before: AppData
    private var activity: ActivityScenario<MainActivity>? = null
    private val catalog by lazy {
        context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }.associateBy { it.id }
    }
    private val ids = listOf(
        "19f5c6f170d8808bb424e98de4472a7e", // Barbell bench press
        "1a15c6f170d8802d969ae139f42062fa", // Chest-supported dumbbell row (elbows-in)
        "30d5c6f170d880e7a00ee1e7319b720d", // Dual handle cable lat pulldown  ┐ superset
        "1a15c6f170d88067875bfd4347b883ef", // Dual cable lateral raise        ┘
        "1a15c6f170d880479d38c49a76cb6b10", // Cable rope triceps pushdown
        "1a15c6f170d88014a710f8ada9a1e94a", // Cable face pull
        "1a35c6f170d880bebc64f4dce0d5f5f1", // Barbell Romanian deadlift
    )
    private fun title(index: Int) = catalog.getValue(ids[index]).title

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString().trim() }
    }

    @Before fun seed() {
        store = WorkoutStore(context)
        before = store.load()
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
    }

    @After fun restore() {
        activity?.close()
        store.save(before)
    }

    private fun sets(done: Int, total: Int, warmup: Boolean = false) =
        (if (warmup) listOf(LoggedSet(weightKg = 20.0, reps = 10, warmup = true, completed = done > 0)) else emptyList()) +
            List(total) { LoggedSet(weightKg = 40.0, reps = 10, completed = it < done) }

    private fun workout(selected: Int = 1): Workout {
        val entries = listOf(
            SessionExercise(id = "nav-0", exerciseId = ids[0], sets = sets(3, 3, warmup = true)),
            SessionExercise(id = "nav-1", exerciseId = ids[1], sets = sets(1, 3)),
            SessionExercise(id = "nav-2", exerciseId = ids[2], sets = sets(0, 3), supersetId = "nav-ss"),
            SessionExercise(id = "nav-3", exerciseId = ids[3], sets = sets(0, 3), supersetId = "nav-ss"),
            SessionExercise(id = "nav-4", exerciseId = ids[4], sets = sets(0, 3)),
            SessionExercise(id = "nav-5", exerciseId = ids[5], sets = sets(0, 2)),
            SessionExercise(id = "nav-6", exerciseId = ids[6], sets = sets(0, 4)),
        )
        return Workout(name = "Pull & Press", exercises = entries, selectedExerciseId = entries[selected].id)
    }

    private fun launch(active: Workout, theme: AppTheme = AppTheme.DARK) {
        store.save(AppData(
            onboarding = OnboardingState(completed = true),
            preferences = Preferences(autoRest = false, rateEffortAfterExercise = false, exerciseAutoNext = false, theme = theme),
            active = active,
        ))
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText("Warmup").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    private val selectedTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    private fun selectedTabDescription(): String =
        compose.onNode(selectedTab).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    /** Scrolls the strip to the tab first: narrow screens (CI's 320dp emulator) show fewer at once. */
    private fun tab(index: Int, of: Int = 7): SemanticsNodeInteraction {
        val matcher = hasContentDescription("Select ${title(index)}, ${index + 1} of $of,", substring = true)
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)))
            .performScrollToNode(matcher)
        return compose.onNode(matcher)
    }
    private fun awaitSelected(id: String) = compose.waitUntil(5_000) { store.load().active?.selectedExerciseId == id }

    private fun shot(name: String) {
        val dir = java.io.File(context.getExternalFilesDir("design-shots"), "").apply { mkdirs() }
        val scale = context.resources.configuration.fontScale
        java.io.File(dir, "$name-font${"%.2f".format(java.util.Locale.US, scale)}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun tabsSwitchShowProgressGroupSupersetsAndOfferTheNextExercise() {
        launch(workout(selected = 1))
        // Each item is a tab named with title, position and working-set progress (warm-ups excluded).
        assertTrue(selectedTabDescription(),
            selectedTabDescription().startsWith("Select ${title(1)}, 2 of 7, 1 of 3 working sets complete"))
        // Every exercise has a tab (reached by scrolling where the screen is narrow).
        (0 until 7).forEach { tab(it).assertExists() }
        tab(0).assertExists()
        compose.onNodeWithContentDescription("Select ${title(0)}, 1 of 7, 3 of 3 working sets complete").assertExists()
        // The superset is visible on both members, and only there.
        tab(2).assertContentDescriptionContains("superset with ${title(3)}", substring = true)
        tab(3).assertContentDescriptionContains("superset with ${title(2)}", substring = true)
        assertFalse(tab(4)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains("superset"))
        // An unfinished exercise offers no Next shortcut.
        compose.onAllNodesWithContentDescription("Next exercise", substring = true).assertCountEquals(0)

        // One tap switches; the strip never names the exercise, the header does.
        tab(5).performClick()
        awaitSelected("nav-5")
        tab(5).assertIsSelected()
        compose.onNodeWithContentDescription("Exercise options for ${title(5)}").assertExists()

        // Completing sets fills the ring and the tab's progress follows.
        compose.onNodeWithContentDescription("Complete set 1 for ${title(5)}").performScrollTo().performClick()
        compose.waitUntil(5_000) { selectedTabDescription().contains("1 of 2 working sets complete") }
        compose.onNodeWithContentDescription("Complete set 2 for ${title(5)}").performScrollTo().performClick()
        compose.waitUntil(5_000) { selectedTabDescription().contains("2 of 2 working sets complete") }

        // Done: the next unfinished exercise is one tap away, through the same selection path.
        val next = compose.onNodeWithContentDescription("Next exercise, ${title(6)}", substring = true)
        next.performScrollTo().assertIsDisplayed().performClick()
        awaitSelected("nav-6")
        assertTrue(selectedTabDescription().startsWith("Select ${title(6)}, 7 of 7, 0 of 4 working sets complete"))
        // Sets were untouched by navigating.
        compose.runOnIdle {
            val saved = store.load().active!!
            assertEquals(2, saved.exercises.first { it.id == "nav-5" }.sets.count { it.completed })
            assertEquals(4, saved.exercises.first { it.id == "nav-0" }.sets.count { it.completed })
        }

        // A finished exercise revisited from the strip points to the first unfinished one after it.
        tab(0).performScrollTo().performClick()
        awaitSelected("nav-0")
        compose.onNodeWithContentDescription("Next exercise, ${title(1)}", substring = true).performScrollTo().performClick()
        awaitSelected("nav-1")
        tab(1).assertIsSelected()
    }

    @Test fun longWorkoutsScrollAndRevealTheCurrentExercise() {
        val base = workout().exercises
        val entries = List(21) { i -> base[i % base.size].copy(id = "long-$i", supersetId = null) }
        launch(Workout(name = "Long", exercises = entries, selectedExerciseId = "long-17"))
        val description = selectedTabDescription()
        assertTrue(description, description.startsWith("Select ${title(17 % 7)}, 18 of 21,"))
        compose.onNode(selectedTab).assertIsDisplayed()
        shot("04-long-workout")
    }

    /** Design evidence only: realistic states saved for review at the current font scale. */
    @Test fun designShots() {
        launch(workout(selected = 1))
        shot("01-in-progress")
        tab(0).performClick()
        awaitSelected("nav-0")
        compose.onNodeWithContentDescription("Next exercise", substring = true).performScrollTo()
        compose.waitForIdle()
        shot("02-done-next")
        tab(2).performClick()
        awaitSelected("nav-2")
        compose.waitForIdle()
        shot("03-superset")
        // Logging a superset set: the ring segment sweeps in while the indicator follows the auto-advance.
        // Scroll before freezing the clock: scrolling animates, so a frozen clock never lets it settle.
        val complete = compose.onNodeWithContentDescription("Complete set 1 for ${title(2)}").performScrollTo()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        complete.performClick()
        compose.mainClock.advanceTimeBy(64)
        shot("06-log-set-mid-motion")
        compose.mainClock.advanceTimeBy(400)
        shot("07-log-set-settled")
        compose.mainClock.autoAdvance = true
        activity?.close()
        launch(workout(selected = 1), AppTheme.LIGHT)
        shot("05-light")
    }
}
