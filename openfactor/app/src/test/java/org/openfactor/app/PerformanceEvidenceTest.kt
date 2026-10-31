package org.openfactor.app

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM evidence for hot paths. "legacy" blocks replicate the code each fix replaced, so the
 * printed before/after pair comes from one run. Timings are host-JVM medians for
 * PERFORMANCE.md, never asserted and not device frame times; structural properties are asserted.
 */
class PerformanceEvidenceTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val catalog: List<Exercise> by lazy {
        val file = File("src/main/assets/catalog.json").takeIf { it.isFile } ?: File("app/src/main/assets/catalog.json")
        DataCodec.catalog(file.readText())
    }

    private fun history(count: Int) = List(count) { index ->
        val start = today.minusDays(index.toLong()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Workout(id = "synthetic-$index", name = "Synthetic $index", startedAt = start, finishedAt = start + 3_600_000,
            exercises = List(6) { exercise -> SessionExercise(id = "e-$index-$exercise", exerciseId = catalog[(index + exercise * 37) % catalog.size].id,
                sets = List(4) { row -> LoggedSet(id = "s-$index-$exercise-$row", weightKg = 50.0, reps = 8, completed = true) }) })
    }

    private fun active() = Workout(id = "active", name = "Push", exercises = List(8) { exercise ->
        SessionExercise(id = "a-$exercise", exerciseId = catalog[catalog.size - 1 - exercise * 11].id,
            sets = List(5) { row -> LoggedSet(id = "a-$exercise-$row", weightKg = 60.0, reps = 8, completed = row < 2) })
    })

    /** A heavy but plausible installation: the live snapshot holds everything except archived history. */
    private fun liveData() = AppData(
        active = active(),
        history = history(2_000),
        routines = List(6) { Routine(name = "Routine $it", exerciseIds = catalog.take(6).map { e -> e.id }) },
        scaleWeights = List(2_000) { ScaleWeightRecord(id = "w-$it", date = today.minusDays(it.toLong()), weightKg = 80.0 + it % 7) },
        gyms = listOf(GymProfile(name = "Everything Gym", equipmentIds = (0 until 200).map { "equipment-$it" }.toSet())),
        exerciseDefaults = catalog.take(40).associate { it.id to ExerciseDefaults("Pinned note", 90, it.id.hashCode() % 3 == 0) },
    )

    private inline fun medianMicros(repeat: Int = 31, block: () -> Unit): Double {
        repeat(5) { block() }
        return List(repeat) { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1_000.0 }.sorted()[repeat / 2]
    }

    private fun Double.f(digits: Int = 1) = "%.${digits}f".format(this)

    @Test fun sessionEffortPromptUsesOneCatalogMap() {
        val workout = active()
        val fallback = listOf(SetMetric.WEIGHT, SetMetric.REPS)
        val legacy = medianMicros {
            repeat(100) {
                workout.exercises.firstOrNull { entry ->
                    entry.pendingEffortSet(catalog.find { it.id == entry.exerciseId }?.loggingMetrics() ?: fallback) != null
                }
            }
        } / 100
        val byId = HashMap<String, Exercise>().apply { catalog.forEach { putIfAbsent(it.id, it) } }
        val mapped = medianMicros {
            repeat(100) {
                workout.exercises.firstOrNull { entry -> entry.pendingEffortSet(byId[entry.exerciseId]?.loggingMetrics() ?: fallback) != null }
            }
        } / 100
        workout.exercises.forEach { assertSame(catalog.first { e -> e.id == it.exerciseId }, byId[it.exerciseId]) }
        println("PERF session effort-prompt scan per recomposition: linear=${legacy.f()}us -> map=${mapped.f()}us (1345 exercises, 8 entries)")
    }

    @Test fun libraryTabSetsAreDerivedOncePerHistory() {
        val data = liveData()
        fun derive() = Triple(
            data.history.flatMap { w -> w.exercises.filter { entry -> entry.sets.any { it.completed } }.map { it.exerciseId } }.toSet(),
            data.exerciseDefaults.filterValues { it.doNotRecommend }.keys,
            data.exerciseDefaults.filterValues { it.hasOverrides }.keys,
        )
        val inline = medianMicros { derive() }
        // After: remember(data.history)/remember(data.exerciseDefaults) only compare keys on recomposition.
        val keyed = medianMicros { repeat(1_000) { check(data.history == data.history && data.exerciseDefaults == data.exerciseDefaults) } } / 1_000
        println("PERF library tab sets per shell recomposition (each scroll frame before): inline=${inline.f()}us -> remembered key check=${keyed.f(3)}us (2000 workouts)")
    }

    @Test fun liveSnapshotEncodingSkipsReparsingAndMatchesTheLegacyDocument() {
        val data = liveData()
        val key = "0".repeat(64)
        val legacyText = JSONObject(DataCodec.encode(data.copy(history = emptyList()), pretty = false)).put("storageHistory", key).toString()
        val currentText = DataCodec.document(data.copy(history = emptyList())).put("storageHistory", key).toString()
        assertEquals(DataCodec.decode(legacyText, data.history), DataCodec.decode(currentText, data.history))
        // Legacy encode + re-parse to append the reference, then prune() re-parsing primary and checkpoint.
        val legacy = medianMicros {
            val raw = JSONObject(DataCodec.encode(data.copy(history = emptyList()), pretty = false)).put("storageHistory", key).toString()
            JSONObject(raw).getString("storageHistory")
            JSONObject(raw).getString("storageHistory")
        }
        val current = medianMicros { DataCodec.document(data.copy(history = emptyList())).put("storageHistory", key).toString() }
        println("PERF live snapshot CPU per save: legacy=${(legacy / 1000).f(2)}ms -> current=${(current / 1000).f(2)}ms, ${currentText.length} chars (2000 weights, 8x5 active sets)")
    }

    @Test fun exerciseTitleCompilesItsPatternOnce() {
        val legacyPattern = "(^|[\\s(\\-/])([\\p{L}])"
        val legacy = medianMicros(11) {
            catalog.forEach { e -> e.name.replace(Regex(legacyPattern)) { it.groupValues[1] + it.groupValues[2].uppercase() } }
        }
        val current = medianMicros(11) { catalog.forEach { it.title } }
        catalog.forEach { e -> assertEquals(e.name.replace(Regex(legacyPattern)) { it.groupValues[1] + it.groupValues[2].uppercase() }, e.title) }
        println("PERF Exercise.title for 1345 exercises: legacy=${(legacy / catalog.size).f(2)}us/title -> current=${(current / catalog.size).f(2)}us/title")
    }

    @Test fun fiftyRapidTypedEditsCoalesceIntoOneBackgroundWrite() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writes = AtomicInteger()
        val written = mutableListOf<Int>()
        val saver = CoalescedSaver<Int>(100, scope) { value -> writes.incrementAndGet(); synchronized(written) { written += value } }
        try {
            val started = System.nanoTime()
            repeat(50) { saver.saveLater(it) }
            val callerMicros = (System.nanoTime() - started) / 1_000.0
            assertEquals("No typed edit writes on the calling thread", 0, writes.get())
            assertTrue(saver.hasPendingWrite)
            Thread.sleep(600)
            assertEquals(1, writes.get())
            assertEquals(listOf(49), synchronized(written) { written.toList() })
            assertFalse(saver.hasPendingWrite)
            println("PERF 50 rapid typed edits: legacy=50 synchronous caller-thread writes -> current=${writes.get()} background write; caller cost ${callerMicros.f()}us total")
        } finally { scope.cancel() }
    }

    @Test fun synchronousSaveSupersedesQueuedEditsAndFlushIsDurable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val written = mutableListOf<Int>()
        val saver = CoalescedSaver<Int>(150, scope) { synchronized(written) { written += it } }
        try {
            saver.saveLater(1)
            saver.saveNow(2) // e.g. completing the set
            Thread.sleep(500)
            assertEquals("An older queued edit must never overwrite a newer durable save", listOf(2), synchronized(written) { written.toList() })
            saver.saveLater(3)
            saver.flush() // lifecycle pause
            assertEquals(listOf(2, 3), synchronized(written) { written.toList() })
            Thread.sleep(400)
            assertEquals("Flushed edits are not written twice", listOf(2, 3), synchronized(written) { written.toList() })
        } finally { scope.cancel() }
    }

    @Test fun failedWritesStayPendingAndAreRetried() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val failures = AtomicInteger()
        val written = mutableListOf<Int>()
        val saver = CoalescedSaver<Int>(50, scope, { failures.incrementAndGet() }) {
            if (fail.get()) error("disk full")
            synchronized(written) { written += it }
        }
        try {
            assertThrows(IllegalStateException::class.java) { saver.saveNow(1) }
            assertFalse("A failed direct save is not recorded as accepted", saver.hasPendingWrite)
            saver.saveLater(2)
            Thread.sleep(300)
            assertEquals(1, failures.get())
            assertTrue(saver.hasPendingWrite)
            fail.set(false)
            saver.flush()
            assertEquals(listOf(2), synchronized(written) { written.toList() })
        } finally { scope.cancel() }
    }

    @Test fun onlyTypedValueEditsMayBeCoalesced() {
        val data = AppData(active = active())
        val entry = data.active!!.exercises.first()
        val row = entry.sets.last()
        fun edit(block: (LoggedSet) -> LoggedSet) = data.copy(active = data.active!!.copy(exercises = data.active!!.exercises.map { e ->
            if (e.id == entry.id) e.copy(sets = e.sets.map { if (it.id == row.id) block(it) else it }) else e
        }))
        assertTrue(data.onlyTypedSetValuesChanged(edit { it.copy(weightKg = 62.5) }, entry.id, row.id))
        assertTrue(data.onlyTypedSetValuesChanged(edit { it.copy(reps = null, durationSeconds = 30.0, distanceMeters = 5.0, assistanceKg = 10.0) }, entry.id, row.id))
        assertFalse("Completion is always durable before it publishes", data.onlyTypedSetValuesChanged(edit { it.copy(reps = 9, completed = true) }, entry.id, row.id))
        assertFalse(data.onlyTypedSetValuesChanged(edit { it.copy(rir = 2) }, entry.id, row.id))
        assertFalse(data.onlyTypedSetValuesChanged(edit { it.copy(warmup = true) }, entry.id, row.id))
        assertFalse(data.onlyTypedSetValuesChanged(data.copy(active = data.active!!.copy(id = "other")), entry.id, row.id))
        assertFalse(data.onlyTypedSetValuesChanged(data.copy(active = null), entry.id, row.id))
    }

    @Test fun librarySearchPerKeystroke() {
        val index = ExerciseSearchIndex(catalog)
        val queries = listOf("b", "be", "ben", "benc", "bench", "bench ", "bench p", "bench pr", "bench pre", "bench press")
        val micros = queries.map { q -> medianMicros(11) { index.search(q) } }
        println("PERF library search per keystroke (unchanged): median=${micros.sorted()[micros.size / 2].f(0)}us max=${micros.max().f(0)}us")
    }

    @Test fun startupHistoryDecode() {
        val text = DataCodec.encodeHistory(history(2_000))
        val micros = medianMicros(7) { DataCodec.decodeHistory(text) }
        println("PERF startup history archive decode (unchanged): ${(micros / 1000).f(0)}ms for ${text.length} chars (2000 workouts)")
    }
}
