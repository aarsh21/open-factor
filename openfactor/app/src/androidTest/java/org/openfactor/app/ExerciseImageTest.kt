package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(coil3.annotation.DelicateCoilApi::class)
class ExerciseImageTest {
    @get:Rule val compose = createComposeRule()
    private val exercise = Exercise("preview-test", "Preview exercise", "Chest", "Barbell",
        frames = listOf("test/portrait-preview.png"))
    private lateinit var originalLoader: ImageLoader
    private lateinit var testLoader: ImageLoader

    @Before fun provideInMemoryPortrait() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        originalLoader = SingletonImageLoader.get(context)
        val image = Bitmap.createBitmap(36, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff2468c0.toInt()) }.asImage()
        testLoader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                if (chain.request.data == "file:///android_asset/test/portrait-preview.png")
                    SuccessResult(image = image, request = chain.request, dataSource = DataSource.MEMORY)
                else chain.proceed()
            })
        }.build()
        SingletonImageLoader.setUnsafe(testLoader)
    }

    @After fun restoreImageLoader() {
        SingletonImageLoader.setUnsafe(originalLoader)
        testLoader.shutdown()
    }

    @Test fun portraitPreviewsFillPortraitSquareAndWideSlotsInBothThemes() {
        var theme by mutableStateOf(AppTheme.LIGHT)
        val slots = listOf(46 to 62, 42 to 54, 56 to 56, 40 to 40, 80 to 40)
        compose.setContent {
            OpenFactorTheme(theme) {
                Column {
                    slots.forEachIndexed { index, (width, height) ->
                        ExerciseImage(exercise, Modifier.size(width.dp, height.dp).testTag("preview-$index"))
                    }
                }
            }
        }
        listOf(AppTheme.LIGHT, AppTheme.DARK).forEach { nextTheme ->
            compose.runOnIdle { theme = nextTheme }
            slots.indices.forEach { index ->
                val node = compose.onNodeWithTag("preview-$index")
                compose.waitUntil(10_000) {
                    val pixels = node.captureToImage().toPixelMap()
                    isFixture(pixels[pixels.width / 2, pixels.height / 2])
                }
                val pixels = node.captureToImage().toPixelMap()
                // Sample all four mid-edges, away from the rounded corners. Fit would expose gutters.
                listOf(1 to pixels.height / 2, pixels.width - 2 to pixels.height / 2,
                    pixels.width / 2 to 1, pixels.width / 2 to pixels.height - 2).forEach { (x, y) ->
                    assertTrue("$nextTheme preview $index has a gutter at $x,$y", isFixture(pixels[x, y]))
                }
            }
        }
    }

    @Test fun missingAndFailedImagesUseTheSameAccessibleFallback() {
        compose.setContent {
            OpenFactorTheme {
                Row {
                    ExerciseImage(exercise.copy(frames = emptyList()), Modifier.size(48.dp, 64.dp))
                    ExerciseImage(exercise.copy(frames = listOf("test/missing-preview.png")), Modifier.size(48.dp, 64.dp))
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Exercise image unavailable").fetchSemanticsNodes().size == 2
        }
        compose.onAllNodesWithContentDescription("Exercise image unavailable").assertCountEquals(2)
    }

    private fun isFixture(color: Color): Boolean =
        kotlin.math.abs(color.red - 36f / 255) < 0.03f &&
            kotlin.math.abs(color.green - 104f / 255) < 0.03f &&
            kotlin.math.abs(color.blue - 192f / 255) < 0.03f
}
