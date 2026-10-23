package org.openfactor.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
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
    val resistanceEquipment: List<Set<String>> = emptyList(),
    val supportEquipment: List<Set<String>> = emptyList(),
    val archived: Boolean = false,
    val generationFacts: ExerciseGenerationFacts = ExerciseGenerationFacts(),
)

data class LoggedSet(
    val id: String = newId(),
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rir: Int? = null,
    val completed: Boolean = false,
    val warmup: Boolean = false,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRir: Int? = null,
    val assistanceKg: Double? = null,
    val durationSeconds: Double? = null,
    val distanceMeters: Double? = null,
    val kind: SetKind = SetKind.STANDARD,
    val side: SetSide? = null,
    val pairId: String? = null,
    val segmentOf: String? = null,
)

data class SessionExercise(
    val id: String = newId(),
    val exerciseId: String,
    val sets: List<LoggedSet> = listOf(LoggedSet()),
    val notes: String = "",
    val restSeconds: Int? = null,
    val supersetId: String? = null,
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
    val programDayId: String? = null,
    val programCycle: Int? = null,
    val gymId: String? = null,
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
    val exerciseAutoNext: Boolean = true,
    val keepAlive: Boolean = false,
    val showWorkoutTimer: Boolean = true,
    val showBodyweightContribution: Boolean = true,
    val distanceImperial: Boolean = false,
    val heightInFeet: Boolean = false,
    val clock24Hour: Boolean = false,
    val dataVisibility: DataVisibility = DataVisibility(),
    val dismissedTutorials: Set<String> = emptySet(),
    val theme: AppTheme = AppTheme.DARK,
    val shortcuts: ShortcutPreferences = ShortcutPreferences(),
    val dashboard: DashboardPreferences = DashboardPreferences(),
    // Older backups keep rest enabled at these boundaries; these are compatibility defaults.
    val restAfterLastWarmupSet: Boolean = true,
    val restBetweenExercises: Boolean = true,
    // Local compatibility defaults, not claims about source installation defaults.
    val supersetAutoNext: Boolean = true,
    val restBetweenSidesPercent: Int = 100,
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
    val programs: List<TrainingProgram> = emptyList(),
    val programDraft: TrainingProgram? = null,
    val onboarding: OnboardingState = OnboardingState(),
    val scaleWeights: List<ScaleWeightRecord> = emptyList(),
    val bodyMeasurements: List<BodyMeasurementRecord> = emptyList(),
    val workoutDraft: WorkoutDraft? = null,
    val progressPhotos: List<ProgressPhotoRecord> = emptyList(),
    val exerciseDefaults: Map<String, ExerciseDefaults> = emptyMap(),
)

fun newId() = UUID.randomUUID().toString()

data class WorkingSetTarget(
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val rir: Int? = null,
) {
    fun isValid(): Boolean =
        (repsMin == null || repsMin > 0) &&
            (repsMax == null || repsMax > 0) &&
            (repsMin == null || repsMax == null || repsMin <= repsMax) &&
            (rir == null || rir in 0..10)
}

/** Updates only current working rows, keyed by stable set ID, in one session change. */
fun Workout.withExerciseTargets(
    exerciseId: String,
    targets: Map<String, WorkingSetTarget>,
    restSeconds: Int?,
): Workout {
    require(restSeconds == null || restSeconds in 0..1800) { "Rest must be 0–1800 seconds" }
    require(targets.values.all { it.isValid() }) { "Invalid working set targets" }
    return copy(exercises = exercises.map { exercise ->
        if (exercise.id != exerciseId) exercise
        else exercise.copy(
            restSeconds = restSeconds,
            sets = exercise.sets.map { set ->
                val target = targets[set.id] ?: targets[set.pairId]
                if (set.warmup || target == null) set
                else set.copy(
                    targetRepsMin = target.repsMin,
                    targetRepsMax = target.repsMax,
                    targetRir = target.rir,
                )
            },
        )
    })
}

fun Workout.withEmptyWarmupSet(exerciseId: String): Workout =
    copy(exercises = exercises.map { exercise ->
        if (exercise.id != exerciseId) exercise
        else exercise.withNewSet(warmup = true)
    })

fun Workout.completedSets() = exercises.sumOf { e -> e.logicalSets().count { it.logs.any { log -> log.completed } } }

