package org.openfactor.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

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
    // The "Last time" note is a placeholder; hiding it never touches the historical note.
    val lastNoteDismissed: Boolean = false,
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
    /** Template this session was started from; null for empty, program and older workouts. */
    val routineId: String? = null,
    /** Effort questions owed for exercises already left; see [withEffortPromptTransition]. */
    val effortPrompts: List<EffortPrompt> = emptyList(),
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
    val palette: AppPalette = AppPalette.CHARCOAL,
    val shortcuts: ShortcutPreferences = ShortcutPreferences(),
    val dashboard: DashboardPreferences = DashboardPreferences(),
    // Older backups keep rest enabled at these boundaries; these are compatibility defaults.
    val restAfterLastWarmupSet: Boolean = true,
    val restBetweenExercises: Boolean = true,
    // Local compatibility defaults, not claims about source installation defaults.
    val supersetAutoNext: Boolean = true,
    val restBetweenSidesPercent: Int = 100,
    val rateEffortAfterExercise: Boolean = true,
    val effortPromptTiming: EffortPromptTiming = EffortPromptTiming.NEXT_EXERCISE,
    val workoutKeypad: Boolean = true,
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
    fun isValid(maxRir: Int = 10): Boolean =
        (repsMin == null || repsMin > 0) &&
            (repsMax == null || repsMax > 0) &&
            (repsMin == null || repsMax == null || repsMin <= repsMax) &&
            (rir == null || rir in 0..maxRir)
}

/** Updates only current working rows, keyed by stable set ID, in one session change. */
fun Workout.withExerciseTargets(
    exerciseId: String,
    targets: Map<String, WorkingSetTarget>,
    restSeconds: Int?,
): Workout {
    require(restSeconds == null || restSeconds in 0..1800) { "Rest must be 0–1800 seconds" }
    require(targets.values.all { it.isValid(targetRirLimit) }) { "Invalid working set targets" }
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
