package org.openfactor.app

import kotlin.math.floor
import kotlin.math.ceil
import java.util.Locale

/** One editable cell of the active logger: a set row and one of its metrics. */
internal data class KeypadTarget(val setId: String, val metric: SetMetric)

internal sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey {
        init { require(value in 0..9) }
    }
    data object Decimal : KeypadKey
    data object Backspace : KeypadKey
    data object Increase : KeypadKey
    data object Decrease : KeypadKey
}

/** Raw field text plus whether the next typed key replaces it (the select-all state after focus or a step). */
internal data class KeypadEdit(val text: String, val replace: Boolean)

/** Seconds and reps stay whole on the keypad; pasted or restored decimals still go through [SetMetric.parse]. */
internal val SetMetric.keypadDecimal: Boolean
    get() = isLoad || this == SetMetric.DISTANCE_LONG || this == SetMetric.DISTANCE_SHORT || this == SetMetric.DISTANCE_PER_SIDE

/** Step in the units the field displays. Gym loading rules are not modelled, so load uses the common plate step. */
internal fun SetMetric.keypadStep(pounds: Boolean): Double = when {
    isLoad -> if (pounds) 5.0 else 2.5
    isReps -> 1.0
    this == SetMetric.DURATION || this == SetMetric.DURATION_PER_SIDE -> 5.0
    this == SetMetric.DISTANCE_LONG -> 0.1
    else -> 10.0
}

internal fun keypadNumber(value: Double): String =
    String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')

private const val MaxWholeDigits = 5
private const val MaxFractionDigits = 3

/** Pure keypad editing. Rejected keys return [state] unchanged; validity is still decided by [SetMetric.parse]. */
internal fun keypadEdit(
    state: KeypadEdit,
    key: KeypadKey,
    metric: SetMetric,
    pounds: Boolean,
    distanceImperial: Boolean = false,
): KeypadEdit {
    val base = if (state.replace) "" else state.text
    return when (key) {
        is KeypadKey.Digit -> {
            val next = if (base == "0") key.value.toString() else base + key.value
            val parts = next.replace(',', '.').split('.')
            if (parts[0].length > MaxWholeDigits || (parts.size > 1 && parts[1].length > MaxFractionDigits)) state
            else KeypadEdit(next, false)
        }
        KeypadKey.Decimal -> when {
            !metric.keypadDecimal || base.contains('.') || base.contains(',') -> state
            else -> KeypadEdit(base.ifEmpty { "0" } + ".", false)
        }
        KeypadKey.Backspace -> KeypadEdit(base.dropLast(1), false)
        KeypadKey.Increase, KeypadKey.Decrease -> {
            val increase = key == KeypadKey.Increase
            val current = state.text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
            if (current == null && !increase) return state
            val step = metric.keypadStep(pounds)
            // Snap to the step grid: 220.462 lb (a stored 100 kg) steps to 225, not 225.462.
            val base = (current ?: 0.0) / step
            val raw = step * if (increase) floor(base + 1e-9) + 1 else ceil(base - 1e-9) - 1
            val bounded = when {
                metric.isLoad -> maxOf(raw, 0.0) + 0.0
                metric.isReps -> maxOf(raw, 1.0)
                else -> raw
            }
            val text = keypadNumber(bounded)
            if (text == state.text || metric.parse(text, pounds, distanceImperial) == null) state
            else KeypadEdit(text, true)
        }
    }
}

/** Rows in the order the logger draws them: warm-ups, sides and Myo components included. */
internal fun SessionExercise.keypadRows(): List<LoggedSet> = logicalSets().flatMap { it.logs }

/** Next metric in the row, then the first metric of the next unfinished row; null when nothing follows. */
internal fun nextKeypadTarget(rows: List<LoggedSet>, metrics: List<SetMetric>, current: KeypadTarget): KeypadTarget? {
    val metricIndex = metrics.indexOf(current.metric)
    val rowIndex = rows.indexOfFirst { it.id == current.setId }
    if (metricIndex < 0 || rowIndex < 0) return null
    if (metricIndex < metrics.lastIndex) return KeypadTarget(current.setId, metrics[metricIndex + 1])
    return nextUnfinishedSet(rows, metrics, current)
}

/** First field of the next unfinished row after [current]'s row: where logging a set leads. */
internal fun nextUnfinishedSet(rows: List<LoggedSet>, metrics: List<SetMetric>, current: KeypadTarget): KeypadTarget? {
    val rowIndex = rows.indexOfFirst { it.id == current.setId }
    if (rowIndex < 0 || metrics.isEmpty()) return null
    // Later rows first, then any earlier set that was skipped, so the keypad stays up until all are logged.
    return (rows.drop(rowIndex + 1) + rows.take(rowIndex)).firstOrNull { !it.completed }?.let { KeypadTarget(it.id, metrics.first()) }
}
