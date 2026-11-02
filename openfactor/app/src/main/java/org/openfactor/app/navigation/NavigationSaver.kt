package org.openfactor.app

import androidx.compose.runtime.saveable.Saver

/** Versioned, primitive-only Compose state. No Bundle/JSON/platform dependency in the model. */
internal val AppNavigatorSaver: Saver<AppNavigator, Any> = Saver(
    save = { navigator ->
        arrayListOf("1", navigator.rootTab.name, navigator.programExitRequested.toString()).apply {
            writeDestination(navigator.destination)
        }
    },
    restore = { saved ->
        runCatching {
            // Saved state may originate from an older host or a malformed registry value.
            val reader = NavigationReader(saved as List<*>)
            require(reader.next() == "1")
            val tab = AppTab.valueOf(reader.next())
            val programExit = reader.next().toBooleanStrict()
            val destination = reader.destination()
            require(reader.finished)
            require(destination !is AppScreen.Root || destination.tab == tab)
            require(!programExit || destination is AppScreen.ProgramEditor || destination == AppScreen.ProgramSetup)
            AppNavigator(destination, tab).also { if (programExit) it.requestProgramExit() }
        }.getOrNull()
    },
)

private fun MutableList<String>.writeDestination(destination: AppDestination) {
    fun child(screen: AppScreen) = writeDestination(screen)
    fun optional(value: String?) { add(if (value == null) "-" else "+"); if (value != null) add(value) }
    when (destination) {
        is AppScreen.Root -> { add("root"); add(destination.tab.name) }
        AppScreen.Profile -> add("profile")
        AppScreen.BodyData -> add("body-data")
        AppScreen.BodyCharts -> add("body-charts")
        is AppScreen.WeightEntry -> { add("weight-entry"); add(destination.entryId) }
        AppScreen.Programs -> add("programs")
        AppScreen.ProgramSetup -> add("program-setup")
        is AppScreen.ProgramEditor -> { add("program-editor"); add(destination.selectedDayId) }
        is AppScreen.ProgramPicker -> { add("program-picker"); add(destination.dayId); child(destination.returnTo) }
        is AppScreen.Gyms -> { add("gyms"); child(destination.returnTo) }
        is AppScreen.Gym -> { add("gym"); add(destination.gymId); child(destination.returnTo) }
        AppScreen.Levels -> add("levels")
        is AppScreen.Exercise -> { add("exercise"); add(destination.exerciseId); child(destination.returnTo) }
        is AppScreen.ExerciseSettings -> { add("exercise-settings"); add(destination.exerciseId); child(destination.returnTo) }
        AppScreen.SessionPicker -> add("session-picker")
        is AppScreen.SwapPicker -> { add("swap-picker"); add(destination.entryId) }
        is AppScreen.CustomExercise -> { add("custom"); optional(destination.duplicateExerciseId); child(destination.returnTo) }
        AppScreen.DraftPicker -> add("draft-picker")
        AppScreen.WorkoutDraft -> add("workout-draft")
        is AppScreen.Session -> {
            add("session"); add((destination.swap != null).toString())
            destination.swap?.let { add(it.entryId); add(it.exerciseId) }
        }
        AppScreen.History -> add("history")
        is AppScreen.HistoryDetail -> { add("history-detail"); add(destination.workoutId); child(destination.returnTo) }
        is AppScreen.HistoryEdit -> { add("history-edit"); child(destination.returnTo) }
        AppScreen.Templates -> add("templates")
        is AppScreen.RoutineEditor -> { add("routine"); optional(destination.routineId); add(destination.draftKey); child(destination.returnTo) }
        is MeasurementEntry -> { add("measurement-entry"); add(destination.entryId); child(destination.returnTo) }
        is PhotoEntry -> { add("photo-entry"); add(destination.entryId); child(destination.returnTo) }
    }
}

private class NavigationReader(private val values: List<*>) {
    private var index = 0
    val finished get() = index == values.size
    fun next(): String = values[index++] as String
    private fun id(): String = next().also { require(it.isNotBlank()) }
    private fun optional(): String? = when (next()) {
        "-" -> null
        "+" -> id()
        else -> error("Invalid optional id")
    }

    fun destination(depth: Int = 0): AppDestination {
        require(depth < 32) { "Navigation return chain is too deep" }
        fun screen(): AppScreen = destination(depth + 1) as AppScreen
        return when (next()) {
            "root" -> AppScreen.Root(AppTab.valueOf(next()))
            "profile" -> AppScreen.Profile
            "body-data" -> AppScreen.BodyData
            "body-charts" -> AppScreen.BodyCharts
            "weight-entry" -> AppScreen.WeightEntry(next()) // Empty identifies a new entry.
            "programs" -> AppScreen.Programs
            "program-setup" -> AppScreen.ProgramSetup
            "program-editor" -> AppScreen.ProgramEditor(next())
            "program-picker" -> AppScreen.ProgramPicker(id(), screen() as AppScreen.ProgramEditor)
            "gyms" -> AppScreen.Gyms(screen())
            "gym" -> AppScreen.Gym(id(), screen() as AppScreen.Gyms)
            "levels" -> AppScreen.Levels
            "exercise" -> AppScreen.Exercise(id(), screen())
            "exercise-settings" -> AppScreen.ExerciseSettings(id(), screen() as AppScreen.Exercise)
            "session-picker" -> AppScreen.SessionPicker
            "swap-picker" -> AppScreen.SwapPicker(id())
            "custom" -> { val duplicate = optional(); AppScreen.CustomExercise(screen(), duplicate) }
            "draft-picker" -> AppScreen.DraftPicker
            "workout-draft" -> AppScreen.WorkoutDraft
            "session" -> AppScreen.Session(if (next().toBooleanStrict()) ExerciseSwapSelection(id(), id()) else null)
            "history" -> AppScreen.History
            "history-detail" -> AppScreen.HistoryDetail(id(), screen())
            "history-edit" -> AppScreen.HistoryEdit(screen() as AppScreen.HistoryDetail)
            "templates" -> AppScreen.Templates
            "routine" -> { val routine = optional(); val draft = id(); AppScreen.RoutineEditor(routine, draft, screen()) }
            "measurement-entry" -> { val entry = next(); MeasurementEntry(entry, screen()) }
            "photo-entry" -> { val entry = next(); PhotoEntry(entry, screen()) }
            else -> error("Unknown saved destination")
        }
    }
}
