package org.openfactor.app

import org.json.JSONObject

/** Local continuation of the observed three-stage setup; source-only integrations stay optional. */
data class OnboardingState(
    val step: Int = 0,
    val completed: Boolean = false,
    val deferred: Boolean = false,
    val name: String = "",
    val pounds: Boolean = false,
    val heightCm: String = "",
    val weightKg: String = "",
    val preset: String = "current",
    val gymName: String = "Everything Gym",
    val gymId: String? = null,
    val gender: ProfileGender = ProfileGender.UNSPECIFIED,
    val profileDraft: ProfileSetupDraft? = null,
) {
    fun validBasics() =
        name.isNotBlank() &&
            (heightCm.isBlank() || (heightCm.toDoubleOrNull() ?: 0.0) in 30.0..300.0) &&
            (weightKg.isBlank() || (weightKg.toDoubleOrNull() ?: 0.0) in 1.0..1000.0)

    fun json() =
        JSONObject()
            .put("step", step)
            .put("completed", completed)
            .put("deferred", deferred)
            .put("name", name)
            .put("pounds", pounds)
            .put("heightCm", heightCm)
            .put("weightKg", weightKg)
            .put("preset", preset)
            .put("gymName", gymName)
            .put("gymId", gymId)
            .put("gender", gender.name)
            .put("profileDraft", profileDraft?.json())

    companion object {
        // Old backups/installations are already usable; setup is available through Settings.
        fun decode(j: JSONObject?) =
            if (j == null) OnboardingState(completed = true)
            else
                OnboardingState(
                    j.optInt("step").coerceIn(0, 7),
                    j.optBoolean("completed"),
                    j.optBoolean("deferred"),
                    j.optString("name"),
                    j.optBoolean("pounds"),
                    j.optString("heightCm"),
                    j.optString("weightKg"),
                    j.optString("preset", "current"),
                    j.optString("gymName"),
                    j.optString("gymId").takeIf { it.isNotBlank() && it != "null" },
                    gender = ProfileGender.entries.find { it.name == j.optString("gender") } ?: ProfileGender.UNSPECIFIED,
                    profileDraft = j.optJSONObject("profileDraft")?.let(ProfileSetupDraft::fromJson),
                )
    }
}
