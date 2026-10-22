package org.openfactor.app

import android.net.Uri
import android.media.MediaPlayer
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Camera choices are separate views, never animation frames. Remote paths aren't playable URLs. */
@Composable
fun ExerciseMedia(e: Exercise) {
    var selected by rememberSaveable(e.id) { mutableIntStateOf(0) }
    var playing by rememberSaveable(e.id, selected) { mutableStateOf(true) }
    var started by remember(e.id, selected) { mutableStateOf(true) }
    var speed by rememberSaveable(e.id) { mutableFloatStateOf(1f) }
    val latestSpeed by rememberUpdatedState(speed)
    val latestPlaying by rememberUpdatedState(playing)
    var player by remember(e.id, selected) { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember(e.id, selected) { mutableStateOf<VideoView?>(null) }
    var failed by remember(e.id, selected) { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, e.id, selected) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                videoView?.pause()
                playing = false
                started = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val installTime = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }
    val clip = e.clips.getOrNull(selected)?.takeIf { it.isNotBlank() } ?: e.video
    val uri by key(clip) {
        produceState<Uri?>(null) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            if (clip == null) null
                            else if (clip.startsWith("reference/")) {
                                require(
                                    clip.matches(Regex("reference/videos/[0-9a-f]{32}_[a-z]\\.mp4"))
                                )
                                val output =
                                    File(
                                        context.cacheDir,
                                        "exercise-videos/$installTime/${clip.substringAfterLast('/')}",
                                    )
                                output.parentFile?.mkdirs()
                                if (output.length() == 0L) {
                                    val pending = File(output.parentFile, output.name + ".partial")
                                    context.assets.open(clip).use { input ->
                                        pending.outputStream().use { input.copyTo(it) }
                                    }
                                    check(pending.renameTo(output))
                                }
                                Uri.fromFile(output)
                            } else null
                        }
                        .getOrNull()
                }
            if (clip != null && value == null) failed = true
        }
    }
    if (e.frames.isEmpty() && clip == null) {
        EmptyContent(
            Icons.Rounded.FitnessCenter,
            "${e.equipment} exercise",
            "No demonstration is installed for this exercise.",
        )
        return
    }
    LaunchedEffect(player, playing, speed) {
        val native = player ?: return@LaunchedEffect
        runCatching {
            native.playbackParams = native.playbackParams.setSpeed(speed)
            if (playing) videoView?.start() else videoView?.pause()
        }.onFailure {
            failed = true
            playing = false
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clipToBounds(), contentAlignment = Alignment.Center) {
            if (started && uri != null && !failed) {
                key(e.id, selected, uri) {
                    AndroidView(
                        factory = { ctx ->
                            val video = object : VideoView(ctx) {
                                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                                    if (measuredWidth > 0 && measuredHeight > 0) {
                                        val scale = maxOf(
                                            MeasureSpec.getSize(widthMeasureSpec).toFloat() / measuredWidth,
                                            MeasureSpec.getSize(heightMeasureSpec).toFloat() / measuredHeight,
                                        )
                                        setMeasuredDimension(
                                            (measuredWidth * scale).roundToInt(),
                                            (measuredHeight * scale).roundToInt(),
                                        )
                                    }
                                }
                            }.apply {
                                videoView = this
                                contentDescription = "Exercise video, view ${selected + 1}"
                                accessibilityDelegate = object : View.AccessibilityDelegate() {
                                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                                        super.onInitializeAccessibilityNodeInfo(host, info)
                                        val video = host as VideoView
                                        info.contentDescription = String.format(
                                            Locale.US,
                                            "Exercise video, view ${selected + 1}: %.1f seconds of %.1f",
                                            video.currentPosition.coerceAtLeast(0) / 1000f,
                                            video.duration.coerceAtLeast(0) / 1000f,
                                        )
                                    }
                                }
                                setOnPreparedListener { native ->
                                    runCatching {
                                        native.isLooping = true
                                        native.playbackParams =
                                            native.playbackParams.setSpeed(latestSpeed)
                                        player = native
                                        if (latestPlaying) start() else pause()
                                    }.onFailure {
                                        failed = true
                                        playing = false
                                    }
                                }
                                setOnErrorListener { _, _, _ ->
                                    failed = true
                                    playing = false
                                    true
                                }
                                setVideoURI(uri)
                            }
                            FrameLayout(ctx).apply {
                                clipChildren = true
                                addView(video, FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    Gravity.CENTER,
                                ))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { container ->
                            val video = container.getChildAt(0) as VideoView
                            video.setOnPreparedListener(null)
                            video.setOnErrorListener(null)
                            video.stopPlayback()
                            if (videoView === video) {
                                videoView = null
                                player = null
                            }
                        },
                    )
                }
            } else {
                ExerciseImage(e, Modifier.fillMaxSize(), selected, ContentScale.Crop)
                if (uri != null && !failed)
                    FilledIconButton({ started = true; playing = true }, Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Play exercise video")
                    }
            }
        if (e.frames.size > 1)
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp).width(56.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                e.frames.indices.forEach { index ->
                    Surface(
                        onClick = { selected = index },
                        border = if (selected == index) BorderStroke(3.dp, Color.White) else null,
                        color = OF.card,
                        modifier =
                            Modifier.size(56.dp, 72.dp).semantics {
                                contentDescription = "Camera view ${index + 1}"
                                this.selected = selected == index
                            },
                    ) {
                        ExerciseImage(e, Modifier.fillMaxSize(), index, ContentScale.Crop)
                    }
                }
            }
        if (uri != null && !failed) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(5f to "5X", 2.5f to "2.5X", 1f to "1X").forEach { (rate, label) ->
                        TextButton(
                            onClick = { speed = rate },
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (speed == rate) Color.White else Color.Black.copy(alpha = 0.45f),
                                contentColor = if (speed == rate) Color.Black else Color.White,
                            ),
                            modifier = Modifier.width(48.dp).semantics {
                                contentDescription = "Playback speed $label"
                                this.selected = speed == rate
                            },
                        ) { Text(label) }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (started) {
                        IconButton(onClick = { playing = !playing }) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (playing) "Pause exercise video" else "Play exercise video",
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = { videoView?.seekTo(0); playing = true }) {
                            Icon(Icons.Rounded.Replay, "Replay exercise video", tint = Color.White)
                        }
                    }
                }
        }
    }
    if (clip == null || failed)
        Text(
            if (failed) "Video unavailable. Photo preview shown." else "Photo preview",
            color = OF.muted,
            style = MaterialTheme.typography.labelMedium,
        )
}
