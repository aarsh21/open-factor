package org.openfactor.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedLoggerStorageTest {
    @Test fun advancedSessionSurvivesReloadFinishAndBackupWithoutChangingUnrelatedData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val catalog = Exercise("advanced-test", "Advanced test", "Lats", "Dumbbell", custom = true)
            val first = SessionExercise(id = "a", exerciseId = catalog.id, sets = listOf(LoggedSet(id = "root", weightKg = 23.0, reps = 7)))
            val second = SessionExercise(id = "b", exerciseId = catalog.id, sets = listOf(LoggedSet(id = "second")))
            store.save(before.copy(active = Workout(name = "Advanced storage", selectedExerciseId = "a", exercises = listOf(first, second)),
                customExercises = before.customExercises + catalog))
            val vm = WorkoutViewModel(context.applicationContext as Application)
            vm.groupSuperset(setOf("a", "b"))
            vm.setKind("a", "root", SetKind.MYO)
            vm.splitSides("a")
            val rows = vm.data.value.active!!.exercises.first().sets
            vm.editSet("a", rows[1].id) { it.copy(weightKg = 19.0, reps = 5) }
            vm.toggleSetCompletion("a", "root")
            vm.toggleSetCompletion("a", rows[1].id)
            assertEquals("a", vm.data.value.active!!.selectedExerciseId)
            assertEquals(1, vm.data.value.active!!.completedSets())
            assertEquals(256.0, vm.data.value.active!!.volumeKg(), 0.0)
            assertEquals(vm.data.value, WorkoutViewModel(context.applicationContext as Application).data.value)
            assertEquals(vm.data.value, DataCodec.decode(vm.export()))
            vm.moveSessionExercise("b", -1)
            vm.selectExercise("a")
            vm.finish()
            val finished = vm.data.value.history.first()
            assertEquals(listOf("b", "a"), finished.exercises.map { it.id })
            assertEquals(4, finished.exercises.last().sets.size)
            assertFalse(finished.exercises.last().logicalSets().single().completed)
            assertEquals(before.history, vm.data.value.history.drop(1))
            assertEquals(before.programs, vm.data.value.programs)
            assertEquals(before.scaleWeights, vm.data.value.scaleWeights)
            assertEquals(before.bodyMeasurements, vm.data.value.bodyMeasurements)
            assertEquals(before.progressPhotos, vm.data.value.progressPhotos)
            assertEquals(vm.data.value, WorkoutStore(context).load())
            assertEquals(vm.data.value, DataCodec.decode(vm.export()))
        } finally {
            store.save(before)
        }
    }
}
