package org.openfactor.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.floor

data class WorkoutExerciseProgress(val complete: Int, val total: Int) {
    val label: String get() = if (total == 0) "No working sets" else "$complete / $total sets"
    val done: Boolean get() = total > 0 && complete >= total
}

/** A left/right pair or a Myo group is one working set; warmups never inflate this progress. */
fun SessionExercise.workoutExerciseProgress(): WorkoutExerciseProgress {
    val sets = logicalSets().filterNot { it.root.warmup }
    return WorkoutExerciseProgress(sets.count { it.completed }, sets.size)
}

/** Moving on is the most common navigation: the next unfinished exercise, wrapping to skipped ones. */
fun Workout.upNextExercise(currentId: String?): SessionExercise? {
    val start = exercises.indexOfFirst { it.id == currentId }
    val order = if (start < 0) exercises else (1 until exercises.size).map { exercises[(start + it) % exercises.size] }
    return order.firstOrNull { !it.workoutExerciseProgress().done }
}

/** How a navigator item joins its neighbours in a superset (supersets may be non-adjacent). */
data class SupersetLink(val grouped: Boolean, val joinsPrevious: Boolean, val joinsNext: Boolean)

fun Workout.supersetLinks(): List<SupersetLink> = exercises.mapIndexed { index, entry ->
    val group = entry.supersetId
    SupersetLink(
        grouped = group != null,
        joinsPrevious = group != null && exercises.getOrNull(index - 1)?.supersetId == group,
        joinsNext = group != null && exercises.getOrNull(index + 1)?.supersetId == group,
    )
}

// 7 × 48dp fit fully on a 411dp phone beside Add; an eighth peeks in to show the strip scrolls.
private val NavigatorSlot = 48.dp
private val NavigatorRing = 44.dp
private val NavigatorPhoto = 34.dp
private val NavigatorStroke = 2.5.dp

/**
 * The whole workout at a glance: one photo per exercise with its working-set ring, superset
 * groups on a shared track and a small indicator under the current one. The header below names
 * the selection, so the strip never repeats it in text (and never truncates a long title).
 */
