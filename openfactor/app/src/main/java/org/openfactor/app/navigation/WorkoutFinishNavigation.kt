package org.openfactor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Deliberate outcomes, including ignoring a completion that must not interrupt another workflow. */
internal enum class WorkoutFinishDisposition {
    OPENED_SUMMARY,
    ALREADY_SHOWING_SUMMARY,
    IGNORED_ACTIVE_WORKOUT,
    IGNORED_MISSING_HISTORY,
    IGNORED_ELSEWHERE,
}

/**
 * One leaf at the persistent app root, outside AnimatedContent and loading/onboarding branches.
 * Neither the app shell nor this composition subscribes to per-keystroke workout data.
 */
@Composable
internal fun WorkoutFinishNavigation(
    controller: WorkoutFinishController,
    commands: WorkoutCommands,
    navigator: AppNavigator,
) {
    LaunchedEffect(controller, commands, navigator) {
        coordinateWorkoutFinishNavigation(controller, commands, navigator)
    }
}

/** Also exercised without a Compose host: decide from one authoritative snapshot, then acknowledge. */
internal suspend fun coordinateWorkoutFinishNavigation(
    controller: WorkoutFinishController,
    commands: WorkoutCommands,
    navigator: AppNavigator,
) {
    controller.state.collect { state ->
        if (state is WorkoutFinishState.Finished) {
            // No suspension between the decision and acknowledgement. Concurrent product changes
            // cannot invalidate the active/history check while the navigation decision is made.
            commands.withSnapshot { snapshot -> navigator.resolveFinishedWorkout(state.workoutId, snapshot) }
            controller.acknowledgeFinished(state.workoutId)
        }
    }
}
