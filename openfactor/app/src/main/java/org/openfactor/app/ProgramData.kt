package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

/** Manual plan data. Defaults come from the captured scratch draft, not the generator. */
data class SetTarget(
    val id: String = newId(),
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val rir: Int? = null,
)

data class CycleTarget(
    val sets: List<SetTarget> = listOf(SetTarget()),
    val skip: Boolean = false,
    val restSeconds: Int? = null,
)

data class PlannedExercise(
    val id: String = newId(),
    val exerciseId: String,
    val cycles: List<CycleTarget> = List(7) { CycleTarget() },
)

data class ProgramDay(
    val id: String = newId(),
    val name: String = "Rest",
    val gymId: String? = null,
    val exercises: List<PlannedExercise> = emptyList(),
)

/** Presentation provenance, not an inference that every cycle lasts seven days. */
enum class ProgramCycleUnit(val singular: String, val plural: String) {
    CYCLE("Cycle", "Cycles"),
    WEEK("Week", "Weeks"),
}

data class TrainingProgram(
    val id: String = newId(),
    val name: String,
    val color: Long = 0xFFFF2F4A,
    val days: List<ProgramDay> = listOf(ProgramDay()),
    val cycleCount: Int = 7,
    val icon: String = "rocket",
    val cycleUnit: ProgramCycleUnit = ProgramCycleUnit.CYCLE,
)

/** Local resize policy: keep existing cycles; new cycles copy the final prescription. */
fun TrainingProgram.resizeCycles(count: Int): TrainingProgram {
    require(count in 1..52) { "Program cycles must be between 1 and 52" }
    return copy(
        cycleCount = count,
        days =
            days.map { day ->
                day.copy(
                    exercises =
                        day.exercises.map { exercise ->
                            exercise.copy(
                                cycles =
                                    List(count) { index ->
                                        exercise.cycles.getOrNull(index)
                                            ?: exercise.cycles.last().let { target ->
                                                target.copy(
                                                    sets = target.sets.map { it.copy(id = newId()) }
                                                )
                                            }
                                    }
                            )
                        }
                )
            },
    )
}

enum class TargetSaveScope {
    EXERCISE,
    DAY,
    PROGRAM,
}

/** Explicit local replacement policy; source scope controls are observed, save outcomes are not. */
fun TrainingProgram.withTargets(
    dayId: String,
    plannedId: String,
    targets: Map<Int, CycleTarget>,
    scope: TargetSaveScope,
    exercises: List<Exercise>,
): TrainingProgram {
    require(targets.keys.all { it in 0 until cycleCount }) { "Invalid target cycle" }
    val source = days.find { it.id == dayId }?.exercises?.find { it.id == plannedId } ?: return this
    return copy(
        days =
            days.map day@{ day ->
                if (scope != TargetSaveScope.PROGRAM && day.id != dayId) return@day day
                day.copy(
                    exercises =
                        day.exercises.map exercise@{ exercise ->
                            val sameEntry = day.id == dayId && exercise.id == source.id
                            if (scope == TargetSaveScope.EXERCISE && !sameEntry)
                                return@exercise exercise
                            val supportsReps =
                                exercises
                                    .find { it.id == exercise.exerciseId }
                                    ?.loggingMetrics()
                                    ?.any { it.isReps } != false
                            val cycles =
                                exercise.cycles.mapIndexed { index, old ->
                                    val target = targets[index] ?: return@mapIndexed old
                                    val sets =
                                        target.sets.mapIndexed { setIndex, set ->
                                            set.copy(
                                                id =
                                                    if (sameEntry) set.id
                                                    else
                                                        old.sets.getOrNull(setIndex)?.id ?: newId(),
                                                repsMin = set.repsMin.takeIf { supportsReps },
                                                repsMax = set.repsMax.takeIf { supportsReps },
                                                rir = set.rir.takeIf { supportsReps },
                                            )
                                        }
                                    target.copy(sets = sets)
                                }
                            exercise.copy(cycles = cycles)
                        }
                )
            }
    )
}

fun ProgramDay.addExercise(exerciseId: String, cycleCount: Int, workoutName: String): ProgramDay =
    copy(
        name = if (exercises.isEmpty() && name == "Rest") workoutName else name,
        exercises =
            exercises +
                PlannedExercise(
                    exerciseId = exerciseId,
                    cycles = List(cycleCount) { CycleTarget() },
                ),
    )

fun TrainingProgram.toWorkout(
    dayId: String,
    cycle: Int,
    now: Long,
    defaults: Map<String, ExerciseDefaults> = emptyMap(),
): Workout? {
    val day = days.find { it.id == dayId } ?: return null
    if (cycle !in 0 until cycleCount) return null
    val session =
        day.exercises.mapNotNull { planned ->
            val targets = planned.cycles.getOrNull(cycle) ?: return@mapNotNull null
            if (targets.skip || targets.sets.isEmpty()) return@mapNotNull null
            newSessionExercise(
                exerciseId = planned.exerciseId,
                defaults = defaults[planned.exerciseId],
                sets =
                    targets.sets.map {
                        LoggedSet(
                            targetRepsMin = it.repsMin,
                            targetRepsMax = it.repsMax,
                            targetRir = it.rir,
                        )
                    },
                restSeconds = targets.restSeconds,
            )
        }
    if (session.isEmpty()) return null
    return Workout(
        name = day.name,
        startedAt = now,
        exercises = session,
        programId = id,
        selectedExerciseId = session.first().id,
        programDayId = day.id,
        programCycle = cycle,
        gymId = day.gymId,
    )
}

