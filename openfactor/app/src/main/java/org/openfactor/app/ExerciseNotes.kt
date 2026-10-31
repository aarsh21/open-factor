package org.openfactor.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

internal val PinnedNoteIcon = Icons.Rounded.PushPin
internal val TemplateNoteIcon = Icons.AutoMirrored.Rounded.ListAlt
internal val LastNoteIcon = Icons.Rounded.History
private val SessionNoteIcon = Icons.Rounded.EditNote

data class LastExerciseNote(val text: String, val workoutId: String, val startedAt: Long)

/**
 * The newest non-blank note left on [exerciseId] in saved history. Notes count even when only
 * warm-ups were logged: "stopped after warm-up, shoulder" is exactly what next time needs.
 */
fun lastExerciseNote(history: List<Workout>, exerciseId: String, excludeWorkoutId: String? = null): LastExerciseNote? {
    var best: LastExerciseNote? = null
    for (workout in history) {
        if (workout.id == excludeWorkoutId || (best != null && workout.startedAt <= best.startedAt)) continue
        val entry = workout.exercises.lastOrNull { it.exerciseId == exerciseId && it.notes.isNotBlank() } ?: continue
        best = LastExerciseNote(entry.notes.trim(), workout.id, workout.startedAt)
    }
    return best
}

internal fun lastNoteDate(startedAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(date, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "MMM d" else "MMM d, yyyy"))
    }
}

/**
 * Pin = every workout, list = this template, pencil = this workout, clock = last time (a muted
 * placeholder until today's note exists). Notes present when the screen appears render still.
 */
@Composable
internal fun ExerciseNotes(
    entry: SessionExercise,
    pinned: String,
    templateName: String?,
    templateNote: String,
    last: LastExerciseNote?,
    onEditSession: () -> Unit,
    onEditTemplate: () -> Unit,
    onDismissLast: () -> Unit,
) {
    val motion = LocalAppMotionEnabled.current
    val enter = if (motion) expandVertically(tween(AppMotion.State, easing = AppMotion.EaseOut), expandFrom = Alignment.Top)
        else EnterTransition.None
    val exit = if (motion) fadeOut(tween(AppMotion.Quick)) +
        shrinkVertically(tween(AppMotion.State, easing = AppMotion.EaseOut), shrinkTowards = Alignment.Top) else ExitTransition.None
    Column(Modifier.fillMaxWidth()) {
        NoteSlot(pinned.takeIf(String::isNotBlank), enter, exit) {
            NoteRow(PinnedNoteIcon, "Pinned · every workout", it)
        }
        NoteSlot(templateNote.takeIf { it.isNotBlank() && templateName != null }, enter, exit) {
            NoteRow(TemplateNoteIcon, "Template · ${templateName.orEmpty()}", it, onEdit = onEditTemplate, editLabel = "Edit template note")
        }
        NoteSlot(entry.notes.takeIf(String::isNotBlank), enter, exit) {
            NoteRow(SessionNoteIcon, "This workout", it, onEdit = onEditSession, editLabel = "Edit exercise notes")
        }
        NoteSlot(last?.takeIf { entry.notes.isBlank() && !entry.lastNoteDismissed }, enter, exit) {
            LastTimeNote(it, onEditSession, onDismissLast)
        }
    }
}

private class Kept<T> { var value: T? = null }

@Composable
private fun <T : Any> NoteSlot(value: T?, enter: EnterTransition, exit: ExitTransition, content: @Composable (T) -> Unit) {
    // The exit still needs the text it is collapsing away.
    val kept = remember { Kept<T>() }
    if (value != null) kept.value = value
    val shown = value ?: kept.value
    AnimatedVisibility(value != null, enter = enter, exit = exit) {
        // Visible at first composition (load, recreate, exercise switch) stays still; a user's new note settles in.
        val fresh = transition.currentState == EnterExitState.PreEnter
        Box(Modifier.padding(top = 8.dp, bottom = 4.dp).arrival(fresh)) { shown?.let { content(it) } }
    }
}

@Composable
private fun NoteRow(
    icon: ImageVector,
    label: String,
    text: String,
    onEdit: (() -> Unit)? = null,
    editLabel: String? = null,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    labelTone: Color = OF.muted,
    lines: Int = 3,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var clamped by remember(text) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val actionable = clamped || expanded || onEdit != null
    Row(
        Modifier.fillMaxWidth()
            .pressFeedback(source, enabled = actionable)
            .clip(RoundedCornerShape(8.dp))
            .then(if (actionable) Modifier.clickable(
                source, LocalIndication.current,
                onClickLabel = if (clamped) "Show full note" else editLabel ?: "Show less",
            ) {
                // A clamped note reveals itself first; a fully visible one edits (or collapses).
                when { clamped -> { expanded = true; clamped = false }; onEdit != null -> onEdit(); else -> expanded = false }
            } else Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(16.dp), tint = labelTone)
        Column(Modifier.weight(1f).settleSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelTone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text, style = MaterialTheme.typography.bodyMedium, color = tone,
                maxLines = if (expanded) Int.MAX_VALUE else lines, overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) clamped = it.hasVisualOverflow },
            )
        }
        trailing?.invoke(this)
    }
}

