package org.openfactor.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TrainingInsightsTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val bench = Exercise("bench", "Bench press", "Chest", "Barbell", muscles = listOf("Chest", "Chest"), secondary = listOf("Chest", "Triceps", "Triceps"))
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun workout(id: String, date: LocalDate = today, sets: List<LoggedSet> = listOf(LoggedSet(completed = true, weightKg = 50.0, reps = 8))) =
        Workout(id = id, name = "Training", startedAt = at(date), exercises = listOf(SessionExercise(exerciseId = bench.id, sets = sets)))

    @Test fun workingSetsAndMusclesNeverCountWarmupsIncompleteSetsOrDuplicateTargets() {
        val data = AppData(history = listOf(workout("saved", sets = listOf(
            LoggedSet(completed = true, weightKg = 50.0, reps = 8),
            LoggedSet(completed = true, warmup = true, weightKg = 20.0, reps = 10),
            LoggedSet(completed = false, weightKg = 60.0, reps = 8),
        ))))
        val week = data.trainingWeek(listOf(bench), today = today)
        assertEquals(1, week.workingSets)
        assertEquals(1, week.sessions)
        assertEquals(1, week.exercises)
        assertEquals(400.0, week.volumeKg, 0.0)
        assertEquals(2, week.trainedMuscles)
        assertEquals(MuscleWorkload("Chest", 1, 0), week.muscles.single { it.muscle == "Chest" })
        assertEquals(MuscleWorkload("Triceps", 0, 1), week.muscles.single { it.muscle == "Triceps" })
        assertEquals(.5, week.muscles.single { it.muscle == "Triceps" }.weightedSets, 0.0)
    }

    @Test fun liveCopyTakesPriorityOverArchivedIdentityAndAWorkingSetCountsOnceAcrossSidesAndMyo() {
        val root = LoggedSet(id = "root", completed = true, kind = SetKind.MYO, side = SetSide.LEFT, weightKg = 20.0, reps = 8)
        val logs = listOf(root,
            root.copy(id = "right", side = SetSide.RIGHT, pairId = root.id),
            root.copy(id = "extra", side = SetSide.LEFT, segmentOf = root.id, reps = 3),
            root.copy(id = "extra-right", side = SetSide.RIGHT, segmentOf = root.id, pairId = "extra", reps = 3))
        val live = workout("same", sets = logs)
        val data = AppData(active = live, history = listOf(workout("same")))
        val week = data.trainingWeek(listOf(bench), today = today)
        assertEquals(1, week.workingSets)
        assertEquals(440.0, week.volumeKg, 0.0)
        assertEquals(1, week.sessions)
        assertTrue(week.includesActive)
    }

    @Test fun calendarWeeksExcludeFutureAndSupportPreviousWeekWithoutLeakingCurrentSets() {
        val monday = LocalDate.of(2026, 10, 26)
        val data = AppData(history = listOf(
            workout("previous", monday.minusDays(1)), workout("monday", monday), workout("today"), workout("future", today.plusDays(1)),
        ))
        val current = data.trainingWeek(listOf(bench), today = today)
        assertEquals(monday, current.first)
        assertEquals(2, current.workingSets)
        assertEquals(1, current.days.first().sets)
        assertEquals(0, current.days.last().sets)
        assertEquals(1, data.trainingWeek(listOf(bench), -1, today).workingSets)
    }

    @Test fun missingCatalogEntryStillCountsRecordedWorkButDoesNotInventMuscleTargets() {
        val week = AppData(history = listOf(workout("known"))).trainingWeek(emptyList(), today = today)
        assertEquals(1, week.workingSets)
        assertEquals(0, week.trainedMuscles)
    }

    @Test fun calendarMonthIncludesEarlierWeeksButExcludesAdjacentMonthsAndFutureRecords() {
        val data = AppData(history = listOf(
            workout("september", LocalDate.of(2026, 9, 30)),
            workout("first", LocalDate.of(2026, 10, 1)),
            workout("earlier-week", LocalDate.of(2026, 10, 15)),
            workout("today"),
            workout("future", LocalDate.of(2026, 10, 30)),
            workout("november", LocalDate.of(2026, 11, 1)),
        ))
        val month = data.trainingPeriod(listOf(bench), TrainingPeriod.MONTH, today = today)
        assertEquals(LocalDate.of(2026, 10, 1), month.first)
        assertEquals(LocalDate.of(2026, 10, 31), month.last)
        assertEquals(31, month.days.size)
        assertEquals(3, month.workingSets)
        assertEquals(1200.0, month.volumeKg, 0.0)
        assertEquals(3, month.muscles.single { it.muscle == "Chest" }.directSets)
        assertEquals(1, data.trainingWeek(listOf(bench), today = today).workingSets)
        val previous = data.trainingPeriod(listOf(bench), TrainingPeriod.MONTH, -1, today)
        assertEquals(LocalDate.of(2026, 9, 1), previous.first)
        assertEquals(LocalDate.of(2026, 9, 30), previous.last)
        assertEquals(1, previous.workingSets)
    }

    @Test fun monthBoundariesHandleLeapYearsAndOffsetsCannotAdvanceBeyondPresent() {
        val march = LocalDate.of(2024, 3, 1)
        val data = AppData(active = workout("live", march))
        val february = data.trainingPeriod(listOf(bench), TrainingPeriod.MONTH, -1, march)
        assertEquals(LocalDate.of(2024, 2, 29), february.last)
        assertEquals(29, february.days.size)
        assertEquals(0, february.workingSets)
        val current = data.trainingPeriod(listOf(bench), TrainingPeriod.MONTH, 100, march)
        assertEquals(LocalDate.of(2024, 3, 1), current.first)
        assertEquals(1, current.workingSets)
        assertTrue(current.includesActive)
        assertEquals(0, AppData().trainingPeriod(listOf(bench), TrainingPeriod.MONTH, today = today).workingSets)
    }

    @Test fun exerciseCircleCountsDistinctCompletedExercisesNotRowsOrWarmups() {
        val completed = SessionExercise(exerciseId = bench.id, sets = listOf(LoggedSet(completed = true, reps = 8)))
        val warmup = SessionExercise(exerciseId = "squat", sets = listOf(LoggedSet(completed = true, warmup = true, reps = 8)))
        val incomplete = SessionExercise(exerciseId = "row", sets = listOf(LoggedSet(completed = false, reps = 8)))
        val saved = workout("saved").copy(exercises = listOf(completed, completed.copy(), warmup, incomplete))
        val week = AppData(history = listOf(saved, workout("second"))).trainingWeek(listOf(bench), today = today)
        assertEquals(3, week.workingSets)
        assertEquals(1, week.exercises)
    }

    @Test fun weightAverageUsesSevenCalendarDaysAndDoesNotFillMissingDatesWithZero() {
        val records = listOf(
            ScaleWeightRecord(id = "old", date = today.minusDays(8), weightKg = 100.0),
            ScaleWeightRecord(id = "a", date = today.minusDays(6), weightKg = 80.0),
            ScaleWeightRecord(id = "b", date = today.minusDays(2), weightKg = 82.0),
            ScaleWeightRecord(id = "c", date = today, weightKg = 84.0),
            ScaleWeightRecord(id = "future", date = today.plusDays(1), weightKg = 500.0),
        )
        val trend = records.weightTrend(today)
        assertEquals(4, trend.size)
        assertEquals(82.0, trend.last().averageKg, 0.0)
        assertEquals((100.0 + 80.0 + 82.0) / 3, trend[2].averageKg, 0.0)
        assertEquals(84.0, trend.last().weightKg, 0.0)
    }

    @Test fun manualDatedReadingWinsOverImportedDuplicateWithoutMutatingSavedData() {
        val imported = ScaleWeightRecord(id = "import", date = today, weightKg = 90.0, healthConnectOrigin = "scale.app")
        val manual = ScaleWeightRecord(id = "manual", date = today, weightKg = 80.0)
        val records = listOf(manual, imported, manual.copy(id = "older", weightKg = 75.0), imported.copy(id = "again"), ScaleWeightRecord(date = today.minusDays(1), weightKg = Double.NaN))
        val trend = records.weightTrend(today)
        assertEquals(1, trend.size)
        assertEquals("manual", trend.single().recordId)
        assertEquals(80.0, trend.single().averageKg, 0.0)
        assertEquals(5, records.size)
    }
}
