package org.openfactor.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TrainingMetricsTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val now = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val catalog =
        listOf(
            Exercise("chin", "Chin-Up", "Lats", "Bodyweight", secondary = listOf("Biceps")),
            Exercise("dip", "Dip", "Chest", "Bodyweight", secondary = listOf("Triceps")),
        )

    private fun workout(id: String, program: String? = null, time: Long = now) =
        Workout(
            id = id,
            name = "Training",
            startedAt = time,
            programId = program,
            exercises =
                listOf(
                    SessionExercise(
                        exerciseId = "chin",
                        sets =
                            listOf(
                                LoggedSet(completed = true, weightKg = 10.0, reps = 8),
                                LoggedSet(completed = true, warmup = true),
                                LoggedSet(),
                            ),
                    )
                ),
        )

    @Test
    fun noProgramShowsRealHistoryDespiteLegacyProgramScopePreference() {
        val data = AppData(history = listOf(workout("saved")))
        val metrics = data.dashboardMetrics(catalog, today)
        assertTrue(metrics.allWorkouts)
        assertEquals(TrainingTotals(1, 1, 2), metrics.totals)
        assertEquals(1, metrics.completedWorkouts)
        assertNull(metrics.plan)
    }

    @Test
    fun liveCompletionEditingUndoFinishAndDiscardUpdateWithoutDoubleCounting() {
        val active = workout("live")
        val data = AppData(active = active, history = listOf(workout("saved")))
        val initial = data.dashboardMetrics(catalog, today)
        assertEquals(2, initial.totals.sets)
        assertEquals(1, initial.completedWorkouts)
        assertTrue(initial.includesActive)
        val edited =
            active.copy(
                exercises =
                    active.exercises.map { e ->
                        e.copy(
                            sets =
                                e.sets.mapIndexed { n, s ->
                                    if (n == 2) s.copy(completed = true, weightKg = 15.0, reps = 10)
                                    else s
                                }
                        )
                    }
            )
        val changed = data.copy(active = edited)
        assertEquals(3, changed.dashboardMetrics(catalog, today).totals.sets)
        assertEquals(230.0, edited.volumeKg(), 0.0)
        assertEquals(2, data.dashboardMetrics(catalog, today).totals.sets)
        val finished =
            changed.copy(
                active = null,
                history = listOf(edited.copy(finishedAt = now + 1)) + data.history,
            )
        assertEquals(3, finished.dashboardMetrics(catalog, today).totals.sets)
        assertEquals(2, finished.dashboardMetrics(catalog, today).completedWorkouts)
        assertEquals(
            3,
            changed.copy(history = finished.history).dashboardMetrics(catalog, today).totals.sets,
        )
        assertEquals(1, data.copy(active = null).dashboardMetrics(catalog, today).totals.sets)
    }

    @Test
    fun cyclePlanUsesExactSetsSkipsAndMusclesRatherThanUnrelatedTemplates() {
        val cycles =
            listOf(
                CycleTarget(sets = List(2) { SetTarget() }),
                CycleTarget(sets = List(5) { SetTarget() }),
            )
        val plan =
            TrainingProgram(
                id = "p",
                name = "Plan",
                cycleCount = 2,
                days =
                    listOf(
                        ProgramDay(
                            exercises =
                                listOf(
                                    PlannedExercise(exerciseId = "chin", cycles = cycles),
                                    PlannedExercise(
                                        exerciseId = "dip",
                                        cycles = listOf(CycleTarget(), CycleTarget(skip = true)),
                                    ),
                                )
                        ),
                        ProgramDay(),
                    ),
            )
        val data =
            AppData(
                programs = listOf(plan),
                activeProgramId = "p",
                active = workout("active", "p").copy(programCycle = 1),
                history = listOf(workout("unrelated")),
                routines = listOf(Routine(name = "Unrelated", exerciseIds = List(50) { "dip" })),
            )
        val metrics = data.dashboardMetrics(catalog, today)
        assertFalse(metrics.allWorkouts)
        assertEquals(TrainingTotals(1, 1, 2), metrics.totals)
        assertEquals(TrainingTotals(5, 1, 2), metrics.plan)
        assertEquals(1, metrics.planCycle)
        val all =
            data
                .copy(preferences = Preferences(dashboardAllWorkouts = true))
                .dashboardMetrics(catalog, today)
        assertEquals(2, all.totals.sets)
        assertNull(all.plan)
    }

    @Test
    fun weekBoundsAndSavedOrderExcludeFutureAndPreviousWeek() {
        val day = 86400000L
        val data =
            AppData(
                history =
                    listOf(
                        workout("old", time = now - 8 * day),
                        workout("future", time = now + day),
                        workout("mon", time = now - 2 * day),
                        workout("today"),
                    )
            )
        val metrics = data.dashboardMetrics(catalog, today)
        assertEquals(2, metrics.totals.sets)
        assertEquals(listOf("today", "mon", "old"), metrics.history.map { it.id })
        assertEquals(2, metrics.completedWorkouts)
        assertEquals(0, AppData().dashboardMetrics(catalog, today).totals.sets)
    }
}
