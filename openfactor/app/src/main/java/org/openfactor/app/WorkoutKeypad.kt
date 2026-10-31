package org.openfactor.app

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Session-scoped keypad state. Visibility is simply "a set field has focus", so every existing
 * focus.clearFocus() (completion, tab switch, Finish, leaving) also closes the keypad.
 */
@Stable
internal class WorkoutKeypad {
    private val fields = HashMap<KeypadTarget, KeypadFieldNode>()
    var target by mutableStateOf<KeypadTarget?>(null)
        private set
    /** Last focused field, kept so the exit transition still shows its keys. */
    var shown by mutableStateOf<KeypadFieldNode?>(null)
        private set
    var replace by mutableStateOf(true)
        private set
    private var rows by mutableStateOf(emptyList<LoggedSet>())
    private var metrics by mutableStateOf(emptyList<SetMetric>())

    val open get() = target != null
    val next get() = target?.let { nextKeypadTarget(rows, metrics, it) }

    fun order(groups: List<LogicalSet>, metrics: List<SetMetric>) {
        rows = groups.flatMap { it.logs }
        this.metrics = metrics
    }

    internal fun register(node: KeypadFieldNode) { fields[node.target] = node }

    internal fun unregister(node: KeypadFieldNode) {
        if (fields[node.target] === node) fields.remove(node.target)
        if (target == node.target) target = null
    }

    internal fun focused(node: KeypadFieldNode) {
        if (target != node.target) replace = true
        target = node.target
        shown = node
    }

    internal fun blurred(node: KeypadFieldNode) { if (target == node.target) target = null }

    internal fun active() = target?.let(fields::get)

    fun press(key: KeypadKey) {
        val node = active() ?: return
        val edit = keypadEdit(KeypadEdit(node.text, replace), key, node.target.metric, node.pounds, node.distanceImperial)
        replace = edit.replace
        if (edit.text != node.text) {
            node.text = edit.text
            node.onText(edit.text)
        }
    }

    /** False when nothing follows, so the caller hides the keypad instead. */
    fun advance(): Boolean = next?.let(fields::get)?.let { runCatching { it.focus.requestFocus() }.isSuccess } == true

    /** True while the keypad itself completes a set, so the row keeps focus instead of clearing it. */
    var logging = false
        private set

    /**
     * The row's own completion callback (same validation, haptic, rest timer and effort prompt), then
     * straight to the next unfinished set: the keypad stays up between sets instead of sliding away
     * and back. False when nothing is left, so the caller hides it.
     */
    fun log(): Boolean {
        val node = active()?.takeIf { !it.completed && it.canLog } ?: return true
        val following = nextUnfinishedSet(rows, metrics, node.target)
        logging = true
        try { node.onLog() } finally { logging = false }
        return following?.let(fields::get)?.let { runCatching { it.focus.requestFocus() }.isSuccess } == true
    }

    fun containsField(rootPosition: Offset) = fields.values.any { node ->
        node.coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.contains(rootPosition) == true
    }
}

internal class KeypadFieldNode(val target: KeypadTarget) {
    val focus = FocusRequester()
    val view = BringIntoViewRequester()
    var coordinates: LayoutCoordinates? = null
    var text = ""
    var onText: (String) -> Unit = {}
    var onLog: () -> Unit = {}
    var pounds = false
    var distanceImperial = false
    var unit by mutableStateOf("")
    var completed by mutableStateOf(false)
    var canLog by mutableStateOf(false)
}

/** What a set row tells its metric field when the keypad is on. */
internal class KeypadField(
    val keypad: WorkoutKeypad,
    val setId: String,
    val metric: SetMetric,
    val prefs: Preferences,
    val completed: Boolean,
    val canLog: Boolean,
    val onLog: () -> Unit,
)

