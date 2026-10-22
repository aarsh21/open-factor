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
        val w =
            p.toWorkout("day", 2, 1000, mapOf("press" to ExerciseDefaults("Default note", 75)))!!
        assertEquals("plan", w.programId)
        assertEquals("day", w.programDayId)
        assertEquals(2, w.programCycle)
        assertEquals("garage", w.gymId)
        assertEquals(1000L, w.startedAt)
        val e = w.exercises.single()
        val set = e.sets.single()
        assertNull(set.reps)
        assertEquals(8, set.targetRepsMin)
        assertEquals(12, set.targetRepsMax)
        assertEquals(2, set.targetRir)
        assertNull(set.rir)
        assertFalse(set.completed)
        assertEquals(45, e.restSeconds)
        assertEquals("", e.notes)
        val entered =
            w.copy(exercises = listOf(e.copy(sets = listOf(set.copy(weightKg = 37.5, reps = 9)))))
        val complete =
            entered.toggleSetCompletion(e.id, set.id, Preferences(restSeconds = 120), 2000)
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
                                        cycles =
                                            List(7) {
                                                CycleTarget(restSeconds = if (it == 0) 0 else null)
                                            },
                                    )
                                )
                        )
                    ),
            )
        val defaults = mapOf("press" to ExerciseDefaults(restSeconds = 75))
        val w = p.toWorkout(p.days.first().id, 0, 1000, defaults)!!
        val e = w.exercises.single()
        val entered =
            w.copy(
                exercises =
                    listOf(e.copy(sets = listOf(e.sets.single().copy(weightKg = 37.5, reps = 9))))
            )
        assertEquals(
            0L,
            entered.toggleSetCompletion(e.id, e.sets.single().id, Preferences(), 2000).restUntil,
        )
        val fallback = p.toWorkout(p.days.first().id, 1, 1000, defaults)!!
        assertEquals(75, fallback.exercises.single().restSeconds)
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

    @Test
    fun resizingKeepsAsymmetricTargetsAndExtendsOnlyTheFinalPrescription() {
        val cycles =
            List(3) { index ->
                CycleTarget(
                    listOf(SetTarget(id = "set-$index", repsMin = 5 + index, repsMax = 9 + index)),
                    skip = index == 1,
                    restSeconds = if (index == 2) 0 else null,
                )
            }
        val plan =
            TrainingProgram(
                name = "Variable",
                cycleCount = 3,
                icon = "diamond",
                cycleUnit = ProgramCycleUnit.WEEK,
                days =
                    listOf(
                        ProgramDay(
                            id = "upper",
                            exercises =
                                listOf(PlannedExercise(exerciseId = "press", cycles = cycles)),
                        ),
                        ProgramDay(id = "rest"),
                    ),
            )
        val expanded = plan.resizeCycles(5)
        val targets = expanded.days.first().exercises.single().cycles
        assertEquals(cycles, targets.take(3))
        assertEquals(listOf(7, 7), targets.drop(3).map { it.sets.single().repsMin })
        assertTrue(targets.drop(3).all { it.restSeconds == 0 && !it.skip })
        assertNotEquals(targets[2].sets.single().id, targets[3].sets.single().id)
        assertNotEquals(targets[3].sets.single().id, targets[4].sets.single().id)
        assertEquals(
            cycles.take(2),
            expanded.resizeCycles(2).days.first().exercises.single().cycles,
        )
        assertEquals(plan, ProgramCodec.decode(ProgramCodec.encode(plan)))
        assertEquals(expanded, ProgramCodec.decode(ProgramCodec.encode(expanded)))
        assertEquals(3, plan.cycleCount)
        val legacy =
            ProgramCodec.encode(plan).also {
                it.remove("icon")
                it.remove("cycleUnit")
            }
        val restored = ProgramCodec.decode(legacy)
        assertEquals("rocket", restored.icon)
        assertEquals(ProgramCycleUnit.CYCLE, restored.cycleUnit)
    }

    @Test
    fun targetSavingScopesChangeOnlySelectedInstancesAndCycles() {
        val catalog =
            listOf(
                Exercise("press", "Press", "Chest", "Barbell"),
                Exercise(
                    "hold",
                    "Hold",
                    "Abs",
                    "Bodyweight",
                    properties = mapOf("Metrics" to "Duration"),
                ),
            )
        fun entry(id: String, min: Int) =
            PlannedExercise(
                id = id,
                exerciseId = "press",
                cycles =
                    List(3) { index ->
                        CycleTarget(
                            listOf(SetTarget(id = "$id-$index", repsMin = min + index)),
                            restSeconds = 75,
                        )
                    },
            )
        val plan =
            TrainingProgram(
                name = "Scopes",
                cycleCount = 3,
                days =
                    listOf(
                        ProgramDay(
                            id = "upper",
                            exercises = listOf(entry("source", 5), entry("second", 11)),
                        ),
                        ProgramDay(
                            id = "lower",
                            exercises = listOf(entry("third", 17).copy(exerciseId = "hold")),
                        ),
                    ),
            )
        val changed =
            CycleTarget(
                listOf(SetTarget(id = "new", repsMin = 8, repsMax = 13, rir = 1)),
                skip = true,
                restSeconds = 0,
            )
        val single =
            plan.withTargets(
                "upper",
                "source",
                mapOf(1 to changed),
                TargetSaveScope.EXERCISE,
                catalog,
            )
        assertEquals(changed, single.days[0].exercises[0].cycles[1])
        assertEquals(plan.days[0].exercises[0].cycles[0], single.days[0].exercises[0].cycles[0])
        assertEquals(plan.days[0].exercises[1], single.days[0].exercises[1])
        assertEquals(plan.days[1], single.days[1])
        val day =
            plan.withTargets("upper", "source", mapOf(1 to changed), TargetSaveScope.DAY, catalog)
        assertEquals(8, day.days[0].exercises[1].cycles[1].sets.single().repsMin)
        assertEquals("second-1", day.days[0].exercises[1].cycles[1].sets.single().id)
        assertTrue(day.days[0].exercises[1].cycles[1].skip)
        assertEquals(0, day.days[0].exercises[1].cycles[1].restSeconds)
        assertEquals(plan.days[1], day.days[1])
        val all =
            plan.withTargets(
                "upper",
                "source",
                mapOf(1 to changed, 2 to changed),
                TargetSaveScope.PROGRAM,
                catalog,
            )
        all.days
            .flatMap { it.exercises }
            .forEach { exercise ->
                val expected = if (exercise.exerciseId == "hold") null else 8
                assertEquals(
                    listOf(expected, expected),
                    exercise.cycles.drop(1).map { it.sets.single().repsMin },
                )
                if (exercise.exerciseId == "hold")
                    exercise.cycles.drop(1).forEach {
                        assertNull(it.sets.single().repsMax)
                        assertNull(it.sets.single().rir)
                        assertTrue(it.skip)
                        assertEquals(0, it.restSeconds)
                    }
            }
        assertEquals(
            plan.days[1].exercises.single().cycles[0],
            all.days[1].exercises.single().cycles[0],
        )
        assertEquals(
            plan,
            plan.withTargets("upper", "source", emptyMap(), TargetSaveScope.PROGRAM, catalog),
        )
        assertEquals(all, ProgramCodec.decode(ProgramCodec.encode(all)))
    }
}
