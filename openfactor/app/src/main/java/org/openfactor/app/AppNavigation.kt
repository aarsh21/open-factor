package org.openfactor.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Pushes travel a short distance and arrive quickly; the departing screen leaves faster than the
 * new one arrives so the two never compete. Peers (tabs, same-depth swaps) fade through instead
 * of sliding, because they have no spatial relationship to communicate.
 */
internal fun AnimatedContentTransitionScope<AppScreen>.appTransition(motion: Boolean, scrubbing: Boolean = false): ContentTransform {
    if (!motion) return EnterTransition.None togetherWith ExitTransition.None
    val from = initialState.depth
    val to = targetState.depth
    val enter = tween<Float>(AppMotion.Step, easing = AppMotion.EaseOut)
    val transform = if (from == to) {
        // Tabs are core navigation: no delay and no decorative scale, just a quick fade through.
        fadeIn(tween(150, easing = AppMotion.EaseOut)).togetherWith(fadeOut(tween(AppMotion.Quick - 30)))
    } else if (to > from) {
        (fadeIn(enter) + slideInHorizontally(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { it / 12 })
            .togetherWith(
                fadeOut(tween(AppMotion.Quick)) +
                    slideOutHorizontally(tween(AppMotion.State, easing = AppMotion.EaseOut)) { -it / 24 }
            )
            .apply { targetContentZIndex = 1f }
    } else {
        // A predictive back gesture scrubs the pop: linear keeps the departing screen 1:1 with the
        // finger (half a swipe = half gone). A tapped back mirrors the push exit instead.
        val exit = if (scrubbing) LinearEasing else AppMotion.EaseOut
        (fadeIn(enter) + slideInHorizontally(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { -it / 24 })
            .togetherWith(
                fadeOut(tween(if (scrubbing) AppMotion.Step else AppMotion.Quick, easing = exit)) +
                    slideOutHorizontally(tween(if (scrubbing) AppMotion.Step else AppMotion.State, easing = exit)) { it / 12 }
            )
            .apply { targetContentZIndex = -1f }
    }
    // Screens own their full height; never animate a size jump between unrelated layouts.
    return transform.using(SizeTransform(clip = false) { _, _ -> tween(0) })
}
