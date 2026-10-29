package org.openfactor.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real ExoPlayer/decoder coverage with authored, test-only clips; no media service dependency. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ExerciseAutoplayLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private data class Frame(
        val player: Player, val playing: Boolean, val index: Int, val position: Long,
        val volume: Float, val repeat: Int, val count: Int,
    )

    private fun currentFrame(): Frame? {
        var result: Frame? = null
        instrumentation.runOnMainSync {
            fun playerIn(view: View): Player? {
                if (view is PlayerView) return view.player
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    playerIn(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            playerIn(compose.activity.window.decorView)?.let {
                result = Frame(it, it.isPlaying, it.currentMediaItemIndex, it.currentPosition,
                    it.volume, it.repeatMode, it.mediaItemCount)
            }
        }
        return result
    }

    @Test fun visibleGuideAutoplaysRealFramesLoopsAnglesAndHonorsPauseAcrossBackground() {
        try {
            exercisePlaybackJourney()
        } catch (failure: Throwable) {
            android.util.Log.e("ComposeTestAutoplay", "Original test failure before activity teardown", failure)
            throw failure
        }
    }

    private fun exercisePlaybackJourney() {
        // Keep the shared image loader tied to the production application, not this test's asset wrapper.
        SingletonImageLoader.get(instrumentation.targetContext)
        val context = TestVideoContext(instrumentation.targetContext, instrumentation.context.assets)
        val id = "00000000000000000000000000000001"
        val exercise = Exercise(id, "Synthetic playback fixture", "Other", "Other",
            frames = List(3) { "reference/previews/synthetic.svg" },
            clips = listOf("a", "b", "c").map { "reference/videos/${id}_$it.mp4" })
        val active = mutableStateOf(true)
        val shown = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                OpenFactorTheme {
                    if (shown.value) Box(Modifier.width(280.dp)) {
                        ExerciseMedia(exercise, active = active.value)
                    }
                }
            }
        }
        // No Play tap: the real player must decode and advance its first clip automatically.
        compose.waitUntil(15_000) { currentFrame()?.let { it.playing && it.position >= 250 } == true }
        val first = requireNotNull(currentFrame())
        assertEquals(0f, first.volume, 0f)
        assertEquals(Player.REPEAT_MODE_ALL, first.repeat)
        assertEquals(3, first.count)
        val angles = mutableSetOf(first.index)
        compose.waitUntil(20_000) {
            currentFrame()?.takeIf { it.playing }?.let { angles += it.index }
            angles == setOf(0, 1, 2)
        }
        compose.waitUntil(10_000) { currentFrame()?.let { it.playing && it.index == 0 } == true }
        compose.onNodeWithContentDescription("Pause exercise video").performClick()
        compose.waitUntil(3_000) { currentFrame()?.playing == false }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithContentDescription("Play exercise video").assertExists()
        compose.onNodeWithContentDescription("Pause exercise video").assertDoesNotExist()
        compose.waitUntil(3_000) { currentFrame() == null }
        // A deliberate Play recreates the released player instead of reviving a stopped engine.
        compose.onNodeWithContentDescription("Play exercise video").performClick()
        compose.waitUntil(15_000) { currentFrame()?.let { it.playing && it.position >= 250 } == true }
        assertNotSame(first.player, requireNotNull(currentFrame()).player)
        compose.runOnIdle { active.value = false }
        compose.waitUntil(3_000) { currentFrame() == null }
        compose.runOnIdle { active.value = true }
        compose.waitUntil(15_000) { currentFrame()?.playing == true }
        compose.runOnIdle { shown.value = false }
        compose.waitUntil(3_000) { currentFrame() == null }
    }

    /** Assets come from the test APK, while cache/files and Coil's factory remain the real app's. */
    private class TestVideoContext(base: Context, private val videoAssets: AssetManager) :
        ContextWrapper(base), SingletonImageLoader.Factory {
        override fun getAssets(): AssetManager = videoAssets
        override fun getApplicationContext(): Context = this
        override fun newImageLoader(context: Context): ImageLoader =
            (baseContext.applicationContext as SingletonImageLoader.Factory).newImageLoader(context)
    }
}