object ProgramCodec {
    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.nullInt(key: String): Int? = if (isNull(key)) null else getInt(key)

    fun encode(p: TrainingProgram): JSONObject =
        JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("color", p.color)
            .put("cycleCount", p.cycleCount)
            .put("icon", p.icon)
            .put("cycleUnit", p.cycleUnit.name)
            .put(
                "days",
                JSONArray(
                    p.days.map { day ->
                        JSONObject()
                            .put("id", day.id)
                            .put("name", day.name)
                            .put("gymId", day.gymId)
                            .put(
                                "exercises",
                                JSONArray(
                                    day.exercises.map { e ->
                                        JSONObject()
                                            .put("id", e.id)
                                            .put("exerciseId", e.exerciseId)
                                            .put(
                                                "cycles",
                                                JSONArray(
                                                    e.cycles.map { c ->
                                                        JSONObject()
                                                            .put("skip", c.skip)
                                                            .put("restSeconds", c.restSeconds)
                                                            .put(
                                                                "sets",
                                                                JSONArray(
                                                                    c.sets.map { s ->
                                                                        JSONObject()
                                                                            .put("id", s.id)
                                                                            .put(
                                                                                "repsMin",
                                                                                s.repsMin,
                                                                            )
                                                                            .put(
                                                                                "repsMax",
                                                                                s.repsMax,
                                                                            )
                                                                            .put("rir", s.rir)
                                                                    }
                                                                ),
                                                            )
                                                    }
                                                ),
                                            )
                                    }
                                ),
                            )
                    }
                ),
            )

    fun decode(j: JSONObject): TrainingProgram {
        val count = j.optInt("cycleCount", 7)
        require(count in 1..52) { "Program cycles must be between 1 and 52" }
        val days =
            j.getJSONArray("days").objects().map { day ->
                ProgramDay(
                    day.getString("id"),
                    day.getString("name"),
                    day.optString("gymId").takeIf { it.isNotBlank() && it != "null" },
                    day.getJSONArray("exercises").objects().map { e ->
                        val cycles =
                            e.getJSONArray("cycles").objects().map { c ->
                                val rest = c.nullInt("restSeconds")
                                require(rest == null || rest in 0..1800) {
                                    "Invalid program rest timer"
                                }
                                CycleTarget(
                                    c.getJSONArray("sets").objects().map { s ->
                                        val min = s.nullInt("repsMin")
                                        val max = s.nullInt("repsMax")
                                        val rir = s.nullInt("rir")
                                        require(min == null || min in 1..9999) {
                                            "Invalid minimum reps"
                                        }
                                        require(max == null || max in 1..9999) {
                                            "Invalid maximum reps"
                                        }
                                        require(min == null || max == null || min <= max) {
                                            "Minimum reps exceeds maximum"
                                        }
                                        require(rir == null || rir in 0..10) {
                                            "Invalid target RIR"
                                        }
                                        SetTarget(s.getString("id"), min, max, rir)
                                    },
                                    c.optBoolean("skip"),
                                    rest,
                                )
                            }
                        require(cycles.size == count) { "Program exercise cycle count differs" }
                        PlannedExercise(e.getString("id"), e.getString("exerciseId"), cycles)
                    },
                )
            }
        days.forEach { day ->
            require(day.exercises.map { it.id }.distinct().size == day.exercises.size) {
                "Duplicate program exercise"
            }
            day.exercises.forEach { exercise ->
                exercise.cycles.forEach { cycle ->
                    require(cycle.sets.map { it.id }.distinct().size == cycle.sets.size) {
                        "Duplicate program set"
                    }
                }
            }
        }
        require(days.isNotEmpty()) { "Program needs at least one day" }
        require(days.map { it.id }.distinct().size == days.size) { "Duplicate program day" }
        return TrainingProgram(
            j.getString("id"),
            j.getString("name"),
            j.optLong("color", 0xFFFF2F4A),
            days,
            count,
            j.optString("icon", "rocket"),
            j.optString("cycleUnit", "CYCLE").let { value ->
                require(value in ProgramCycleUnit.entries.map { it.name }) { "Invalid cycle unit" }
                ProgramCycleUnit.valueOf(value)
            },
        )
    }
}

/** Scope navigation never writes targets; only an explicit edit may update its selected scope. */
fun applyCycleTargetEdit(
    cycles: List<CycleTarget>,
    scope: Int,
    target: CycleTarget,
    edited: Boolean,
): List<CycleTarget> {
    if (!edited) return cycles
    require(scope == -1 || scope in cycles.indices) { "Invalid target scope" }
    return cycles.mapIndexed { index, old -> if (scope == -1 || scope == index) target else old }
}

fun List<CycleTarget>.haveEquivalentTargets(): Boolean =
    map { it.copy(sets = it.sets.map { s -> s.copy(id = "") }) }.distinct().size <= 1
