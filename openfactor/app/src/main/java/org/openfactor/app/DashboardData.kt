package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

enum class DashboardSection(val label: String) {
    INSIGHTS("Insights & Analytics"),
    HABITS("Habits"),
    BODY("Body Metrics"),
    MUSCLES("Muscle Groups"),
    EXERCISES("Exercises"),
    GENERAL("General"),
}

/** Presentation choices only; this does not store derived analytics or change saved records. */
data class DashboardPreferences(
    val focuses: List<String> = listOf("weekly"),
    val sections: List<DashboardSection> = DashboardSection.entries,
    val insights: List<String> = listOf("workouts", "trend"),
    val habits: List<String> = listOf("weighIn", "workouts"),
    val bodyMetrics: List<String> = listOf("weight", "visualBodyFat"),
    val muscles: List<String> = emptyList(),
    val exerciseIds: List<String> = emptyList(),
    val showSteps: Boolean = true,
) {
    fun json(): JSONObject =
        JSONObject()
            .put("focuses", JSONArray(focuses))
            .put("sections", JSONArray(sections.map { it.name }))
            .put("insights", JSONArray(insights))
            .put("habits", JSONArray(habits))
            .put("bodyMetrics", JSONArray(bodyMetrics))
            .put("muscles", JSONArray(muscles))
            .put("exerciseIds", JSONArray(exerciseIds))
            .put("showSteps", showSteps)

    companion object {
        fun decode(j: JSONObject?): DashboardPreferences {
            if (j == null) return DashboardPreferences()
            val defaults = DashboardPreferences()
            fun strings(key: String, fallback: List<String>): List<String> =
                j.optJSONArray(key)?.let { a ->
                    (0 until a.length())
                        .map { a.getString(it) }
                        .filter { it.isNotBlank() }
                        .distinct()
                } ?: fallback
            val sections =
                strings("sections", defaults.sections.map { it.name }).mapNotNull { name ->
                    DashboardSection.entries.find { it.name == name }
                }
            return DashboardPreferences(
                focuses =
                    strings("focuses", defaults.focuses).filter {
                        it in listOf("weekly", "records")
                    },
                sections = sections + DashboardSection.entries.filter { it !in sections },
                insights =
                    strings("insights", defaults.insights).filter {
                        it in listOf("workouts", "trend")
                    },
                habits =
                    strings("habits", defaults.habits).filter {
                        it in listOf("weighIn", "workouts")
                    },
                bodyMetrics =
                    strings("bodyMetrics", defaults.bodyMetrics).filter {
                        it in listOf("weight", "visualBodyFat") ||
                            BodyMeasurement.entries.any { m -> m.key == it }
                    },
                muscles =
                    strings("muscles", defaults.muscles).filter {
                        it in upperMuscleGroups + lowerMuscleGroups
                    },
                exerciseIds = strings("exerciseIds", defaults.exerciseIds),
                showSteps = j.optBoolean("showSteps", true),
            )
        }
    }
}

fun <T> List<T>.moveDashboardItem(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

fun <T> List<T>.toggleDashboardItem(item: T, selected: Boolean): List<T> =
    if (selected) (this + item).distinct() else filterNot { it == item }
