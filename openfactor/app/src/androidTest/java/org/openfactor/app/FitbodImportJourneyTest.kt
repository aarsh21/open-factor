package org.openfactor.app

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FitbodImportJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun importEntryAndDocumentActionAreReachableAndBackDoesNotChangeHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Settings").onLast().performClick()
            compose.onNodeWithText("Restore Backup").performScrollTo()
            compose.onNodeWithText("Import Fitbod CSV").assertIsDisplayed().performClick()
            val action = compose.onNodeWithText("Choose Fitbod CSV").assertIsDisplayed().assertIsEnabled().getUnclippedBoundsInRoot()
            val navigation = compose.onNodeWithContentDescription("Quick actions").getUnclippedBoundsInRoot()
            assertTrue("Document action must clear floating navigation", action.bottom <= navigation.top)
            compose.onNodeWithContentDescription("Back").performClick()
            assertTrue(store.load().history.isEmpty())
            assertTrue(store.load().programs.isEmpty())
            assertNull(store.load().active)
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun confirmedImportAndDeduplicationSurviveDurableStoreReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val custom = Exercise("fitbod-native-test", "Synthetic import press", "Chest", "Barbell", custom = true)
            val active = Workout(name = "Keep active")
            store.save(AppData(active = active, customExercises = listOf(custom), onboarding = OnboardingState(completed = true)))
            val vm = WorkoutViewModel(context.applicationContext as Application)
            val original = vm.data.value
            val header = "date_local,date_utc,workout_name,exercise_name,set_number,reps,weight_kg,is_warmup,is_amrap,rest_seconds,set_duration_raw,distance_m,notes,workout_id,exercise_external_resource_id,exercise_position,circuit_position,set_id"
            val row = "2026-01-01T12:00:00Z,2026-01-01T12:00:00Z,Synthetic Fitbod workout,${custom.name},1,8,40,False,True,90,,,,native-test,press,0,,native-set"
            val preview = FitbodImport.read("$header\n$row".byteInputStream())
            assertEquals(1, vm.appendFitbodHistory(preview, mapOf(custom.name to custom.id), emptySet()))
            val saved = store.load()
            assertEquals(vm.data.value, saved)
            assertEquals(original, saved.copy(history = original.history))
            val workout = saved.history.single()
            assertEquals("fitbod-workout:native-test", workout.id)
            assertEquals(40.0, workout.exercises.single().sets.single().weightKg!!, 0.0)
            assertTrue(workout.exercises.single().notes.contains("AMRAP"))
            assertEquals(0, vm.appendFitbodHistory(preview, mapOf(custom.name to custom.id), emptySet()))
            assertEquals(saved, store.load())
        } finally { store.save(before) }
    }

    @Test fun unmatchedSourceIsPreservedAndCommittedWithItsHistoryInOneStoreSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            store.save(AppData(active = Workout(name = "Keep active"), onboarding = OnboardingState(completed = true)))
            val vm = WorkoutViewModel(context.applicationContext as Application)
            val original = vm.data.value
            val header = "date_local,date_utc,workout_name,exercise_name,set_number,reps,weight_kg,is_warmup,is_amrap,rest_seconds,set_duration_raw,distance_m,notes,workout_id,exercise_external_resource_id,exercise_position,circuit_position,set_id"
            val row = "2026-01-01T12:00:00Z,2026-01-01T12:00:00Z,Native preserved import,Native unmapped press,1,8,40,False,False,90,,,,native-preserved,press,0,,native-preserved-set"
            val source = FitbodImport.read("$header\n$row".byteInputStream())
            val plan = FitbodExerciseMatcher(vm.exercises()).plan(source)
            assertEquals(1, plan.newExercises.size)
            assertEquals(1, vm.appendFitbodHistory(source, plan.choices, emptySet(), plan.newExercises))
            val saved = store.load()
            assertEquals(original, saved.copy(history = original.history, customExercises = original.customExercises))
            assertEquals(plan.newExercises.single().id, saved.history.single().exercises.single().exerciseId)
            assertEquals(plan.newExercises, saved.customExercises)
            assertEquals(0, vm.appendFitbodHistory(source, plan.choices, emptySet(), plan.newExercises))
            assertEquals(saved, store.load())
        } finally { store.save(before) }
    }
}

class FitbodReviewLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeTextConfirmationStaysPinnedWithoutScrollingThroughAllSourceWorkouts() {
        var reviewed by mutableStateOf(false)
        var imported = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                OpenFactorTheme {
                    Column(Modifier.requiredSize(320.dp, 560.dp).testTag("fitbod-viewport")) {
                        PageHeading("Import Fitbod CSV", {})
                        SpreadsheetImportLayout(navigationBars = WindowInsets(bottom = 48.dp), ime = WindowInsets(0), content = {
                            items(290) { Text("Source workout ${it + 1}") }
                        }, actions = {
                            FitbodReviewAcknowledgement(reviewed, true) { reviewed = it }
                            Button({ imported++ }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = reviewed) { Text("Import 290 workouts") }
                            OutlinedButton({}, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel import") }
                        })
                    }
                }
            }
        }
        val acknowledgement = compose.onNodeWithText("I reviewed the exercise matches and limits.").assertIsDisplayed()
        val action = compose.onNodeWithText("Import 290 workouts").assertIsDisplayed().assertIsNotEnabled()
        val cancel = compose.onNodeWithText("Cancel import").assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("fitbod-viewport").getUnclippedBoundsInRoot()
        assertTrue(action.getUnclippedBoundsInRoot().bottom <= cancel.top)
        assertEquals((viewport.bottom - 156.dp).value, cancel.bottom.value, 0.5f)
        acknowledgement.performClick()
        action.assertIsEnabled().performClick()
        assertEquals(1, imported)
    }
}
