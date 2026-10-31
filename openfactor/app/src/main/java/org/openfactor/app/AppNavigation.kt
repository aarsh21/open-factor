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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * What the screen shows. A tab only matters on the root route, and an id only for routes that
 * render one record, so unrelated id writes never restart a transition.
 */
internal data class AppDestination(val route: String, val key: String = "") {
    companion object {
        private val keyedRoutes = setOf("weight-entry", "gym", "exercise", "exercise-settings", "history-detail")

        fun of(route: String, detailId: String, tab: Int) = when {
            route.isBlank() -> AppDestination("", "tab:$tab")
            route in keyedRoutes -> AppDestination(route, detailId)
            else -> AppDestination(route)
        }
    }
}

/** Depth decides direction: deeper pushes in from the right, shallower reveals from the left. */
internal fun routeDepth(route: String): Int = when (route) {
    "" -> 0
    "profile", "body-data", "programs", "gyms", "levels", "session", "workout-draft", "history", "templates" -> 1
    "body-charts", "weight-entry", "program-setup", "program-editor", "gym", "picker", "draft-picker", "routine" -> 2
    "program-picker", "history-detail", "exercise", "custom" -> 3
    else -> 4
}

/**
 * Pushes travel a short distance and arrive quickly; the departing screen leaves faster than the
 * new one arrives so the two never compete. Peers (tabs, same-depth swaps) fade through instead
 * of sliding, because they have no spatial relationship to communicate.
 */
internal fun AnimatedContentTransitionScope<AppDestination>.appTransition(motion: Boolean): ContentTransform {
    if (!motion) return EnterTransition.None togetherWith ExitTransition.None
    val from = routeDepth(initialState.route)
    val to = routeDepth(targetState.route)
    val enter = tween<Float>(AppMotion.Step, easing = AppMotion.EaseOut)
    val transform = if (from == to) {
        (fadeIn(tween(AppMotion.Step, delayMillis = 40, easing = AppMotion.EaseOut)) +
            scaleIn(tween(AppMotion.Step, delayMillis = 40, easing = AppMotion.EaseOut), initialScale = .985f))
            .togetherWith(fadeOut(tween(AppMotion.Quick - 30)))
    } else if (to > from) {
        (fadeIn(enter) + slideInHorizontally(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { it / 12 })
            .togetherWith(
                fadeOut(tween(AppMotion.Quick)) +
                    slideOutHorizontally(tween(AppMotion.State, easing = AppMotion.EaseOut)) { -it / 24 }
            )
            .apply { targetContentZIndex = 1f }
    } else {
        // A pop is also what a predictive back gesture scrubs. Linear keeps the departing screen
        // 1:1 with the finger (half a swipe = half gone) instead of vanishing at the first touch.
        (fadeIn(enter) + slideInHorizontally(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { -it / 24 })
            .togetherWith(
                fadeOut(tween(AppMotion.Step, easing = LinearEasing)) +
                    slideOutHorizontally(tween(AppMotion.Step, easing = LinearEasing)) { it / 10 }
            )
            .apply { targetContentZIndex = -1f }
    }
    // Screens own their full height; never animate a size jump between unrelated layouts.
    return transform.using(SizeTransform(clip = false) { _, _ -> tween(0) })
}
