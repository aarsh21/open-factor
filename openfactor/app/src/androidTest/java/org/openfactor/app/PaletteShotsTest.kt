package org.openfactor.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Design evidence: every palette in dark and light over realistic training data, saved to
 * `files/design-shots` for review. It also proves the Settings picker applies a palette at once.
 */
@RunWith(AndroidJUnit4::class)
class PaletteShotsTest {
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

    private val catalog by lazy { context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) } }
    private fun exercise(vararg words: String) =
        catalog.first { e -> words.all { e.name.contains(it, ignoreCase = true) } }

    private fun preferences(palette: AppPalette, dark: Boolean) = Preferences(
        palette = palette, theme = if (dark) AppTheme.DARK else AppTheme.LIGHT,
        autoRest = false, rateEffortAfterExercise = false, weeklyWorkoutGoal = 4,
    )

    private fun history(): List<Workout> {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val bench = exercise("barbell bench press")
        val squat = exercise("barbell", "squat")
        return (0 until minOf(3, today.dayOfWeek.value)).map { day ->
            val start = monday.plusDays(day.toLong()).atTime(7, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Workout(name = if (day % 2 == 0) "Upper" else "Lower", startedAt = start, finishedAt = start + 3_600_000,
                exercises = listOf(
                    SessionExercise(exerciseId = bench.id, sets = List(3) { LoggedSet(weightKg = 80.0, reps = 8, completed = true) }),
                    SessionExercise(exerciseId = squat.id, sets = List(4) { LoggedSet(weightKg = 110.0, reps = 5, completed = true) }),
                ))
        }
    }

    private fun active(): Workout {
        val bench = exercise("barbell bench press")
        val row = exercise("barbell", "row")
        val pressing = SessionExercise(id = "shots-bench", exerciseId = bench.id, sets = listOf(
            LoggedSet(weightKg = 40.0, reps = 10, warmup = true, completed = true),
            LoggedSet(weightKg = 82.5, reps = 8, completed = true),
            LoggedSet(weightKg = 82.5, reps = 7, completed = true),
            LoggedSet(weightKg = 82.5, reps = 6),
            LoggedSet(),
        ))
        val pulling = SessionExercise(id = "shots-row", exerciseId = row.id,
            sets = List(3) { LoggedSet(weightKg = 70.0, reps = 10, completed = true) })
        return Workout(name = "Upper", startedAt = System.currentTimeMillis() - 1_860_000,
            exercises = listOf(pulling, pressing), selectedExerciseId = pressing.id)
    }

    private fun launch(data: AppData, ready: String) {
        activity?.close()
        store.save(data)
        activity = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { runCatching { compose.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        settle()
    }

    /** Lets exercise photos decode; their loading is not tracked by the Compose idling resource. */
    private fun settle() {
        compose.waitForIdle()
        Thread.sleep(700)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val dir = java.io.File(context.getExternalFilesDir("design-shots"), "").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun tab(label: String) {
        compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab))
            .onFirst().performClick()
        settle()
    }

    /** Scrolled only to the viewport edge, a low row sits under the floating navigation, whose plus takes a tap. */
    private fun openAppearanceRow() {
        compose.onNodeWithText("Appearance").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test fun everyPaletteInDarkAndLight() {
        val history = history()
        AppPalette.entries.forEach { palette ->
            listOf(true, false).forEach { dark ->
                val prefix = "${palette.name.lowercase()}-${if (dark) "dark" else "light"}"
                val base = AppData(onboarding = OnboardingState(completed = true), preferences = preferences(palette, dark), history = history)
                launch(base.copy(active = active()), "Warmup")
                shot("$prefix-1-session")
                launch(base, "Start workout")
                shot("$prefix-2-home")
                tab("Dashboard")
                shot("$prefix-3-dashboard")
                tab("Settings")
                shot("$prefix-4-settings")
                openAppearanceRow()
                settle()
                shot("$prefix-5-appearance")
            }
        }
    }

    private fun openAppearance(palette: AppPalette) {
        launch(AppData(onboarding = OnboardingState(completed = true), preferences = preferences(palette, true)), "Start workout")
        tab("Settings")
        openAppearanceRow()
        settle()
    }

    /** A pixel of page background beside the content. */
    private fun page() = compose.onRoot().captureToImage().toPixelMap().let { it[4, it.height / 2] }

    /** The navigation plus button's fill, beside its glyph: white in Charcoal, amber in Ember. */
    private fun plus() = compose.onNodeWithContentDescription("Quick actions").captureToImage().toPixelMap().let { it[it.width * 3 / 20, it.height / 2] }

    @Test fun pickingASwatchAppliesAndPersistsThePaletteWithAShortCrossFade() {
        openAppearance(AppPalette.CHARCOAL)
        val from = AppPalette.CHARCOAL.colors(dark = true).navigationAction
        val to = AppPalette.EMBER.colors(dark = true).navigationAction
        assertEquals(from, plus())
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Ember").performClick()
        compose.mainClock.advanceTimeBy(48)
        shot("crossfade-mid")
        val mid = plus()
        assertTrue("Mid cross-fade the plus is between palettes: $mid", mid != from && mid != to)
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Settled, the plus is exactly Ember", to, plus())
        assertEquals("Settled, the page is exactly Ember", AppPalette.EMBER.colors(dark = true).background, page())
        assertEquals(AppPalette.EMBER, store.load().preferences.palette)
        compose.onNodeWithText(AppPalette.EMBER.intent).assertExists()
        compose.onNode(hasText("Ember") and isSelected()).assertExists()
    }

    @Test fun withRemoveAnimationsThePaletteSwapsOnTheNextFrame() {
        shell("settings put global animator_duration_scale 0")
        openAppearance(AppPalette.CHARCOAL)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Midnight").performClick()
        // One frame for the tap to commit the preference, one to recompose with it.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(AppPalette.MIDNIGHT.colors(dark = true).background, page())
        assertEquals(AppPalette.MIDNIGHT.colors(dark = true).navigationAction, plus())
        compose.mainClock.autoAdvance = true
    }
}
