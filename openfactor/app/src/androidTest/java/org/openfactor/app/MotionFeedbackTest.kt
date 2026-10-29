package org.openfactor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Exercises the shipped control and native press/cancel path, not a parallel animation fixture. */
@RunWith(AndroidJUnit4::class)
class MotionFeedbackTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            // Set the native window default before the Compose host is attached. Android8
            // does not update an existing host's mode from synthetic Compose TouchInput.
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        }
    }).around(compose)

    private fun enterTouchMode(manager: InputModeManager) {
        // API26 synthetic TouchInput does not change a window left in Keyboard mode by a
        // previous test. The shipped effect intentionally excludes keyboard activation.
        // Establish and assert the real platform precondition; keep the pixel check below.
        compose.waitUntil(1_000) { manager.inputMode == InputMode.Touch }
        compose.runOnIdle { assertEquals(InputMode.Touch, manager.inputMode) }
    }

    private fun lightEdge(image: ImageBitmap): Int {
        val pixels = image.toPixelMap()
        val y = image.height / 2
        // The button fills a black stage. Its center row has a solid light edge, away from
        // corners and text; measuring the silhouette avoids font/ripple color comparisons.
        return (0 until image.width / 4).first { x ->
            val color = pixels[x, y]
            color.red > .6f && color.green > .6f && color.blue > .6f
        }
    }

    @Test fun heldTouchAcknowledgesPressWithoutRelayoutAndCancellationDoesNotActivate() {
        var clicks = 0
        var layoutSize = IntSize.Zero
        var inputModeManager: InputModeManager? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OpenFactorTheme {
                CompositionLocalProvider(LocalAppMotionEnabled provides true) {
                    val manager = LocalInputModeManager.current
                    SideEffect { inputModeManager = manager }
                    Box(Modifier.size(240.dp, 72.dp).background(Color.Black).testTag("stage")) {
                        PrimaryAction("Continue", { clicks++ }, Modifier.align(Alignment.Center)
                            .testTag("action").onSizeChanged { layoutSize = it })
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        enterTouchMode(inputModeManager!!)
        val stage = compose.onNodeWithTag("stage")
        val action = compose.onNodeWithTag("action")
        val restingEdge = lightEdge(stage.captureToImage())
        val restingSize = layoutSize
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pressedEdge = lightEdge(stage.captureToImage())
        compose.runOnIdle {
            assertTrue("The held press must visibly acknowledge touch (mode=${inputModeManager!!.inputMode}, resting=$restingEdge, pressed=$pressedEdge)", pressedEdge > restingEdge)
            assertEquals("Press feedback must not resize layout", restingSize, layoutSize)
            assertEquals("A hold must not activate the action", 0, clicks)
        }
        action.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("Cancelled feedback must return to its resting edge", restingEdge, lightEdge(stage.captureToImage()))
        compose.runOnIdle { assertEquals("Cancelling must never click", 0, clicks) }
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        // Release within the original hit rectangle, just beyond the compressed painted edge.
        // A visual layer that shrinks the touch matrix would lose this near-edge activation.
        action.performTouchInput { moveTo(Offset(width - 1f, center.y)); up() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals("A completed tap activates immediately and only once", 1, clicks) }
    }

    @Test fun reducedMotionKeepsTheControlStillAndPreservesImmediateActivation() {
        var clicks = 0
        var inputModeManager: InputModeManager? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            OpenFactorTheme {
                CompositionLocalProvider(LocalAppMotionEnabled provides false) {
                    val manager = LocalInputModeManager.current
                    SideEffect { inputModeManager = manager }
                    Box(Modifier.size(240.dp, 72.dp).background(Color.Black).testTag("stage")) {
                        PrimaryAction("Continue", { clicks++ }, Modifier.align(Alignment.Center).testTag("action"))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        enterTouchMode(inputModeManager!!)
        val stage = compose.onNodeWithTag("stage")
        val action = compose.onNodeWithTag("action")
        val restingEdge = lightEdge(stage.captureToImage())
        action.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("Reduced motion must not displace the control", restingEdge, lightEdge(stage.captureToImage()))
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals("Reduced motion must keep activation immediate and singular", 1, clicks) }
        assertEquals(restingEdge, lightEdge(stage.captureToImage()))
    }
}
