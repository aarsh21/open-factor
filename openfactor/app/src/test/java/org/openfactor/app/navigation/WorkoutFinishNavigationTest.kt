package org.openfactor.app

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

/** Uses the production root observer, commands and controller without mounting an animated session. */
class WorkoutFinishNavigationTest {
    private class Fixture : AutoCloseable {
        val workout = Workout(
            id = "finished-workout", name = "Old workout",
            exercises = listOf(SessionExercise(exerciseId = "bench", sets = listOf(
                LoggedSet(weightKg = 60.0, reps = 8, completed = true),
            ))),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failWrites = false
        val commands = WorkoutCommands(
            AppData(active = workout), scope, nowMillis = { 10_000L },
            write = { if (failWrites) error("disk full") },
        )
        val controller = WorkoutFinishController(commands, scope, Dispatchers.Unconfined)
        fun observe(navigator: AppNavigator) = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinateWorkoutFinishNavigation(controller, commands, navigator)
        }
        fun finish(id: String = workout.id) { assertTrue(controller.submit(id, saveTemplate = false)) }
        fun startReplacement(): String {
            commands.start("New workout", listOf("row"))
            val active = commands.data.value.active!!
            val entry = active.exercises.single()
            commands.editSet(entry.id, entry.sets.single().id) { it.copy(weightKg = 40.0, reps = 10, completed = true) }
            return active.id
        }
        override fun close() { scope.cancel() }
    }

    private fun recreate(navigator: AppNavigator): AppNavigator {
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(AppNavigatorSaver) { with(scope) { save(navigator)!! } }
        return AppNavigatorSaver.restore(saved)!!
    }

