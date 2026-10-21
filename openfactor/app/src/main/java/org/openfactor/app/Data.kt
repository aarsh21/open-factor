package org.openfactor.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The public catalog contains factual exercise names. Imported media has its own provenance. */
data class Exercise(
    val id: String,
    val name: String,
    val muscle: String,
    val equipment: String,
    val secondary: List<String> = emptyList(),
    val frames: List<String> = emptyList(),
    val instructions: String = "",
    val video: String? = null,
    val custom: Boolean = false,
    val muscles: List<String> = listOf(muscle),
    val aliases: List<String> = emptyList(),
    val equipmentOptions: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val bodyweight: Double = 0.0,
    val searchBoost: Double = 0.0,
    val clips: List<String> = emptyList(),
    val browseCategories: List<String> = emptyList(),
)

data class LoggedSet(
    val id: String = newId(),
    val weightKg: Double = 0.0,
    val reps: Int = 8,
    val rir: Int? = null,
    val completed: Boolean = false,
    val warmup: Boolean = false,
)

data class SessionExercise(
    val id: String = newId(),
    val exerciseId: String,
    val sets: List<LoggedSet> = listOf(LoggedSet(), LoggedSet(), LoggedSet()),
    val notes: String = "",
)

data class Workout(
    val id: String = newId(),
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val notes: String = "",
    val restUntil: Long = 0,
    val programId: String? = null,
    val selectedExerciseId: String? = null,
    val pausedAt: Long? = null,
    val pausedDurationMs: Long = 0,
)

data class Routine(val id: String = newId(), val name: String, val exerciseIds: List<String>)

data class Preferences(
    val pounds: Boolean = false,
    val restSeconds: Int = 120,
    val autoRest: Boolean = true,
    val showRir: Boolean = true,
    val displayName: String = "Athlete",
    val dashboardAllWorkouts: Boolean = false,
    val showWeeklyWorkouts: Boolean = true,
    val showInsights: Boolean = true,
    val showHabits: Boolean = true,
)

data class AppData(
    val active: Workout? = null,
    val history: List<Workout> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val customExercises: List<Exercise> = emptyList(),
    val preferences: Preferences = Preferences(),
    val activeProgramId: String? = null,
    val gyms: List<GymProfile> = emptyList(),
)

fun newId() = UUID.randomUUID().toString()

fun Workout.completedSets() = exercises.sumOf { e -> e.sets.count { it.completed } }

fun Workout.volumeKg() =
    exercises.sumOf { e ->
        e.sets.filter { it.completed && !it.warmup }.sumOf { it.weightKg * it.reps }
    }

fun Workout.localDate(): LocalDate =
    Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

fun Workout.elapsedSeconds(now: Long): Long =
    (((finishedAt ?: pausedAt ?: now) - startedAt - pausedDurationMs) / 1000).coerceAtLeast(0)

fun Workout.withPause(paused: Boolean, now: Long): Workout =
    when {
        paused && pausedAt == null -> copy(pausedAt = now)
        !paused && pausedAt != null ->
            copy(
                pausedAt = null,
                pausedDurationMs = pausedDurationMs + (now - pausedAt).coerceAtLeast(0),
            )
        else -> this
    }

fun Double.displayWeight(pounds: Boolean): String {
    val value = if (pounds) this * 2.2046226218 else this
    return if (value % 1.0 < 0.005) value.toLong().toString()
    else "%.1f".format(java.util.Locale.US, value)
}

fun String.toWeightKg(pounds: Boolean): Double? =
    replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 && it <= 5000 }
        ?.let { if (pounds) it / 2.2046226218 else it }

object DataCodec {
    private fun LoggedSet.json() =
        JSONObject()
            .put("id", id)
            .put("weightKg", weightKg)
            .put("reps", reps)
            .put("rir", rir)
            .put("completed", completed)
            .put("warmup", warmup)

    private fun SessionExercise.json() =
        JSONObject()
            .put("id", id)
            .put("exerciseId", exerciseId)
            .put("sets", JSONArray(sets.map { it.json() }))
            .put("notes", notes)