/**
 * Without [field] this is exactly the previous OutlinedTextField. With it, the same field keeps its
 * semantics (description, error, SetText) but never opens the system IME; the keypad edits it.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun KeypadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = LocalTextStyle.current,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    field: KeypadField? = null,
) {
    if (field == null) {
        OutlinedTextField(
            value, onValueChange, modifier, isError = isError, supportingText = supportingText, singleLine = singleLine,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, textStyle = textStyle, shape = shape,
        )
        return
    }
    val keypad = field.keypad
    val node = remember(keypad, field.setId, field.metric) { KeypadFieldNode(KeypadTarget(field.setId, field.metric)) }
    val change by rememberUpdatedState(onValueChange)
    SideEffect {
        node.text = value
        node.onText = { change(it) }
        node.onLog = field.onLog
        node.pounds = field.prefs.pounds
        node.distanceImperial = field.prefs.distanceImperial
        node.unit = field.metric.label(field.prefs.pounds, field.prefs.distanceImperial)
        node.completed = field.completed
        node.canLog = field.canLog
    }
    DisposableEffect(keypad, node) {
        keypad.register(node)
        onDispose { keypad.unregister(node) }
    }
    // Select-all shows that the next key replaces the value; a step keeps it selected.
    val selectAll = keypad.target == node.target && keypad.replace
    InterceptPlatformTextInput({ _, _ -> awaitCancellation() }) {
        OutlinedTextField(
            TextFieldValue(value, if (selectAll) TextRange(0, value.length) else TextRange(value.length)),
            { next -> if (next.text != value) onValueChange(next.text) },
            modifier.focusRequester(node.focus)
                .bringIntoViewRequester(node.view)
                .onFocusChanged { if (it.hasFocus) keypad.focused(node) else keypad.blurred(node) }
                .onPlaced { node.coordinates = it },
            isError = isError, supportingText = supportingText, singleLine = singleLine,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, textStyle = textStyle, shape = shape,
        )
    }
}

/** A tap that no child handled and that misses every set field hides the keypad; scrolling does not. */
@Composable
internal fun Modifier.keypadOutsideTap(keypad: WorkoutKeypad?, onOutside: () -> Unit): Modifier {
    if (keypad == null) return this
    val latest by rememberUpdatedState(onOutside)
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onPlaced { holder[0] = it }.pointerInput(keypad) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(PointerEventPass.Final) ?: return@awaitEachGesture
            if (!keypad.open) return@awaitEachGesture
            val root = holder[0]?.takeIf { it.isAttached }?.localToRoot(up.position) ?: return@awaitEachGesture
            if (!keypad.containsField(root)) latest()
        }
    }
}

/** Sits below the session list, so the list's viewport (not an overlay) shrinks by the keypad height. */
@Composable
internal fun WorkoutKeypadPanel(keypad: WorkoutKeypad, onHide: () -> Unit) {
    val open = keypad.open
    BackHandler(open, onHide)
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(keypad.target) {
        val node = keypad.active() ?: return@LaunchedEffect
        // One frame lets the list adopt its shorter viewport before revealing the focused field.
        withFrameNanos { }
        val size = node.coordinates?.takeIf { it.isAttached }?.size ?: return@LaunchedEffect
        node.view.bringIntoView(Rect(0f, -margin, size.width.toFloat(), size.height + margin))
    }
    val motion = LocalAppMotionEnabled.current
    AnimatedVisibility(
        open,
        enter = if (motion) slideInVertically(tween(AppMotion.Step, easing = AppMotion.EaseOut)) { it } +
            fadeIn(tween(AppMotion.Step, easing = AppMotion.EaseOut)) else EnterTransition.None,
        exit = if (motion) slideOutVertically(tween(AppMotion.Quick, easing = AppMotion.EaseOut)) { it } +
            fadeOut(tween(AppMotion.Quick, easing = AppMotion.EaseOut)) else ExitTransition.None,
        label = "workout keypad",
    ) {
        keypad.shown?.let { KeypadKeys(keypad, it, onHide) }
    }
}

