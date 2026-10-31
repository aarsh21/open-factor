package org.openfactor.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * Set rows swipe toward the reading end to log and toward the start to delete. Distances here are
 * "travel": the finger's displacement along the reading direction, positive toward the end (log).
 */

internal enum class SetSwipeOutcome { REST, LOG, REFUSE, DELETE }

internal object SetSwipe {
    /** Share of the row width that commits a slow drag. */
    const val CommitFraction = .4f
    /** ≈0.11 px/ms on the web, in dp so a flick feels the same on every density. */
    const val FlickDpPerMs = .11f
    /** A flick still needs this much travel past the touch slop, so a jittery release never commits. */
    const val FlickMinDp = 12f
    /** How far a refused swipe (invalid or already logged set) can be pulled before it stops giving. */
    const val RefusalReachDp = 40f
    const val RubberBand = .55f
}

/** Apple's rubber band: follows at [SetSwipe.RubberBand] at first and never passes [reach]. */
internal fun rubberBand(over: Float, reach: Float): Float =
    if (over <= 0f || reach <= 0f) 0f else over * reach * SetSwipe.RubberBand / (reach + SetSwipe.RubberBand * over)

internal fun rubberBandInverse(shown: Float, reach: Float): Float {
    if (shown <= 0f || reach <= 0f) return 0f
    val r = shown.coerceAtMost(reach * .999f)
    return r * reach / (SetSwipe.RubberBand * (reach - r))
}

/**
 * Finger travel to on-screen offset: 1:1 up to the commit point, then resistance that never passes
 * the row's edge. A row that cannot be logged only gives a short, resisting pull toward log.
 */
internal fun setSwipeShown(travel: Float, width: Float, canLog: Boolean, refusalReach: Float): Float {
    if (width <= 0f) return 0f
    val threshold = width * SetSwipe.CommitFraction
    val distance = abs(travel)
    if (travel > 0f && !canLog) return rubberBand(distance, refusalReach)
    val shown = if (distance <= threshold) distance else threshold + rubberBand(distance - threshold, width - threshold)
    return shown * sign(travel)
}

/** Inverse of [setSwipeShown], so a row grabbed mid-settle continues from where it is on screen. */
internal fun setSwipeTravel(shown: Float, width: Float, canLog: Boolean, refusalReach: Float): Float {
    if (width <= 0f) return 0f
    val threshold = width * SetSwipe.CommitFraction
    val distance = abs(shown)
    if (shown > 0f && !canLog) return rubberBandInverse(distance, refusalReach)
    val travel = if (distance <= threshold) distance else threshold + rubberBandInverse(distance - threshold, width - threshold)
    return travel * sign(shown)
}

/** 1 = log armed, -1 = delete armed, 0 = below the commit point (or log refused). */
internal fun setSwipeArmed(travel: Float, width: Float, canLog: Boolean): Int {
    val threshold = width * SetSwipe.CommitFraction
    return when {
        width <= 0f -> 0
        travel >= threshold && canLog -> 1
        travel <= -threshold -> -1
        else -> 0
    }
}

/**
 * Release decision. A flick commits only toward the side the row is already on, and a drag past
 * the commit point is cancelled by flicking back. [velocity] and [flick] are in the same units.
 */
internal fun setSwipeOutcome(
    travel: Float,
    velocity: Float,
    width: Float,
    flick: Float,
    flickMinTravel: Float,
    canLog: Boolean,
): SetSwipeOutcome {
    if (width <= 0f) return SetSwipeOutcome.REST
    val threshold = width * SetSwipe.CommitFraction
    return when {
        travel > 0f && ((velocity >= flick && travel >= flickMinTravel) || (travel >= threshold && velocity > -flick)) ->
            if (canLog) SetSwipeOutcome.LOG else SetSwipeOutcome.REFUSE
        travel < 0f && ((velocity <= -flick && -travel >= flickMinTravel) || (-travel >= threshold && velocity < flick)) ->
            SetSwipeOutcome.DELETE
        else -> SetSwipeOutcome.REST
    }
}

/** The active workout exactly before and after one set removal. */
data class SetRemovalUndo(val before: Workout, val after: Workout)

/** Same rule as the menu's Remove Set: a side removes its pair, a Myo component only itself (with its side). */
fun AppData.withRemovedSet(exerciseId: String, setId: String): AppData {
    val workout = active ?: return this
    return copy(active = workout.copy(exercises = workout.exercises.map {
        if (it.id == exerciseId) it.withoutSet(setId).validateSetLinks() else it
    }))
}

fun AppData.setRemovalUndo(next: AppData): SetRemovalUndo? {
    val before = active ?: return null
    val after = next.active?.takeIf { it.id == before.id && it != before } ?: return null
    return SetRemovalUndo(before, after)
}

/**
 * Undo can arrive late (a long snackbar); any edit after the removal withdraws it, so it can never
 * erase new work. Moving between exercises is not an edit and keeps the offer.
 */
fun AppData.canUndoSetRemoval(undo: SetRemovalUndo): Boolean =
    active?.let { it.copy(selectedExerciseId = undo.after.selectedExerciseId) == undo.after } == true

