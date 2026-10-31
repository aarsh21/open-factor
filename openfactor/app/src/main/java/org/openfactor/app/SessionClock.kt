package org.openfactor.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * The workout's one-second wall clock. Read [LongState.longValue] only inside small leaf
 * composables or `derivedStateOf`, so a tick never recomposes the session or its set rows.
 */
@Composable
internal fun rememberSessionClock(workoutId: String, restUntil: Long): LongState {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(workoutId) {
        while (true) {
            delay(1000)
            now.longValue = System.currentTimeMillis()
        }
    }
    LaunchedEffect(restUntil) { now.longValue = System.currentTimeMillis() }
    return now
}

@Composable
internal fun SessionElapsedText(workout: Workout, clock: LongState) {
    TraceRecomposition("SessionClock")
    Text(
        if (workout.pausedAt != null) "Paused"
        else workout.elapsedSeconds(clock.longValue).let { "%d:%02d:%02d".format(it / 3600, it / 60 % 60, it % 60) },
        style = MaterialTheme.typography.labelLarge,
        color = OF.muted,
    )
}

@Composable
internal fun RestCountdownText(restUntil: Long, clock: LongState, modifier: Modifier = Modifier) {
    val remaining = ((restUntil - clock.longValue + 999) / 1000).coerceAtLeast(0)
    Text("Rest ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}", modifier, maxLines = 1, style = MaterialTheme.typography.labelLarge)
}
