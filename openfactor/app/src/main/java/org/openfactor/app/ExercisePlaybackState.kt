package org.openfactor.app

/** Playback intent is independent of asynchronous source/decoder readiness. */
internal data class ExercisePlaybackState(
    val foreground: Boolean = false,
    val requested: Boolean = true,
    val pausedByUser: Boolean = false,
    val failed: Boolean = false,
) {
    val shouldLoad: Boolean get() = foreground && requested && !failed
    val shouldPlay: Boolean get() = shouldLoad && !pausedByUser

    fun foreground() = copy(foreground = true, requested = !pausedByUser && !failed)
    fun background() = copy(foreground = false, requested = false)
    fun pause() = copy(pausedByUser = true)
    fun play() = copy(requested = true, pausedByUser = false, failed = false)
    fun fail() = copy(requested = false, failed = true)
    fun selectCamera(hasVideo: Boolean) = copy(requested = hasVideo, failed = false)
}