@Composable
fun WorkoutExerciseSelector(
    workout: Workout,
    exercises: List<Exercise>,
    selected: SessionExercise?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val byId = remember(exercises) { exercises.associateBy { it.id } }
    val motion = LocalAppMotionEnabled.current
    val listState = rememberLazyListState()
    val position = workout.exercises.indexOfFirst { it.id == selected?.id }
    val links = remember(workout.exercises) { workout.supersetLinks() }
    LaunchedEffect(workout.id, selected?.id, workout.exercises.map { it.id }) {
        if (position < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == position }
        if (item == null || item.offset < layout.viewportStartOffset ||
            item.offset + item.size > layout.viewportEndOffset) {
            // Immediate reveal (auto-next, restored sessions), keeping the previous exercise in view.
            listState.scrollToItem((position - 1).coerceAtLeast(0))
        }
    }
    // The indicator glides between items (spatial continuity, also for automatic advances);
    // the selection itself is already committed and the session body swaps instantly.
    val indicator = remember(workout.id) { Animatable(position.toFloat()) }
    LaunchedEffect(workout.id, position) {
        if (motion && position >= 0 && indicator.value >= 0f)
            indicator.animateTo(position.toFloat(), tween(AppMotion.Quick, easing = AppMotion.EaseOut))
        else indicator.snapTo(position.toFloat())
    }
    val indicatorColor = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).selectableGroup().drawWithContent {
                drawContent()
                // Read in draw only: scrolling and the glide redraw the bar, never recompose items.
                val at = indicator.value
                val layout = listState.layoutInfo
                val first = layout.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
                if (at < 0f) return@drawWithContent
                val left = first.offset - layout.viewportStartOffset + (at - first.index) * first.size
                val width = 16.dp.toPx()
                val height = 3.dp.toPx()
                // Drawn outside the list's own clip: an offscreen selection must not paint over Add.
                clipRect {
                    drawRoundRect(
                        indicatorColor,
                        topLeft = Offset(left + (first.size - width) / 2f, size.height - 5.dp.toPx()),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2f),
                    )
                }
            },
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        ) {
            itemsIndexed(workout.exercises, key = { _, entry -> entry.id }) { index, entry ->
                val exercise = byId[entry.exerciseId]
                val title = exercise?.title ?: "Exercise"
                val current = entry.id == selected?.id
                val progress = entry.workoutExerciseProgress()
                val link = links.getOrElse(index) { SupersetLink(false, false, false) }
                val partners = if (link.grouped) workout.exercises
                    .filter { it.supersetId == entry.supersetId && it.id != entry.id }
                    .joinToString(" and ") { byId[it.exerciseId]?.title ?: "Exercise" } else ""
                val press = remember { MutableInteractionSource() }
                NavigatorItem(
                    exercise = exercise,
                    progress = progress,
                    current = current,
                    link = link,
                    title = title,
                    description = "Select $title, ${index + 1} of ${workout.exercises.size}, " +
                        "${progress.complete} of ${progress.total} working sets complete" +
                        (if (partners.isNotEmpty()) ", superset with $partners" else ""),
                    press = press,
                    onClick = { onSelect(entry.id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = if (motion) tween(AppMotion.State, easing = AppMotion.EaseOut) else null,
                        placementSpec = if (motion) tween(AppMotion.State, easing = AppMotion.EaseOut) else null,
                        fadeOutSpec = null,
                    ),
                )
            }
        }
        IconButton(onAdd, Modifier.padding(end = 6.dp, bottom = 4.dp).size(48.dp)) {
            Icon(Icons.Rounded.Add, "Add exercise")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigatorItem(
    exercise: Exercise?,
    progress: WorkoutExerciseProgress,
    current: Boolean,
    link: SupersetLink,
    title: String,
    description: String,
    press: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalAppMotionEnabled.current
    // Logging a set confirms itself in the ring: the segment sweeps in, a finished ring closes.
    val fill = animateFloatAsState(progress.complete.toFloat(),
        tween(if (motion) AppMotion.State else 0, easing = AppMotion.EaseOut), label = "set progress")
    val closed = animateFloatAsState(if (progress.done) 1f else 0f,
        tween(if (motion) AppMotion.State else 0, easing = AppMotion.EaseOut), label = "exercise done")
    // Derived from theme roles so the track and rings read in light, dark and future palettes.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = onSurface.copy(alpha = .08f)
    val trackLine = onSurface.copy(alpha = .22f)
    // The current exercise's ring lights up: its remaining sets read brighter than everyone else's.
    val pending = if (current) onSurface.copy(alpha = .62f) else trackLine
    val complete = OF.green
    val background = OF.background
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(title) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(NavigatorSlot, NavigatorRing + 4.dp)
                .drawBehind {
                    if (!link.grouped) return@drawBehind
                    // Superset members share one tonal track; adjacent items meet edge to edge.
                    val inset = 1.dp.toPx()
                    val radius = CornerRadius(size.height / 2f)
                    val none = CornerRadius.Zero
                    drawPath(Path().apply {
                        addRoundRect(RoundRect(
                            left = if (link.joinsPrevious) 0f else inset, top = 0f,
                            right = if (link.joinsNext) size.width else size.width - inset, bottom = size.height,
                            topLeftCornerRadius = if (link.joinsPrevious) none else radius,
                            bottomLeftCornerRadius = if (link.joinsPrevious) none else radius,
                            topRightCornerRadius = if (link.joinsNext) none else radius,
                            bottomRightCornerRadius = if (link.joinsNext) none else radius,
                        ))
                    }, track)
                }
                .selectable(
                    selected = current,
                    interactionSource = press,
                    indication = ripple(bounded = false, radius = NavigatorRing / 2),
                    role = Role.Tab,
                    onClick = onClick,
                )
                .pressFeedback(press)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(NavigatorRing)) {
                val stroke = NavigatorStroke.toPx()
                val radius = (size.minDimension - stroke) / 2f
                val arcSize = Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)
                val total = progress.total
                if (total == 0) {
                    drawCircle(trackLine, radius, style = Stroke(stroke))
                    return@Canvas
                }
                val slice = 360f / total
                val capDegrees = Math.toDegrees((stroke / radius).toDouble()).toFloat()
                val gap = if (total == 1) 0f else minOf(capDegrees + 8f, slice * .5f) * (1f - closed.value)
                val sweep = slice - gap
                val cap = if (gap > 0f) StrokeCap.Round else StrokeCap.Butt
                // Round caps grow each arc by half the stroke at either end; inset the drawn sweep.
                val capInset = if (cap == StrokeCap.Round) capDegrees * (1f - closed.value) else 0f
                for (i in 0 until total) {
                    val start = -90f + i * slice + gap / 2f + capInset / 2f
                    val length = (sweep - capInset).coerceAtLeast(.1f)
                    drawArc(pending, start, length, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
                    val f = (fill.value - i).coerceIn(0f, 1f)
                    if (f > 0f) drawArc(complete, start, length * f, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
                }
            }
            Box(
                Modifier.size(NavigatorPhoto).clip(CircleShape).alpha(if (progress.done && !current) .55f else 1f)
                    .clearAndSetSemantics { },
            ) {
                if (exercise != null) ExerciseImage(exercise, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(OF.elevated))
            }
            // The finished badge settles in from slightly smaller, never from nothing.
            val badge = closed.value
            if (badge > 0f) Box(
                Modifier.align(Alignment.Center).offset(14.dp, 14.dp).size(17.dp)
                    .graphicsLayer { alpha = badge; scaleX = .8f + .2f * badge; scaleY = .8f + .2f * badge }
                    .clip(CircleShape).background(complete).border(2.dp, background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(11.dp), tint = OF.onComplete)
            }
        }
    }
}

/**
 * Shown under a finished exercise: names and pictures the exercise that comes next, so moving on
 * is one tap at the thumb instead of a hunt along the strip. Navigation goes through [onClick].
 */
@Composable
fun NextExerciseAction(
    next: SessionExercise,
    exercise: Exercise?,
    fresh: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = exercise?.title ?: "Exercise"
    val press = remember { MutableInteractionSource() }
    val progress = next.workoutExerciseProgress()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp).arrival(fresh).pressFeedback(press)
            .clearAndSetSemantics {
                contentDescription = "Next exercise, $title" +
                    if (progress.total > 0) ", ${progress.complete} of ${progress.total} working sets complete" else ""
                role = Role.Button
                onClick(action = { onClick(); true })
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        interactionSource = press,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape)) {
                if (exercise != null) ExerciseImage(exercise, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Next exercise", style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = .7f))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp))
        }
    }
}
