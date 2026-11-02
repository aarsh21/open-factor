package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

data class GymProfile(
    val id: String = newId(),
    val name: String,
    val icon: String = "gym",
    val equipmentIds: Set<String> = emptySet(),
    val weightsKg: Map<String, List<Double>> = emptyMap(),
    val weightSelectionsKg: Map<String, List<EquipmentWeight>> = emptyMap(),
    val weightSelectionsLb: Map<String, List<EquipmentWeight>> = emptyMap(),
) {
    fun weights(item: EquipmentItem, pounds: Boolean): List<EquipmentWeight> =
        (if (pounds) weightSelectionsLb else weightSelectionsKg)[item.id]
            ?: weightsKg[item.id]?.distinct()?.map {
                EquipmentWeight(if (pounds) it * 2.2046226218 else it)
            }
            ?: (if (pounds) item.selectionLb else item.selectionKg).map { EquipmentWeight(it) }

    fun selectCategory(items: List<EquipmentItem>, selected: Boolean): GymProfile {
        val ids = items.map { it.id }.toSet()
        return copy(equipmentIds = if (selected) equipmentIds + ids else equipmentIds - ids)
    }
}

/** Quantity is inventory metadata; no source-backed loading rule has been recovered. */
data class EquipmentWeight(val value: Double, val enabled: Boolean = true, val quantity: Int? = null) {
    init {
        require(value.isFinite() && value >= 0.0) { "Invalid equipment weight" }
        require(quantity == null || quantity > 0) { "Invalid equipment quantity" }
    }
}

data class EquipmentItem(
    val id: String,
    val name: String,
    val category: String,
    val selectionKg: List<Double> = emptyList(),
    val selectionLb: List<Double> = emptyList(),
    val presets: Set<String> = emptySet(),
)

object GymCodec {
    fun encode(g: GymProfile) =
        JSONObject()
            .put("id", g.id)
            .put("name", g.name)
            .put("icon", g.icon)
            .put("equipmentIds", JSONArray(g.equipmentIds.toList()))
            .put(
                "weightsKg",
                JSONObject().also { j -> g.weightsKg.forEach { (k, v) -> j.put(k, JSONArray(v)) } },
            )
            .put("weightSelectionsKg", encodeSelections(g.weightSelectionsKg))
            .put("weightSelectionsLb", encodeSelections(g.weightSelectionsLb))

    private fun encodeSelections(selections: Map<String, List<EquipmentWeight>>) =
        JSONObject().also { j -> selections.forEach { (id, rows) -> j.put(id, encodeWeights(rows)) } }

    fun encodeWeights(rows: List<EquipmentWeight>): JSONArray = JSONArray(rows.map {
        JSONObject().put("value", it.value).put("enabled", it.enabled)
            .put("quantity", it.quantity ?: JSONObject.NULL)
    })

    fun decodeWeights(a: JSONArray): List<EquipmentWeight> = (0 until a.length()).map { i ->
        val row = a.getJSONObject(i)
        val quantity = if (row.isNull("quantity")) null else {
            val number = row.getDouble("quantity")
            require(number.isFinite() && number >= 1 && number <= Int.MAX_VALUE && number % 1 == 0.0) {
                "Invalid equipment quantity"
            }
            number.toInt()
        }
        EquipmentWeight(row.getDouble("value"), row.getBoolean("enabled"), quantity)
    }.also { rows -> require(rows.map { it.value }.distinct().size == rows.size) { "Duplicate equipment weights" } }

    private fun decodeSelections(j: JSONObject?) = j?.keys()?.asSequence()?.associateWith {
        decodeWeights(j.getJSONArray(it))
    }.orEmpty()

    fun decode(j: JSONObject): GymProfile {
        val ids = j.optJSONArray("equipmentIds") ?: JSONArray()
        val weights = j.optJSONObject("weightsKg") ?: JSONObject()
        return GymProfile(
            j.getString("id"),
            j.getString("name"),
            j.optString("icon", "gym"),
            (0 until ids.length()).map { ids.getString(it) }.toSet(),
            weights.keys().asSequence().associateWith { k ->
                val a = weights.getJSONArray(k)
                (0 until a.length())
                    .map { a.getDouble(it) }
                    .also { values ->
                        require(values.all { it.isFinite() && it in 0.0..5000.0 }) {
                            "Invalid equipment weights"
                        }
                    }
            },
            decodeSelections(if (j.has("weightSelectionsKg")) j.getJSONObject("weightSelectionsKg") else null),
            decodeSelections(if (j.has("weightSelectionsLb")) j.getJSONObject("weightSelectionsLb") else null),
        )
    }
}
