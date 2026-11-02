package org.openfactor.app

fun dashboardOptions(data: AppData, exercises: List<Exercise>, section: DashboardSection): List<Pair<String, String>> = when (section) {
    DashboardSection.INSIGHTS -> listOf("workouts" to "Workouts", "trend" to "Weight Trend")
    DashboardSection.HABITS -> listOf("weighIn" to "Weigh-In", "workouts" to "Workouts")
    DashboardSection.BODY -> (listOf("weight" to "Scale Weight", "visualBodyFat" to "Visual Body Fat") + BodyMeasurement.entries.map { it.key to it.label })
        .filter { data.preferences.dataVisibility.isVisible(it.first) }
    DashboardSection.MUSCLES -> (upperMuscleGroups + lowerMuscleGroups).map { it to it }
    DashboardSection.EXERCISES -> exercises.sortedBy { it.title }.map { it.id to it.title }
    DashboardSection.GENERAL -> listOf("steps" to "Steps")
}

fun DashboardPreferences.selected(section: DashboardSection): List<String> = when (section) {
    DashboardSection.INSIGHTS -> insights
    DashboardSection.HABITS -> habits
    DashboardSection.BODY -> bodyMetrics
    DashboardSection.MUSCLES -> muscles
    DashboardSection.EXERCISES -> exerciseIds
    DashboardSection.GENERAL -> if (showSteps) listOf("steps") else emptyList()
}

fun DashboardPreferences.withSelection(section: DashboardSection, keys: List<String>): DashboardPreferences = when (section) {
    DashboardSection.INSIGHTS -> copy(insights = keys)
    DashboardSection.HABITS -> copy(habits = keys)
    DashboardSection.BODY -> copy(bodyMetrics = keys)
    DashboardSection.MUSCLES -> copy(muscles = keys)
    DashboardSection.EXERCISES -> copy(exerciseIds = keys)
    DashboardSection.GENERAL -> copy(showSteps = "steps" in keys)
}
