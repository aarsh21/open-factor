package org.openfactor.app

import org.json.JSONObject

fun resolveEquipmentRequirement(index: JSONObject, id: String): Set<String> {
    val entry = index.optJSONObject(id) ?: return setOf(id)
    if (entry.optString("type") == "equipment") return setOf(id)
    val members = entry.optJSONArray("equipment") ?: return setOf(id)
    return (0 until members.length()).map { members.getString(it) }.toSet()
}

fun equipmentRequirementsMet(
    resistance: List<Set<String>>,
    support: List<Set<String>>,
    available: Set<String>,
): Boolean =
    (resistance.isEmpty() || resistance.any { available.containsAll(it) }) &&
        (support.isEmpty() || support.any { available.containsAll(it) })