    private fun Workout.json() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("startedAt", startedAt)
            .put("finishedAt", finishedAt)
            .put("restUntil", restUntil)
            .put("programId", programId)
            .put("selectedExerciseId", selectedExerciseId)
            .put("pausedAt", pausedAt)
            .put("pausedDurationMs", pausedDurationMs)
            .put("exercises", JSONArray(exercises.map { it.json() }))
            .put("notes", notes)

    private fun Exercise.json() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("muscle", muscle)
            .put("equipment", equipment)
            .put("instructions", instructions)
            .put("custom", custom)

    fun encode(d: AppData): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("activeProgramId", d.activeProgramId)
            .put("gyms", JSONArray(d.gyms.map { GymCodec.encode(it) }))
            .put("active", d.active?.json())
            .put("history", JSONArray(d.history.map { it.json() }))
            .put(
                "routines",
                JSONArray(
                    d.routines.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("name", it.name)
                            .put("exerciseIds", JSONArray(it.exerciseIds))
                    }
                ),
            )
            .put("favorites", JSONArray(d.favorites.toList()))
            .put("customExercises", JSONArray(d.customExercises.map { it.json() }))
            .put(
                "preferences",
                JSONObject()
                    .put("pounds", d.preferences.pounds)
                    .put("restSeconds", d.preferences.restSeconds)
                    .put("autoRest", d.preferences.autoRest)
                    .put("showRir", d.preferences.showRir)
                    .put("displayName", d.preferences.displayName)
                    .put("dashboardAllWorkouts", d.preferences.dashboardAllWorkouts)
                    .put("showWeeklyWorkouts", d.preferences.showWeeklyWorkouts)
                    .put("showInsights", d.preferences.showInsights)
                    .put("showHabits", d.preferences.showHabits),
            )
            .toString(2)

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun JSONObject.array(k: String) = optJSONArray(k) ?: JSONArray()

    private fun JSONObject.workout(): Workout =
        Workout(
            getString("id"),
            getString("name"),
            getLong("startedAt"),
            if (isNull("finishedAt")) null else getLong("finishedAt"),
            array("exercises").objects().map { e ->
                SessionExercise(
                    e.getString("id"),
                    e.getString("exerciseId"),
                    e.array("sets").objects().map { s ->
                        LoggedSet(
                            s.getString("id"),
                            s.optDouble("weightKg", 0.0).coerceIn(0.0, 5000.0),
                            s.optInt("reps", 8).coerceIn(0, 9999),
                            if (s.isNull("rir")) null else s.getInt("rir").coerceIn(0, 10),
                            s.optBoolean("completed"),
                            s.optBoolean("warmup"),
                        )
                    },
                    e.optString("notes"),
                )
            },
            optString("notes"),
            optLong("restUntil", 0),
            optString("programId").takeIf { it.isNotBlank() && it != "null" },
            optString("selectedExerciseId").takeIf { it.isNotBlank() && it != "null" },
            if (isNull("pausedAt")) null else getLong("pausedAt"),
            optLong("pausedDurationMs", 0).coerceAtLeast(0),
        )

    fun decode(text: String): AppData {
        val j = JSONObject(text)
        require(j.optInt("schemaVersion") == 1) { "Unsupported backup version" }
        val p = j.optJSONObject("preferences") ?: JSONObject()
        return AppData(
            j.optJSONObject("active")?.workout(),
            j.array("history").objects().map { it.workout() },
            j.array("routines").objects().map {
                Routine(it.getString("id"), it.getString("name"), it.array("exerciseIds").strings())
            },
            j.array("favorites").strings().toSet(),
            j.array("customExercises").objects().map {
                Exercise(
                    it.getString("id"),
                    it.getString("name"),
                    it.getString("muscle"),
                    it.getString("equipment"),
                    instructions = it.optString("instructions"),
                    custom = true,
                )
            },
            Preferences(
                p.optBoolean("pounds"),
                p.optInt("restSeconds", 120).coerceIn(0, 1800),
                p.optBoolean("autoRest", true),
                p.optBoolean("showRir", true),
                p.optString("displayName", "Athlete"),
                p.optBoolean("dashboardAllWorkouts"),
                p.optBoolean("showWeeklyWorkouts", true),
                p.optBoolean("showInsights", true),
                p.optBoolean("showHabits", true),
            ),
            j.optString("activeProgramId").takeIf { it.isNotBlank() && it != "null" },
            j.array("gyms").objects().map { GymCodec.decode(it) },
        )
    }

    fun catalog(text: String): List<Exercise> =
        JSONArray(text).objects().map { e ->
            val properties = e.optJSONObject("properties")
            Exercise(
                e.getString("id"),
                e.getString("name"),
                e.optString("muscle", "Other"),
                e.optString("equipment", "Other"),
                e.array("secondary").strings(),
                e.array("frames").strings(),
                e.optString("instructions"),
                e.optString("video").takeIf { it.isNotBlank() && it != "null" },
                e.optBoolean("custom"),
                e.array("muscles").strings().ifEmpty { listOf(e.optString("muscle", "Other")) },
                e.array("aliases").strings(),
                e.array("equipmentOptions").strings(),
                properties?.keys()?.asSequence()?.associateWith { properties.getString(it) }
                    ?: emptyMap(),
                e.optDouble("bodyweight", 0.0),
                e.optDouble("searchBoost", 0.0),
                e.array("clips").strings(),
                e.array("browseCategories").strings(),
            )
        }
}

class WorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("open_factor", Context.MODE_PRIVATE)

    fun load(): AppData =
        prefs.getString("data", null)?.let { runCatching { DataCodec.decode(it) }.getOrNull() }
            ?: AppData()

    fun save(data: AppData) {
        check(prefs.edit().putString("data", DataCodec.encode(data)).commit()) {
            "Could not save workout data"
        }
    }
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore(application)
    private val _data = MutableStateFlow(store.load())
    val data = _data.asStateFlow()
    private val imported =
        runCatching {
                application.assets.open("reference/catalog.json").bufferedReader().use {
                    DataCodec.catalog(it.readText())
                }
            }
            .getOrElse { emptyList() }
    val catalog: List<Exercise> =
        imported.ifEmpty {
            runCatching {
                    application.assets.open("catalog.json").bufferedReader().use {
                        DataCodec.catalog(it.readText())
                    }
                }
                .getOrElse { emptyList() }
        }

    val equipmentCatalog = EquipmentCatalog(application)

    init {
        if (_data.value.gyms.isEmpty())
            update {
                it.copy(
                    gyms =
                        listOf(
                            GymProfile(
                                name = "Everything Gym",
                                equipmentIds = equipmentCatalog.prefill("everything"),
                            )
                        )
                )
            }
    }

    fun saveGym(gym: GymProfile): String {
        update { d ->
            d.copy(
                gyms =
                    if (d.gyms.any { it.id == gym.id })
                        d.gyms.map { if (it.id == gym.id) gym else it }
                    else d.gyms + gym
            )
        }
        return gym.id
    }

    fun defaultGym(id: String) = update { d ->
        d.copy(gyms = d.gyms.sortedBy { if (it.id == id) 0 else 1 })
    }

    fun deleteGym(id: String) = update { d ->
        if (d.gyms.size > 1) d.copy(gyms = d.gyms.filterNot { it.id == id }) else d
    }

    fun exercises(d: AppData = _data.value) = catalog + d.customExercises

    private fun update(block: (AppData) -> AppData) {
        val next = block(_data.value)
        store.save(next)
        _data.value = next
    }

    fun start(name: String = "Workout", ids: List<String> = emptyList()) {
        if (_data.value.active == null)
            update {
                it.copy(
                    active =
                        Workout(
                            name = name,
                            exercises = ids.map { id -> SessionExercise(exerciseId = id) },
                        )
                )
            }
    }

    fun startRest(seconds: Int) = editActive {
        it.copy(restUntil = System.currentTimeMillis() + seconds * 1000L)
    }

    fun extendRest(seconds: Int) = editActive {
        it.copy(restUntil = maxOf(System.currentTimeMillis(), it.restUntil) + seconds * 1000L)
    }

    fun clearRest() = editActive { it.copy(restUntil = 0) }

    fun pauseWorkout(paused: Boolean) = editActive {
        it.withPause(paused, System.currentTimeMillis())
    }

    fun selectExercise(id: String) = editActive { w ->
        if (w.exercises.any { it.id == id }) w.copy(selectedExerciseId = id) else w
    }

    fun renameActive(name: String) = editActive {
        it.copy(name = name.trim().ifBlank { "Workout" })
    }

    private fun editActive(block: (Workout) -> Workout) = update { d ->
        d.copy(active = d.active?.let(block))
    }

    fun addExercise(id: String) = editActive {
        val exercise = SessionExercise(exerciseId = id)
        it.copy(exercises = it.exercises + exercise, selectedExerciseId = exercise.id)
    }

    fun removeExercise(id: String) = editActive {
        it.copy(exercises = it.exercises.filterNot { e -> e.id == id })
    }

    fun addSet(exerciseId: String) = editActive { w ->
        w.copy(
            exercises =
                w.exercises.map {
                    if (it.id == exerciseId)
                        it.copy(
                            sets =
                                it.sets +
                                    LoggedSet(
                                        weightKg = it.sets.lastOrNull()?.weightKg ?: 0.0,
                                        reps = it.sets.lastOrNull()?.reps ?: 8,
                                    )
                        )
                    else it
                }
        )
    }

    fun removeSet(exerciseId: String, setId: String) = editActive { w ->
        w.copy(
            exercises =
                w.exercises.map {
                    if (it.id == exerciseId)
                        it.copy(sets = it.sets.filterNot { s -> s.id == setId })
                    else it
                }
        )
    }

    fun editSet(exerciseId: String, setId: String, block: (LoggedSet) -> LoggedSet) =
        editActive { w ->
            w.copy(
                exercises =
                    w.exercises.map {
                        if (it.id == exerciseId)
                            it.copy(sets = it.sets.map { s -> if (s.id == setId) block(s) else s })
                        else it
                    }
            )
        }

    fun exerciseNotes(id: String, notes: String) = editActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) it.copy(notes = notes) else it })
    }

    fun workoutNotes(notes: String) = editActive { it.copy(notes = notes) }

    fun discard() = update { it.copy(active = null) }

    fun finish() {
        val w = _data.value.active ?: return
        if (w.completedSets() == 0) return
        update {
            it.copy(
                active = null,
                history =
                    listOf(
                        w.withPause(false, System.currentTimeMillis())
                            .copy(finishedAt = System.currentTimeMillis())
                    ) + it.history,
            )
        }
    }

    fun deleteWorkout(id: String) = update {
        it.copy(history = it.history.filterNot { w -> w.id == id })
    }

    fun saveRoutine(name: String, ids: List<String>) = update {
        it.copy(
            routines =
                it.routines + Routine(name = name.trim().ifBlank { "Routine" }, exerciseIds = ids)
        )
    }

    fun deleteRoutine(id: String) = update {
        it.copy(routines = it.routines.filterNot { r -> r.id == id })
    }

    fun favorite(id: String) = update {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    fun settings(block: (Preferences) -> Preferences) = update {
        it.copy(preferences = block(it.preferences))
    }

    fun addCustom(name: String, muscle: String, equipment: String, instructions: String): String {
        val e =
            Exercise(
                newId(),
                name.trim(),
                muscle,
                equipment,
                instructions = instructions,
                custom = true,
            )
        update { it.copy(customExercises = it.customExercises + e) }
        return e.id
    }

    fun restore(text: String) {
        val decoded = DataCodec.decode(text)
        val d =
            if (decoded.gyms.isEmpty())
                decoded.copy(
                    gyms =
                        listOf(
                            GymProfile(
                                name = "Everything Gym",
                                equipmentIds = equipmentCatalog.prefill("everything"),
                            )
                        )
                )
            else decoded
        store.save(d)
        _data.value = d
    }

    fun export() = DataCodec.encode(_data.value)
}

val Exercise.title: String
    get() =
        name.replace(Regex("(^|[\\s(\\-/])([\\p{L}])")) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
