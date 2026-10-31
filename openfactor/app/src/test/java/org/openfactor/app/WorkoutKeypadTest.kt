package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutKeypadTest {
    private fun type(start: KeypadEdit, vararg keys: KeypadKey, metric: SetMetric = SetMetric.WEIGHT, pounds: Boolean = false) =
        keys.fold(start) { state, key -> keypadEdit(state, key, metric, pounds) }

    private fun digits(text: String) = text.map { if (it == '.') KeypadKey.Decimal else KeypadKey.Digit(it.digitToInt()) }.toTypedArray()

    @Test
    fun stepsSnapToThePlateGridFromConvertedValues() {
        // 100 kg shown in pounds is 220.462: steps land on 225 / 220, never 225.462.
        assertEquals("225", type(KeypadEdit("220.462", false), KeypadKey.Increase, pounds = true).text)
        assertEquals("220", type(KeypadEdit("220.462", false), KeypadKey.Decrease, pounds = true).text)
        assertEquals("230", type(KeypadEdit("225", false), KeypadKey.Increase, pounds = true).text)
        assertEquals("62.5", type(KeypadEdit("61", false), KeypadKey.Increase).text)
        assertEquals("60", type(KeypadEdit("61", false), KeypadKey.Decrease).text)
    }

    @Test
    fun firstKeyReplacesPrefilledValueThenAppends() {
        val focused = KeypadEdit("47.5", replace = true)
        assertEquals(KeypadEdit("5", false), type(focused, KeypadKey.Digit(5)))
        assertEquals(KeypadEdit("52.5", false), type(focused, *digits("52.5")))
        assertEquals(KeypadEdit("0.", false), type(focused, KeypadKey.Decimal))
        assertEquals(KeypadEdit("", false), type(focused, KeypadKey.Backspace))
        assertEquals(KeypadEdit("47.", false), type(KeypadEdit("47.5", false), KeypadKey.Backspace))
        assertEquals(KeypadEdit("", false), type(KeypadEdit("", false), KeypadKey.Backspace))
    }

    @Test
    fun digitsNormaliseLeadingZeroAndRespectLengthLimits() {
        assertEquals("7", type(KeypadEdit("0", false), KeypadKey.Digit(7)).text)
        assertEquals("0", type(KeypadEdit("0", false), KeypadKey.Digit(0)).text)
        assertEquals("12345", type(KeypadEdit("", false), *digits("123456")).text)
        assertEquals("1.125", type(KeypadEdit("", false), *digits("1.1259")).text)
    }

    @Test
    fun decimalOnlyForLoadAndDistanceAndOnlyOnce() {
        assertEquals("2.5", type(KeypadEdit("", false), *digits("2..5")).text)
        assertEquals("1,5", type(KeypadEdit("1,", false), KeypadKey.Decimal, KeypadKey.Digit(5)).text)
        for (metric in listOf(SetMetric.REPS, SetMetric.REPS_PER_SIDE, SetMetric.DURATION, SetMetric.DURATION_PER_SIDE)) {
            assertFalse(metric.keypadDecimal)
            assertEquals(KeypadEdit("8", false), type(KeypadEdit("8", false), KeypadKey.Decimal, metric = metric))
        }
        for (metric in listOf(SetMetric.WEIGHT, SetMetric.WEIGHT_PER_SIDE, SetMetric.ASSISTANCE, SetMetric.DISTANCE_LONG, SetMetric.DISTANCE_SHORT))
            assertTrue(metric.keypadDecimal)
    }

    @Test
    fun steppersUseUnitAwareStepsBoundsAndKeepValueSelected() {
        assertEquals(KeypadEdit("50", true), type(KeypadEdit("47.5", false), KeypadKey.Increase))
        assertEquals(KeypadEdit("105", true), type(KeypadEdit("100", false), KeypadKey.Increase, pounds = true))
        assertEquals(KeypadEdit("0", true), type(KeypadEdit("1", false), KeypadKey.Decrease))
        assertEquals(KeypadEdit("0", false), type(KeypadEdit("0", false), KeypadKey.Decrease))
        assertEquals(KeypadEdit("2.5", true), type(KeypadEdit("", false), KeypadKey.Increase))
        assertEquals(KeypadEdit("", false), type(KeypadEdit("", false), KeypadKey.Decrease))
        // Above the valid 5000 kg the stepper refuses to produce an invalid value, but can step back down.
        assertEquals(KeypadEdit("5000", false), type(KeypadEdit("5000", false), KeypadKey.Increase))
        assertEquals(KeypadEdit("5000", true), type(KeypadEdit("5002", false), KeypadKey.Decrease))
        assertEquals("102.5", type(KeypadEdit("100,0", false), KeypadKey.Increase).text)

        assertEquals(KeypadEdit("9", true), type(KeypadEdit("8", false), KeypadKey.Increase, metric = SetMetric.REPS))
        assertEquals(KeypadEdit("1", false), type(KeypadEdit("1", false), KeypadKey.Decrease, metric = SetMetric.REPS))
        assertEquals(KeypadEdit("1", true), type(KeypadEdit("", false), KeypadKey.Increase, metric = SetMetric.REPS))
        assertEquals(KeypadEdit("25", true), type(KeypadEdit("30", false), KeypadKey.Decrease, metric = SetMetric.DURATION))
        assertEquals(KeypadEdit("5", false), type(KeypadEdit("5", false), KeypadKey.Decrease, metric = SetMetric.DURATION))
        assertEquals("0.3", type(KeypadEdit("0.2", false), KeypadKey.Increase, metric = SetMetric.DISTANCE_LONG).text)

        // A step leaves the result selected, so the next digit starts a fresh number.
        val stepped = type(KeypadEdit("60", false), KeypadKey.Decrease)
        assertEquals(KeypadEdit("57.5", true), stepped)
        assertEquals("8", type(stepped, KeypadKey.Digit(8)).text)
    }

    @Test
    fun keypadValuesParseThroughExistingMetricRules() {
        val set = LoggedSet()
        val typed = type(KeypadEdit("", true), *digits("102.5")).text
        val pounds = SetMetric.WEIGHT.parse(typed, pounds = true)!!
        assertEquals("102.5", SetMetric.WEIGHT.display(SetMetric.WEIGHT.update(set, pounds), pounds = true))
        assertNull(SetMetric.REPS.parse(type(KeypadEdit("", true), KeypadKey.Digit(0), metric = SetMetric.REPS).text, false))
    }

    @Test
    fun nextMovesAcrossTheRowThenToTheNextUnfinishedRowInDrawnOrder() {
        val metrics = listOf(SetMetric.WEIGHT, SetMetric.REPS)
        val warmup = LoggedSet(id = "warmup", warmup = true)
        val done = LoggedSet(id = "done", completed = true)
        val left = LoggedSet(id = "left", side = SetSide.LEFT)
        val right = LoggedSet(id = "right", side = SetSide.RIGHT, pairId = "left")
        val myo = LoggedSet(id = "myo", kind = SetKind.MYO)
        val component = LoggedSet(id = "component", kind = SetKind.MYO, segmentOf = "myo")
        val entry = SessionExercise(exerciseId = "bench", sets = listOf(warmup, done, left, right, myo, component))
        val rows = entry.logicalSets().flatMap { it.logs }
        assertEquals(listOf("warmup", "done", "left", "right", "myo", "component"), rows.map { it.id })

        assertEquals(KeypadTarget("warmup", SetMetric.REPS), nextKeypadTarget(rows, metrics, KeypadTarget("warmup", SetMetric.WEIGHT)))
        assertEquals(KeypadTarget("left", SetMetric.WEIGHT), nextKeypadTarget(rows, metrics, KeypadTarget("warmup", SetMetric.REPS)))
        assertEquals(KeypadTarget("right", SetMetric.WEIGHT), nextKeypadTarget(rows, metrics, KeypadTarget("left", SetMetric.REPS)))
        assertEquals(KeypadTarget("component", SetMetric.WEIGHT), nextKeypadTarget(rows, metrics, KeypadTarget("myo", SetMetric.REPS)))
        // Past the last row it wraps to the earliest set still unfinished (here the warm-up).
        assertEquals(KeypadTarget("warmup", SetMetric.WEIGHT), nextKeypadTarget(rows, metrics, KeypadTarget("component", SetMetric.REPS)))
        val allDone = rows.map { it.copy(completed = true) }
        assertNull(nextKeypadTarget(allDone, metrics, KeypadTarget("component", SetMetric.REPS)))
        // Editing a completed row still walks its own fields first.
        assertEquals(KeypadTarget("done", SetMetric.REPS), nextKeypadTarget(rows, metrics, KeypadTarget("done", SetMetric.WEIGHT)))
        assertNull(nextKeypadTarget(rows, metrics, KeypadTarget("removed", SetMetric.WEIGHT)))
        assertNull(nextKeypadTarget(rows, listOf(SetMetric.DURATION), KeypadTarget("left", SetMetric.WEIGHT)))
    }

    @Test
    fun keypadPreferenceRoundTripsAndDefaultsOnForOlderBackups() {
        assertTrue(Preferences().workoutKeypad)
        val off = AppData(preferences = Preferences(workoutKeypad = false, pounds = true))
        val encoded = DataCodec.encode(off)
        assertFalse(JSONObject(encoded).getJSONObject("preferences").getBoolean("workoutKeypad"))
        assertEquals(off, DataCodec.decode(encoded))
        val legacy = JSONObject(encoded).apply { getJSONObject("preferences").remove("workoutKeypad") }
        assertEquals(off.copy(preferences = off.preferences.copy(workoutKeypad = true)), DataCodec.decode(legacy.toString()))
        val scoped = ScopedDataExport.document(
            off, emptyList(), ScopedExportRequest(false, categories = setOf(ExportCategory.WORKOUT_SETTINGS)),
            java.time.LocalDate.of(2026, 10, 31), java.time.ZoneOffset.UTC,
        ).getJSONObject("data").getJSONObject("workoutSettings")
        assertFalse(scoped.getBoolean("workoutKeypad"))
    }
}
