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
)

data class Routine(val id: String = newId(), val name: String, val exerciseIds: List<String>)

data class Preferences(
    val pounds: Boolean = false,
    val restSeconds: Int = 120,
    val autoRest: Boolean = true,
    val showRir: Boolean = true,
    val displayName: String = "Athlete",
)

data class AppData(
    val active: Workout? = null,
    val history: List<Workout> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val customExercises: List<Exercise> = emptyList(),
    val preferences: Preferences = Preferences(),
)

fun newId() = UUID.randomUUID().toString()

fun Workout.completedSets() = exercises.sumOf { e -> e.sets.count { it.completed } }

fun Workout.volumeKg() =
    exercises.sumOf { e ->
        e.sets.filter { it.completed && !it.warmup }.sumOf { it.weightKg * it.reps }
    }

fun Workout.localDate(): LocalDate =
    Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

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
                    .put("displayName", d.preferences.displayName),
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
            ),
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

    fun renameActive(name: String) = editActive {
        it.copy(name = name.trim().ifBlank { "Workout" })
    }

    private fun editActive(block: (Workout) -> Workout) = update { d ->
        d.copy(active = d.active?.let(block))
    }

    fun addExercise(id: String) = editActive {
        it.copy(exercises = it.exercises + SessionExercise(exerciseId = id))
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
                history = listOf(w.copy(finishedAt = System.currentTimeMillis())) + it.history,
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
        val d = DataCodec.decode(text)
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
