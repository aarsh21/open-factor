package org.openfactor.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WorkoutFinishState {
    data object Idle : WorkoutFinishState
    data class Saving(val workoutId: String) : WorkoutFinishState
    data class Failed(val workoutId: String) : WorkoutFinishState
    data class Finished(val workoutId: String) : WorkoutFinishState
}

/** Owns submission lifetime; a disappearing dialog cannot abandon its durable result. */
class WorkoutFinishController(
    private val commands: WorkoutCommands,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<WorkoutFinishState>(WorkoutFinishState.Idle)
    val state = _state.asStateFlow()

    @Synchronized fun submit(workoutId: String, saveTemplate: Boolean): Boolean {
        if (_state.value is WorkoutFinishState.Saving || _state.value is WorkoutFinishState.Finished) return false
        _state.value = WorkoutFinishState.Saving(workoutId)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val finished = withContext(dispatcher) { commands.finish(saveTemplate, expectedWorkoutId = workoutId) }
                _state.value = if (finished == null) WorkoutFinishState.Failed(workoutId) else WorkoutFinishState.Finished(finished)
            } catch (cancelled: CancellationException) {
                // A synchronous commit may finish while the caller is being cancelled.
                val committed = commands.data.value.let { it.active?.id != workoutId && it.history.any { item -> item.id == workoutId } }
                _state.value = if (committed) WorkoutFinishState.Finished(workoutId) else WorkoutFinishState.Idle
                throw cancelled
            } catch (_: Exception) {
                _state.value = WorkoutFinishState.Failed(workoutId)
            }
        }
        return true
    }

    /**
     * A navigation owner acknowledges only after deciding what to do with this result. Checking the
     * expected id prevents a delayed observer from clearing another workout's completion.
     */
    @Synchronized fun acknowledgeFinished(expectedWorkoutId: String): Boolean {
        val finished = _state.value as? WorkoutFinishState.Finished ?: return false
        if (finished.workoutId != expectedWorkoutId) return false
        _state.value = WorkoutFinishState.Idle
        return true
    }

    /** Compatibility for non-navigation consumers. The Android app decides before acknowledging. */
    @Synchronized fun consumeFinished(): String? {
        val finished = _state.value as? WorkoutFinishState.Finished ?: return null
        return finished.workoutId.takeIf { acknowledgeFinished(it) }
    }
}
