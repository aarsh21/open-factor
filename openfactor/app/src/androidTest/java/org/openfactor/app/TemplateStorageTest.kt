package org.openfactor.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemplateStorageTest {
    @Test fun importedTemplatesEditReloadAndLaunchWithBlankActualsWithoutOverwritingActiveWorkout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        val app = context.applicationContext as Application
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true),
                history = listOf(Workout(id = "existing-history", name = "Previous", finishedAt = 1))))
            val vm = WorkoutViewModel(app)
            assertNull(vm.installStarterSplit("upper-lower"))
            val imported = vm.data.value
            assertEquals(4, imported.routines.size)
            val routine = imported.routines.first()
            val edited = routine.copy(name = "My upper", prescriptions = routine.prescriptions.mapIndexed { index, p ->
                if (index == 0) p.copy(sets = 2, repsMin = 5, repsMax = 8, restSeconds = 150) else p
            })
            assertNull(vm.saveRoutine(edited))
            val loaded = WorkoutViewModel(app)
            assertEquals(edited, loaded.data.value.routines.first())
            assertEquals(imported.templateSplits, loaded.data.value.templateSplits)
            assertNull(loaded.startRoutine(edited.id))
            val active = loaded.data.value.active!!
            assertEquals("My upper", active.name)
            assertEquals(2, active.exercises.first().sets.size)
            assertEquals(150, active.exercises.first().restSeconds)
            assertTrue(active.exercises.flatMap { it.sets }.all { it.weightKg == null && it.reps == null && !it.completed })
            assertEquals(5, active.exercises.first().sets.first().targetRepsMin)
            assertNotNull(loaded.startRoutine(edited.id))
            assertEquals(active, loaded.data.value.active)
            assertNotNull(loaded.installStarterSplit("missing-preset"))
            assertEquals(4, loaded.data.value.routines.size)
            assertEquals("existing-history", loaded.data.value.history.single().id)
            assertEquals(loaded.data.value, DataCodec.decode(loaded.export()))
            loaded.deleteRoutine(edited.id)
            assertEquals(3, loaded.data.value.routines.size)
            assertTrue(loaded.data.value.templateSplits.isEmpty())
            assertEquals(active, loaded.data.value.active)
            assertEquals(loaded.data.value, store.load())
        } finally { store.save(before) }
    }
}