/** ≈0.11 px/ms on the web, in dp so a flick feels the same on every density. */
private const val FlickDpPerMs = .11f
private const val CommitFraction = .4f
private const val RubberBand = .55f

@Composable
private fun LastTimeNote(note: LastExerciseNote, onWrite: () -> Unit, onDismiss: () -> Unit) {
    val motion = LocalAppMotionEnabled.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val flick = with(density) { FlickDpPerMs * 1000f * 1.dp.toPx() }
    val friction = with(density) { 40.dp.toPx() }
    // Past the origin the note follows less and less, bounded by `friction`.
    fun resist(p: Float) = if (p >= 0f) p else -(-p * friction * RubberBand) / (friction + RubberBand * -p)
    fun unresist(shown: Float) = if (shown >= 0f) shown
        else -(-shown * friction) / (RubberBand * (friction + shown).coerceAtLeast(1f))
    fun leave(velocity: Float) {
        if (leaving) return
        leaving = true
        scope.launch {
            if (motion && width > 0) offset.animateTo(
                direction * width,
                // A flick keeps its speed on the way out; the button path eases out.
                if (velocity > 0f) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium, with(density) { 1.dp.toPx() })
                else tween(AppMotion.State, easing = AppMotion.EaseOut),
                initialVelocity = direction * velocity,
            )
            dismiss()
        }
    }
    fun release(velocity: Float, p: Float) {
        if (velocity > flick || (p >= width * CommitFraction && velocity > -flick)) leave(velocity)
        else scope.launch {
            if (motion) offset.animateTo(0f, spring(if (abs(velocity) > flick) .8f else Spring.DampingRatioNoBouncy,
                Spring.StiffnessMediumLow), initialVelocity = direction * velocity)
            else offset.snapTo(0f)
        }
    }
    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                val progress = if (width == 0) 0f else (offset.value * direction / width).coerceIn(0f, 1f)
                // Remove animations: no travel, the fade alone shows how close the swipe is to committing.
                translationX = if (motion) offset.value else 0f
                alpha = 1f - progress
            }
            .pointerInput(motion, direction, leaving) {
                if (leaving) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var travel = Offset.Zero
                    // Only the first finger counts; vertical intent belongs to the list.
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        travel += change.positionChange()
                        if (abs(travel.y) > slop && abs(travel.y) >= abs(travel.x)) return@awaitEachGesture
                        if (abs(travel.x) > slop) { change.consume(); break }
                    }
                    val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    // Grab from where it is on screen (mid-settle included), without a slop jump.
                    var p = unresist(offset.value * direction) + (travel.x - sign(travel.x) * slop) * direction
                    scope.launch { offset.snapTo(direction * resist(p)) }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        p += change.positionChange().x * direction
                        change.consume()
                        scope.launch { offset.snapTo(direction * resist(p)) }
                    }
                    release(tracker.calculateVelocity().x * direction, p)
                }
            },
    ) {
        val muted = OF.muted.copy(alpha = .78f)
        NoteRow(
            LastNoteIcon, "Last time · ${lastNoteDate(note.startedAt)}", note.text,
            onEdit = onWrite, editLabel = "Write today's note", tone = muted, labelTone = muted, lines = 2,
        ) {
            val press = remember { MutableInteractionSource() }
            IconButton({ leave(0f) }, Modifier.align(Alignment.CenterVertically).pressFeedback(press), interactionSource = press) {
                Icon(Icons.Rounded.Close, "Dismiss last time note", Modifier.size(18.dp), tint = muted)
            }
        }
    }
}

/** Editing a note; `onSave` returns an error to keep the dialog open. */
@Composable
internal fun NoteDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> String?,
    placeholder: String? = null,
    supporting: String? = null,
    limit: Int? = null,
) {
    var notes by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val tooLong = limit != null && notes.trim().length > limit
    val support = error ?: if (tooLong) "Use $limit characters or fewer." else supporting
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                notes, { notes = it; error = null }, Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text(title) }, minLines = 3, isError = tooLong || error != null,
                placeholder = if (placeholder != null) ({ Text(placeholder, maxLines = 3, overflow = TextOverflow.Ellipsis) }) else null,
                supportingText = if (support != null) ({ Text(support) }) else null,
            )
        },
        confirmButton = { TextButton({ error = onSave(notes) }, enabled = !tooLong) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
