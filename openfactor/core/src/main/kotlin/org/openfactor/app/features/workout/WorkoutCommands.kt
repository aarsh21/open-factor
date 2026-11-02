package org.openfactor.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local product operations. Android supplies storage, catalog, clock and host side effects. */
class WorkoutCommands(
    initial: AppData,
    scope: CoroutineScope,
    write: (AppData) -> Unit,
    private val catalog: () -> List<Exercise> = { emptyList() },
    private val equipmentPreset: (String) -> Set<String> = { emptySet() },
    private val equipmentItems: () -> List<EquipmentItem> = { emptyList() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val requirePhotoFiles: (AppData) -> Unit = { require(it.photoReferences().isEmpty()) { "A photo store is required" } },
    private val onPublished: (AppData, AppData) -> Unit = { _, _ -> },
    onDeferredFailure: (Throwable) -> Unit = {},
) {
    private val _data = MutableStateFlow(initial)
    val data = _data.asStateFlow()
    private val saver = CoalescedSaver<AppData>(TYPED_SAVE_DELAY_MS, scope, onDeferredFailure, write)
    val persistence = saver.state
    private var healthGeneration = 0L

    @Synchronized fun healthSyncGeneration(): Long = healthGeneration

    @Synchronized fun importHealthConnectSyncIfCurrent(sync: HealthConnectSyncData, expectedGeneration: Long): Boolean {
        if (expectedGeneration != healthGeneration) return false
        importHealthConnectSync(sync)
        return true
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

    fun exercises(d: AppData = _data.value) = catalog() + d.customExercises

    /** The host calls this before its lifetime ends; failure remains observable and retryable. */
    fun flushPendingSaves(): Boolean = saver.flushQuietly()

    fun retryPendingSaves(): Boolean = saver.flushQuietly()

    fun dismissPersistenceFailure(): Boolean = saver.dismissFailure()

    @Synchronized fun <T> withSnapshot(block: (AppData) -> T): T = block(_data.value)

    /** Trusted storage adapters install attachments before replacing the durable snapshot. */
    @Synchronized fun replaceData(data: AppData, installAttachments: () -> Unit = {}) {
        installAttachments()
        saver.saveNow(data)
        val previous = _data.value
        healthGeneration++
        _data.value = data
        onPublished(previous, data)
    }

    fun ensureDefaultGym(equipmentIds: Set<String>) = update { data ->
        if (data.gyms.isNotEmpty()) data
        else data.copy(gyms = listOf(GymProfile(name = "Everything Gym", equipmentIds = equipmentIds)))
    }

    @Synchronized private fun update(coalesce: (AppData, AppData) -> Boolean = { _, _ -> false }, block: (AppData) -> AppData) {
        val previous = _data.value
        val next = block(previous).withEffortPrompts(previous)
        if (next == previous) return
        // Only typed set values may trail on a worker; every other change is durable before it publishes.
        if (coalesce(previous, next)) saver.saveLater(next) else saver.saveNow(next)
        if (previous.healthConnect != next.healthConnect || previous.scaleWeights != next.scaleWeights) healthGeneration++
        _data.value = next
        onPublished(previous, next)
    }

    // One choke point, so every selection change (tabs, Next, auto-advance, supersets) is covered.
    private fun AppData.withEffortPrompts(previous: AppData): AppData {
        val workout = active ?: return this
        val updated = workout.withEffortPromptTransition(previous.active, preferences) { metricsFor(it, this) }
        return if (updated === workout) this else copy(active = updated)
    }

    private fun metricsFor(entry: SessionExercise, d: AppData): List<SetMetric> =
        loggingMetricsOf(catalog().find { it.id == entry.exerciseId } ?: d.customExercises.find { it.id == entry.exerciseId })

    /** True when an owed effort question must be answered or skipped before the finish confirmation. */
    @Synchronized fun askEffortBeforeFinish(): Boolean {
        update { d -> d.copy(active = d.active?.withEffortPromptsBeforeFinish(d.preferences) { metricsFor(it, d) }) }
        return _data.value.let { it.preferences.asksEffortOnLeave && it.active?.effortPrompts?.isNotEmpty() == true }
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
        update { data ->
            data.copy(progressPhotos = listOf(record) +
                data.progressPhotos.filterNot { it.id == record.id }).also(requirePhotoFiles)
        }
    }

    fun deleteProgressPhotos(id: String) {
        update { it.copy(progressPhotos = it.progressPhotos.filterNot { record -> record.id == id }) }
    }

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
                        if (s.preset == "blank") emptySet() else equipmentPreset(s.preset),
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
        var started = false
        update { data ->
            if (data.active != null || data.workoutDraft == null) data
            else data.startWorkoutDraft(nowMillis()).also { started = true }
        }
        return started
    }

    fun start(name: String = "Workout", ids: List<String> = emptyList()) = update { data ->
        if (data.active != null) data
        else data.copy(active = Workout(
            name = name,
            startedAt = nowMillis(),
            exercises = ids.map { id -> newSessionExercise(id, data.exerciseDefaults[id]) },
        ))
    }

    fun editProgramDraft(block: (TrainingProgram) -> TrainingProgram) = update { d ->
        d.copy(programDraft = d.programDraft?.let(block))
    }

    fun appendSpreadsheetPrograms(programs: List<TrainingProgram>) = update { data ->
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
        var started = false
        update { data ->
            if (data.active != null) data
            else {
                val workout = data.programs.find { it.id == programId }
                    ?.toWorkout(dayId, cycle, nowMillis(), data.exerciseDefaults, exercises(data))
                if (workout == null) data else data.copy(active = workout).also { started = true }
            }
        }
        return started
    }

    fun startRest(seconds: Int) = editActive {
        it.copy(restUntil = nowMillis() + seconds * 1000L)
    }

    fun extendRest(seconds: Int) = editActive {
        it.copy(restUntil = maxOf(nowMillis(), it.restUntil) + seconds * 1000L)
    }

    fun clearRest() = editActive { it.copy(restUntil = 0) }

    fun pauseWorkout(paused: Boolean) = editActive {
        it.withPause(paused, nowMillis())
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

    @Synchronized fun swapExercise(entryId: String, replacementId: String, scope: SwapScope): ExerciseSwapUndo? {
        val before = _data.value
        update { d -> d.withExerciseSwap(entryId, replacementId, scope, exercises(d)) }
        // Snapshot what was published (after update()'s effort bookkeeping), not the raw block result.
        return before.exerciseSwapUndo(_data.value)
    }

    fun undoExerciseSwap(undo: ExerciseSwapUndo) = update { it.withoutExerciseSwap(undo) }

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

    /** The swipe and accessibility removal: no confirmation, an Undo offer instead. */
    @Synchronized fun removeSetWithUndo(exerciseId: String, setId: String): SetRemovalUndo? {
        val before = _data.value
        update { d -> d.withRemovedSet(exerciseId, setId) }
        return before.setRemovalUndo(_data.value)
    }

    fun undoSetRemoval(undo: SetRemovalUndo) = update { it.withoutSetRemoval(undo) }

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
                    nowMillis(),
                    loggingMetricsOf(exercises(d).find { it.id == d.active?.exercises?.find { entry -> entry.id == exerciseId }?.exerciseId }),
                )
        )
    }

    fun exerciseNotes(id: String, notes: String) = editActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) it.copy(notes = notes) else it })
    }

    fun dismissLastNote(id: String) = editActive { w ->
        w.copy(exercises = w.exercises.map { if (it.id == id) it.copy(lastNoteDismissed = true) else it })
    }

    fun saveTemplateNote(routineId: String, exerciseId: String, note: String): String? {
        var error: String? = null
        update { data ->
            try { data.withTemplateNote(routineId, exerciseId, note) }
            catch (failure: IllegalArgumentException) { error = failure.message; data }
        }
        return error
    }

    fun workoutNotes(notes: String) = editActive { it.copy(notes = notes) }

    fun discard() = update { it.copy(active = null) }

    fun finish(saveTemplate: Boolean = false, expectedWorkoutId: String? = null): String? {
        var finishedId: String? = null
        update { data ->
            val workout = data.active ?: return@update data
            if (expectedWorkoutId != null && workout.id != expectedWorkoutId) return@update data
            if (workout.completedSets() == 0) return@update data
            val now = nowMillis()
            data.copy(
                active = null,
                history = listOf(workout.withPause(false, now).copy(finishedAt = now, effortPrompts = emptyList())) + data.history,
                routines = if (saveTemplate) data.routines + Routine(name = workout.name.trim().ifBlank { "Routine" },
                    exerciseIds = workout.exercises.map { it.exerciseId }) else data.routines,
            ).also { finishedId = workout.id }
        }
        return finishedId
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
        d.copy(active = d.active?.withAllSetsCompleted(exerciseId, d.preferences, nowMillis(), metrics))
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
            try { data.withStartedRoutine(routineId, exercises(data), nowMillis()) }
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
        val e = draft.toExercise(equipmentItems())
        update { it.copy(customExercises = it.customExercises + e) }
        return e.id
    }

}
