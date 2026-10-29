package org.openfactor.app

import java.time.LocalDate
import kotlin.math.floor
import org.json.JSONObject

enum class ProfileGender(val label: String) {
    MAN("Man"), WOMAN("Woman"), NON_BINARY("Non-binary"), UNSPECIFIED("Prefer not to say");
}

/** Raw input stays durable; canonical measurements only reach saved records after validation. */
data class ProfileSetupDraft(
    val stage: Int = 0,
    val name: String = "",
    val gender: ProfileGender = ProfileGender.UNSPECIFIED,
    val pounds: Boolean = false,
    val heightInFeet: Boolean = false,
    val weightText: String = "",
    val heightText: String = "",
    val heightInchesText: String = "",
    val weeklyGoal: Int = 3,
    val rateEffort: Boolean = true,
    val autoRest: Boolean = true,
    val restSeconds: Int = 120,
    val weightEdited: Boolean = false,
    val weightSourceDate: String? = null,
    val weightSourceId: String? = null,
    val localWeightId: String = newId(),
) {
    fun nameError(): String? = when {
        name.isBlank() -> "Enter a name, or skip setup for now."
        name.trim().length > 60 -> "Keep your name to 60 characters."
        else -> null
    }
    fun weightKg(): Double? = weightText.bodyDecimal()?.let { if (pounds) it / 2.2046226218 else it }
        ?.takeIf { it.isFinite() && it in 1.0..1000.0 }
    fun heightCm(): Double? {
        if (!heightInFeet) return heightText.bodyDecimal()?.takeIf { it in 30.0..300.0 }
        val feet = heightText.bodyDecimal()?.takeIf { it >= 0 && it % 1.0 == 0.0 } ?: return null
        val inches = if (heightInchesText.isBlank()) 0.0 else heightInchesText.bodyDecimal() ?: return null
        if (inches !in 0.0..<12.0) return null
        return ((feet * 12 + inches) * 2.54).takeIf { it.isFinite() && it in 30.0..300.0 }
    }
    fun bodyError(): String? = when {
        weightText.isNotBlank() && weightKg() == null -> "Enter a valid weight in ${if (pounds) "lb" else "kg"}."
        (heightText.isNotBlank() || heightInchesText.isNotBlank()) && heightCm() == null ->
            if (heightInFeet) "Use whole feet and inches from 0 to less than 12." else "Enter a height from 30 to 300 cm."
        else -> null
    }
    fun validationError(): String? = nameError() ?: bodyError() ?: when {
        weeklyGoal !in 1..7 -> "Choose 1–7 workouts a week."
        restSeconds !in 0..1800 -> "Choose a rest time from 0 to 1800 seconds."
        localWeightId.isBlank() -> "Reopen setup to refresh this draft."
        else -> null
    }
    fun withPounds(value: Boolean): ProfileSetupDraft {
        if (value == pounds) return this
        val kg = weightKg()
        // Keep invalid input visibly invalid rather than interpreting it in a different unit.
        if (weightText.isNotBlank() && kg == null) return this
        return copy(pounds = value, weightText = kg?.bodyWeightNumber(value, 3).orEmpty())
    }
    fun withHeightInFeet(value: Boolean): ProfileSetupDraft {
        if (value == heightInFeet) return this
        val cm = heightCm()
        if ((heightText.isNotBlank() || heightInchesText.isNotBlank()) && cm == null) return this
        if (cm == null) return copy(heightInFeet = value, heightText = "", heightInchesText = "")
        if (!value) return copy(heightInFeet = false, heightText = cm.bodyNumber(3), heightInchesText = "")
        val whole = floor(cm / 2.54 / 12).toInt()
        val rounded = (cm / 2.54 - whole * 12).bodyNumber(3).toDouble()
        return copy(heightInFeet = true, heightText = (whole + if (rounded >= 12) 1 else 0).toString(),
            heightInchesText = if (rounded >= 12) "0" else rounded.bodyNumber(3))
    }
    /** Provider refresh may fill an untouched baseline, but never replace what the user typed. */
    fun refreshWeight(records: List<ScaleWeightRecord>, today: LocalDate = LocalDate.now()): ProfileSetupDraft {
        if (weightEdited) return this
        val latest = latestProfileWeight(records, today) ?: return this
        return copy(weightText = latest.weightKg.bodyWeightNumber(pounds, 3),
            weightSourceId = latest.id, weightSourceDate = latest.date.toString())
    }
    fun json(): JSONObject = JSONObject()
        .put("stage", stage).put("name", name).put("gender", gender.name)
        .put("pounds", pounds).put("heightInFeet", heightInFeet)
        .put("weightText", weightText).put("heightText", heightText).put("heightInchesText", heightInchesText)
        .put("weeklyGoal", weeklyGoal).put("rateEffort", rateEffort).put("autoRest", autoRest).put("restSeconds", restSeconds)
        .put("weightEdited", weightEdited).put("weightSourceDate", weightSourceDate).put("weightSourceId", weightSourceId)
        .put("localWeightId", localWeightId)

    companion object {
        fun fromJson(j: JSONObject): ProfileSetupDraft = ProfileSetupDraft(
            stage = j.optInt("stage").coerceIn(0, 2), name = j.optString("name"),
            gender = ProfileGender.entries.find { it.name == j.optString("gender") } ?: ProfileGender.UNSPECIFIED,
            pounds = j.optBoolean("pounds"), heightInFeet = j.optBoolean("heightInFeet"),
            weightText = j.optString("weightText"), heightText = j.optString("heightText"), heightInchesText = j.optString("heightInchesText"),
            weeklyGoal = j.optInt("weeklyGoal", 3), rateEffort = j.optBoolean("rateEffort", true),
            autoRest = j.optBoolean("autoRest", true), restSeconds = j.optInt("restSeconds", 120),
            weightEdited = j.optBoolean("weightEdited"),
            weightSourceDate = j.optString("weightSourceDate").takeIf { it.isNotBlank() && it != "null" },
            weightSourceId = j.optString("weightSourceId").takeIf { it.isNotBlank() && it != "null" },
            localWeightId = j.optString("localWeightId").takeIf { it.isNotBlank() } ?: newId(),
        )
    }
}

