package org.openfactor.app

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class CameraVideo(val camera: Int, val location: ExerciseVideoLocation)

/** One player loops the ordered camera playlist. A guide still never downloads before Play. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExerciseMedia(e: Exercise, viewportHeight: Dp = Dp.Infinity) {
    var selected by rememberSaveable(e.id) { mutableIntStateOf(0) }
    var requested by remember(e.id) { mutableStateOf(false) }
    var playing by remember(e.id) { mutableStateOf(false) }
    var speed by rememberSaveable(e.id) { mutableFloatStateOf(1f) }
    var retry by remember(e.id) { mutableIntStateOf(0) }
    var position by rememberSaveable(e.id) { mutableLongStateOf(0L) }
    var playbackError by remember(e.id) { mutableStateOf<String?>(null) }
    var player by remember(e.id) { mutableStateOf<ExoPlayer?>(null) }
    var buffering by remember(e.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestPlaying by rememberUpdatedState(playing)
    val latestSpeed by rememberUpdatedState(speed)
    val cameras = maxOf(e.frames.size, e.clips.size, if (e.video != null) 1 else 0)
    val cameraVideos by produceState<List<CameraVideo>?>(null, e.id, e.clips, e.video) {
        value = withContext(Dispatchers.IO) {
            (0 until cameras).mapNotNull { camera ->
                val path = e.clips.getOrNull(camera) ?: e.video.takeIf { camera == 0 }
                val ref = ExerciseVideoReference.parse(path)?.takeIf { it.exerciseId == e.id }
                    ?: return@mapNotNull null
                val bundled = runCatching { context.assets.open(ref.assetPath).use { } }.isSuccess
                exerciseVideoLocation(ref, bundled, BuildConfig.EXERCISE_MEDIA_BASE_URL)
                    ?.let { CameraVideo(camera, it) }
            }
        }
    }
    val videos = cameraVideos.orEmpty()
    val hasVideo = videos.any { it.camera == selected }
    LaunchedEffect(e.id, cameras) { if (selected >= cameras && cameras > 0) selected = 0 }
    DisposableEffect(lifecycle, e.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.let { position = it.currentPosition.coerceAtLeast(0); it.pause() }
                playing = false
            }
            if (event == Lifecycle.Event.ON_STOP) {
                player?.let { position = it.currentPosition.coerceAtLeast(0); it.stop() }
                requested = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, playing, speed) { player?.let { it.setPlaybackSpeed(speed); it.playWhenReady = playing } }
    if (cameras == 0) {
        EmptyContent(Icons.Rounded.FitnessCenter, "${e.equipment} exercise", "No demonstration is available for this exercise.")
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        playbackError?.let { error -> Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = OF.muted)
            TextButton({ playbackError = null; retry++; requested = true; playing = true }) { Text("Retry") }
        } }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val original = e.frames.firstOrNull()?.startsWith("reference/") == true
            val ratio = if (original) 9f / 16f else 1f
            val height = maxWidth / ratio
            Box(Modifier.fillMaxWidth().height(height).clipToBounds(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    e.frames.getOrNull(selected)?.let { "file:///android_asset/$it" }, e.name,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                )
                if (requested && hasVideo && playbackError == null && videos.isNotEmpty()) {
                    val factory by produceState<DataSource.Factory?>(null, videos, retry) {
                        value = withContext(Dispatchers.IO) {
                            ExerciseVideoCache.dataSourceFactory(context, videos.firstOrNull { !it.location.bundled }?.location ?: videos.first().location)
                        }
                    }
                    val readyFactory = factory
                    if (readyFactory != null) {
                        val engine = remember(e.id, videos, retry, readyFactory) {
                            ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 30_000, 500, 1_000).build())
                                .setMediaSourceFactory(DefaultMediaSourceFactory(readyFactory)).build()
                        }
                        DisposableEffect(engine) {
                            val listener = object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
                                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                                    item?.mediaId?.toIntOrNull()?.let { selected = it; position = 0 }
                                }
                                override fun onPlayerError(error: PlaybackException) {
                                    position = engine.currentPosition.coerceAtLeast(0)
                                    playing = false
                                    buffering = false
                                    playbackError = exerciseVideoErrorMessage(error.errorCodeName)
                                }
                            }
                            player = engine
                            engine.addListener(listener)
                            engine.repeatMode = Player.REPEAT_MODE_ALL
                            engine.volume = 0f
                            engine.setPlaybackSpeed(latestSpeed)
                            engine.setMediaItems(videos.map { camera ->
                                MediaItem.Builder().setMediaId(camera.camera.toString()).setUri(camera.location.uri)
                                    .setCustomCacheKey(camera.location.cacheKey).build()
                            }, videos.indexOfFirst { it.camera == selected }.coerceAtLeast(0), position)
                            engine.playWhenReady = latestPlaying
                            engine.prepare()
                            onDispose {
                                position = engine.currentPosition.coerceAtLeast(0)
                                engine.removeListener(listener)
                                engine.release()
                                if (player === engine) player = null
                            }
                        }
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                player = engine
                                accessibilityDelegate = object : View.AccessibilityDelegate() {
                                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                        super.onInitializeAccessibilityNodeInfo(host, info)
                                        info.contentDescription = String.format(Locale.US, "Exercise video, view %d: %.1f seconds of %.1f", engine.currentMediaItemIndex + 1,
                                            engine.currentPosition.coerceAtLeast(0) / 1000f, engine.duration.coerceAtLeast(0) / 1000f)
                                    }
                                }
                            } }, modifier = Modifier.fillMaxSize(), update = { it.player = engine }, onRelease = { it.player = null },
                        )
                    }
                    if (factory == null || buffering) CircularProgressIndicator(Modifier.size(44.dp).semantics { contentDescription = "Loading exercise video" })
                } else if (hasVideo && playbackError == null) {
                    FilledIconButton({ requested = true; playing = true }, Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Play exercise video", Modifier.size(32.dp))
                    }
                }
                if (videos.isNotEmpty()) Box(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .padding(start = 10.dp, top = 10.dp, end = if (cameras > 1) 68.dp else 10.dp),
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
                        color = Color(0xEB202020), contentColor = Color.White,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(1f to "1X", 2.5f to "2.5X", 5f to "5X").forEach { (rate, label) ->
                                TextButton(
                                    { speed = rate }, Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                        contentDescription = "Playback speed $label"; this.selected = speed == rate
                                    }, contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (speed == rate) Color.White else Color.Transparent,
                                        contentColor = if (speed == rate) Color.Black else Color.White,
                                    ),
                                ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                            }
                            IconButton({
                                if (!requested) { requested = true; playing = true } else playing = !playing
                            }, modifier = Modifier.weight(1f).height(48.dp).semantics {
                                if (!requested) contentDescription = "Start playback"
                            }, enabled = playbackError == null) {
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (playing) "Pause exercise video" else if (requested) "Play exercise video" else null)
                            }
                            IconButton({ position = 0; player?.seekTo(0); requested = true; playing = true },
                                modifier = Modifier.weight(1f).height(48.dp), enabled = requested && playbackError == null) {
                                Icon(Icons.Rounded.Replay, "Replay exercise video")
                            }
                        }
                    }
                }
                if (cameras > 1) Column(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).width(48.dp)
                        .heightIn(max = (minOf(height, viewportHeight) - 24.dp).coerceAtLeast(80.dp))
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(cameras) { camera ->
                        Box(
                            Modifier.size(48.dp, 80.dp).clip(RoundedCornerShape(4.dp))
                                .border(if (selected == camera) 2.dp else 0.dp,
                                    if (selected == camera) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
                                .selectable(selected == camera, role = Role.Tab, onClick = {
                                    if (selected != camera) {
                                        selected = camera; position = 0; playbackError = null
                                        val index = videos.indexOfFirst { it.camera == camera }
                                        if (index >= 0) player?.seekTo(index, 0) else requested = false
                                    }
                                }).semantics {
                                    contentDescription = (if (e.frames.firstOrNull()?.startsWith("previews/") == true) "Preview " else "Camera view ") + (camera + 1)
                                }, contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(e.frames.getOrNull(camera)?.let { "file:///android_asset/$it" }, null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

fun exerciseVideoErrorMessage(codeName: String): String = when {
    codeName.contains("DECOD") -> "This device can’t play this video. Preview shown."
    codeName.contains("TIMEOUT") -> "Connection timed out. Try again."
    codeName.contains("NETWORK") -> "Couldn’t connect. Check your connection and retry."
    else -> "Video unavailable. Preview shown."
}
