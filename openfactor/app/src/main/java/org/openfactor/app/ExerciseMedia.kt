package org.openfactor.app

import android.net.Uri
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class VideoAvailability(val location: ExerciseVideoLocation?, val problem: String? = null)

/** Camera choices retain original clip/preview order. Opening this surface never starts a download. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExerciseMedia(e: Exercise, viewportHeight: Dp = Dp.Infinity) {
    var selected by rememberSaveable(e.id) { mutableIntStateOf(0) }
    var requested by remember(e.id) { mutableStateOf(false) }
    var playing by remember(e.id) { mutableStateOf(false) }
    var speed by rememberSaveable(e.id) { mutableFloatStateOf(1f) }
    var retry by remember(e.id, selected) { mutableIntStateOf(0) }
    var position by rememberSaveable(e.id, selected) { mutableLongStateOf(0L) }
    var playbackError by remember(e.id, selected) { mutableStateOf<String?>(null) }
    var player by remember(e.id, selected) { mutableStateOf<ExoPlayer?>(null) }
    var buffering by remember(e.id, selected) { mutableStateOf(false) }
    var rendered by remember(e.id, selected) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestPlaying by rememberUpdatedState(playing)
    val latestSpeed by rememberUpdatedState(speed)
    val clip = e.clips.getOrNull(selected)?.takeIf(String::isNotBlank)
        ?: e.video.takeIf { selected == 0 }
    val reference = remember(e.id, clip) { ExerciseVideoReference.parse(clip)?.takeIf { it.exerciseId == e.id } }
    val availability by key(reference, retry) {
        produceState<VideoAvailability?>(null) {
            value = reference?.let { ref ->
                withContext(Dispatchers.IO) {
                    val bundled = runCatching { context.assets.open(ref.assetPath).use { } }.isSuccess
                    val source = exerciseVideoLocation(ref, bundled, BuildConfig.EXERCISE_MEDIA_BASE_URL)
                    VideoAvailability(source, if (source != null) null else
                        if (BuildConfig.EXERCISE_MEDIA_BASE_URL.isBlank()) "Video isn’t installed on this device."
                        else "Video service is unavailable. Preview shown.")
                }
            }
        }
    }
    val location = availability?.location
    DisposableEffect(lifecycle, e.id, selected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.let { position = it.currentPosition.coerceAtLeast(0L); it.pause() }
                playing = false
            }
            if (event == Lifecycle.Event.ON_STOP) {
                // Background recomposition can be suspended, so cancel loading immediately;
                // the conditional player's later disposal releases its decoder as well.
                player?.let { position = it.currentPosition.coerceAtLeast(0L); it.stop() }
                requested = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, playing, speed) {
        player?.let { it.setPlaybackSpeed(speed); it.playWhenReady = playing }
    }
    val cameras = maxOf(e.frames.size, e.clips.size, if (e.video != null) 1 else 0)
    LaunchedEffect(e.id, cameras) { if (selected >= cameras && cameras > 0) selected = 0 }
    if (cameras == 0) {
        EmptyContent(Icons.Rounded.FitnessCenter, "${e.equipment} exercise", "No demonstration is available for this exercise.")
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val mediaHeight = minOf(maxWidth * 1.25f, (viewportHeight - 64.dp).coerceAtLeast(120.dp))
        Box(
            Modifier.fillMaxWidth().height(mediaHeight).clipToBounds().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            // Preserve the whole demonstration: cropping can hide feet, grip or overhead travel.
            ExerciseImage(e, Modifier.fillMaxSize(), selected, ContentScale.Fit)
            val source = location
            if (requested && source != null && playbackError == null) {
                // SimpleCache may need to read its disk index. Keep that work off the UI thread.
                val dataSourceFactory by produceState<DataSource.Factory?>(null, source, retry) {
                    value = withContext(Dispatchers.IO) { ExerciseVideoCache.dataSourceFactory(context, source) }
                }
                val factory = dataSourceFactory
                if (factory == null) {
                    CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Loading exercise video" }, color = Color.White)
                } else {
                    val engine = remember(e.id, selected, source, retry, factory) {
                        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 30_000, 500, 1_000).build())
                            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
                            .build()
                    }
                    DisposableEffect(engine) {
                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
                            override fun onRenderedFirstFrame() { rendered = true }
                            override fun onPlayerError(error: PlaybackException) {
                                position = engine.currentPosition.coerceAtLeast(0L)
                                playing = false
                                buffering = false
                                playbackError = exerciseVideoErrorMessage(error.errorCodeName)
                            }
                        }
                        player = engine
                        rendered = false
                        engine.addListener(listener)
                        engine.repeatMode = Player.REPEAT_MODE_ONE
                        engine.volume = 0f
                        engine.setPlaybackSpeed(latestSpeed)
                        engine.setMediaItem(MediaItem.Builder().setUri(Uri.parse(source.uri)).setCustomCacheKey(source.cacheKey).build(), position)
                        engine.playWhenReady = latestPlaying
                        engine.prepare()
                        onDispose {
                            position = engine.currentPosition.coerceAtLeast(0L)
                            engine.removeListener(listener)
                            engine.release()
                            if (player === engine) player = null
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                player = engine
                                contentDescription = "Exercise video, view ${selected + 1}"
                                accessibilityDelegate = object : View.AccessibilityDelegate() {
                                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                        super.onInitializeAccessibilityNodeInfo(host, info)
                                        info.contentDescription = String.format(Locale.US,
                                            "Exercise video, view ${selected + 1}: %.1f seconds of %.1f",
                                            engine.currentPosition.coerceAtLeast(0L) / 1000f,
                                            engine.duration.coerceAtLeast(0L) / 1000f,
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.player = engine },
                        onRelease = { it.player = null },
                    )
                    if (buffering || !rendered)
                        CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Loading exercise video" }, color = Color.White)
                }
            } else if (source != null && playbackError == null) {
                FilledIconButton({ requested = true; playing = true }, Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Play exercise video", Modifier.size(32.dp))
                }
            }
            if (cameras > 1)
                Column(Modifier.align(Alignment.TopEnd).padding(12.dp).width(56.dp)
                    .heightIn(max = (mediaHeight - 96.dp).coerceAtLeast(48.dp)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(cameras) { index ->
                        Surface(
                            onClick = { if (selected != index) selected = index },
                            border = if (selected == index) BorderStroke(3.dp, Color.White) else null,
                            color = OF.card,
                            modifier = Modifier.size(56.dp, 72.dp).semantics {
                                contentDescription = (if (e.frames.firstOrNull()?.startsWith("previews/") == true) "Preview " else "Camera view ") + (index + 1)
                                this.selected = selected == index
                            },
                        ) { ExerciseImage(e, Modifier.fillMaxSize(), index, ContentScale.Crop) }
                    }
                }
            if (requested && source != null && playbackError == null)
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(1f to "1X", 2.5f to "2.5X", 5f to "5X").forEach { (rate, label) ->
                        TextButton(
                            { speed = rate },
                            Modifier.width(48.dp).heightIn(min = 48.dp).semantics {
                                contentDescription = "Playback speed $label"; this.selected = speed == rate
                            },
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (speed == rate) Color.White else Color.Black.copy(alpha = .65f),
                                contentColor = if (speed == rate) Color.Black else Color.White,
                            ),
                        ) { Text(label) }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton({ playing = !playing }, Modifier.background(Color.Black.copy(alpha = .65f))) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause exercise video" else "Play exercise video", tint = Color.White)
                    }
                    IconButton({ position = 0; player?.seekTo(0); playing = true }, Modifier.background(Color.Black.copy(alpha = .65f))) {
                        Icon(Icons.Rounded.Replay, "Replay exercise video", tint = Color.White)
                    }
                }
        }
    }
    val error = playbackError
    if (error != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(error, Modifier.weight(1f), color = OF.muted, style = MaterialTheme.typography.bodyMedium)
        TextButton({ playbackError = null; retry++; requested = true; playing = true }) { Text("Retry") }
    } else availability?.problem?.let { Text(it, color = OF.muted, style = MaterialTheme.typography.bodyMedium) }
}

fun exerciseVideoErrorMessage(codeName: String): String = when {
    codeName.contains("DECOD") -> "This device can’t play this video. Preview shown."
    codeName.contains("TIMEOUT") -> "Connection timed out. Try again."
    codeName.contains("NETWORK") -> "Couldn’t connect. Check your connection and retry."
    else -> "Video unavailable. Preview shown."
}
