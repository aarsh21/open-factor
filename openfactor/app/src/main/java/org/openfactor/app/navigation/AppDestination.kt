package org.openfactor.app

/** Navigation arguments live with their destination, including the exact screen to reveal on Back. */
internal sealed interface AppDestination

internal enum class AppTab { DASHBOARD, WORKOUT, EXERCISES, SETTINGS }

internal sealed interface AppScreen : AppDestination {
    data class Root(val tab: AppTab = AppTab.WORKOUT) : AppScreen
    data object Profile : AppScreen
    data object BodyData : AppScreen
    data object BodyCharts : AppScreen
    /** An empty entry id starts a new weigh-in. */
    data class WeightEntry(val entryId: String) : AppScreen
    data object Programs : AppScreen
    data object ProgramSetup : AppScreen
    data class ProgramEditor(val selectedDayId: String = "") : AppScreen
    data class ProgramPicker(val dayId: String, val returnTo: ProgramEditor) : AppScreen {
        init { require(dayId.isNotBlank() && returnTo.selectedDayId == dayId) }
    }
    data class Gyms(val returnTo: AppScreen) : AppScreen
    data class Gym(val gymId: String, val returnTo: Gyms) : AppScreen {
        init { require(gymId.isNotBlank()) }
    }
    data object Levels : AppScreen
    data class Exercise(val exerciseId: String, val returnTo: AppScreen) : AppScreen {
        init { require(exerciseId.isNotBlank()) }
    }
    data class ExerciseSettings(val exerciseId: String, val returnTo: Exercise) : AppScreen {
        init { require(exerciseId.isNotBlank() && returnTo.exerciseId == exerciseId) }
    }
    data object SessionPicker : AppScreen
    data class SwapPicker(val entryId: String) : AppScreen {
        init { require(entryId.isNotBlank()) }
    }
    data class CustomExercise(val returnTo: AppScreen, val duplicateExerciseId: String? = null) : AppScreen {
        init { require(duplicateExerciseId == null || duplicateExerciseId.isNotBlank()) }
    }
    data object DraftPicker : AppScreen
    data object WorkoutDraft : AppScreen
    data class Session(val swap: ExerciseSwapSelection? = null) : AppScreen
    data object History : AppScreen
    data class HistoryDetail(val workoutId: String, val returnTo: AppScreen = History) : AppScreen {
        init { require(workoutId.isNotBlank()) }
    }
    data class HistoryEdit(val returnTo: HistoryDetail) : AppScreen {
        val workoutId: String get() = returnTo.workoutId
    }
    data object Templates : AppScreen
    data class RoutineEditor(val routineId: String?, val draftKey: String, val returnTo: AppScreen) : AppScreen {
        init { require(draftKey.isNotBlank() && (routineId == null || routineId.isNotBlank())) }
    }
}

internal data class ExerciseSwapSelection(val entryId: String, val exerciseId: String) {
    init { require(entryId.isNotBlank() && exerciseId.isNotBlank()) }
}
/** An empty entry id opens a new sheet; sheets may only overlay actual screens. */
internal data class MeasurementEntry(val entryId: String, val returnTo: AppScreen) : AppDestination
internal data class PhotoEntry(val entryId: String, val returnTo: AppScreen) : AppDestination

/** Sheets reveal their real caller, rather than borrowing the latest record id or root tab. */
internal val AppDestination.contentScreen: AppScreen
    get() = when (this) {
        is AppScreen -> this
        is MeasurementEntry -> returnTo
        is PhotoEntry -> returnTo
    }

/** UI-only updates on an existing editor/session must not replay a screen entrance. */
internal val AppScreen.transitionKey: AppScreen
    get() = when (this) {
        is AppScreen.ProgramEditor -> copy(selectedDayId = "")
        is AppScreen.Session -> copy(swap = null)
        else -> this
    }

/** Exhaustive depth policy; adding a destination requires deciding its motion. */
internal val AppScreen.depth: Int
    get() = when (this) {
        is AppScreen.Root -> 0
        AppScreen.Profile, AppScreen.BodyData, AppScreen.Programs, is AppScreen.Gyms,
        AppScreen.Levels, is AppScreen.Session, AppScreen.WorkoutDraft, AppScreen.History,
        AppScreen.Templates -> 1
        AppScreen.BodyCharts, is AppScreen.WeightEntry, AppScreen.ProgramSetup,
        is AppScreen.ProgramEditor, is AppScreen.Gym, AppScreen.SessionPicker,
        is AppScreen.SwapPicker, AppScreen.DraftPicker, is AppScreen.RoutineEditor -> 2
        is AppScreen.ProgramPicker, is AppScreen.HistoryDetail, is AppScreen.Exercise,
        is AppScreen.CustomExercise -> 3
        is AppScreen.ExerciseSettings, is AppScreen.HistoryEdit -> 4
    }