/** Restores the set with its ids, values, completion, kind and side/Myo links, at its old position. */
fun AppData.withoutSetRemoval(undo: SetRemovalUndo): AppData =
    if (!canUndoSetRemoval(undo)) this else copy(active = undo.before.copy(selectedExerciseId = active?.selectedExerciseId))

private class SwipeColors(
    val idle: Color,
    val idleIcon: Color,
    val log: Color,
    val onLog: Color,
    val delete: Color,
    val onDelete: Color,
)

/**
 * Gesture state lives here, read only from layout, draw and layer lambdas: a drag frame redraws
 * this row and never recomposes it, its fields or any other row.
 */
@Stable
internal class SetRowSwipeState(private val scope: CoroutineScope) {
    val offset = Animatable(0f)
    val armed = Animatable(0f)
    val height = Animatable(1f)
    var width = 0
    var leaving = false
        private set
    /** Captured when a drag starts so a log's return keeps its green even though the set is now completed. */
    var logStyle = false
        private set

    internal var canLog = false
    internal var onLog: () -> Unit = {}
    internal var onDelete: () -> Unit = {}
    internal var motion = true
    internal var haptics: HapticFeedback? = null
    internal var density: Density = Density(1f)
    private var armedSide = 0

    private val refusalReach get() = with(density) { SetSwipe.RefusalReachDp.dp.toPx() }
    fun shown(travel: Float) = setSwipeShown(travel, width.toFloat(), logStyle, refusalReach)
    fun travelFrom(shown: Float) = setSwipeTravel(shown, width.toFloat(), logStyle, refusalReach)

    fun begin() { logStyle = canLog }

    fun track(travel: Float) {
        scope.launch { offset.snapTo(shown(travel)) }
        val side = setSwipeArmed(travel, width.toFloat(), logStyle)
        if (side == armedSide) return
        // One light tick on crossing the commit point, and another on drawing back below it.
        haptics?.performHapticFeedback(if (side != 0) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
        armedSide = side
        scope.launch {
            val target = if (side != 0) 1f else 0f
            if (motion) armed.animateTo(target, tween(AppMotion.Quick, easing = AppMotion.EaseOut)) else armed.snapTo(target)
        }
    }

    fun release(travel: Float, velocity: Float) {
        val flick = with(density) { SetSwipe.FlickDpPerMs * 1000f * 1.dp.toPx() }
        val minTravel = with(density) { SetSwipe.FlickMinDp.dp.toPx() }
        // The row's own velocity, so the settle continues the motion on screen without a seam.
        val shownVelocity = velocity * (shown(travel + 1f) - shown(travel))
        armedSide = 0
        when (setSwipeOutcome(travel, velocity, width.toFloat(), flick, minTravel, logStyle)) {
            SetSwipeOutcome.LOG -> { onLog(); settle(shownVelocity, keepArmed = true) }
            SetSwipeOutcome.DELETE -> delete(shownVelocity)
            SetSwipeOutcome.REFUSE -> {
                // Heard, but this set cannot be logged yet: a gentle no, and the row rubber-bands home.
                haptics?.performHapticFeedback(HapticFeedbackType.Reject)
                settle(shownVelocity)
            }
            SetSwipeOutcome.REST -> settle(shownVelocity)
        }
    }

    private fun settle(velocity: Float, keepArmed: Boolean = false) {
        scope.launch {
            if (!keepArmed) launch { if (motion) armed.animateTo(0f, tween(AppMotion.Quick, easing = AppMotion.EaseOut)) else armed.snapTo(0f) }
            // Critically damped: home without crossing into the other side's colour.
            if (motion) offset.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow), initialVelocity = velocity)
            else offset.snapTo(0f)
            armed.snapTo(0f)
        }
    }

    /** Also the accessibility path: no travel, the same removal. */
    fun delete(velocity: Float = 0f) {
        if (leaving) return
        leaving = true
        scope.launch {
            if (motion && width > 0) {
                // Leaves in the swipe direction at release speed, then its space closes and neighbours glide up.
                launch { armed.animateTo(1f, tween(AppMotion.Quick, easing = AppMotion.EaseOut)) }
                offset.animateTo(
                    -width.toFloat(),
                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium, with(density) { 8.dp.toPx() }),
                    initialVelocity = velocity.coerceAtMost(0f),
                )
                height.animateTo(0f, tween(AppMotion.State, easing = AppMotion.EaseOut))
            }
            onDelete()
        }
    }
}

@Composable
internal fun rememberSetRowSwipe(canLog: Boolean, onLog: () -> Unit, onDelete: () -> Unit): SetRowSwipeState {
    val scope = rememberCoroutineScope()
    val state = remember { SetRowSwipeState(scope) }
    val motion = LocalAppMotionEnabled.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    SideEffect {
        state.canLog = canLog
        state.onLog = onLog
        state.onDelete = onDelete
        state.motion = motion
        state.haptics = haptics
        state.density = density
    }
    return state
}

