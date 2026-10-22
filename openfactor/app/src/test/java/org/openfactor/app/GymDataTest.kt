package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GymDataTest {
    private val item = EquipmentItem("bar", "Bar", "Bars", listOf(20.0, 15.0, 7.0), listOf(45.0, 33.0, 15.0))

    @Test
    fun unitListsKeepTheirOwnValuesEnabledStateQuantityAndExplicitEmpty() {
        val gym = GymProfile(
            name = "Asymmetric", icon = "cottage", equipmentIds = setOf("bar", "unknown"),
            weightsKg = mapOf("old" to listOf(2.5)),
            weightSelectionsKg = mapOf("bar" to listOf(EquipmentWeight(7.25, false, 1), EquipmentWeight(19.5, true, 3))),
            weightSelectionsLb = mapOf("bar" to listOf(EquipmentWeight(33.0, true, null)), "unknown" to emptyList()),
        )
        val restored = DataCodec.decode(DataCodec.encode(AppData(gyms = listOf(gym)))).gyms.single()
        assertEquals(gym, restored)
        assertEquals(listOf(7.25, 19.5), restored.weights(item, false).map { it.value })
        assertEquals(listOf(33.0), restored.weights(item, true).map { it.value })
        assertFalse(restored.weights(item, false).first().enabled)
        assertEquals(1, restored.weights(item, false).first().quantity)
        val cleared = restored.copy(weightSelectionsKg = mapOf("bar" to emptyList()))
        assertTrue(GymCodec.decode(GymCodec.encode(cleared)).weights(item, false).isEmpty())
        assertEquals(listOf(33.0), cleared.weights(item, true).map { it.value })
        assertEquals(listOf(20.0, 15.0, 7.0), GymProfile(name = "New").weights(item, false).map { it.value })
        assertEquals(listOf(45.0, 33.0, 15.0), GymProfile(name = "New").weights(item, true).map { it.value })
    }

    @Test
    fun legacyOverridesRemainReadableIncludingLargeValuesAndUnknownIds() {
        val legacy = JSONObject("""{"id":"old","name":"Old gym","icon":"unrecognized","equipmentIds":["bar","missing"],"weightsKg":{"bar":[0,1.25,5000],"missing":[]}}""")
        val gym = GymCodec.decode(legacy)
        assertEquals(setOf("bar", "missing"), gym.equipmentIds)
        assertEquals("unrecognized", gym.icon)
        assertEquals(listOf(0.0, 1.25, 5000.0), gym.weights(item, false).map { it.value })
        assertEquals(2.75577827725, gym.weights(item, true)[1].value, 1e-10)
        assertEquals(11023.113109, gym.weights(item, true)[2].value, 1e-8)
        assertTrue(gym.weights(item.copy(id = "missing"), true).isEmpty())
        assertEquals(gym, GymCodec.decode(GymCodec.encode(gym)))
    }

    @Test
    fun bulkSelectionChangesOnlyCategoryMembershipNotInventoryOrOtherEquipment() {
        val category = listOf(item, item.copy(id = "axle"))
        val gym = GymProfile(name = "Gym", equipmentIds = setOf("bar", "bench", "unknown"),
            weightSelectionsKg = mapOf("bar" to listOf(EquipmentWeight(0.0, false, 2))))
        val selected = gym.selectCategory(category, true)
        assertEquals(setOf("bar", "axle", "bench", "unknown"), selected.equipmentIds)
        val deselected = selected.selectCategory(category, false)
        assertEquals(setOf("bench", "unknown"), deselected.equipmentIds)
        assertEquals(gym.weightSelectionsKg, deselected.weightSelectionsKg)
        assertTrue(equipmentRequirementsMet(listOf(setOf("bar", "axle")), listOf(setOf("bench")), selected.equipmentIds))
        assertFalse(equipmentRequirementsMet(listOf(setOf("bar", "axle")), listOf(setOf("bench")), deselected.equipmentIds))
        assertTrue(equipmentRequirementsMet(listOf(setOf("bench")), emptyList(), deselected.equipmentIds))
    }

    @Test
    fun invalidInventoryIsRejectedRatherThanDroppedFromBackup() {
        val valid = GymCodec.encode(GymProfile(name = "Gym", weightSelectionsLb = mapOf("bar" to listOf(EquipmentWeight(0.0, false, Int.MAX_VALUE)))))
        assertEquals(Int.MAX_VALUE, GymCodec.decode(valid).weightSelectionsLb.getValue("bar").single().quantity)
        for (quantity in listOf(-1, 0, 1.5, 2147483648.0)) {
            val invalid = JSONObject(valid.toString())
            invalid.getJSONObject("weightSelectionsLb").getJSONArray("bar").getJSONObject(0).put("quantity", quantity)
            assertThrows(IllegalArgumentException::class.java) { GymCodec.decode(invalid) }
        }
        val negative = JSONObject(valid.toString())
        negative.getJSONObject("weightSelectionsLb").getJSONArray("bar").getJSONObject(0).put("value", -0.5)
        assertThrows(IllegalArgumentException::class.java) { GymCodec.decode(negative) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentWeight(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { EquipmentWeight(Double.POSITIVE_INFINITY) }
        val duplicates = JSONObject(valid.toString())
        val rows = duplicates.getJSONObject("weightSelectionsLb").getJSONArray("bar")
        rows.put(JSONObject(rows.getJSONObject(0).toString()))
        assertThrows(IllegalArgumentException::class.java) { GymCodec.decode(duplicates) }
        assertThrows(Exception::class.java) { GymCodec.decode(JSONObject(valid.toString()).put("weightSelectionsLb", "bad")) }
    }
}
