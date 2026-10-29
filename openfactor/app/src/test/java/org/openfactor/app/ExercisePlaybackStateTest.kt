package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ExercisePlaybackStateTest {
    @Test fun aNewGuideAutoplaysOnlyWhenItsLifecycleIsResumed() {
        val visit = ExercisePlaybackState()
        assertFalse(visit.shouldLoad)
        assertFalse(visit.shouldPlay)
        assertTrue(visit.foreground().shouldLoad)
        assertTrue(visit.foreground().shouldPlay)
    }

    @Test fun backgroundImmediatelyCancelsLoadingAndForegroundResumesAutomaticPlayback() {
        val playing = ExercisePlaybackState().foreground()
        val background = playing.background()
        assertFalse(background.shouldLoad)
        assertFalse(background.shouldPlay)
        assertTrue(background.foreground().shouldLoad)
        assertTrue(background.foreground().shouldPlay)
    }

    @Test fun manualPauseSurvivesBackgroundAndRequiresExplicitPlayBeforeAnotherLoad() {
        val paused = ExercisePlaybackState().foreground().pause()
        assertFalse(paused.shouldPlay)
        val returned = paused.background().foreground()
        assertFalse(returned.shouldLoad)
        assertFalse(returned.shouldPlay)
        assertTrue(returned.play().shouldPlay)
    }

    @Test fun aPauseBeforeTheAsyncSourceArrivesStillCannotStartPlayback() {
        val waiting = ExercisePlaybackState().foreground().pause()
        assertFalse(waiting.shouldPlay)
        assertFalse(waiting.background().shouldLoad)
        assertFalse(waiting.background().foreground().shouldPlay)
    }

    @Test fun failedLoadDoesNotRetryOnResumeAndExplicitRetryClearsFailure() {
        val failed = ExercisePlaybackState().foreground().fail()
        assertFalse(failed.shouldLoad)
        assertFalse(failed.shouldPlay)
        assertFalse(failed.background().foreground().shouldLoad)
        assertTrue(failed.background().foreground().play().shouldPlay)
    }

    @Test fun cameraSelectionPreservesManualPauseAndMissingVideoStopsLoading() {
        val paused = ExercisePlaybackState().foreground().pause()
        assertFalse(paused.selectCamera(true).shouldPlay)
        assertFalse(paused.selectCamera(false).shouldLoad)
        val playing = ExercisePlaybackState().foreground()
        assertFalse(playing.selectCamera(false).shouldLoad)
        assertTrue(playing.selectCamera(false).selectCamera(true).shouldPlay)
    }

    @Test fun selectingAnotherCameraIsAnExplicitRecoveryFromAPlaybackFailure() {
        val failed = ExercisePlaybackState().foreground().fail()
        assertTrue(failed.selectCamera(true).shouldPlay)
        assertFalse(failed.selectCamera(true).failed)
    }

    @Test fun restoringTheSavedPauseIntentDoesNotCarryAStaleForegroundFlag() {
        val original = ExercisePlaybackState().foreground().pause()
        val restored = ExercisePlaybackState(requested = original.requested,
            pausedByUser = original.pausedByUser, failed = original.failed)
        assertFalse(restored.shouldLoad)
        assertFalse(restored.foreground().shouldLoad)
        assertTrue(restored.foreground().play().shouldPlay)
    }
}
