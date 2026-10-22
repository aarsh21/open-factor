package org.openfactor.app

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreferenceChoicesTest {
    @Test
    fun preferencesAndHiddenOrderedShortcutsRoundTripAlongsideTraining() {
        val preferences = Preferences(
            pounds = true, keepAlive = true, showWorkoutTimer = false,
            showBodyweightContribution = false, distanceImperial = true,
            heightInFeet = true, clock24Hour = true, theme = AppTheme.LIGHT,
            shortcuts = ShortcutPreferences(listOf(WorkoutShortcut.HISTORY, WorkoutShortcut.PHOTOS, WorkoutShortcut.WEIGHT),
                mapOf(WorkoutShortcut.HISTORY to ShortcutColor.TEAL, WorkoutShortcut.WEIGHT to ShortcutColor.RED), false),
        )
        val data = AppData(preferences = preferences, active = Workout(name = "Preserve me", notes = "Entry note"),
            scaleWeights = listOf(ScaleWeightRecord(weightKg = 81.73)), exerciseDefaults = mapOf("press" to ExerciseDefaults(note = "Pin note")))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        val legacy = DataCodec.decode("""{"schemaVersion":1}""").preferences
        assertFalse(legacy.keepAlive)
        assertTrue(legacy.showWorkoutTimer)
        assertTrue(legacy.showBodyweightContribution)
        assertFalse(legacy.distanceImperial)
        assertFalse(legacy.heightInFeet)
        assertFalse(legacy.clock24Hour)
        assertEquals(AppTheme.DARK, legacy.theme)
        assertEquals(listOf(WorkoutShortcut.WEIGHT, WorkoutShortcut.PHOTOS, WorkoutShortcut.MEASUREMENTS,
            WorkoutShortcut.HISTORY, WorkoutShortcut.EMPTY_WORKOUT, WorkoutShortcut.NEW_PROGRAM,
            WorkoutShortcut.NEW_WORKOUT), legacy.shortcuts.order)
        assertTrue(legacy.shortcuts.showUpNext)
        assertTrue(legacy.shortcuts.colors.isEmpty())
        val empty = data.copy(preferences = preferences.copy(shortcuts = ShortcutPreferences(order = emptyList())))
        assertEquals(empty, DataCodec.decode(DataCodec.encode(empty)))
        for (invalid in listOf(
            """{"order":["WEIGHT","WEIGHT"]}""", """{"order":["UNKNOWN"]}""",
            """{"colors":{"PHOTOS":"UNKNOWN"}}""")) {
            assertThrows(IllegalArgumentException::class.java) { ShortcutPreferences.decode(JSONObject(invalid)) }
        }
    }

    @Test
    fun profileHeightAndClockFormatUseUnitsWithoutChangingCanonicalValues() {
        val time = Instant.parse("2026-10-21T18:07:00Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        assertEquals("18:07", Preferences(clock24Hour = true).clockTime(time, zone, Locale.US))
        assertEquals("6:07 PM", Preferences().clockTime(time, zone, Locale.US))
        assertEquals("5 ft 11 in", Preferences(heightInFeet = true).heightLabel(180.34))
        assertEquals("180.34 cm", Preferences().heightLabel(180.34))
    }
}
