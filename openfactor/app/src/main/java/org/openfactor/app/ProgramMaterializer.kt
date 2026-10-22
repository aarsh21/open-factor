package org.openfactor.app

enum class ProgramGoal {
    HYPERTROPHY,
    STRENGTH,
    BOTH,
}

enum class ProgramPeriodization {
    STATIC,
    LINEAR,
    REVERSE_LINEAR,
    UNDULATING,
}

enum class ProgramExerciseRole {
    PRIMARY,
    SECONDARY,
    ACCESSORY,
}

/**
 * Output of exercise selection, not an exercise-selection request.
 *
 * The caller supplies the allocated working-set count, source template role, and exercise facts.
 * Movement Patterns and Metrics must be present. Neither is inferred from names, muscles, joint
 * actions, equipment, or searchBoost.
 */
data class SelectedProgramExercise(
    val exercise: Exercise,
    val workingSets: Int,
    val role: ProgramExerciseRole,
)

/** Ordered training days after split resolution, exercise ranking and time/focus allocation. */
data class SelectedProgramDay(val name: String, val exercises: List<SelectedProgramExercise>)

/**
 * Materializes the recovered normal seven-week block into the editable/logger target model.
 *
 * This is not the complete smart generator: it does not select exercises or allocate sets. Goal and
 * periodization must already be resolved. There are deliberately no ignored equipment, focus,
 * duration, experience, or No preference inputs. Deload and history transforms are separate.
 *
 * Source bounds: calendar ecf9ac–ecfba0, block length ecfc48–ecfc7c, targets ed42f4–ed5858. See
 * docs/open-factor/SMART-GENERATION.md for evidence and unresolved upstream contracts.
 */
fun materializeProgram(
    name: String,
    color: Long,
    gymId: String?,
    goal: ProgramGoal,
    periodization: ProgramPeriodization,
    trainingDays: List<SelectedProgramDay>,
): TrainingProgram {
    val placement =
        when (trainingDays.size) {
            2 -> listOf(0, null, null, 1, null, null, null)
            3 -> listOf(0, null, 1, null, 2, null, null)
            4 -> listOf(0, null, 1, null, 2, 3, null)
            5 -> listOf(0, 1, 2, 3, 4, null, null)
            6 -> listOf(0, 1, 2, 3, 4, 5, null)
            else -> throw IllegalArgumentException("Programs support 2 through 6 workout days")
        }
    return TrainingProgram(
        name = name,
        color = color,
        days =
            placement.map { index ->
                if (index == null) {
                    ProgramDay(gymId = gymId)
                } else {
                    val day = trainingDays[index]
                    ProgramDay(
                        name = day.name,
                        gymId = gymId,
                        exercises = day.exercises.map { it.materialize(goal, periodization) },
                    )
                }
            },
        cycleCount = 7,
        cycleUnit = ProgramCycleUnit.WEEK,
    )
}

private fun SelectedProgramExercise.materialize(
    goal: ProgramGoal,
    periodization: ProgramPeriodization,
): PlannedExercise {
    val patterns =
        requireNotNull(exercise.movementPatterns) {
            "Missing movement-pattern facts for ${exercise.id}"
        }
    require(exercise.properties.containsKey("Metrics")) {
        "Missing metric facts for ${exercise.id}"
    }
    val hasPullPattern = patterns.any { it == "Horizontal Pull" || it == "Vertical Pull" }
    val hasRepMetric = exercise.loggingMetrics().any { it.isReps }
    val offsets =
        when (workingSets) {
            1 -> listOf(0)
            2 -> listOf(0, -1)
            3 -> listOf(1, 0, -1)
            4 -> listOf(1, 0, 0, -1)
            5 -> listOf(1, 0, 0, -1, -1)
            6 -> listOf(1, 1, 0, 0, -1, -1)
            else -> throw IllegalArgumentException("Expected between 1 and 6 sets")
        }
    val (staticCenter, centers) =
        when (role) {
            ProgramExerciseRole.ACCESSORY ->
                if (goal == ProgramGoal.STRENGTH) {
                    10 to listOf(8, 10, 10, 11, 13, 13, 15)
                } else {
                    15 to listOf(10, 13, 13, 15, 17, 18, 20)
                }
            ProgramExerciseRole.PRIMARY ->
                if (goal == ProgramGoal.HYPERTROPHY) {
                    if (hasPullPattern) {
                        10 to listOf(8, 10, 10, 11, 13, 13, 15)
                    } else {
                        8 to listOf(6, 8, 8, 9, 10, 11, 12)
                    }
                } else {
                    if (hasPullPattern) {
                        8 to listOf(6, 8, 8, 9, 10, 11, 12)
                    } else {
                        5 to listOf(3, 4, 5, 5, 6, 7, 8)
                    }
                }
            ProgramExerciseRole.SECONDARY ->
                if (goal == ProgramGoal.STRENGTH) {
                    if (hasPullPattern) {
                        10 to listOf(8, 10, 10, 11, 13, 13, 15)
                    } else {
                        8 to listOf(5, 6, 7, 7, 8, 9, 10)
                    }
                } else {
                    (if (hasPullPattern) 12 else 10) to listOf(8, 10, 10, 11, 13, 13, 15)
                }
        }
    val weeklyCenters =
        when (periodization) {
            ProgramPeriodization.STATIC -> List(7) { staticCenter }
            ProgramPeriodization.LINEAR -> listOf(6, 6, 4, 3, 2, 0, 0).map { centers[it] }
            ProgramPeriodization.REVERSE_LINEAR -> listOf(0, 0, 2, 3, 4, 6, 6).map { centers[it] }
            ProgramPeriodization.UNDULATING -> listOf(6, 3, 0, 6, 2, 4, 0).map { centers[it] }
        }
    val rirBase =
        when {
            role == ProgramExerciseRole.ACCESSORY -> 0
            hasPullPattern -> 1
            else -> 2
        }
    return PlannedExercise(
        exerciseId = exercise.id,
        cycles =
            weeklyCenters.map { center ->
                CycleTarget(
                    sets =
                        offsets.map { offset ->
                            SetTarget(
                                repsMin = if (hasRepMetric) center - 1 else null,
                                repsMax = if (hasRepMetric) center + 1 else null,
                                rir =
                                    if (hasRepMetric) (rirBase + offset).coerceAtLeast(0) else null,
                            )
                        }
                )
            },
    )
}
