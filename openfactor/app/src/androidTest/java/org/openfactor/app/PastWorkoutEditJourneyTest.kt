package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PastWorkoutEditJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(text: String) = compose.waitUntil(15_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    @Test fun confirmedWarmupConversionClearsOnlyTheEffortErrorsForRemovedFields() {
        val store = WorkoutStore(context)
        val before = store.load()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", true) }
            val main = LoggedSet(id = "warmup-main", kind = SetKind.MYO, weightKg = 40.0, reps = 8, completed = true)
            val entry = SessionExercise(exerciseId = bench.id, sets = listOf(main,
                LoggedSet(id = "warmup-extra", kind = SetKind.MYO, segmentOf = main.id, weightKg = 40.0, reps = 3, completed = true),
            )).withSplitSides()
            val now = System.currentTimeMillis()
            val saved = Workout(name = "Effort validation", startedAt = now - 120000, finishedAt = now, exercises = listOf(entry))
            store.save(AppData(history = listOf(saved), pastWorkoutEdit = PastWorkoutEdit(saved), onboarding = OnboardingState(completed = true)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Edit Workout")
            val left = "Edit ${bench.title} Set 1 · Left · Myo"
            val right = "Edit ${bench.title} Set 1 · Right · Myo"
            compose.onNodeWithContentDescription("$left RIR").performScrollTo().performTextReplacement("bad")
            compose.onNodeWithContentDescription("$right RIR").performScrollTo().performTextReplacement("bad")
            compose.onNodeWithContentDescription("$left Weight").performScrollTo().performTextReplacement("bad")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            fun markWarmup() {
                compose.onNodeWithContentDescription("Options for ${bench.title} Set 1 · Left · Myo").performScrollTo().performClick()
                compose.onNodeWithText("Mark as warmup").performClick()
                await("Remove Myo components?")
            }
            markWarmup()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Save").assertIsNotEnabled()
            markWarmup()
            compose.onNodeWithText("Change set type").performClick()
            // Weight remains editable, so its invalid raw text must still block Save.
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Edit ${bench.title} Warm-up 1 · Left · Myo Weight")
                .performScrollTo().assertTextContains("bad").performTextReplacement("40")
            compose.onNodeWithText("Save").assertIsEnabled()
            assertEquals(saved, store.load().history.single())
            compose.onNodeWithText("Save").performClick()
            await("Workout Complete")
            assertEquals(entry.sets.take(2).map { it.copy(warmup = true) }, store.load().history.single().exercises.single().sets)
        } finally { scenario?.close(); store.save(before) }
    }

    @Test fun markingSavedMyoAsWarmupRequiresConfirmationBeforeRemovingActuals() {
        val store = WorkoutStore(context)
        val before = store.load()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
            }
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", true) }
            val main = LoggedSet(id = "myo-main", kind = SetKind.MYO, weightKg = 40.0, reps = 8, completed = true)
            val entry = SessionExercise(id = "myo-entry", exerciseId = bench.id, sets = listOf(
                main,
                LoggedSet(id = "myo-extra", kind = SetKind.MYO, segmentOf = main.id, weightKg = 40.0, reps = 3, completed = true),
            )).withSplitSides()
            val now = System.currentTimeMillis()
            val saved = Workout(id = "past-myo", name = "Saved Myo", startedAt = now - 120000, finishedAt = now, exercises = listOf(entry))
            val active = Workout(id = "separate-active", name = "Active stays unchanged")
            store.save(AppData(history = listOf(saved), active = active, pastWorkoutEdit = PastWorkoutEdit(saved),
                onboarding = OnboardingState(completed = true)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Edit Workout")
            val options = "Options for ${bench.title} Set 1 · Left · Myo"
            fun markWarmup() {
                compose.onNodeWithContentDescription(options).performScrollTo().performClick()
                compose.onNodeWithText("Mark as warmup").performClick()
                await("Remove Myo components?")
            }
            markWarmup()
            compose.runOnIdle {
                assertEquals(saved, store.load().pastWorkoutEdit!!.edited)
                assertEquals(saved, store.load().history.single())
            }
            compose.onNodeWithText("Cancel").performClick()
            compose.runOnIdle { assertEquals(entry.sets, store.load().pastWorkoutEdit!!.edited.exercises.single().sets) }
            markWarmup()
            compose.onNodeWithText("Change set type").performClick()
            compose.runOnIdle {
                val edited = store.load().pastWorkoutEdit!!.edited.exercises.single()
                assertEquals(entry.sets.take(2).map { it.copy(warmup = true) }, edited.sets)
                assertEquals(saved, store.load().history.single())
                assertEquals(active, store.load().active)
            }
            compose.onNodeWithText("Save").assertIsEnabled().performClick()
            await("Workout Complete")
            compose.runOnIdle {
                val data = store.load()
                assertEquals(saved.id, data.history.single().id)
                assertEquals(entry.sets.take(2).map { it.copy(warmup = true) }, data.history.single().exercises.single().sets)
                assertEquals(active, data.active)
            }
        } finally { scenario?.close(); store.save(before) }
    }

    @Test fun correctSavedValuesPersistDraftAndCancelWithoutTouchingActiveWorkout() {
        val store = WorkoutStore(context)
        val before = store.load()
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
            }
            val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
            val bench = catalog.first { it.name.equals("Barbell bench press", true) }
            val now = System.currentTimeMillis()
            val saved = Workout(id = "past-edit", name = "Saved upper", startedAt = now - 120000, finishedAt = now,
                exercises = listOf(SessionExercise(exerciseId = bench.id, sets = listOf(LoggedSet(weightKg = 40.0, reps = 8, completed = true)))))
            val active = Workout(id = "separate-active", name = "Active stays unchanged")
            store.save(AppData(history = listOf(saved), active = active, pastWorkoutEdit = PastWorkoutEdit(saved), onboarding = OnboardingState(completed = true)))
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await("Edit Workout")
            val weight = "Edit Barbell Bench Press Set 1 Weight"
            compose.onNodeWithContentDescription(weight).performScrollTo().performTextReplacement("wrong")
            compose.onNodeWithText("Save").assertIsNotEnabled()
            compose.onNodeWithContentDescription(weight).performTextReplacement("60")
            compose.runOnIdle {
                assertEquals(40.0, store.load().history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(active, store.load().active)
                assertEquals(60.0, store.load().pastWorkoutEdit!!.edited.exercises.single().sets.single().weightKg!!, .001)
            }
            scenario.recreate()
            await("Edit Workout")
            compose.onNodeWithContentDescription(weight).performScrollTo().assertTextContains("60")
            compose.onNodeWithText("Save").assertIsEnabled().performClick()
            await("Workout Complete")
            compose.runOnIdle {
                val data = store.load()
                assertEquals(60.0, data.history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(480.0, data.history.single().volumeKg(), .001)
                assertEquals(active, data.active)
                assertNull(data.pastWorkoutEdit)
            }
            compose.onNodeWithContentDescription("Edit workout").performClick()
            await("Edit Workout")
            compose.onNodeWithContentDescription(weight).performScrollTo().performTextReplacement("70")
            compose.onNodeWithContentDescription("Back").performClick()
            await("Discard changes?")
            compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
            await("Workout Complete")
            compose.runOnIdle {
                assertEquals(60.0, store.load().history.single().exercises.single().sets.single().weightKg!!, .001)
                assertEquals(active, store.load().active)
                assertNull(store.load().pastWorkoutEdit)
            }
        } finally { scenario?.close(); store.save(before) }
    }
}