private fun latestProfileWeight(records: List<ScaleWeightRecord>, today: LocalDate): ScaleWeightRecord? = records
    .filter { it.date <= today && it.weightKg.isFinite() && it.weightKg in 1.0..1000.0 }
    .maxWithOrNull(compareBy<ScaleWeightRecord> { it.date }.thenBy { it.healthConnectOrigin == null })

fun AppData.profileSetupDraft(today: LocalDate = LocalDate.now()): ProfileSetupDraft {
    val p = preferences
    val old = onboarding
    val legacyWeight = old.weightKg.bodyDecimal()?.takeIf { it in 1.0..1000.0 }
    val height = old.heightCm.bodyDecimal()?.takeIf { it in 30.0..300.0 }
    val current = latestProfileWeight(scaleWeights, today)
    val manualToday = scaleWeights.firstOrNull { it.date == today && it.healthConnectOrigin == null }
    return ProfileSetupDraft(
        name = if (!old.completed && !old.deferred && old.name.isNotBlank()) old.name
            else p.displayName.takeUnless { it == "Athlete" && !old.completed }.orEmpty(),
        gender = old.gender, pounds = p.pounds, heightInFeet = false,
        weightText = current?.weightKg?.bodyWeightNumber(p.pounds, 3) ?: legacyWeight?.bodyWeightNumber(p.pounds, 3).orEmpty(),
        heightText = height?.bodyNumber(3).orEmpty(), weeklyGoal = p.weeklyWorkoutGoal ?: 3,
        rateEffort = p.rateEffortAfterExercise, autoRest = p.autoRest, restSeconds = p.restSeconds,
        weightEdited = current == null && legacyWeight != null && !old.completed,
        weightSourceDate = current?.date?.toString(), weightSourceId = current?.id,
        localWeightId = manualToday?.id ?: newId(),
    ).withHeightInFeet(p.heightInFeet)
}

fun AppData.withProfileSetup(draft: ProfileSetupDraft, today: LocalDate = LocalDate.now()): AppData {
    require(draft.validationError() == null) { draft.validationError().orEmpty() }
    var updated = copy(
        preferences = preferences.copy(displayName = draft.name.trim(), pounds = draft.pounds,
            heightInFeet = draft.heightInFeet, weeklyWorkoutGoal = draft.weeklyGoal,
            rateEffortAfterExercise = draft.rateEffort, autoRest = draft.autoRest, restSeconds = draft.restSeconds),
        onboarding = onboarding.copy(completed = true, deferred = false, step = 0,
            name = draft.name.trim(), pounds = draft.pounds, gender = draft.gender,
            heightCm = draft.heightCm()?.bodyNumber(3).orEmpty(), weightKg = draft.weightKg()?.bodyNumber(3).orEmpty(), profileDraft = null),
    )
    // An imported or unchanged historical baseline never becomes a fabricated weigh-in today.
    if (draft.weightEdited && draft.weightKg() != null) {
        val previous = scaleWeights.find { it.id == draft.localWeightId && it.healthConnectOrigin == null }
        updated = updated.withScaleWeight(ScaleWeightRecord(id = draft.localWeightId, date = today,
            weightKg = draft.weightKg()!!, bodyFatPercent = previous?.bodyFatPercent))
    }
    return updated
}
