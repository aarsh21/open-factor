package org.openfactor.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageRecoveryTest {
    private fun withStore(test: (Context, String, WorkoutStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "storage-recovery-test-${newId()}"
        try { test(context, name, WorkoutStore(context, name)) }
        finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("${name}_recovery")
        }
    }

    private fun originals(store: WorkoutStore): List<String> {
        val bytes = ByteArrayOutputStream().also(store::exportRecovery).toByteArray()
        return buildList {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (zip.nextEntry != null) add(zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    @Test fun unreadablePrimaryFallsBackToCheckpointAndOriginalSurvivesNextSave() = withStore { context, name, store ->
        val known = AppData(active = Workout(name = "Keep these sets", exercises = listOf(
            SessionExercise(exerciseId = "bench", sets = listOf(LoggedSet(weightKg = 52.5, reps = 8, completed = true))),
        )), gyms = listOf(GymProfile(name = "Known gym")))
        store.save(known)
        assertEquals(known, WorkoutStore(context, name).load()) // Establish startup checkpoint.
        val damaged = "{\"active\":\"unfinished JSON — keep original"
        assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("data", damaged).commit())
        val recovered = WorkoutStore(context, name)
        assertEquals(known, recovered.load())
        assertEquals(StorageRecovery(restoredPreviousData = true, originalCount = 1), recovered.recovery())
        assertEquals(listOf(damaged), originals(recovered))
        val next = known.copy(preferences = known.preferences.copy(pounds = true))
        recovered.save(next)
        val restarted = WorkoutStore(context, name)
        assertEquals(next, restarted.load())
        assertEquals(listOf(damaged), originals(restarted))
        assertTrue(restarted.dismissRecovery())
        assertNull(WorkoutStore(context, name).recovery())
        assertTrue(restarted.hasRecoveryPayloads())
        assertEquals(listOf(damaged), originals(WorkoutStore(context, name)))
    }

    @Test fun noCheckpointPreservesOriginalBeforeFreshGymInitialization() = withStore { context, name, _ ->
        val damaged = "{\"schemaVersion\":999999,\"training\":\"newer app data\"}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("data", damaged).commit())
        val recovered = WorkoutStore(context, name)
        val initial = recovered.load()
        assertEquals(AppData(), initial)
        assertEquals(StorageRecovery(restoredPreviousData = false, originalCount = 1), recovered.recovery())
        // This is the ViewModel's initial gym-save path that previously destroyed the raw payload.
        recovered.save(initial.copy(gyms = listOf(GymProfile(name = "Fresh gym"))))
        assertNotEquals(damaged, prefs.getString("data", null))
        assertEquals(listOf(damaged), originals(WorkoutStore(context, name)))
    }

    @Test fun checkpointRefreshesOnLoadWithoutCopyingHistoryOnEverySave() = withStore { context, name, store ->
        val first = AppData(active = Workout(name = "First"))
        val second = AppData(active = Workout(name = "Second", notes = "More recent workout"))
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val recoveryPrefs = context.getSharedPreferences("${name}_recovery", Context.MODE_PRIVATE)
        store.save(first)
        assertEquals(first, store.load())
        val checkpoint = recoveryPrefs.getString("data_last_good", null)
        store.save(second)
        assertEquals(checkpoint, recoveryPrefs.getString("data_last_good", null))
        assertFalse(prefs.contains("data_last_good"))
        assertEquals(second, WorkoutStore(context, name).load())
        assertNotEquals(checkpoint, recoveryPrefs.getString("data_last_good", null))
        assertTrue(prefs.edit().putString("data", "not JSON").commit())
        assertEquals(second, WorkoutStore(context, name).load())
    }
}