fun Workout.volumeKg() =
    exercises.sumOf { e ->
        e.sets.filter { it.completed && !it.warmup }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
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

/** The captured logger advances after an exercise's final set to unfinished work. */
fun Workout.toggleSetCompletion(
    exerciseId: String,
    setId: String,
    preferences: Preferences,
    now: Long,
    metrics: List<SetMetric> = listOf(SetMetric.WEIGHT, SetMetric.REPS),
): Workout {
    val index = exercises.indexOfFirst { it.id == exerciseId }
    if (index < 0) return this
    val exercise = exercises[index]
    val set = exercise.sets.find { it.id == setId } ?: return this
    val completing = !set.completed
    if (completing && !set.canComplete(metrics)) return this
    val changed =
        exercise.copy(
            sets = exercise.sets.map { if (it.id == setId) it.copy(completed = completing) else it }
        )
    val updated = exercises.map { if (it.id == exerciseId) changed else it }
    val logicalSet = changed.logicalSets().first { it.root.id == set.logicalRootId }
    val component = logicalSet.components.first { logs -> logs.any { it.id == setId } }
    val nextInGroup = if (completing && logicalSet.completed && !set.warmup && preferences.supersetAutoNext)
        copy(exercises = updated).nextSupersetExercise(exerciseId) else null
    val nextUnfinished =
        if (completing && changed.sets.all { it.completed }) {
            (1 until updated.size)
                .map { updated[(index + it) % updated.size] }
                .firstOrNull { candidate -> candidate.sets.any { !it.completed } }
                ?.id
        } else null
    // Warm-up rest belongs only to the last warm-up row, not every warm-up completion.
    val eligibleForRest = when {
        set.warmup -> exercise.logicalSets().lastOrNull { it.root.warmup }?.root?.id == set.logicalRootId &&
            preferences.restAfterLastWarmupSet
        changed.sets.filterNot { it.warmup }.all { it.completed } -> preferences.restBetweenExercises
        else -> true
    }
    val rest = if (set.kind == SetKind.MYO && !set.warmup && component != logicalSet.components.last()) 15
        else exercise.restSeconds ?: preferences.restSeconds
    val restMs = rest * 1000L * (if (component.any { !it.completed }) preferences.restBetweenSidesPercent.coerceIn(0, 100) else 100) / 100
    return copy(
        exercises = updated,
        selectedExerciseId = nextInGroup ?: nextUnfinished.takeIf { preferences.exerciseAutoNext } ?: selectedExerciseId,
        restUntil =
            if (completing && preferences.autoRest && eligibleForRest && restMs > 0)
                now + restMs else restUntil,
    )
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
            .put("weightKg", weightKg ?: JSONObject.NULL)
            .put("reps", reps ?: JSONObject.NULL)
            .put("rir", rir)
            .put("completed", completed)
            .put("warmup", warmup)
            .put("targetRepsMin", targetRepsMin)
            .put("targetRepsMax", targetRepsMax)
            .put("targetRir", targetRir)
            .put("assistanceKg", assistanceKg)
            .put("durationSeconds", durationSeconds)
            .put("distanceMeters", distanceMeters)
            .put("kind", kind.name)
            .put("side", side?.name)
            .put("pairId", pairId)
            .put("segmentOf", segmentOf)

    private fun SessionExercise.json() =
        JSONObject()
            .put("id", id)
            .put("exerciseId", exerciseId)
            .put("sets", JSONArray(sets.map { it.json() }))
            .put("notes", notes)
            .put("restSeconds", restSeconds)
            .put("supersetId", supersetId)

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
            .put("programDayId", programDayId)
            .put("programCycle", programCycle)
            .put("gymId", gymId)
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
            .put("archived", archived)
            .put("secondary", JSONArray(secondary))
            .put("frames", JSONArray(frames))
            .put("video", video)
            .put("muscles", JSONArray(muscles))
            .put("aliases", JSONArray(aliases))
            .put("equipmentOptions", JSONArray(equipmentOptions))
            .put("properties", JSONObject(properties))
            .put("bodyweight", bodyweight)
            .put("searchBoost", searchBoost)
            .put("clips", JSONArray(clips))
            .put("browseCategories", JSONArray(browseCategories))
            .put("resistanceEquipment", JSONArray(resistanceEquipment.map { JSONArray(it.toList()) }))
            .put("supportEquipment", JSONArray(supportEquipment.map { JSONArray(it.toList()) }))
            .also {
                if (generationFacts.descriptorLists.isNotEmpty() || generationFacts.recommendationLevels.isNotEmpty()) {
                    it.put("generationFacts", generationFacts.json())
                }
            }

    fun encode(d: AppData): String =
        JSONObject()
            .put("schemaVersion", 1)
            .put("activeProgramId", d.activeProgramId)
            .put("programs", JSONArray(d.programs.map { ProgramCodec.encode(it) }))
            .put("programDraft", d.programDraft?.let { ProgramCodec.encode(it) })
            .put("workoutDraft", d.workoutDraft?.let {
                JSONObject()
                    .put("name", it.name)
                    .put("gymId", it.gymId)
                    .put("notes", it.notes)
                    .put("exercises", JSONArray(it.exercises.map { entry -> entry.json() }))
            })
            .put("onboarding", d.onboarding.json())
            .put("scaleWeights", BodyDataCodec.weights(d.scaleWeights))
            .put("bodyMeasurements", BodyDataCodec.measurements(d.bodyMeasurements))
            .put("progressPhotos", ProgressPhotoCodec.encode(d.progressPhotos))
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
            .put("exerciseDefaults", ExerciseDefaultsCodec.encode(d.exerciseDefaults))
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
                    .put("showHabits", d.preferences.showHabits)
                    .put("exerciseAutoNext", d.preferences.exerciseAutoNext)
                    .put("keepAlive", d.preferences.keepAlive)
                    .put("showWorkoutTimer", d.preferences.showWorkoutTimer)
                    .put("showBodyweightContribution", d.preferences.showBodyweightContribution)
                    .put("distanceImperial", d.preferences.distanceImperial)
                    .put("heightInFeet", d.preferences.heightInFeet)
                    .put("clock24Hour", d.preferences.clock24Hour)
                    .put("dataVisibility", DataVisibilityCodec.encode(d.preferences.dataVisibility))
                    .put("dismissedTutorials", JSONArray(d.preferences.dismissedTutorials.sorted()))
                    .put("theme", d.preferences.theme.name)
                    .put("shortcuts", d.preferences.shortcuts.json())
                    .put("dashboard", d.preferences.dashboard.json())
                    .put("restAfterLastWarmupSet", d.preferences.restAfterLastWarmupSet)
                    .put("restBetweenExercises", d.preferences.restBetweenExercises)
                    .put("supersetAutoNext", d.preferences.supersetAutoNext)
                    .put("restBetweenSidesPercent", d.preferences.restBetweenSidesPercent),
            )
            .toString(2)

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun JSONObject.array(k: String) = optJSONArray(k) ?: JSONArray()

    private fun JSONObject.actual(key: String, maximum: Double = Double.MAX_VALUE): Double? =
        if (isNull(key)) null else getDouble(key).also {
            require(it.isFinite() && it in 0.0..maximum) { "Invalid $key actual" }
        }

    private fun JSONObject.sessionExercise(): SessionExercise =
        SessionExercise(
            getString("id"),
            getString("exerciseId"),
            array("sets").objects().map { s ->
                LoggedSet(
                    s.getString("id"),
                    if (!s.has("weightKg")) 0.0 else s.actual("weightKg", 5000.0),
                    if (!s.has("reps")) 8 else s.actual("reps", 9999.0)?.also {
                        require(it % 1.0 == 0.0) { "Reps must be a whole number" }
                    }?.toInt(),
                    if (s.isNull("rir")) null else s.getInt("rir").coerceIn(0, 10),
                    s.optBoolean("completed"),
                    s.optBoolean("warmup"),
                    if (s.isNull("targetRepsMin")) null else s.getInt("targetRepsMin"),
                    if (s.isNull("targetRepsMax")) null else s.getInt("targetRepsMax"),
                    if (s.isNull("targetRir")) null else s.getInt("targetRir"),
                    assistanceKg = s.actual("assistanceKg", 5000.0),
                    durationSeconds = s.actual("durationSeconds"),
                    distanceMeters = s.actual("distanceMeters"),
                    kind = if (!s.has("kind")) SetKind.STANDARD else SetKind.valueOf(s.getString("kind")),
                    side = if (s.isNull("side")) null else SetSide.valueOf(s.getString("side")),
                    pairId = if (s.isNull("pairId")) null else s.getString("pairId"),
                    segmentOf = if (s.isNull("segmentOf")) null else s.getString("segmentOf"),
                )
            },
            optString("notes"),
            if (isNull("restSeconds")) null else getInt("restSeconds"),
            supersetId = if (isNull("supersetId")) null else getString("supersetId").also { require(it.isNotBlank()) },
        ).validateSetLinks()

    private fun JSONObject.workout(): Workout =
        Workout(
            getString("id"),
            getString("name"),
            getLong("startedAt"),
            if (isNull("finishedAt")) null else getLong("finishedAt"),
            array("exercises").objects().map { it.sessionExercise() },
            optString("notes"),
            optLong("restUntil", 0),
            optString("programId").takeIf { it.isNotBlank() && it != "null" },
            optString("selectedExerciseId").takeIf { it.isNotBlank() && it != "null" },
            if (isNull("pausedAt")) null else getLong("pausedAt"),
            optLong("pausedDurationMs", 0).coerceAtLeast(0),
            optString("programDayId").takeIf { it.isNotBlank() && it != "null" },
            if (isNull("programCycle")) null else getInt("programCycle"),
            optString("gymId").takeIf { it.isNotBlank() && it != "null" },
        )

    fun decode(text: String): AppData {
        val j = JSONObject(text)
        require(j.optInt("schemaVersion") == 1) { "Unsupported backup version" }
        val p = j.optJSONObject("preferences") ?: JSONObject()
        val programs = j.array("programs").objects().map { ProgramCodec.decode(it) }
        require(programs.map { it.id }.distinct().size == programs.size) { "Duplicate program" }
        return AppData(
            j.optJSONObject("active")?.workout(),
            j.array("history").objects().map { it.workout() },
            j.array("routines").objects().map {
                Routine(it.getString("id"), it.getString("name"), it.array("exerciseIds").strings())
            },
            j.array("favorites").strings().toSet(),
            j.array("customExercises").objects().map { it.exercise(custom = true) },
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
                p.optBoolean("exerciseAutoNext", true),
                keepAlive = p.optBoolean("keepAlive", false),
                showWorkoutTimer = p.optBoolean("showWorkoutTimer", true),
                showBodyweightContribution = p.optBoolean("showBodyweightContribution", true),
                distanceImperial = p.optBoolean("distanceImperial", false),
                heightInFeet = p.optBoolean("heightInFeet", false),
                clock24Hour = p.optBoolean("clock24Hour", false),
                dataVisibility = if (p.has("dataVisibility"))
                    DataVisibilityCodec.decode(p.getJSONObject("dataVisibility")) else DataVisibility(),
                dismissedTutorials = if (p.has("dismissedTutorials"))
                    p.getJSONArray("dismissedTutorials").strings().toSet() else emptySet(),
                theme = AppTheme.valueOf(p.optString("theme", AppTheme.DARK.name)),
                shortcuts = ShortcutPreferences.decode(p.optJSONObject("shortcuts")),
                dashboard = DashboardPreferences.decode(p.optJSONObject("dashboard")),
                restAfterLastWarmupSet = p.optBoolean("restAfterLastWarmupSet", true),
                restBetweenExercises = p.optBoolean("restBetweenExercises", true),
                supersetAutoNext = p.optBoolean("supersetAutoNext", true),
                restBetweenSidesPercent = if (!p.has("restBetweenSidesPercent")) 100 else p.get("restBetweenSidesPercent").let {
                    require(it is Number && it.toDouble() % 1.0 == 0.0 && it.toDouble() in 0.0..100.0) { "Side rest must be 0–100%" }
                    it.toInt()
                },
            ),
            j.optString("activeProgramId").takeIf { it.isNotBlank() && it != "null" },
            j.array("gyms").objects().map { GymCodec.decode(it) },
            programs,
            j.optJSONObject("programDraft")?.let { ProgramCodec.decode(it) },
            OnboardingState.decode(j.optJSONObject("onboarding")),
            BodyDataCodec.readWeights(j),
            BodyDataCodec.readMeasurements(j),
            workoutDraft = j.optJSONObject("workoutDraft")?.let {
                WorkoutDraft(
                    name = it.getString("name"),
                    exercises = it.array("exercises").objects().map { entry -> entry.sessionExercise() },
                    gymId = it.optString("gymId").takeIf { id -> id.isNotBlank() && id != "null" },
                    notes = it.optString("notes"),
                )
            },
            progressPhotos = ProgressPhotoCodec.decode(j),
            exerciseDefaults = ExerciseDefaultsCodec.decode(j.optJSONObject("exerciseDefaults")),
        )
    }

    private fun JSONObject.exercise(custom: Boolean = optBoolean("custom")): Exercise {
        val properties = optJSONObject("properties")
        val muscles = array("muscles").strings()
        fun requirements(key: String): List<Set<String>> = array(key).let { groups ->
            (0 until groups.length()).map { groups.getJSONArray(it).strings().toSet() }
        }
        return Exercise(
            getString("id"),
            getString("name"),
            optString("muscle", "Other"),
            optString("equipment", "Other"),
            array("secondary").strings(),
            array("frames").strings(),
            optString("instructions"),
            optString("video").takeIf { it.isNotBlank() && it != "null" },
            custom,
            if (custom && has("muscles")) muscles else muscles.ifEmpty { listOf(optString("muscle", "Other")) },
            array("aliases").strings(),
            array("equipmentOptions").strings(),
            properties?.keys()?.asSequence()?.associateWith { properties.getString(it) } ?: emptyMap(),
            optDouble("bodyweight", 0.0).also {
                require(it.isFinite() && it >= 0.0 && (!custom || it <= 1.0)) { "Invalid bodyweight contribution" }
            },
            optDouble("searchBoost", 0.0),
            array("clips").strings(),
            array("browseCategories").strings(),
            requirements("resistanceEquipment"),
            requirements("supportEquipment"),
            optBoolean("archived"),
            ExerciseGenerationFacts.decode(if (has("generationFacts")) getJSONObject("generationFacts") else null),
        ).also { if (custom) it.loggingMetrics() }
    }

    fun catalog(text: String, bundled: List<Exercise> = emptyList()): List<Exercise> {
        val factsById = bundled.associateBy { it.id }
        return JSONArray(text).objects().map { json ->
            val exercise = json.exercise()
            val facts = factsById[exercise.id]
            if (facts == null) exercise else exercise.copy(
                properties = facts.properties.filterKeys { it in listOf("Region", "Movement Patterns") } + exercise.properties,
                generationFacts = ExerciseGenerationFacts(
                    facts.generationFacts.descriptorLists + exercise.generationFacts.descriptorLists,
                    facts.generationFacts.recommendationLevels + exercise.generationFacts.recommendationLevels,
                ),
            )
        }
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
    val photoStore = ProgressPhotoStore(application)
    private val _data = MutableStateFlow(store.load())
    val data = _data.asStateFlow()
    private val bundled =
        runCatching {
                application.assets.open("catalog.json").bufferedReader().use {
                    DataCodec.catalog(it.readText())
                }
            }
            .getOrElse { emptyList() }
    private val imported =
        runCatching {
                application.assets.open("reference/catalog.json").bufferedReader().use {
                    DataCodec.catalog(it.readText(), bundled)
                }
            }
            .getOrElse { emptyList() }
    val catalog: List<Exercise> = imported.ifEmpty { bundled }

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

    fun saveScaleWeight(record: ScaleWeightRecord) = update { it.withScaleWeight(record) }

    fun importHealthConnectWeights(records: List<ScaleWeightRecord>) =
        update { it.withHealthConnectWeights(records) }

    fun deleteScaleWeight(id: String) = update {
        it.copy(scaleWeights = it.scaleWeights.filterNot { record -> record.id == id })
    }

    fun saveBodyMeasurements(record: BodyMeasurementRecord) = update { it.withBodyMeasurements(record) }

    fun deleteBodyMeasurements(id: String) = update {
        it.copy(bodyMeasurements = it.bodyMeasurements.filterNot { record -> record.id == id })
    }

    fun saveProgressPhotos(record: ProgressPhotoRecord) {
        record.validate()
        val next = _data.value.copy(progressPhotos = listOf(record) +
            _data.value.progressPhotos.filterNot { it.id == record.id })
        photoStore.requireFiles(next)
        update { next }
        cleanPhotos()
    }

    fun deleteProgressPhotos(id: String) {
        update { it.copy(progressPhotos = it.progressPhotos.filterNot { record -> record.id == id }) }
        cleanPhotos()
    }

    fun cleanPhotos(draft: Collection<String> = emptyList()) =
        photoStore.clean(_data.value.photoReferences() + draft)

    fun editOnboarding(block: (OnboardingState) -> OnboardingState) = update {
        it.copy(onboarding = block(it.onboarding))
    }

    fun restartOnboarding() = update { d ->
        val setup =
            if (d.onboarding.completed)
                d.onboarding.copy(
                    step = 0,
                    completed = false,
                    name = d.preferences.displayName,
                    pounds = d.preferences.pounds,
                    preset = "current",
                    gymName = d.gyms.firstOrNull()?.name ?: "Everything Gym",
                    gymId = null,
                )
            else d.onboarding
        d.copy(onboarding = setup.copy(deferred = false))
    }

    fun applyOnboardingBasics() = update { d ->
        if (!d.onboarding.validBasics()) d
        else
            d.copy(
                preferences =
                    d.preferences.copy(
                        displayName = d.onboarding.name.trim(),
                        pounds = d.onboarding.pounds,
                    )
            )
    }

    fun applyOnboardingGym() = update { d ->
        val s = d.onboarding
        val existing =
            if (s.preset == "current") d.gyms.firstOrNull() else d.gyms.find { it.id == s.gymId }
        val gym =
            if (s.preset == "current" && existing != null) existing.copy(name = s.gymName.trim())
            else
                GymProfile(
                    id = existing?.id ?: newId(),
                    name = s.gymName.trim(),
                    equipmentIds =
                        if (s.preset == "blank") emptySet() else equipmentCatalog.prefill(s.preset),
                )
        d.copy(
            gyms = listOf(gym) + d.gyms.filterNot { it.id == gym.id },
            onboarding = s.copy(gymId = gym.id),
        )
    }

    fun finishOnboarding() = update {
        it.copy(onboarding = it.onboarding.copy(completed = true, deferred = false))
    }

    fun prepareWorkout() = update { d ->
        if (d.workoutDraft != null) d
        else d.copy(workoutDraft = WorkoutDraft(gymId = d.gyms.firstOrNull()?.id))
    }

    fun editWorkoutDraft(block: (WorkoutDraft) -> WorkoutDraft) = update { d ->
        d.copy(workoutDraft = d.workoutDraft?.let(block))
    }

    fun addDraftExercise(id: String) = update { d ->
        d.copy(workoutDraft = d.workoutDraft?.let {
            it.copy(exercises = it.exercises + newSessionExercise(id, d.exerciseDefaults[id]))
        })
    }

    fun discardWorkoutDraft() = update { it.copy(workoutDraft = null) }

    fun startWorkoutDraft(): Boolean {
        val d = _data.value
        if (d.active != null || d.workoutDraft == null) return false
        update { it.startWorkoutDraft(System.currentTimeMillis()) }
        return true
    }

    fun start(name: String = "Workout", ids: List<String> = emptyList()) {
        if (_data.value.active == null)
            update {
                it.copy(
                    active =
                        Workout(
                            name = name,
                            exercises = ids.map { id -> newSessionExercise(id, it.exerciseDefaults[id]) },
                        )
                )
            }
    }

    fun editProgramDraft(block: (TrainingProgram) -> TrainingProgram) = update { d ->
        d.copy(programDraft = d.programDraft?.let(block))
    }

    fun beginProgram(program: TrainingProgram) = update { it.copy(programDraft = program) }

    fun discardProgramDraft() = update { it.copy(programDraft = null) }

    fun saveProgram(activate: Boolean) = update { d ->
        val p = d.programDraft ?: return@update d
        if (p.name.isBlank()) return@update d
        d.copy(
            programs = d.programs.filterNot { it.id == p.id } + p,
            programDraft = null,
            activeProgramId = if (activate) p.id else d.activeProgramId,
        )
    }

    fun activateProgram(id: String?) = update { d ->
        if (id == null || d.programs.any { it.id == id }) d.copy(activeProgramId = id) else d
    }

    fun deleteProgram(id: String) = update { d ->
        d.copy(
            programs = d.programs.filterNot { it.id == id },
            activeProgramId = d.activeProgramId?.takeUnless { it == id },
            programDraft = d.programDraft?.takeUnless { it.id == id },
        )
    }

    fun startProgramDay(programId: String, dayId: String, cycle: Int): Boolean {
        val d = _data.value
        if (d.active != null) return false
        val workout =
            d.programs
                .find { it.id == programId }
                ?.toWorkout(dayId, cycle, System.currentTimeMillis(), d.exerciseDefaults) ?: return false
        update { it.copy(active = workout) }
        return true
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

    fun addExercise(id: String) = update { d ->
        d.copy(active = d.active?.let {
            val exercise = newSessionExercise(id, d.exerciseDefaults[id])
            it.copy(exercises = it.exercises + exercise, selectedExerciseId = exercise.id)
        })
    }

    fun removeExercise(id: String) = editActive {
        it.copy(exercises = it.exercises.filterNot { e -> e.id == id }).normalizeSupersets()
    }

    fun groupSuperset(ids: Set<String>) = editActive { it.withSuperset(ids) }

    fun leaveSuperset(id: String) = editActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) it.copy(supersetId = null) else it }).normalizeSupersets()
    }

    fun moveSessionExercise(id: String, offset: Int) = editActive { w ->
        val index = w.exercises.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in w.exercises.indices) w else w.copy(exercises = w.exercises.toMutableList().apply {
            add(target, removeAt(index))
        })
    }

    private fun editSessionExercise(id: String, block: (SessionExercise) -> SessionExercise) = editActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) block(it).validateSetLinks() else it })
    }

    fun splitSides(id: String) = editSessionExercise(id) { it.withSplitSides() }
    fun keepLeftSide(id: String) = editSessionExercise(id) { it.withLeftSideOnly() }
    fun setKind(id: String, setId: String, kind: SetKind, warmup: Boolean = false) =
        editSessionExercise(id) { it.withSetKind(setId, kind, warmup) }
    fun addMyoComponent(id: String, rootId: String) = editSessionExercise(id) { it.withMyoComponent(rootId) }

    fun saveExerciseTargets(
        exerciseId: String,
        targets: Map<String, WorkingSetTarget>,
        restSeconds: Int?,
    ) = editActive { it.withExerciseTargets(exerciseId, targets, restSeconds) }

    fun addWarmupSet(exerciseId: String) = editActive { it.withEmptyWarmupSet(exerciseId) }

    fun addSet(exerciseId: String) = editSessionExercise(exerciseId) { it.withNewSet() }

    fun removeSet(exerciseId: String, setId: String) = editSessionExercise(exerciseId) { it.withoutSet(setId) }

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

    fun toggleSetCompletion(exerciseId: String, setId: String) = update { d ->
        d.copy(
            active =
                d.active?.toggleSetCompletion(
                    exerciseId,
                    setId,
                    d.preferences,
                    System.currentTimeMillis(),
                    exercises(d).find { it.id == d.active?.exercises?.find { entry -> entry.id == exerciseId }?.exerciseId }
                        ?.loggingMetrics() ?: listOf(SetMetric.WEIGHT, SetMetric.REPS),
                )
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
        val now = System.currentTimeMillis()
        update {
            it.copy(
                active = null,
                history = listOf(w.withPause(false, now).copy(finishedAt = now)) + it.history,
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

    fun reorderRoutines(ids: List<String>) = update { data ->
        val byId = data.routines.associateBy { it.id }
        if (ids.size != data.routines.size || ids.toSet() != byId.keys) data
        else data.copy(routines = ids.map { byId.getValue(it) })
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

    fun saveExerciseDefaults(id: String, value: ExerciseDefaults) = update { d ->
        d.copy(exerciseDefaults = if (value == ExerciseDefaults()) d.exerciseDefaults - id else d.exerciseDefaults + (id to value))
    }

    fun archiveExercise(id: String, archived: Boolean) = update { d ->
        d.copy(customExercises = d.customExercises.map { if (it.id == id) it.copy(archived = archived) else it })
    }

    fun addCustom(draft: CustomExerciseDraft): String {
        val e = draft.toExercise(equipmentCatalog.items)
        update { it.copy(customExercises = it.customExercises + e) }
        return e.id
    }

    fun restore(text: String) {
        photoStore.prepare(ByteArrayInputStream(text.toByteArray())).use { restore(it) }
    }

    fun restore(backup: PreparedPhotoBackup) {
        val decoded = backup.data
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
        backup.install()
        store.save(d)
        _data.value = d
        cleanPhotos()
    }

    fun export(output: OutputStream) = photoStore.export(_data.value, output)

    fun export(): String = ByteArrayOutputStream().also { export(it) }.toString("UTF-8")
}

val Exercise.title: String
    get() =
        name.replace(Regex("(^|[\\s(\\-/])([\\p{L}])")) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
