package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ProgramMaterializerTest {
    private fun exercise(
        id: String,
        sets: Int,
        role: ProgramExerciseRole,
        pull: Boolean,
        reps: Boolean = true,
        patterns: String = if (pull) "Squat, Vertical Pull" else "Horizontal Push",
    ) =
        SelectedProgramExercise(
            Exercise(
                id = id,
                name = "A name that does not classify the movement",
                muscle = "Abs",
                equipment = "Unrelated equipment label",
                properties =
                    mapOf(
                        // Multiple source facts distinguish any from first-element/all predicates.
                        "Movement Patterns" to patterns,
                        "Metrics" to if (reps) "Weight per side, Reps per side" else "Duration",
                    ),
            ),
            sets,
            role,
        )

    private fun program(
        goal: ProgramGoal,
        periodization: ProgramPeriodization,
        exercises: List<SelectedProgramExercise>,
    ) =
        materializeProgram(
            name = "Selected plan",
            color = 0xFF123456,
            gymId = "home",
            goal = goal,
            periodization = periodization,
            trainingDays =
                listOf(
                    SelectedProgramDay("Upper", exercises),
                    SelectedProgramDay("Lower", exercises.take(1)),
                ),
        )

    @Test
    fun capturedStaticTargetsBecomeEditableProgramAndBlankWorkoutActuals() {
        // Container-associated source capture: press 7–9/3,2,1; row 9–11/2,1,0.
        // These are selected templates, not a claim that this test recovers their selection.
        val generated =
            program(
                ProgramGoal.HYPERTROPHY,
                ProgramPeriodization.STATIC,
                listOf(
                    exercise("pause-bench", 3, ProgramExerciseRole.PRIMARY, false),
                    exercise(
                        "barbell-row",
                        3,
                        ProgramExerciseRole.PRIMARY,
                        true,
                        patterns = "Horizontal Pull",
                    ),
                ),
            )
        val saved = ProgramCodec.decode(ProgramCodec.encode(generated))
        assertEquals(generated, saved)
        assertEquals(ProgramCycleUnit.WEEK, saved.cycleUnit)
        assertEquals(7, saved.cycleCount)
        assertEquals(
            listOf("Upper", "Rest", "Rest", "Lower", "Rest", "Rest", "Rest"),
            saved.days.map { it.name },
        )
        assertTrue(saved.days.all { it.gymId == "home" })
        assertTrue(
            saved.days
                .flatMap { it.exercises }
                .flatMap { it.cycles }
                .all { !it.skip && it.restSeconds == null }
        )
        val selectedDay = saved.days.first()
        val workout = saved.toWorkout(selectedDay.id, 5, 1234L)!!
        assertEquals(saved.id, workout.programId)
        assertEquals(selectedDay.id, workout.programDayId)
        assertEquals(5, workout.programCycle)
        assertEquals("home", workout.gymId)
        assertEquals(listOf("pause-bench", "barbell-row"), workout.exercises.map { it.exerciseId })
        assertEquals(listOf(7, 7, 7), workout.exercises[0].sets.map { it.targetRepsMin })
        assertEquals(listOf(9, 9, 9), workout.exercises[0].sets.map { it.targetRepsMax })
        assertEquals(listOf(3, 2, 1), workout.exercises[0].sets.map { it.targetRir })
        assertEquals(listOf(9, 9, 9), workout.exercises[1].sets.map { it.targetRepsMin })
        assertEquals(listOf(11, 11, 11), workout.exercises[1].sets.map { it.targetRepsMax })
        assertEquals(listOf(2, 1, 0), workout.exercises[1].sets.map { it.targetRir })
        assertTrue(
            workout.exercises
                .flatMap { it.sets }
                .all {
                    it.reps == null &&
                        it.rir == null &&
                        it.weightKg == null &&
                        it.assistanceKg == null &&
                        it.durationSeconds == null &&
                        it.distanceMeters == null &&
                        !it.completed
                }
        )
        assertNull(saved.toWorkout(saved.days[1].id, 0, 1234L))
    }

    @Test
    fun frequencyPlacesOrderedSelectedSplitDaysWithoutInventingSpacing() {
        // Directly transcribed tagged-Smi stores at ecf9ac–ecfba0, not an even-spacing formula.
        val expected =
            mapOf(
                2 to listOf("A", "Rest", "Rest", "B", "Rest", "Rest", "Rest"),
                3 to listOf("A", "Rest", "B", "Rest", "C", "Rest", "Rest"),
                4 to listOf("A", "Rest", "B", "Rest", "C", "D", "Rest"),
                5 to listOf("A", "B", "C", "D", "E", "Rest", "Rest"),
                6 to listOf("A", "B", "C", "D", "E", "F", "Rest"),
            )
        for ((frequency, names) in expected) {
            val days =
                List(frequency) { index ->
                    SelectedProgramDay(
                        ('A' + index).toString(),
                        listOf(
                            exercise(
                                "chosen-$index",
                                index + 1,
                                ProgramExerciseRole.SECONDARY,
                                false,
                            )
                        ),
                    )
                }
            val p =
                materializeProgram(
                    "Chosen split",
                    0xFF123456,
                    null,
                    ProgramGoal.BOTH,
                    ProgramPeriodization.UNDULATING,
                    days,
                )
            assertEquals("frequency=$frequency", names, p.days.map { it.name })
            assertEquals(
                days.flatMap { it.exercises }.map { it.exercise.id },
                p.days.flatMap { it.exercises }.map { it.exerciseId },
            )
            assertEquals(
                (1..frequency).toList(),
                p.days.flatMap { it.exercises }.map { it.cycles.first().sets.size },
            )
            assertTrue(p.days.all { it.gymId == null })
        }
        for (frequency in listOf(1, 7)) {
            assertThrows(IllegalArgumentException::class.java) {
                materializeProgram(
                    "Unsupported",
                    0L,
                    null,
                    ProgramGoal.BOTH,
                    ProgramPeriodization.STATIC,
                    List(frequency) { SelectedProgramDay("A", emptyList()) },
                )
            }
        }
    }

    @Test
    fun goalsRolesAndPullFactsHaveDistinctStaticTargets() {
        // Six columns: primary non-pull/pull, secondary non-pull/pull, accessory non-pull/pull.
        // Independent branch results from ed4648–ed4f3c; BOTH is not an average of the goals.
        val expected =
            mapOf(
                ProgramGoal.HYPERTROPHY to listOf(8, 10, 10, 12, 15, 15),
                ProgramGoal.STRENGTH to listOf(5, 8, 8, 10, 10, 10),
                ProgramGoal.BOTH to listOf(5, 8, 10, 12, 15, 15),
            )
        val selected =
            ProgramExerciseRole.entries.flatMap { role ->
                listOf(false, true).map { pull -> exercise("$role-$pull", 1, role, pull) }
            }
        for ((goal, centers) in expected) {
            val firstDay = program(goal, ProgramPeriodization.STATIC, selected).days.first()
            assertEquals(
                centers.map { it - 1 },
                firstDay.exercises.map { it.cycles[0].sets.single().repsMin },
            )
            assertEquals(
                centers.map { it + 1 },
                firstDay.exercises.map { it.cycles[6].sets.single().repsMax },
            )
            assertEquals(
                listOf(2, 1, 2, 1, 0, 0),
                firstDay.exercises.map { it.cycles[4].sets.single().rir },
            )
        }
    }

    @Test
    fun periodizationUsesSourceWeekOrderAndDoesNotVaryRirWithWeek() {
        // Expected centers are independently expanded from native branch literals/index stores.
        data class Fixture(
            val goal: ProgramGoal,
            val periodization: ProgramPeriodization,
            val selected: SelectedProgramExercise,
            val centers: List<Int>,
            val rir: List<Int>,
        )
        val fixtures =
            listOf(
                Fixture(
                    ProgramGoal.STRENGTH,
                    ProgramPeriodization.LINEAR,
                    exercise("squat", 6, ProgramExerciseRole.PRIMARY, false),
                    listOf(8, 8, 6, 5, 5, 3, 3),
                    listOf(3, 3, 2, 2, 1, 1),
                ),
                Fixture(
                    ProgramGoal.STRENGTH,
                    ProgramPeriodization.REVERSE_LINEAR,
                    exercise("secondary-push", 4, ProgramExerciseRole.SECONDARY, false),
                    listOf(5, 5, 7, 7, 8, 10, 10),
                    listOf(3, 2, 2, 1),
                ),
                Fixture(
                    ProgramGoal.BOTH,
                    ProgramPeriodization.UNDULATING,
                    exercise("chin-up", 2, ProgramExerciseRole.PRIMARY, true),
                    listOf(12, 9, 6, 12, 8, 10, 6),
                    listOf(1, 0),
                ),
                Fixture(
                    ProgramGoal.HYPERTROPHY,
                    ProgramPeriodization.REVERSE_LINEAR,
                    exercise("cable-curl", 5, ProgramExerciseRole.ACCESSORY, true),
                    listOf(10, 10, 13, 15, 17, 20, 20),
                    listOf(1, 0, 0, 0, 0),
                ),
                Fixture(
                    ProgramGoal.HYPERTROPHY,
                    ProgramPeriodization.LINEAR,
                    exercise("secondary-row", 3, ProgramExerciseRole.SECONDARY, true),
                    listOf(15, 15, 13, 11, 10, 8, 8),
                    listOf(2, 1, 0),
                ),
            )
        for (fixture in fixtures) {
            val plan = program(fixture.goal, fixture.periodization, listOf(fixture.selected))
            val cycles = plan.days.first().exercises.single().cycles
            assertEquals(fixture.centers.map { it - 1 }, cycles.map { it.sets.first().repsMin })
            assertEquals(fixture.centers.map { it + 1 }, cycles.map { it.sets.last().repsMax })
            assertTrue(cycles.all { it.sets.map { set -> set.rir } == fixture.rir })
        }
    }

    @Test
    fun nonRepMetricsKeepAllocatedSetsButDoNotAcquireRepOrRirTargets() {
        val p =
            program(
                ProgramGoal.STRENGTH,
                ProgramPeriodization.LINEAR,
                listOf(
                    exercise("timed-hold", 3, ProgramExerciseRole.PRIMARY, true, reps = false),
                    exercise("rep-accessory", 3, ProgramExerciseRole.ACCESSORY, false),
                ),
            )
        val hold = p.days.first().exercises.first()
        assertTrue(
            hold.cycles.all { cycle ->
                cycle.sets.size == 3 &&
                    cycle.sets.all { it.repsMin == null && it.repsMax == null && it.rir == null }
            }
        )
        assertEquals(listOf(1, 0, 0), p.days.first().exercises[1].cycles[0].sets.map { it.rir })
        for (sets in listOf(0, 7)) {
            assertThrows(IllegalArgumentException::class.java) {
                program(
                    ProgramGoal.STRENGTH,
                    ProgramPeriodization.STATIC,
                    listOf(exercise("unsupported", sets, ProgramExerciseRole.PRIMARY, false)),
                )
            }
        }
    }

    @Test
    fun absentFactsAreNotTheSameAsAnExplicitEmptyMovementOrMetricList() {
        val selected = exercise("explicit-none", 2, ProgramExerciseRole.PRIMARY, false)
        val none =
            selected.copy(
                exercise =
                    selected.exercise.copy(
                        properties = mapOf("Movement Patterns" to "None", "Metrics" to "None")
                    )
            )
        val p = program(ProgramGoal.BOTH, ProgramPeriodization.STATIC, listOf(none))
        assertTrue(
            p.days.first().exercises.single().cycles.all { cycle ->
                cycle.sets.size == 2 &&
                    cycle.sets.all { it.repsMin == null && it.repsMax == null && it.rir == null }
            }
        )
        for (field in listOf("Movement Patterns", "Metrics")) {
            val unknown =
                selected.copy(
                    exercise =
                        selected.exercise.copy(properties = selected.exercise.properties - field)
                )
            assertThrows(IllegalArgumentException::class.java) {
                program(ProgramGoal.BOTH, ProgramPeriodization.STATIC, listOf(unknown))
            }
        }
    }
}
