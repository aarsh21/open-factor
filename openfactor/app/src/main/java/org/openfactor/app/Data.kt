package org.openfactor.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
    val reviewedEffortSetIds: Set<String> = emptySet(),
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

data class Routine(val id: String = newId(), val name: String, val exerciseIds: List<String>,
    val prescriptions: List<TemplateExercise> = emptyList())

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
    val rateEffortAfterExercise: Boolean = true,
    val weeklyWorkoutGoal: Int? = null,
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
    val healthConnect: HealthConnectSyncState = HealthConnectSyncState(),
    val pastWorkoutEdit: PastWorkoutEdit? = null,
    val templateSplits: List<TemplateSplit> = emptyList(),
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
            .put("reviewedEffortSetIds", JSONArray(reviewedEffortSetIds.sorted()))

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

    internal fun encodeHistory(history: List<Workout>): String = JSONArray(history.map { it.json() }).toString()

    internal fun decodeHistory(text: String): List<Workout> = JSONArray(text).objects().map { it.workout() }

    fun encode(d: AppData, pretty: Boolean = true): String =
        document(d).let { if (pretty) it.toString(2) else it.toString() }

    internal fun workoutDocument(workout: Workout): JSONObject = workout.json()

    internal fun document(d: AppData): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("activeProgramId", d.activeProgramId)
            .put("programs", JSONArray(d.programs.map { ProgramCodec.encode(it) }))
            .put("programDraft", d.programDraft?.let { ProgramCodec.encode(it) })
            .put("pastWorkoutEdit", d.pastWorkoutEdit?.let {
                JSONObject().put("original", it.original.json()).put("edited", it.edited.json())
            })
            .put("workoutDraft", d.workoutDraft?.let {
                JSONObject()
                    .put("name", it.name)
                    .put("gymId", it.gymId)
                    .put("notes", it.notes)
                    .put("exercises", JSONArray(it.exercises.map { entry -> entry.json() }))
            })
            .put("onboarding", d.onboarding.json())
            .put("scaleWeights", BodyDataCodec.weights(d.scaleWeights))
            .put("healthConnect", HealthConnectSyncCodec.encode(d.healthConnect))
            .put("bodyMeasurements", BodyDataCodec.measurements(d.bodyMeasurements))
            .put("progressPhotos", ProgressPhotoCodec.encode(d.progressPhotos))
            .put("gyms", JSONArray(d.gyms.map { GymCodec.encode(it) }))
            .put("active", d.active?.json())
            .put("history", JSONArray(d.history.map { it.json() }))
            .put(
                "routines",
                JSONArray(
                    d.routines.map { TemplateCodec.encodeRoutine(it) }
                ),
            )
            .put("templateSplits", JSONArray(d.templateSplits.map { TemplateCodec.encodeSplit(it) }))
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
                    .put("restBetweenSidesPercent", d.preferences.restBetweenSidesPercent)
                    .put("rateEffortAfterExercise", d.preferences.rateEffortAfterExercise)
                    .put("weeklyWorkoutGoal", d.preferences.weeklyWorkoutGoal),
            )

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
            reviewedEffortSetIds = array("reviewedEffortSetIds").strings().toSet(),
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

    fun decode(text: String, historyOverride: List<Workout>? = null): AppData = decode(JSONObject(text), historyOverride)

    internal fun decode(j: JSONObject, historyOverride: List<Workout>? = null): AppData {
        require(j.optInt("schemaVersion") == 1) { "Unsupported backup version" }
        require(!j.has("storageHistory") || historyOverride != null) { "Internal storage snapshot is not a restore backup" }
        val p = j.optJSONObject("preferences") ?: JSONObject()
        val programs = j.array("programs").objects().map { ProgramCodec.decode(it) }
        require(programs.map { it.id }.distinct().size == programs.size) { "Duplicate program" }
        val routines = j.array("routines").objects().map { TemplateCodec.decodeRoutine(it) }
        require(routines.map { it.id }.distinct().size == routines.size) { "Duplicate template" }
        return AppData(
            j.optJSONObject("active")?.workout(),
            historyOverride ?: j.array("history").objects().map { it.workout() },
            routines,
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
                rateEffortAfterExercise = p.optBoolean("rateEffortAfterExercise", true),
                weeklyWorkoutGoal = if (p.isNull("weeklyWorkoutGoal")) null else p.optInt("weeklyWorkoutGoal").takeIf { it in 1..7 },
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
            healthConnect = HealthConnectSyncCodec.decode(j.optJSONObject("healthConnect")),
            pastWorkoutEdit = j.optJSONObject("pastWorkoutEdit")?.let {
                PastWorkoutEdit(it.getJSONObject("original").workout(), it.getJSONObject("edited").workout())
            },
            templateSplits = TemplateCodec.decodeSplits(j.optJSONArray("templateSplits"), routines),
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

data class StorageRecovery(val restoredPreviousData: Boolean, val originalCount: Int)

class WorkoutStore(context: Context, preferencesName: String = "open_factor") {
    private val storageLock = storageLocks.computeIfAbsent("${context.filesDir.absolutePath}/$preferencesName") { Any() }
    private inline fun <T> locked(block: () -> T): T = synchronized(storageLock, block)
    private val historyArchive = WorkoutHistoryArchive(context, preferencesName)
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    // SharedPreferences rewrites its whole XML on commit: keep large checkpoints outside the hot file.
    private val recoveryPrefs = context.getSharedPreferences("${preferencesName}_recovery", Context.MODE_PRIVATE)
    private var unreadablePrimary: String? = null
    private var restoredPreviousData = false

    private fun decode(raw: String?): AppData? = raw?.let {
        try { historyArchive.decode(it) } catch (_: Exception) { null }
    }

    fun load(): AppData = locked {
        val raw = prefs.getString("data", null) ?: return AppData()
        val decoded = decode(raw)
        if (decoded != null) {
            unreadablePrimary = null
            // A startup checkpoint avoids duplicating the full history on each typed digit.
            if (recoveryPrefs.getString("data_last_good", null) != raw)
                recoveryPrefs.edit().putString("data_last_good", raw).commit()
            return decoded
        }
        unreadablePrimary = raw
        val previous = decode(recoveryPrefs.getString("data_last_good", null))
        restoredPreviousData = previous != null
        // Keep the original before allowing initialization or a later edit to replace primary data.
        // If this commit fails, save() includes preservation in the same atomic commit as new data.
        if (preserveUnreadable(recoveryPrefs.edit()).commit()) unreadablePrimary = null
        return previous ?: AppData()
    }

    private fun preserveUnreadable(editor: android.content.SharedPreferences.Editor): android.content.SharedPreferences.Editor {
        unreadablePrimary?.let { raw ->
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            editor.putString("recovery_raw_$digest", raw)
                .putBoolean("recovery_pending", true)
                .putBoolean("recovery_restored_previous", restoredPreviousData)
        }
        return editor
    }

    fun save(data: AppData) = locked {
        val raw = historyArchive.encode(data)
        check(preserveUnreadable(prefs.edit()).putString("data", raw).commit()) {
            "Could not save workout data"
        }
        unreadablePrimary = null
        historyArchive.prune(raw, recoveryPrefs.getString("data_last_good", null), hasRecoveryPayloads())
    }

    fun recovery(): StorageRecovery? = locked {
        val pending = when {
            prefs.getBoolean("recovery_pending", false) -> prefs
            recoveryPrefs.getBoolean("recovery_pending", false) -> recoveryPrefs
            else -> return null
        }
        return StorageRecovery(pending.getBoolean("recovery_restored_previous", false), recoveryPayloads().size)
    }

    private fun recoveryPayloads(): Map<String, String> = (recoveryPrefs.all + prefs.all).entries
        .filter { it.key.startsWith("recovery_raw_") && it.value is String }
        .associate { it.key.removePrefix("recovery_raw_") to it.value as String }.toSortedMap()

    /** Archive the exact original strings; damaged/newer JSON must not be decoded and rewritten. */
    fun exportRecovery(output: OutputStream) = locked {
        val originals = recoveryPayloads()
        check(originals.isNotEmpty()) { "No original storage payload is available" }
        ZipOutputStream(output).use { zip ->
            originals.forEach { (digest, raw) ->
                zip.putNextEntry(ZipEntry("original-$digest.json"))
                zip.write(raw.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            historyArchive.exportRecovery(zip)
        }
    }

    /** Acknowledgment hides the notice, while original payloads stay available for later export. */
    fun dismissRecovery(): Boolean = locked {
        val primary = prefs.edit().putBoolean("recovery_pending", false).commit()
        val archive = recoveryPrefs.edit().putBoolean("recovery_pending", false).commit()
        return primary && archive
    }

    fun hasRecoveryPayloads(): Boolean = locked { recoveryPayloads().isNotEmpty() }

    private companion object {
        // A reader must finish before another store can prune the archive it just referenced.
        val storageLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    }
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore(application)
    val photoStore = ProgressPhotoStore(application)
    private val _data = MutableStateFlow(store.load())
    val data = _data.asStateFlow()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saver = CoalescedSaver<AppData>(TYPED_SAVE_DELAY_MS, saveScope,
        { android.util.Log.w("WorkoutStore", "Deferred save failed; retrying on the next save", it) }, store::save)
    private val _storageRecovery = MutableStateFlow(store.recovery())
    val storageRecovery = _storageRecovery.asStateFlow()
    val hasStorageRecovery: Boolean get() = store.hasRecoveryPayloads()

    fun exportStorageRecovery(output: OutputStream) = store.exportRecovery(output)

    fun dismissStorageRecovery() {
        if (store.dismissRecovery()) _storageRecovery.value = null
    }
    private val library = (application as? OpenFactorApplication)?.exerciseLibrary
        ?: ExerciseLibraryRepository().also { it.load(application) }
    private val _libraryReady = MutableStateFlow(false)
    val libraryReady = _libraryReady.asStateFlow()
    internal val libraryLoad = library.state
    val catalog: List<Exercise> get() = library.snapshot?.catalog.orEmpty()
    val equipmentCatalog: EquipmentCatalog get() = requireNotNull(library.snapshot) { "The library is still loading" }.equipment

    private fun prepareLibrary(snapshot: LoadedExerciseLibrary) {
        if (_data.value.gyms.isEmpty())
            update {
                it.copy(
                    gyms =
                        listOf(
                            GymProfile(
                                name = "Everything Gym",
                                equipmentIds = snapshot.equipment.prefill("everything"),
                            )
                        )
                )
            }
        _libraryReady.value = true
    }

    fun retryLibrary() {
        library.load(getApplication())
        viewModelScope.launch { runCatching { library.await() }.onSuccess(::prepareLibrary) }
    }

    init {
        // Production ViewModels are created on Main: never parse the catalog in their constructor.
        // Direct model/device tests run on a worker and retain synchronous initialization semantics.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) retryLibrary()
        else prepareLibrary(runBlocking { library.await() })
        WorkoutNotifications.sync(application, _data.value)
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

    /** Writes typed set values still queued for the worker; call before the process may be killed. */
    fun flushPendingSaves() = saver.flush()

    override fun onCleared() {
        saver.flush()
        saveScope.cancel()
    }

    @Synchronized private fun update(coalesce: (AppData, AppData) -> Boolean = { _, _ -> false }, block: (AppData) -> AppData) {
        val previous = _data.value
        val next = block(previous)
        if (next == previous) return
        // Only typed set values may trail on a worker; every other change is durable before it publishes.
        if (coalesce(previous, next)) saver.saveLater(next) else saver.saveNow(next)
        _data.value = next
        if (previous.active != next.active) WorkoutNotifications.sync(getApplication(), next)
    }

    fun saveScaleWeight(record: ScaleWeightRecord) = update { it.withScaleWeight(record) }

    fun importHealthConnectWeights(records: List<ScaleWeightRecord>) =
        update { it.withHealthConnectWeights(records) }

    fun importHealthConnectSync(sync: HealthConnectSyncData) = update { it.withHealthConnectSync(sync) }

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

    fun ensureProfileSetup() = update { d ->
        if (d.onboarding.profileDraft != null) d else d.copy(onboarding = d.onboarding.copy(profileDraft = d.profileSetupDraft()))
    }
    fun editProfileSetup(block: (ProfileSetupDraft) -> ProfileSetupDraft) = update { d ->
        d.copy(onboarding = d.onboarding.copy(profileDraft = block(d.onboarding.profileDraft ?: d.profileSetupDraft())))
    }
    fun completeProfileSetup(): String? = saveProfile(_data.value.onboarding.profileDraft ?: _data.value.profileSetupDraft())
    fun saveProfile(draft: ProfileSetupDraft): String? {
        draft.validationError()?.let { return it }
        update { it.withProfileSetup(draft) }
        return null
    }
    fun skipProfileSetup() = update { d ->
        d.copy(onboarding = d.onboarding.copy(deferred = true, profileDraft = d.onboarding.profileDraft ?: d.profileSetupDraft()))
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
                    profileDraft = d.profileSetupDraft(),
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

    internal fun appendSpreadsheetPrograms(programs: List<TrainingProgram>) = update { data ->
        data.withSpreadsheetPrograms(programs, exercises(data))
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
                ?.toWorkout(dayId, cycle, System.currentTimeMillis(), d.exerciseDefaults, exercises()) ?: return false
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

    private fun editActive(coalesce: (AppData, AppData) -> Boolean = { _, _ -> false }, block: (Workout) -> Workout) = update(coalesce) { d ->
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
        editActive({ previous, next -> previous.onlyTypedSetValuesChanged(next, exerciseId, setId) }) { w ->
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

    fun finish(saveTemplate: Boolean = false) = update { data ->
        val workout = data.active ?: return@update data
        if (workout.completedSets() == 0) return@update data
        val now = System.currentTimeMillis()
        data.copy(
            active = null,
            history = listOf(workout.withPause(false, now).copy(finishedAt = now)) + data.history,
            routines = if (saveTemplate) data.routines + Routine(name = workout.name.trim().ifBlank { "Routine" },
                exerciseIds = workout.exercises.map { it.exerciseId }) else data.routines,
        )
    }

    fun deleteWorkout(id: String) = update {
        it.copy(history = it.history.filterNot { w -> w.id == id },
            pastWorkoutEdit = it.pastWorkoutEdit?.takeUnless { edit -> edit.original.id == id })
    }

    fun beginPastWorkoutEdit(id: String) = update { it.beginPastWorkoutEdit(id) }
    fun editPastWorkout(block: (Workout) -> Workout) = update { d ->
        d.copy(pastWorkoutEdit = d.pastWorkoutEdit?.let { it.copy(edited = block(it.edited)) })
    }
    fun discardPastWorkoutEdit() = update { it.copy(pastWorkoutEdit = null) }
    fun savePastWorkoutEdit(): String? {
        var error: String? = null
        update { d ->
            try { d.commitPastWorkoutEdit(exercises(d)) }
            catch (e: IllegalArgumentException) { error = e.message; d }
        }
        return error
    }

    fun logAllSets(exerciseId: String) = update { d ->
        val entry = d.active?.exercises?.find { it.id == exerciseId }
        val metrics = exercises(d).find { it.id == entry?.exerciseId }?.loggingMetrics()
            ?: listOf(SetMetric.WEIGHT, SetMetric.REPS)
        d.copy(active = d.active?.withAllSetsCompleted(exerciseId, d.preferences, System.currentTimeMillis(), metrics))
    }
    fun reviewExerciseEffort(exerciseId: String, setId: String, rir: Int?) =
        editSessionExercise(exerciseId) { it.withReviewedEffort(setId, rir) }

    fun saveRoutine(name: String, ids: List<String>) = update {
        it.copy(
            routines =
                it.routines + Routine(name = name.trim().ifBlank { "Routine" }, exerciseIds = ids)
        )
    }

    fun saveRoutine(routine: Routine): String? {
        var error: String? = null
        update { data ->
            try { data.withSavedRoutine(routine, exercises(data)) }
            catch (failure: IllegalArgumentException) { error = failure.message; data }
        }
        return error
    }

    fun installStarterSplit(presetId: String): String? {
        var error: String? = null
        update { data ->
            val catalog = exercises(data)
            val preset = starterTemplateSplits(catalog).find { it.id == presetId }
            if (preset == null) { error = "This split has unavailable exercises. Reload the library and try again."; data }
            else try { data.withStarterSplit(preset, catalog) }
            catch (failure: IllegalArgumentException) { error = failure.message; data }
        }
        return error
    }

    fun startRoutine(routineId: String): String? {
        var error: String? = null
        update { data ->
            try { data.withStartedRoutine(routineId, exercises(data), System.currentTimeMillis()) }
            catch (failure: IllegalArgumentException) { error = failure.message; data }
        }
        return error
    }

    fun reorderRoutines(ids: List<String>) = update { data ->
        val byId = data.routines.associateBy { it.id }
        if (ids.size != data.routines.size || ids.toSet() != byId.keys) data
        else data.copy(routines = ids.map { byId.getValue(it) })
    }

    fun deleteRoutine(id: String) = update {
        it.copy(routines = it.routines.filterNot { r -> r.id == id },
            templateSplits = it.templateSplits.filterNot { split -> id in split.days })
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
        // Through the saver, so a queued typed edit can never overwrite the restored data.
        saver.saveNow(d)
        _data.value = d
        WorkoutNotifications.sync(getApplication(), d)
        cleanPhotos()
    }

    fun export(output: OutputStream) = photoStore.export(_data.value, output)

    fun export(): String = ByteArrayOutputStream().also { export(it) }.toString("UTF-8")
}

/** True when an edit changed only typed metric values of one set, never its completion or kind. */
internal fun AppData.onlyTypedSetValuesChanged(next: AppData, exerciseId: String, setId: String): Boolean {
    val before = active?.exercises?.find { it.id == exerciseId }?.sets?.find { it.id == setId } ?: return false
    val after = next.active?.takeIf { it.id == active?.id }?.exercises?.find { it.id == exerciseId }?.sets?.find { it.id == setId } ?: return false
    return after.copy(weightKg = before.weightKg, reps = before.reps, assistanceKg = before.assistanceKg,
        durationSeconds = before.durationSeconds, distanceMeters = before.distanceMeters) == before
}

// Compiled once: titles are derived in list rows and set rows during composition.
private val titleWordStart = Regex("(^|[\\s(\\-/])([\\p{L}])")

val Exercise.title: String
    get() =
        name.replace(titleWordStart) {
            it.groupValues[1] + it.groupValues[2].uppercase()
        }
