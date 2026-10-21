package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ProgramDataTest {
    @Test
    fun manualDraftStartsWithRestAndConvertsOnlyTheEditedDay() {
        val first = ProgramDay(id = "rest")
        val workout = first.addExercise("chin-up", 7, "Workout A")
        assertEquals("Rest", first.name)
        assertTrue(first.exercises.isEmpty())
        assertEquals("Workout A", workout.name)
        assertEquals(7, workout.exercises.single().cycles.size)
        assertTrue(workout.exercises.single().cycles.all { it.sets.size == 1 && !it.skip })
        assertEquals(
            "Upper",
            first.copy(name = "Upper").addExercise("chin-up", 7, "Workout A").name,
        )
    }

    @Test
    fun draftAndLibrarySurviveBackupWithDifferentCycleTargets() {
        val cycles =
            List(7) { n ->
                CycleTarget(
                    listOf(SetTarget(id = "set-$n", repsMin = 6 + n, repsMax = 10 + n, rir = 2)),
                    skip = n == 5,
                    restSeconds = if (n == 6) 0 else 90,
                )
            }
        val p =
            TrainingProgram(
                id = "plan",
                name = "Training",
                days =
                    listOf(
                        ProgramDay(
                            id = "upper",
                            name = "Upper",
                            gymId = "garage",
                            exercises =
                                listOf(
                                    PlannedExercise(
                                        id = "press",
                                        exerciseId = "bench",
                                        cycles = cycles,
                                    )
                                ),
                        ),
                        ProgramDay(id = "rest"),
                    ),
            )
        val source =
            AppData(
                programs = listOf(p),
                programDraft = p.copy(name = "Editing"),
                activeProgramId = p.id,
            )
        assertEquals(source, DataCodec.decode(DataCodec.encode(source)))
        val old = DataCodec.decode("{\"schemaVersion\":1}")
        assertTrue(old.programs.isEmpty())
        assertNull(old.programDraft)
    }

    @Test
    fun startDayCarriesOnlySelectedCycleAndKeepsPlanUnchanged() {
        val cycles =
            List(7) { n ->
                CycleTarget(
                    listOf(SetTarget(repsMin = 6 + n, repsMax = 10 + n, rir = 2)),
                    skip = n == 1,
                    restSeconds = 45,
                )
            }
        val p =
            TrainingProgram(
                id = "plan",
                name = "Training",
                days =
                    listOf(
                        ProgramDay(
                            id = "day",
                            name = "Upper",
                            gymId = "garage",
                            exercises =
                                listOf(PlannedExercise(exerciseId = "press", cycles = cycles)),
                        ),
                        ProgramDay(id = "rest"),
                    ),
            )
        val w = p.toWorkout("day", 2, 1000)!!
        assertEquals("plan", w.programId)
        assertEquals("day", w.programDayId)
        assertEquals(2, w.programCycle)
        assertEquals("garage", w.gymId)
        assertEquals(1000L, w.startedAt)
        val e = w.exercises.single()
        val set = e.sets.single()
        assertEquals(8, set.reps)
        assertEquals(8, set.targetRepsMin)
        assertEquals(12, set.targetRepsMax)
        assertEquals(2, set.targetRir)
        assertNull(set.rir)
        assertFalse(set.completed)
        assertEquals(45, e.restSeconds)
        val complete = w.toggleSetCompletion(e.id, set.id, Preferences(restSeconds = 120), 2000)
        assertEquals(47000L, complete.restUntil)
        assertEquals(w, DataCodec.decode(DataCodec.encode(AppData(active = w))).active)
        assertNull(p.toWorkout("rest", 0, 1000))
        assertNull(p.toWorkout("day", 1, 1000))
        assertNull(p.toWorkout("day", 7, 1000))
        assertNull(p.toWorkout("missing", 0, 1000))
        assertFalse(p.days.first().exercises.first().cycles.first().skip)
    }

    @Test
    fun zeroRestOverrideDoesNotStartGlobalRest() {
        val p =
            TrainingProgram(
                name = "Training",
                days =
                    listOf(
                        ProgramDay(
                            exercises =
                                listOf(
                                    PlannedExercise(
                                        exerciseId = "press",
                                        cycles = List(7) { CycleTarget(restSeconds = 0) },
                                    )
                                )
                        )
                    ),
            )
        val w = p.toWorkout(p.days.first().id, 0, 1000)!!
        val e = w.exercises.single()
        assertEquals(
            0L,
            w.toggleSetCompletion(e.id, e.sets.single().id, Preferences(), 2000).restUntil,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidRepRangeRejectedOnImport() {
        val p =
            TrainingProgram(
                name = "Invalid",
                days =
                    listOf(
                        ProgramDay(
                            exercises =
                                listOf(
                                    PlannedExercise(
                                        exerciseId = "press",
                                        cycles =
                                            List(7) {
                                                CycleTarget(
                                                    sets =
                                                        listOf(SetTarget(repsMin = 10, repsMax = 5))
                                                )
                                            },
                                    )
                                )
                        )
                    ),
            )
        ProgramCodec.decode(ProgramCodec.encode(p))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteCyclesRejectedOnImport() {
        val p =
            TrainingProgram(
                name = "Invalid",
                days =
                    listOf(
                        ProgramDay(
                            exercises =
                                listOf(
                                    PlannedExercise(
                                        exerciseId = "press",
                                        cycles = listOf(CycleTarget()),
                                    )
                                )
                        )
                    ),
            )
        ProgramCodec.decode(ProgramCodec.encode(p))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateExerciseIdsRejectedBeforeRendering() {
        val e = PlannedExercise(exerciseId = "press")
        ProgramCodec.decode(
            ProgramCodec.encode(
                TrainingProgram(
                    name = "Invalid",
                    days = listOf(ProgramDay(exercises = listOf(e, e))),
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateProgramIdsRejectedBeforeRendering() {
        val p = TrainingProgram(name = "Invalid")
        DataCodec.decode(DataCodec.encode(AppData(programs = listOf(p, p))))
    }

    @Test
    fun reopeningAndScopeNavigationWithoutEditsPreserveDifferentiatedTargets() {
        val first =
            CycleTarget(listOf(SetTarget(repsMin = 6, repsMax = 10, rir = 2)), restSeconds = 45)
        val second =
            CycleTarget(
                listOf(SetTarget(repsMin = 8, repsMax = 12, rir = 1)),
                skip = true,
                restSeconds = 90,
            )
        val cycles = List(7) { if (it == 1) second else first }
        assertFalse(cycles.haveEquivalentTargets())
        val reopened = applyCycleTargetEdit(cycles, -1, first, edited = false)
        assertSame(cycles, reopened)
        assertEquals(cycles, applyCycleTargetEdit(reopened, 1, second, edited = false))
        assertEquals(cycles, applyCycleTargetEdit(reopened, 2, first, edited = false))
        val p =
            TrainingProgram(
                name = "Training",
                days =
                    listOf(
                        ProgramDay(
                            exercises =
                                listOf(PlannedExercise(exerciseId = "press", cycles = reopened))
                        )
                    ),
            )
        assertEquals(
            cycles,
            ProgramCodec.decode(ProgramCodec.encode(p)).days.single().exercises.single().cycles,
        )
    }

    @Test
    fun intentionalSingleCycleAndAllCycleEditsHaveDistinctScopes() {
        val first = CycleTarget(listOf(SetTarget(repsMin = 6, repsMax = 10)), restSeconds = 45)
        val changed =
            CycleTarget(
                listOf(SetTarget(repsMin = 8, repsMax = 12, rir = 1)),
                skip = true,
                restSeconds = 90,
            )
        val cycles = List(7) { first }
        val single = applyCycleTargetEdit(cycles, 1, changed, edited = true)
        assertEquals(changed, single[1])
        assertEquals(first, single[0])
        assertEquals(first, single[2])
        val all = applyCycleTargetEdit(single, -1, changed, edited = true)
        assertTrue(all.all { it == changed })
        assertTrue(all.haveEquivalentTargets())
    }
}
