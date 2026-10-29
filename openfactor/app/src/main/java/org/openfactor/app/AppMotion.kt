package org.openfactor.app

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** One timing vocabulary. State changes never wait for their visual acknowledgement. */
internal object AppMotion {
    const val Quick = 120
    const val State = 180
    const val Step = 220
    const val Enter = 280
    val EaseOut = CubicBezierEasing(.16f, 1f, .3f, 1f)
}

internal val LocalAppMotionEnabled = staticCompositionLocalOf { true }
internal val LocalAppWindowRevealed = staticCompositionLocalOf { true }

/** Updates while visible as well as after returning from Android accessibility settings. */
@Composable
internal fun rememberAppMotionEnabled(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun readEnabled() = ValueAnimator.areAnimatorsEnabled() &&
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var enabled by remember(context) { mutableStateOf(readEnabled()) }
    DisposableEffect(context, owner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = readEnabled() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = readEnabled()
        }
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            owner.lifecycle.removeObserver(lifecycle)
        }
    }
    return enabled
}

/** Uses the component's own interactions: native ripple, cancellation and hit bounds survive. */
@Composable
internal fun Modifier.pressFeedback(source: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalAppMotionEnabled.current
    val touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    val pressScale = animateFloatAsState(
        if (enabled && motion && touch && pressed) .975f else 1f,
        tween(if (motion) AppMotion.Quick else 0, easing = AppMotion.EaseOut),
        label = "press acknowledgement",
    )
    // A draw transform, rather than a layout-layer transform, leaves the actual touch region
    // unchanged even for a 48dp icon button. The existing ripple belongs to the same drawing.
    return drawWithContent { scale(pressScale.value) { this@drawWithContent.drawContent() } }
}