@Composable
private fun KeypadKeys(keypad: WorkoutKeypad, node: KeypadFieldNode, onHide: () -> Unit) {
    val view = LocalView.current
    val tick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    val metric = node.target.metric
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // At most ~42% of the window (header and gaps included), so landscape and large text keep the list visible.
    val keyHeight = ((windowHeight * .42f - 70.dp) / 4).coerceIn(36.dp, 54.dp)
    val step = keypadNumber(metric.keypadStep(node.pounds))
    val unit = node.unit.lowercase()
    val hasNext = keypad.next != null
    Surface(
        Modifier.fillMaxWidth().testTag("Workout keypad"),
        color = OF.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(node.unit, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = OF.muted)
                    val hide = remember { MutableInteractionSource() }
                    IconButton({ tick(); onHide() }, Modifier.focusProperties { canFocus = false }.pressFeedback(hide), interactionSource = hide) {
                        Icon(Icons.Rounded.KeyboardHide, "Hide keypad")
                    }
                }
                val digit: @Composable RowScope.(Int) -> Unit = { d ->
                    KeypadButton("$d", keyHeight, onClick = { tick(); keypad.press(KeypadKey.Digit(d)) }) {
                        Text("$d", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
                KeyRow {
                    digit(1); digit(2); digit(3)
                    KeypadButton("Increase by $step $unit", keyHeight, tone = KeyTone.Function,
                        repeat = { tick(); keypad.press(KeypadKey.Increase) }) { KeyLabel("+$step") }
                }
                KeyRow {
                    digit(4); digit(5); digit(6)
                    KeypadButton("Decrease by $step $unit", keyHeight, tone = KeyTone.Function,
                        repeat = { tick(); keypad.press(KeypadKey.Decrease) }) { KeyLabel("−$step") }
                }
                KeyRow {
                    digit(7); digit(8); digit(9)
                    KeypadButton(if (hasNext) "Next field" else "Done", keyHeight, tone = KeyTone.Function,
                        onClick = { tick(); if (!keypad.advance()) onHide() }) { KeyLabel(if (hasNext) "Next" else "Done") }
                }
                KeyRow {
                    KeypadButton("Decimal point", keyHeight, enabled = metric.keypadDecimal,
                        onClick = { tick(); keypad.press(KeypadKey.Decimal) }) {
                        Text(".", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                    digit(0)
                    KeypadButton("Delete", keyHeight, tone = KeyTone.Function,
                        onClick = { tick(); keypad.press(KeypadKey.Backspace) }) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, null, Modifier.size(22.dp))
                    }
                    val logged = node.completed
                    KeypadButton(if (logged) "Set logged" else "Log set", keyHeight, tone = KeyTone.Primary,
                        enabled = !logged && node.canLog, onClick = { if (!keypad.log()) onHide() }) {
                        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Large text: the label matters more than the icon on a quarter-width key.
                            if (LocalDensity.current.fontScale <= 1.15f) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            KeyLabel(if (logged) "Logged" else "Log set")
                        }
                    }
                }
            }
        }
    }
}

private enum class KeyTone { Digit, Function, Primary }

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)

@Composable
private fun KeyLabel(text: String) = BasicText(
    text, style = LocalTextStyle.current.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current),
    maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp),
)

/**
 * Keys never take input focus (the field keeps it) and only acknowledge presses with the shared
 * press feedback; values change instantly. [repeat] fires on press and repeats while held.
 */
@Composable
private fun RowScope.KeypadButton(
    description: String,
    height: Dp,
    tone: KeyTone = KeyTone.Digit,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    repeat: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    val container = when {
        tone == KeyTone.Primary && enabled -> MaterialTheme.colorScheme.primary
        tone == KeyTone.Function -> OF.background
        else -> OF.elevated
    }
    val foreground = when {
        !enabled -> OF.muted.copy(alpha = .55f)
        tone == KeyTone.Primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val scope = rememberCoroutineScope()
    val repeatAction by rememberUpdatedState(repeat)
    // Keys are pressed 100+ times a session: an instant tint, not an expanding ripple that stacks.
    val pressed by source.collectIsPressedAsState()
    val input = if (repeat != null) Modifier
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val press = PressInteraction.Press(down.position)
                source.tryEmit(press)
                repeatAction?.invoke()
                // Gentle acceleration: 400ms hold, then 160ms easing down to 60ms between steps.
                val job = scope.launch {
                    delay(400)
                    var interval = 160L
                    while (isActive) {
                        repeatAction?.invoke()
                        delay(interval)
                        interval = (interval * 85 / 100).coerceAtLeast(60)
                    }
                }
                val up = waitForUpOrCancellation()
                job.cancel()
                up?.consume()
                source.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
            }
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (enabled) onClick { repeatAction?.invoke(); true }
        }
    else Modifier.clickable(source, null, enabled = enabled, role = Role.Button) { onClick?.invoke() }
    Box(
        Modifier.weight(1f).height(height)
            .pressFeedback(source, enabled)
            .clip(shape)
            .background(if (tone == KeyTone.Primary && !enabled) OF.elevated.copy(alpha = .6f)
                else if (pressed && enabled) lerp(container, foreground, .16f) else container)
            .focusProperties { canFocus = false }
            .then(input)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { content() }
    }
}