/**
 * Outside the row's padding: closes the whole slot once a deleted row has left. The clip comes first
 * so it follows the collapsing height (and keeps a sliding row inside its slot).
 */
internal fun Modifier.setSwipeCollapse(state: SetRowSwipeState): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * state.height.value).roundToInt()
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/**
 * The reveal is drawn behind and outside the moving layer, and the finger is measured before it,
 * so 1:1 tracking does not cancel the velocity out.
 */
@Composable
internal fun Modifier.setRowSwipe(state: SetRowSwipeState): Modifier {
    val motion = LocalAppMotionEnabled.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val check = rememberVectorPainter(Icons.Rounded.Check)
    val trash = rememberVectorPainter(Icons.Rounded.Delete)
    val colors = SwipeColors(
        idle = OF.elevated, idleIcon = OF.muted,
        log = OF.complete, onLog = OF.onComplete,
        delete = MaterialTheme.colorScheme.error, onDelete = MaterialTheme.colorScheme.onError,
    )
    return onSizeChanged { state.width = it.width }
        .drawBehind { drawSwipeReveal(state, colors, check, trash, motion, direction) }
        .pointerInput(state, direction) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (state.leaving) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                var moved = Offset.Zero
                // Only the first finger counts; vertical intent belongs to the list, taps to the fields and buttons.
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || change.isConsumed) return@awaitEachGesture
                    tracker.addPosition(change.uptimeMillis, change.position)
                    moved += change.positionChange()
                    if (abs(moved.y) > slop && abs(moved.y) >= abs(moved.x)) return@awaitEachGesture
                    if (abs(moved.x) > slop && abs(moved.x) > abs(moved.y) * 1.2f) { change.consume(); break }
                }
                state.begin()
                // Grab from where it is on screen (mid-settle included), without a slop jump.
                var travel = state.travelFrom(state.offset.value) + (moved.x - sign(moved.x) * slop) * direction
                state.track(travel)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) break
                    travel += change.positionChange().x * direction
                    change.consume()
                    state.track(travel)
                }
                state.release(travel, tracker.calculateVelocity().x * direction)
            }
        }
        .graphicsLayer {
            // Remove animations: no travel; the reveal's tint alone shows how close the swipe is.
            translationX = if (motion) state.offset.value * direction else 0f
            if (!motion && state.width > 0) {
                val progress = (abs(state.offset.value) / (state.width * SetSwipe.CommitFraction)).coerceIn(0f, 1f)
                alpha = 1f - .2f * progress
            }
        }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSwipeReveal(
    state: SetRowSwipeState,
    colors: SwipeColors,
    check: VectorPainter,
    trash: VectorPainter,
    motion: Boolean,
    direction: Float,
) {
    val shown = state.offset.value
    if (shown == 0f || size.width <= 0f) return
    val logSide = shown > 0f
    val progress = (abs(shown) / (size.width * SetSwipe.CommitFraction)).coerceIn(0f, 1f)
    val armed = state.armed.value
    val active = !logSide || state.logStyle
    val accent = if (!active) colors.idle else if (logSide) colors.log else colors.delete
    val onAccent = if (!active) colors.idleIcon else if (logSide) colors.onLog else colors.onDelete
    // A hint of the outcome while approaching; the full colour arrives exactly at the commit point.
    val background = lerp(lerp(colors.idle, accent, .22f * progress), accent, armed)
    val iconTint = lerp(lerp(colors.idleIcon, accent, progress), onAccent, armed)
    // The icon sits on the edge the row leaves from (start for log, end for delete, mirrored in RTL).
    val physical = shown * direction
    val atLeft = physical > 0f
    val corner = CornerRadius(8.dp.toPx())
    if (!motion) {
        // Remove animations: nothing moves, so a wash over the still row (like the completed wash) carries it.
        drawRoundRect(accent, cornerRadius = corner, alpha = .08f + .14f * progress + .2f * armed)
        return
    }
    val revealed = abs(physical)
    val left = if (atLeft) 0f else size.width - revealed
    val iconSize = 24.dp.toPx()
    val inset = 18.dp.toPx()
    // While a deleted row's slot closes, the reveal shrinks with it and the icon fades rather than being sliced.
    val closing = state.height.value
    val height = size.height * closing
    val scale = (.6f + .4f * progress) * (.8f + .2f * closing)
    clipRect(left, 0f, left + revealed, height) {
        drawRoundRect(background, size = Size(size.width, height), cornerRadius = corner)
        // Pinned near the edge once there is room; centred in a narrow strip (a refused pull) instead of cut off.
        val fromEdge = ((revealed - iconSize) / 2f).coerceAtMost(inset)
        val x = if (atLeft) fromEdge else size.width - fromEdge - iconSize
        translate(x, (height - iconSize) / 2f) {
            scale(scale, Offset(iconSize / 2f, iconSize / 2f)) {
                with(if (logSide) check else trash) {
                    draw(Size(iconSize, iconSize), alpha = (.45f + .55f * progress) * closing, colorFilter = ColorFilter.tint(iconTint))
                }
            }
        }
    }
}
