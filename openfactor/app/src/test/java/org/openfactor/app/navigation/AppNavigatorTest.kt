package org.openfactor.app

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.*
import org.junit.Test

class AppNavigatorTest {
    private val saveScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = value is String || value is ArrayList<*>
    }
    private fun saved(navigator: AppNavigator): Any =
        with(AppNavigatorSaver) { with(saveScope) { save(navigator)!! } }
    private fun restored(navigator: AppNavigator): AppNavigator = AppNavigatorSaver.restore(saved(navigator))!!

    private class Workouts(override var hasActiveWorkout: Boolean = false) : NavigationWorkoutActions {
        val operations = mutableListOf<String>()
        override fun prepareWorkout() { operations += "prepare" }
        override fun startEmptySession() { operations += "start"; hasActiveWorkout = true }
        override fun addSessionExercise(exerciseId: String) { operations += "session:$exerciseId" }
        override fun addDraftExercise(exerciseId: String) { operations += "draft:$exerciseId" }
        override fun addProgramExercise(dayId: String, exerciseId: String) { operations += "program:$dayId:$exerciseId" }
    }

    @Test fun saverRoundTripsEveryDestinationAndPayload() {
        val root = AppScreen.Root(AppTab.EXERCISES)
        val exercise = AppScreen.Exercise("bench", root)
        val gyms = AppScreen.Gyms(AppScreen.Session())
        val destinations = listOf(
            root, AppScreen.Profile, AppScreen.BodyData, AppScreen.BodyCharts,
            AppScreen.WeightEntry(""), AppScreen.WeightEntry("scale-id"), AppScreen.Programs,
            AppScreen.ProgramSetup, AppScreen.ProgramEditor("day-A"),
            AppScreen.ProgramPicker("day-B", AppScreen.ProgramEditor("day-B")), gyms,
            AppScreen.Gym("gym-id", gyms), AppScreen.Levels, exercise,
            AppScreen.ExerciseSettings("bench", exercise), AppScreen.SessionPicker,
            AppScreen.SwapPicker("entry-7"), AppScreen.CustomExercise(root),
            AppScreen.CustomExercise(AppScreen.ExerciseSettings("bench", exercise), "bench"),
            AppScreen.DraftPicker, AppScreen.WorkoutDraft, AppScreen.Session(),
            AppScreen.Session(ExerciseSwapSelection("entry-7", "row")), AppScreen.History,
            AppScreen.HistoryDetail("workout", exercise), AppScreen.HistoryEdit(AppScreen.HistoryDetail("workout", exercise)),
            AppScreen.Templates, AppScreen.RoutineEditor(null, "new-draft", root),
            AppScreen.RoutineEditor("routine-id", "edit-draft", AppScreen.Templates),
            MeasurementEntry("", AppScreen.Session()), MeasurementEntry("measurement", root),
            PhotoEntry("", root), PhotoEntry("photo", AppScreen.BodyData),
        )
        for (destination in destinations) {
            val original = AppNavigator(destination, AppTab.SETTINGS)
            val copy = restored(original)
            assertEquals("Destination $destination", destination, copy.destination)
            assertEquals(original.rootTab, copy.rootTab)
            assertEquals(original.backDestination(), copy.backDestination())
        }
    }

    @Test fun saverRejectsUnknownTruncatedTrailingAndInvalidPayloads() {
        assertNull(AppNavigatorSaver.restore(1)) // Older positional root-tab state must not crash.
        assertNull(AppNavigatorSaver.restore("history"))
        assertNull(AppNavigatorSaver.restore(listOf(1, "WORKOUT", "false", "history")))
        val invalid = listOf(
            arrayListOf<String>(), arrayListOf("2", "WORKOUT", "false", "history"),
            arrayListOf("1", "WORKOUT", "false", "unrecognized"),
            arrayListOf("1", "MISSING_TAB", "false", "history"),
            arrayListOf("1", "WORKOUT", "maybe", "history"),
            arrayListOf("1", "WORKOUT", "false", "exercise", "bench"),
            arrayListOf("1", "WORKOUT", "false", "history", "extra"),
            arrayListOf("1", "WORKOUT", "false", "root", "SETTINGS"),
            arrayListOf("1", "WORKOUT", "true", "session", "false"),
            arrayListOf("1", "WORKOUT", "false", "exercise", "", "root", "WORKOUT"),
            arrayListOf("1", "WORKOUT", "false", "program-picker", "day", "history"),
            arrayListOf("1", "WORKOUT", "false", "gym", "gym", "session", "false"),
            arrayListOf("1", "WORKOUT", "false", "exercise-settings", "bench", "history"),
            arrayListOf("1", "WORKOUT", "false", "photo-entry", "photo", "measurement-entry", "", "history"),
            arrayListOf("1", "WORKOUT", "false", "custom", "invalid-optional", "history"),
            arrayListOf("1", "WORKOUT", "false", "session", "true", "entry"),
            arrayListOf("1", "WORKOUT", "false", "session", "true", "entry", ""),
        )
        invalid.forEach { assertNull("Must reject $it", AppNavigatorSaver.restore(it)) }
    }

    @Test fun modelRejectsMismatchedAndMissingRequiredArguments() {
        val root = AppScreen.Root()
        assertThrows(IllegalArgumentException::class.java) { AppScreen.Exercise("", root) }
        assertThrows(IllegalArgumentException::class.java) { AppScreen.SwapPicker(" ") }
        assertThrows(IllegalArgumentException::class.java) { ExerciseSwapSelection("entry", "") }
        assertThrows(IllegalArgumentException::class.java) { AppScreen.ProgramPicker("day-A", AppScreen.ProgramEditor("day-B")) }
        assertThrows(IllegalArgumentException::class.java) {
            AppScreen.ExerciseSettings("row", AppScreen.Exercise("bench", root))
        }
        assertThrows(IllegalArgumentException::class.java) { AppScreen.RoutineEditor(null, "", root) }
        assertThrows(IllegalArgumentException::class.java) { AppScreen.CustomExercise(root, "") }
        assertThrows(IllegalArgumentException::class.java) { AppScreen.HistoryDetail("") }
        assertNull(AppNavigatorSaver.restore(arrayListOf(
            "1", "WORKOUT", "false", "program-picker", "day-A", "program-editor", "day-B",
        )))
        assertNull(AppNavigatorSaver.restore(arrayListOf(
            "1", "WORKOUT", "false", "exercise-settings", "row", "exercise", "bench", "root", "WORKOUT",
        )))
    }

    @Test fun saverBoundsNestedReturnChains() {
        val malicious = arrayListOf("1", "WORKOUT", "false")
        repeat(40) { malicious += listOf("exercise", "bench") }
        malicious += listOf("root", "WORKOUT")
        assertNull(AppNavigatorSaver.restore(malicious))
    }

    @Test fun nestedDetailsKeepTheirOwnIdentityAndReturnToOriginalPicker() {
        val picker = AppScreen.ProgramPicker("day-A", AppScreen.ProgramEditor("day-A"))
        val navigator = AppNavigator(picker)
        navigator.openExercise("bench")
        val bench = navigator.destination as AppScreen.Exercise
        navigator.navigate(AppScreen.ExerciseSettings("bench", bench))
        navigator.openCustom(duplicateExerciseId = "bench")
        val custom = navigator.destination as AppScreen.CustomExercise
        navigator.customCreated("custom-bench", custom)
        val copy = restored(navigator)
        assertEquals(AppScreen.Exercise("custom-bench", custom.returnTo), copy.destination)
        copy.back()
        assertEquals(AppScreen.ExerciseSettings("bench", bench), copy.destination)
        copy.back()
        assertEquals(bench, copy.destination)
        copy.back()
        assertEquals(picker, copy.destination)
        assertEquals("bench", bench.exerciseId) // Not overwritten by the new exercise id.
    }

    @Test fun pendingSwapOwnsEntryAndExerciseAndCanOnlyBeConsumedOnce() {
        val navigator = AppNavigator(AppScreen.Session())
        navigator.openSwapPicker("entry-2", navigator.destination as AppScreen.Session)
        val picker = navigator.destination as AppScreen.SwapPicker
        navigator.openExercise("row")
        val workouts = Workouts(true)
        navigator.pickExercise("row", AppScreen.Exercise("row", picker), workouts)
        val selection = ExerciseSwapSelection("entry-2", "row")
        assertEquals(AppScreen.Session(selection), navigator.destination)
        navigator.pickExercise("row", AppScreen.Exercise("row", picker), workouts)
        assertEquals(AppScreen.Session(selection), navigator.destination)
        assertTrue(workouts.operations.isEmpty()) // Session owns actual swap/undo, not navigation.
        val copy = restored(navigator)
        copy.consumeSwap(selection.copy(entryId = "wrong-entry"))
        assertEquals(AppScreen.Session(selection), copy.destination)
        copy.consumeSwap(selection)
        copy.consumeSwap(selection)
        assertEquals(AppScreen.Session(), copy.destination)
    }

    @Test fun pickerDispatchesEachTypedWorkflowAndRejectsQueuedSecondTap() {
        val origins = listOf(
            AppScreen.ProgramPicker("day-2", AppScreen.ProgramEditor("day-2")),
            AppScreen.DraftPicker, AppScreen.WorkoutDraft, AppScreen.SessionPicker,
        )
        for (origin in origins) {
            val navigator = AppNavigator(origin)
            val workouts = Workouts(true)
            navigator.openExercise("bench")
            navigator.pickExercise("bench", AppScreen.Exercise("bench", origin), workouts)
            navigator.pickExercise("bench", AppScreen.Exercise("bench", origin), workouts)
            val expectedOperations = when (origin) {
                is AppScreen.ProgramPicker -> listOf("program:day-2:bench")
                AppScreen.DraftPicker, AppScreen.WorkoutDraft -> listOf("draft:bench")
                else -> listOf("session:bench")
            }
            assertEquals("Origin $origin", expectedOperations, workouts.operations)
        }
    }

    @Test fun directPickerAddAndDepartingDetailCallbacksAreGuarded() {
        val navigator = AppNavigator(AppScreen.SessionPicker)
        val workouts = Workouts(true)
        navigator.pickExercise("bench", AppScreen.SessionPicker, workouts)
        navigator.pickExercise("bench", AppScreen.SessionPicker, workouts)
        assertEquals(listOf("session:bench"), workouts.operations)
        navigator.navigate(AppScreen.SessionPicker)
        navigator.openExercise("first")
        val departed = navigator.destination as AppScreen.Exercise
        navigator.navigate(AppScreen.Exercise("second", AppScreen.SessionPicker))
        navigator.pickExercise("first", departed, workouts)
        assertEquals(AppScreen.Exercise("second", AppScreen.SessionPicker), navigator.destination)
        assertEquals(listOf("session:bench"), workouts.operations)
    }

    @Test fun libraryAddWithoutActiveWorkoutPreparesDraftBeforeAdding() {
        val origin = AppScreen.Root(AppTab.EXERCISES)
        val navigator = AppNavigator(origin)
        val workouts = Workouts()
        navigator.openExercise("bench")
        navigator.pickExercise("bench", AppScreen.Exercise("bench", origin), workouts)
        assertEquals(listOf("prepare", "draft:bench"), workouts.operations)
        assertEquals(AppScreen.WorkoutDraft, navigator.destination)
    }

    @Test fun customExerciseFromPickerReturnsAddToTheCorrectWorkflow() {
        val picker = AppScreen.ProgramPicker("day-9", AppScreen.ProgramEditor("day-9"))
        val navigator = AppNavigator(picker)
        navigator.openCustom()
        navigator.customCreated("new-exercise", navigator.destination as AppScreen.CustomExercise)
        val workouts = Workouts()
        navigator.pickExercise("new-exercise", AppScreen.Exercise("new-exercise", picker), workouts)
        assertEquals(listOf("program:day-9:new-exercise"), workouts.operations)
        assertEquals(picker.returnTo, navigator.destination)
    }

    @Test fun departingScreenCannotRedirectCurrentScreenOrStartAWorkout() {
        val root = AppScreen.Root()
        val navigator = AppNavigator(root)
        navigator.openExercise("first")
        val departing = navigator.destination as AppScreen.Exercise
        navigator.navigate(AppScreen.Exercise("second", root))
        navigator.navigateFrom(departing, root)
        navigator.openExercise("third", departing)
        navigator.openCustom(departing)
        navigator.startWorkout(Workouts(), root)
        navigator.back(departing)
        assertEquals(AppScreen.Exercise("second", root), navigator.destination)
        assertEquals("first", departing.exerciseId)
    }

    @Test fun predictiveBackPreviewIsPureAndCommitUsesTheSameTypedTarget() {
        val origin = AppScreen.Exercise("bench", AppScreen.Root(AppTab.EXERCISES))
        val current = AppScreen.HistoryDetail("workout", origin)
        val navigator = AppNavigator(current, AppTab.EXERCISES)
        val preview = navigator.backDestination()
        repeat(3) { assertEquals(preview, navigator.backDestination()) }
        assertEquals(current, navigator.destination) // Cancellation requires no model rollback.
        navigator.back()
        assertEquals(preview, navigator.destination)
        assertEquals(origin, navigator.destination)
    }

    @Test fun programBackRequestsConfirmationAndKeepRetainsTheTypedEditor() {
        val editor = AppScreen.ProgramEditor("day-3")
        val navigator = AppNavigator(editor, AppTab.SETTINGS)
        assertNull(navigator.backDestination())
        assertFalse(navigator.programExitRequested)
        navigator.back()
        assertEquals(editor, navigator.destination)
        assertTrue(navigator.programExitRequested)
        val copy = restored(navigator)
        assertTrue(copy.programExitRequested)
        copy.cancelProgramExit()
        assertEquals(editor, copy.destination)
        assertFalse(copy.programExitRequested)
        copy.back()
        copy.leaveProgramDraft()
        assertEquals(AppScreen.Programs, copy.destination)
        assertFalse(copy.programExitRequested)
        assertEquals(AppScreen.Root(AppTab.SETTINGS), copy.backDestination())
    }

    @Test fun measurementAndPhotoSheetsKeepExactCallerAcrossRestore() {
        val caller = AppScreen.HistoryDetail("workout", AppScreen.History)
        for (sheet in listOf(MeasurementEntry("measurement", caller), PhotoEntry("photo", caller))) {
            val navigator = restored(AppNavigator(sheet, AppTab.DASHBOARD))
            assertEquals(caller, navigator.contentScreen)
            navigator.back()
            assertEquals(caller, navigator.destination)
            assertEquals(AppTab.DASHBOARD, navigator.rootTab)
        }
    }

    @Test fun templateDraftIdentityAndTypedReturnSurviveRestore() {
        val origin = AppScreen.Root(AppTab.WORKOUT)
        val navigator = AppNavigator(origin)
        navigator.editTemplate("routine", "draft-unique")
        val copy = restored(navigator)
        assertEquals(AppScreen.RoutineEditor("routine", "draft-unique", origin), copy.destination)
        copy.back()
        assertEquals(origin, copy.destination)
    }

    @Test fun sessionAndEditorUiUpdatesDoNotChangeTransitionIdentity() {
        assertEquals(AppScreen.Session().transitionKey,
            AppScreen.Session(ExerciseSwapSelection("entry", "row")).transitionKey)
        assertEquals(AppScreen.ProgramEditor("day-A").transitionKey,
            AppScreen.ProgramEditor("day-B").transitionKey)
        val root = AppScreen.Root()
        assertNotEquals(AppScreen.Exercise("bench", root).transitionKey,
            AppScreen.Exercise("row", root).transitionKey)
        assertNotEquals(AppScreen.Root(AppTab.DASHBOARD).transitionKey,
            AppScreen.Root(AppTab.WORKOUT).transitionKey)
        assertTrue(AppScreen.HistoryDetail("workout").depth > AppScreen.History.depth)
        assertTrue(AppScreen.ExerciseSettings("bench", AppScreen.Exercise("bench", root)).depth >
            AppScreen.Exercise("bench", root).depth)
    }

    @Test fun startAndShortcutPrepareFlowsKeepTheExistingEntryPoints() {
        val navigator = AppNavigator()
        val workouts = Workouts()
        navigator.prepareOrResumeWorkout(workouts)
        assertEquals(AppScreen.WorkoutDraft, navigator.destination)
        assertEquals(listOf("prepare"), workouts.operations)
        workouts.hasActiveWorkout = true
        navigator.prepareOrResumeWorkout(workouts)
        assertEquals(AppScreen.Session(), navigator.destination)
        assertEquals(listOf("prepare"), workouts.operations)
        navigator.selectTab(AppTab.WORKOUT)
        navigator.startWorkout(workouts)
        assertEquals(AppScreen.SessionPicker, navigator.destination)
        assertEquals(listOf("prepare", "start"), workouts.operations)
    }
}
