package org.openfactor.app

import java.io.Serializable

enum class CustomExerciseType(val label: String, val catalogType: String, val region: String) {
    COMPOUND_UPPER("Compound Upper", "Multi-joint (compound)", "Upper body"),
    COMPOUND_LOWER("Compound Lower", "Multi-joint (compound)", "Lower body"),
    COMPOUND_FULL("Compound Full Body", "Multi-joint (compound)", "Full body"),
    ISOLATION_UPPER("Isolation Upper", "Single joint (isolation)", "Upper body"),
    ISOLATION_LOWER("Isolation Lower", "Single joint (isolation)", "Lower body"),
    CORE("Core", "Core", "Core"),
}

/** Incomplete wizard state is separate from saved exercises and training data. */
data class CustomExerciseDraft(
    val name: String = "",
    val metric1: SetMetric? = null,
    val metric2: SetMetric? = null,
    val type: CustomExerciseType? = CustomExerciseType.COMPOUND_UPPER,
    val laterality: String = "Bilateral",
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val resistance: List<Set<String>> = listOf(emptySet()),
    val support: List<Set<String>> = emptyList(),
    val rom: Int = 0,
    val stability: Int = 0,
    val bodyPercent: String = "",
    val primaryJoint: String = "",
    val secondaryJoint: String = "",
    val alternateNames: String = "",
    val instructions: String = "",
) : Serializable {
    val metrics get() = listOfNotNull(metric1, metric2)
    val validBasics get() = name.isNotBlank() && type != null && metrics.isNotEmpty() &&
        metrics.distinct().size == metrics.size &&
        metrics.count { it.isLoad } <= 1 && metrics.count { it.isReps } <= 1 &&
        metrics.count { it == SetMetric.DURATION || it == SetMetric.DURATION_PER_SIDE } <= 1 &&
        metrics.count { it in listOf(SetMetric.DISTANCE_LONG, SetMetric.DISTANCE_SHORT, SetMetric.DISTANCE_PER_SIDE) } <= 1
    val contribution get() = bodyPercent.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
    val validDetails get() = contribution != null && resistance.isNotEmpty() && rom in 0..5 && stability in 0..5

    fun cycleMuscle(name: String): CustomExerciseDraft = when (name) {
        in primary -> copy(primary = primary - name, secondary = secondary + name)
        in secondary -> copy(secondary = secondary - name)
        else -> copy(primary = primary + name)
    }

    fun toExercise(equipment: List<EquipmentItem>, id: String = newId()): Exercise {
        require(validBasics && validDetails) { "Complete the exercise name, metrics and body weight contribution" }
        require(resistance.isNotEmpty() && primary.intersect(secondary.toSet()).isEmpty())
        val selectedType = requireNotNull(type)
        val names = equipment.associate { it.id to it.name }
        val resistanceNames = resistance.map { group ->
            group.map { names[it] ?: it }.sorted().joinToString(" + ").ifEmpty { "Bodyweight Only" }
        }
        val categories = equipment.filter { item -> resistance.any { item.id in it } }.map {
            when (it.category) {
                "Free Weights", "Fixed Weight Bars", "Loadable Bars", "Loadable Accessories" -> "Free Weights"
                "Cable Machines", "Pin Loaded Machines", "Plate Loaded Machines" -> "Machines"
                "Cardio" -> "Cardio"
                "Personal", "Body Weights", "Bands & Ropes" -> "Bodyweight & Personal"
                else -> "Other"
            }
        }.distinct()
        return Exercise(
            id, name.trim(), primary.firstOrNull() ?: "Other",
            resistanceNames.first(), secondary = secondary, instructions = instructions.trim(), custom = true,
            muscles = primary, aliases = alternateNames.split(',', '\n').map(String::trim).filter(String::isNotEmpty).distinct(),
            equipmentOptions = resistanceNames,
            properties = buildMap {
                put("Metrics", metrics.joinToString(", ") { it.catalogName })
                put("Type", selectedType.catalogType)
                put("Region", selectedType.region)
                put("Laterality", laterality)
                put("Range of Motion", rom.toString())
                put("Stability", stability.toString())
                if (primaryJoint.isNotBlank()) put("Primary Joint Action", primaryJoint)
                if (secondaryJoint.isNotBlank()) put("Secondary Joint Action", secondaryJoint)
            },
            bodyweight = requireNotNull(contribution) / 100.0,
            browseCategories = categories.ifEmpty { listOf("Bodyweight & Personal") },
            resistanceEquipment = resistance, supportEquipment = support,
        )
    }
}

val Exercise.classifiedType: CustomExerciseType?
    get() = CustomExerciseType.entries.find {
        it.catalogType == properties["Type"] && (it == CustomExerciseType.CORE || it.region == properties["Region"])
    }

fun Exercise.duplicateDraft(
    resistance: List<Set<String>> = resistanceEquipment,
    support: List<Set<String>> = supportEquipment,
    instructionText: String = instructions,
): CustomExerciseDraft {
    val metrics = loggingMetrics()
    return CustomExerciseDraft(
        name = "$title (Copy)", metric1 = metrics.getOrNull(0), metric2 = metrics.getOrNull(1),
        type = classifiedType,
        laterality = properties["Laterality"] ?: "Bilateral",
        primary = muscles, secondary = secondary,
        resistance = resistance, support = support,
        rom = properties["Range of Motion"]?.toIntOrNull() ?: 0,
        stability = properties["Stability"]?.toIntOrNull() ?: 0,
        bodyPercent = java.math.BigDecimal.valueOf(bodyweight).movePointRight(2).stripTrailingZeros().toPlainString(),
        primaryJoint = properties["Primary Joint Action"].orEmpty(),
        secondaryJoint = properties["Secondary Joint Action"].orEmpty(),
        alternateNames = aliases.joinToString(", "), instructions = instructionText,
    )
}
