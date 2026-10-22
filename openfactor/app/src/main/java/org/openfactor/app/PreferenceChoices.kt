package org.openfactor.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class AppTheme(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Source order from canonical ShortcutConfiguration, restricted to workout actions. */
enum class WorkoutShortcut(val label: String) {
    WEIGHT("Log Weight"), PHOTOS("Log Photos"), MEASUREMENTS("Log Measurements"),
    HISTORY("History"), EMPTY_WORKOUT("Empty Workout"), NEW_PROGRAM("New Program"), NEW_WORKOUT("New Workout");
}

/** Color families in the source shortcut editor; rendering uses the local palette. */
enum class ShortcutColor(val label: String, val argb: Long) {
    RED("Red", 0xFFEF9A9A), ORANGE("Orange", 0xFFFFBC80), YELLOW("Yellow", 0xFFFFDC7D),
    GREEN("Green", 0xFF91D8AD), TEAL("Teal", 0xFF80CBC4), BLUE("Blue", 0xFF9DC3FF), PURPLE("Purple", 0xFFCEB3FF);
}

data class ShortcutPreferences(
    val order: List<WorkoutShortcut> = WorkoutShortcut.entries.toList(),
    val colors: Map<WorkoutShortcut, ShortcutColor> = emptyMap(),
    val showUpNext: Boolean = true,
) {
    fun json(): JSONObject = JSONObject()
        .put("order", JSONArray(order.map { it.name }))
        .put("colors", JSONObject().also { j -> colors.forEach { (id, color) -> j.put(id.name, color.name) } })
        .put("showUpNext", showUpNext)

    companion object {
        fun decode(j: JSONObject?): ShortcutPreferences {
            if (j == null) return ShortcutPreferences()
            val order = j.optJSONArray("order")?.let { a ->
                (0 until a.length()).map { WorkoutShortcut.valueOf(a.getString(it)) }
            } ?: WorkoutShortcut.entries.toList()
            require(order.distinct().size == order.size) { "Duplicate shortcut" }
            val colors = j.optJSONObject("colors")?.let { c ->
                c.keys().asSequence().associate { key -> WorkoutShortcut.valueOf(key) to ShortcutColor.valueOf(c.getString(key)) }
            }.orEmpty()
            return ShortcutPreferences(order, colors, j.optBoolean("showUpNext", true))
        }
    }
}

fun Preferences.clockTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern(if (clock24Hour) "HH:mm" else "h:mm a", locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun Preferences.heightLabel(cm: Double): String = if (heightInFeet) {
    val inches = kotlin.math.round(cm / 2.54).toInt()
    "${inches / 12} ft ${inches % 12} in"
} else "${cm.bodyNumber()} cm"
