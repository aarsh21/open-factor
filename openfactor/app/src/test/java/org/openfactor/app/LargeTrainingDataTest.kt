package org.openfactor.app

import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureNanoTime
import org.junit.Assert.*
import org.junit.Test

class LargeTrainingDataTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val catalog = List(12) { Exercise("exercise-$it", "Exercise $it", "Chest", "Barbell", secondary = listOf("Triceps")) }
    private fun history(count: Int) = List(count) { index ->
        val start = today.minusDays(index.toLong()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        Workout(id = "synthetic-$index", name = "Synthetic $index", startedAt = start, finishedAt = start + 3_600_000,
            exercises = List(6) { exercise -> SessionExercise(id = "e-$index-$exercise", exerciseId = catalog[exercise].id,
                sets = List(4) { row -> LoggedSet(id = "s-$index-$exercise-$row", weightKg = 50.0, reps = 8, completed = true) }) })
    }

    @Test fun fiveThousandWorkoutsSupportDashboardHistoryAndAllExerciseTilesWithoutLosingRecords() {
        val data = AppData(history = history(5_000))
        val weekMs = measureNanoTime {
            val week = data.trainingWeek(catalog, today = today)
            assertEquals(4 * 24, week.workingSets)
            assertEquals(4, week.sessions)
            assertEquals(6, week.exercises)
        } / 1_000_000.0
        val indexMs = measureNanoTime {
            val counts = recordedExerciseSets(data.history + data.history.first(), today)
            assertEquals(6, counts.size)
            assertTrue(counts.values.all { it == 20_000 })
        } / 1_000_000.0
        val historyMs = measureNanoTime {
            val groups = workoutHistoryDays(data.history.reversed() + data.history.first())
            assertEquals(5_000, groups.size)
            assertEquals("synthetic-0", groups.first().second.single().id)
            assertEquals(1, workoutHistoryDays(data.history, today).size)
        } / 1_000_000.0
        println("5000 workouts / 120000 working sets: week=${weekMs}ms, all-exercise index=${indexMs}ms, history=${historyMs}ms (JVM, not device frame timings)")
    }

    @Test fun twentyThousandWeightReadingsUseExactCalendarWindows() {
        val records = List(20_000) { index -> ScaleWeightRecord(id = "weight-$index", date = today.minusDays(index.toLong()), weightKg = 70 + index % 20 / 10.0) }
        val started = System.nanoTime()
        val trend = records.weightTrend(today)
        println("20000 weight readings: sliding-window trend=${(System.nanoTime() - started) / 1_000_000.0}ms (JVM)")
        assertEquals(records.size, trend.size)
        trend.takeLast(30).forEach { reading ->
            val expected = records.filter { it.date in reading.date.minusDays(6)..reading.date }.map { it.weightKg }.average()
            assertEquals(expected, reading.averageKg, 1e-9)
        }
    }

    @Test fun recordedExerciseIndexExcludesFutureWarmupsAndIncompleteWork() {
        val saved = history(1).single()
        val changed = saved.copy(id = "changed", exercises = saved.exercises.take(1).map { entry ->
            entry.copy(sets = entry.sets.mapIndexed { index, row -> row.copy(warmup = index == 0, completed = index != 1) })
        })
        val future = saved.copy(id = "future", startedAt = saved.startedAt + 86_400_000)
        assertEquals(mapOf("exercise-0" to 2), recordedExerciseSets(listOf(changed, changed, future), today))
    }

    @Test fun compactAndHumanReadableBackupsDecodeTheSameLargeDataset() {
        val data = AppData(history = history(250))
        val compact = DataCodec.encode(data, pretty = false)
        assertTrue(compact.length < DataCodec.encode(data).length)
        assertEquals(data, DataCodec.decode(compact))
    }

    @Test fun customizingBodyMetricOptionsDoesNotTraverseSavedReadings() {
        val unreadable = object : AbstractList<ScaleWeightRecord>() {
            override val size = 1_000_000
            override fun get(index: Int): ScaleWeightRecord = error("Customization must use metadata, not build chart points")
        }
        val options = dashboardOptions(AppData(scaleWeights = unreadable), catalog, DashboardSection.BODY)
        assertTrue(options.any { it.first == "weight" })
        assertFalse(options.any { it.first == "bodyFat" })
    }
}
