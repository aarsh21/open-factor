package org.openfactor.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceStorageTest {
    @Test
    fun preferenceEditsSurviveNewViewModelAndFullRestoreWithoutChangingTraining() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val vm = WorkoutViewModel(context.applicationContext as Application)
            val initialized = vm.data.value
            vm.settings { it.copy(
                keepAlive = true, showWorkoutTimer = false, showBodyweightContribution = false,
                heightInFeet = true, clock24Hour = true, distanceImperial = true, theme = AppTheme.LIGHT,
                shortcuts = ShortcutPreferences(listOf(WorkoutShortcut.HISTORY, WorkoutShortcut.WEIGHT),
                    mapOf(WorkoutShortcut.HISTORY to ShortcutColor.PURPLE), false),
            ) }
            val saved = vm.data.value
            assertEquals(initialized.copy(preferences = saved.preferences), saved)
            val reopened = WorkoutViewModel(context.applicationContext as Application)
            assertEquals(saved, reopened.data.value)
            val backup = reopened.export()
            reopened.settings { Preferences() }
            reopened.restore(backup)
            assertEquals(saved, reopened.data.value)
            assertEquals(saved, WorkoutStore(context).load())
            val invalid = JSONObject(backup).apply {
                getJSONObject("preferences").getJSONObject("shortcuts").put("order", org.json.JSONArray(listOf("WEIGHT", "WEIGHT")))
            }.toString()
            assertThrows(IllegalArgumentException::class.java) { reopened.restore(invalid) }
            assertEquals(saved, reopened.data.value)
            assertEquals(saved, WorkoutStore(context).load())
        } finally {
            store.save(before)
        }
    }
}
