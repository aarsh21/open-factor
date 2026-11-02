package org.openfactor.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Product operations needed by root navigation; the owner is headless and JVM-testable. */
internal interface NavigationWorkoutActions {
    val hasActiveWorkout: Boolean
    fun prepareWorkout()
    fun startEmptySession()
    fun addSessionExercise(exerciseId: String)
    fun addDraftExercise(exerciseId: String)
    fun addProgramExercise(dayId: String, exerciseId: String)
}

/** Owns root navigation and workflows, not windows, lifecycle, rendering, or durable workout data. */
internal class AppNavigator(
    initialDestination: AppDestination = AppScreen.Root(),
    initialTab: AppTab = AppTab.WORKOUT,
) {
    var destination by mutableStateOf(initialDestination)
        private set
    var rootTab by mutableStateOf((initialDestination as? AppScreen.Root)?.tab ?: initialTab)
        private set
    var programExitRequested by mutableStateOf(false)
        private set

    val root: AppScreen.Root get() = AppScreen.Root(rootTab)
    val contentScreen: AppScreen get() = destination.contentScreen

    fun navigate(to: AppDestination) {
        if (to is AppScreen.Root) rootTab = to.tab
        programExitRequested = false
        destination = to
    }

    /** Departing content cannot redirect the current screen (or replay a queued picker tap). */
    fun navigateFrom(from: AppScreen, to: AppDestination) {
        if (destination == from) navigate(to)
    }

    fun selectTab(tab: AppTab) = navigate(AppScreen.Root(tab))

    /** Null means Back needs the program-draft confirmation before navigation can commit. */
    fun backDestination(from: AppDestination = destination): AppDestination? = when (from) {
        is MeasurementEntry -> from.returnTo
        is PhotoEntry -> from.returnTo
        is AppScreen.Root -> null
        is AppScreen.Exercise -> from.returnTo
        is AppScreen.ExerciseSettings -> from.returnTo
        is AppScreen.CustomExercise -> from.returnTo
        AppScreen.SessionPicker, is AppScreen.SwapPicker -> AppScreen.Session()
        AppScreen.DraftPicker -> AppScreen.WorkoutDraft
        is AppScreen.Gym -> from.returnTo
        is AppScreen.Gyms -> from.returnTo
        AppScreen.BodyCharts, is AppScreen.WeightEntry -> AppScreen.BodyData
        is AppScreen.ProgramPicker -> from.returnTo
        is AppScreen.ProgramEditor, AppScreen.ProgramSetup -> null
        is AppScreen.HistoryDetail -> from.returnTo
        is AppScreen.HistoryEdit -> from.returnTo
        is AppScreen.RoutineEditor -> from.returnTo
        AppScreen.Profile, AppScreen.BodyData, AppScreen.Programs, AppScreen.Levels,
        AppScreen.WorkoutDraft, is AppScreen.Session, AppScreen.History, AppScreen.Templates -> root
    }

    fun back(from: AppDestination = destination) {
        if (destination != from) return
        val target = backDestination(from)
        if (target != null) navigate(target)
        else if (from is AppScreen.ProgramEditor || from == AppScreen.ProgramSetup) requestProgramExit()
    }

    fun requestProgramExit() {
        if (destination is AppScreen.ProgramEditor || destination == AppScreen.ProgramSetup) programExitRequested = true
    }
    fun cancelProgramExit() { programExitRequested = false }
    fun leaveProgramDraft() {
        programExitRequested = false
        navigate(AppScreen.Programs)
    }

    fun openExercise(exerciseId: String, from: AppScreen = contentScreen) =
        navigateFrom(from, AppScreen.Exercise(exerciseId, from))

    fun openCustom(from: AppScreen = contentScreen, duplicateExerciseId: String? = null) =
        navigateFrom(from, AppScreen.CustomExercise(from, duplicateExerciseId))

    fun customCreated(exerciseId: String, from: AppScreen.CustomExercise) =
        navigateFrom(from, AppScreen.Exercise(exerciseId, from.returnTo))

    fun editTemplate(routineId: String?, draftKey: String, from: AppScreen = contentScreen) =
        navigateFrom(from, AppScreen.RoutineEditor(routineId, draftKey, from))

    fun openSwapPicker(entryId: String, from: AppScreen.Session) =
        navigateFrom(from, AppScreen.SwapPicker(entryId))

    fun consumeSwap(selection: ExerciseSwapSelection) {
        val session = destination as? AppScreen.Session ?: return
        if (session.swap == selection) navigate(session.copy(swap = null))
    }

    fun templateSaved(from: AppScreen.RoutineEditor) {
        if (destination != from) return
        rootTab = AppTab.WORKOUT
        navigate(AppScreen.Templates)
    }

    /**
     * Completion belongs to authoritative product state and the current destination, never to an
     * outgoing AnimatedContent instance. A different active workout always wins over an old result.
     * The root observer acknowledges the result only after this explicit navigation/ignore decision.
     */
    fun resolveFinishedWorkout(workoutId: String, snapshot: AppData): WorkoutFinishDisposition = when {
        snapshot.active != null -> WorkoutFinishDisposition.IGNORED_ACTIVE_WORKOUT
        snapshot.history.none { it.id == workoutId } -> WorkoutFinishDisposition.IGNORED_MISSING_HISTORY
        (destination as? AppScreen.HistoryDetail)?.workoutId == workoutId -> WorkoutFinishDisposition.ALREADY_SHOWING_SUMMARY
        destination is AppScreen.Session -> {
            navigate(AppScreen.HistoryDetail(workoutId))
            WorkoutFinishDisposition.OPENED_SUMMARY
        }
        else -> WorkoutFinishDisposition.IGNORED_ELSEWHERE
    }

    fun startWorkout(actions: NavigationWorkoutActions, from: AppScreen = contentScreen) {
        if (destination != from) return
        actions.startEmptySession()
        navigate(AppScreen.SessionPicker)
    }

    fun prepareOrResumeWorkout(actions: NavigationWorkoutActions, from: AppScreen = contentScreen) {
        if (destination != from) return
        if (actions.hasActiveWorkout) navigate(AppScreen.Session())
        else {
            actions.prepareWorkout()
            navigate(AppScreen.WorkoutDraft)
        }
    }

    fun pickExercise(exerciseId: String, from: AppScreen, actions: NavigationWorkoutActions) {
        if (destination != from) return
        val origin = when (from) {
            is AppScreen.Exercise -> from.returnTo
            is AppScreen.ProgramPicker, AppScreen.DraftPicker, AppScreen.SessionPicker, is AppScreen.SwapPicker -> from
            else -> return // Only an actual picker or its exercise detail can emit Add.
        }
        when (origin) {
            is AppScreen.ProgramPicker -> {
                actions.addProgramExercise(origin.dayId, exerciseId)
                navigate(origin.returnTo)
            }
            AppScreen.WorkoutDraft, AppScreen.DraftPicker -> {
                actions.addDraftExercise(exerciseId)
                navigate(AppScreen.WorkoutDraft)
            }
            is AppScreen.SwapPicker -> navigate(AppScreen.Session(ExerciseSwapSelection(origin.entryId, exerciseId)))
            else -> {
                if (actions.hasActiveWorkout) {
                    actions.addSessionExercise(exerciseId)
                    navigate(AppScreen.Session())
                } else {
                    actions.prepareWorkout()
                    actions.addDraftExercise(exerciseId)
                    navigate(AppScreen.WorkoutDraft)
                }
            }
        }
    }
}
