package org.openfactor.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real bundled font rendering and enlarged-type evidence, using synthetic source-mode data. */
@RunWith(AndroidJUnit4::class)
class GeistTypographyJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString().trim() }
    }

    private fun awaitText(text: String) {
        try {
            compose.waitUntil(15_000) {
                runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
            }
        } catch (failure: Exception) {
            runCatching { shot("failed-wait"); println(compose.onRoot().printToString()) }
            throw failure
        }
    }

    private fun shot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("typography-shots")).apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun tab(label: String) = compose.onAllNodes(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        .onFirst().performClick()

    @Test fun bundledFontsRemainReadableAndLoggingWorksAtNormalAndEnlargedScales() {
        val store = WorkoutStore(context)
        val before = store.load()
        val originalScale = shell("settings get system font_scale").ifBlank { "1" }
        var activity: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", ignoreCase = true) }
            assertNotNull(context.resources.getFont(R.font.geist_sans))
            assertNotNull(context.resources.getFont(R.font.geist_serif_regular))
            for (scale in listOf(1f, 1.35f, 2f)) {
                activity?.close()
                shell("settings put system font_scale $scale")
                val entry = SessionExercise(exerciseId = bench.id, sets = listOf(LoggedSet(weightKg = 82.5, reps = 8)))
                store.save(AppData(
                    onboarding = OnboardingState(completed = true),
                    preferences = Preferences(showRir = false, autoRest = false, rateEffortAfterExercise = false),
                    active = Workout(name = "Typography workout", exercises = listOf(entry), selectedExerciseId = entry.id),
                ))
                val intent = android.content.Intent(context, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity = ActivityScenario.launch(intent)
                awaitText("Typography workout")
                val launched = activity
                compose.waitUntil(10_000) {
                    var actual = 0f
                    launched.onActivity { actual = it.resources.configuration.fontScale }
                    kotlin.math.abs(actual - scale) < .01f
                }
                compose.onNodeWithText("Finish").assertIsDisplayed()
                val fieldDescription = "Barbell Bench Press set 1 weight (kg)"
                compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .performScrollToNode(hasContentDescription(fieldDescription))
                val field = compose.onNodeWithContentDescription(fieldDescription)
                field.assertTextEquals("82.5")
                field.performClick()
                compose.onNodeWithTag("Workout keypad").assertIsDisplayed()
                field.performTextReplacement("85")
                field.assertTextEquals("85")
                compose.waitUntil(5_000) { store.load().active!!.exercises.single().sets.single().weightKg == 85.0 }
                activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                shot("geist-$scale-session")
                compose.onNodeWithContentDescription("Minimize workout").performClick()
                awaitText("Resume workout")
                shot("geist-$scale-home")
                tab("Settings")
                val title = compose.onNode(hasText("Settings") and !hasClickAction())
                title.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                shot("geist-$scale-settings")
                val layout = layouts.single()
                assertFalse("Settings title at $scale: size=${layout.size}, constraints=${layout.layoutInput.constraints}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}", layout.hasVisualOverflow)
            }
        } finally {
            activity?.close()
            store.save(before)
            shell("settings put system font_scale ${if (originalScale == "null") "1" else originalScale}")
        }
    }
}