    @Test fun persistentRootHandlesSuccessWithoutAnySessionComposition() = Fixture().use { f ->
        val navigator = AppNavigator(AppScreen.Session())
        f.observe(navigator)
        f.finish()
        assertEquals(AppScreen.HistoryDetail(f.workout.id), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        assertNull(f.commands.data.value.active)
        assertEquals(f.workout.id, f.commands.data.value.history.single().id)
    }

    @Test fun outgoingSessionCannotConsumeOrRedirectAfterUserMovedElsewhere() = Fixture().use { f ->
        val outgoing = AppScreen.Session()
        val navigator = AppNavigator(outgoing)
        f.observe(navigator)
        navigator.selectTab(AppTab.SETTINGS) // Old Session may still be rendered by AnimatedContent.
        f.finish()
        assertEquals(AppScreen.Root(AppTab.SETTINGS), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        // Even a late callback from the outgoing screen cannot override the root's ignore decision.
        navigator.navigateFrom(outgoing, AppScreen.HistoryDetail(f.workout.id))
        assertEquals(AppScreen.Root(AppTab.SETTINGS), navigator.destination)
        navigator.navigate(AppScreen.Session())
        assertEquals(AppScreen.Session(), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
    }

    @Test fun absentObserverDeliberatelyIgnoresElsewhereResultBeforeAFutureSession() = Fixture().use { f ->
        f.finish()
        assertEquals(WorkoutFinishState.Finished(f.workout.id), f.controller.state.value)
        val navigator = AppNavigator(AppScreen.Root(AppTab.DASHBOARD))
        f.observe(navigator)
        assertEquals(AppScreen.Root(AppTab.DASHBOARD), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        val replacementId = f.startReplacement()
        navigator.navigate(AppScreen.Session())
        assertEquals(replacementId, f.commands.data.value.active!!.id)
        assertEquals(AppScreen.Session(), navigator.destination)
    }

    @Test fun recreatedSessionOwnerHandlesPendingSuccessAndDoesNotReplayIt() = Fixture().use { f ->
        val original = AppNavigator(AppScreen.Session(), AppTab.EXERCISES)
        f.finish() // Completion arrives while the previous activity/root observer is absent.
        val recreated = recreate(original)
        val firstObserver = f.observe(recreated)
        assertEquals(AppScreen.HistoryDetail(f.workout.id), recreated.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        firstObserver.cancel()
        recreated.selectTab(AppTab.SETTINGS)
        val recreatedAgain = recreate(recreated)
        f.observe(recreatedAgain)
        assertEquals(AppScreen.Root(AppTab.SETTINGS), recreatedAgain.destination)
    }

    @Test fun newActiveWorkoutWinsOverOldPendingSuccessAndCanFinishNormally() = Fixture().use { f ->
        f.finish()
        val replacementId = f.startReplacement()
        val navigator = AppNavigator(AppScreen.Session())
        f.observe(navigator)
        assertEquals(AppScreen.Session(), navigator.destination)
        assertEquals(replacementId, f.commands.data.value.active!!.id)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        f.finish(replacementId)
        assertEquals(AppScreen.HistoryDetail(replacementId), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        assertEquals(setOf(f.workout.id, replacementId), f.commands.data.value.history.map { it.id }.toSet())
    }

    @Test fun replacedHistoryCannotTurnAStaleCompletionIntoNavigation() = Fixture().use { f ->
        f.finish()
        f.commands.replaceData(AppData())
        val navigator = AppNavigator(AppScreen.Session())
        f.observe(navigator)
        assertEquals(AppScreen.Session(), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        val replacementId = f.startReplacement()
        assertEquals(replacementId, f.commands.data.value.active!!.id)
        assertEquals(AppScreen.Session(), navigator.destination)
    }

    @Test fun alreadyOpenSummaryIsAcknowledgedWithoutReplacingItsTypedReturn() = Fixture().use { f ->
        f.finish()
        val summary = AppScreen.HistoryDetail(f.workout.id, AppScreen.Root(AppTab.EXERCISES))
        val navigator = AppNavigator(summary)
        f.observe(navigator)
        assertEquals(summary, navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
    }

    @Test fun differentSummaryAndSheetsAreNotInterrupted() {
        val elsewhere = listOf(
            AppScreen.HistoryDetail("another-workout"),
            PhotoEntry("", AppScreen.Session()),
            MeasurementEntry("", AppScreen.Session()),
        )
        for (destination in elsewhere) Fixture().use { f ->
            f.finish()
            val navigator = AppNavigator(destination)
            f.observe(navigator)
            assertEquals(destination, navigator.destination)
            assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
        }
    }

    @Test fun authorityCheckExplicitlyRejectsStillActiveAndMissingHistoryResults() = Fixture().use { f ->
        val navigator = AppNavigator(AppScreen.Session())
        val duplicate = AppData(active = f.workout, history = listOf(f.workout.copy(finishedAt = 10_000L)))
        assertEquals(WorkoutFinishDisposition.IGNORED_ACTIVE_WORKOUT,
            navigator.resolveFinishedWorkout(f.workout.id, duplicate))
        assertEquals(WorkoutFinishDisposition.IGNORED_MISSING_HISTORY,
            navigator.resolveFinishedWorkout(f.workout.id, AppData()))
        assertEquals(AppScreen.Session(), navigator.destination)
    }

    @Test fun expectedIdAcknowledgementCannotClearAnotherCompletion() = Fixture().use { f ->
        f.finish()
        assertFalse(f.controller.acknowledgeFinished("wrong-id"))
        assertEquals(WorkoutFinishState.Finished(f.workout.id), f.controller.state.value)
        assertEquals(f.workout.id, f.controller.consumeFinished()) // Public compatibility contract.
        assertNull(f.controller.consumeFinished())
        val replacementId = f.startReplacement()
        f.finish(replacementId)
        assertFalse(f.controller.acknowledgeFinished(f.workout.id))
        assertEquals(WorkoutFinishState.Finished(replacementId), f.controller.state.value)
        assertTrue(f.controller.acknowledgeFinished(replacementId))
        assertFalse(f.controller.acknowledgeFinished(replacementId))
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
    }

    @Test fun failedSaveRemainsRetryableAndIsNeverAcknowledgedAsSuccess() = Fixture().use { f ->
        val navigator = AppNavigator(AppScreen.Session())
        f.observe(navigator)
        f.failWrites = true
        f.finish()
        assertEquals(WorkoutFinishState.Failed(f.workout.id), f.controller.state.value)
        assertFalse(f.controller.acknowledgeFinished(f.workout.id))
        assertEquals(f.workout.id, f.commands.data.value.active!!.id)
        assertTrue(f.commands.data.value.history.isEmpty())
        assertEquals(AppScreen.Session(), navigator.destination)
        f.failWrites = false
        f.finish()
        assertEquals(AppScreen.HistoryDetail(f.workout.id), navigator.destination)
        assertEquals(WorkoutFinishState.Idle, f.controller.state.value)
    }
}
