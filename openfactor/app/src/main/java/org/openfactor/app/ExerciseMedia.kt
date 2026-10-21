package org.openfactor.app

import android.net.Uri
import android.widget.MediaController
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Camera choices are separate views, never animation frames. Remote paths aren't playable URLs. */
@Composable
fun ExerciseMedia(e: Exercise) {
    var selected by rememberSaveable(e.id) { mutableIntStateOf(0) }
    var playing by rememberSaveable(e.id, selected) { mutableStateOf(false) }
    var failed by remember(e.id, selected) { mutableStateOf(false) }
    val context = LocalContext.current
    val clip = e.clips.getOrNull(selected)?.takeIf { it.isNotBlank() } ?: e.video
    val uri by
        produceState<Uri?>(null, clip) {
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
                                        "exercise-videos/${clip.substringAfterLast('/')}",
                                    )
                                output.parentFile?.mkdirs()
                                if (!output.exists()) {
                                    val pending = File(output.parentFile, output.name + ".partial")
                                    context.assets.open(clip).use { input ->
                                        pending.outputStream().use { input.copyTo(it) }
                                    }
                                    check(pending.renameTo(output))
                                }
                                Uri.fromFile(output)
                            } else Uri.parse(clip)
                        }
                        .getOrNull()
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(290.dp), contentAlignment = Alignment.Center) {
            if (playing && uri != null && !failed) {
                key(uri) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setMediaController(
                                    MediaController(ctx).also { it.setAnchorView(this) }
                                )
                                setOnPreparedListener {
                                    it.isLooping = true
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    failed = true
                                    playing = false
                                    true
                                }
                            }
                        },
                        modifier =
                            Modifier.fillMaxSize().semantics {
                                contentDescription = "Exercise video, view ${selected + 1}"
                            },
                        onRelease = { it.stopPlayback() },
                    )
                }
            } else {
                ExerciseImage(e, Modifier.fillMaxSize(), selected)
                if (uri != null && !failed)
                    FilledIconButton({ playing = true }, Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.PlayArrow, "Play exercise video")
                    }
            }
        }
        if (e.frames.size > 1)
            Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                e.frames.indices.forEach { index ->
                    Surface(
                        onClick = { selected = index },
                        border = if (selected == index) BorderStroke(2.dp, OF.blue) else null,
                        color = OF.card,
                        modifier =
                            Modifier.size(64.dp, 86.dp).semantics {
                                contentDescription = "Camera view ${index + 1}"
                            },
                    ) {
                        ExerciseImage(e, Modifier.fillMaxSize(), index)
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
