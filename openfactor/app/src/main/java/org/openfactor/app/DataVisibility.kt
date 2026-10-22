package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

/** Presentation preferences only. Records and complete exports never consult this filter. */
data class DataVisibility(
    val hiddenBodyMetrics: Set<String> = emptySet(),
    val showPeriod: Boolean = false,
) {
    init {
        require(hiddenBodyMetrics.all { it in toggleableKeys || it in defaultKeys }) { "Unknown hidden body metric" }
    }

    fun isVisible(key: String): Boolean = key in defaultKeys || key !in hiddenBodyMetrics

    fun withVisible(key: String, visible: Boolean): DataVisibility {
        require(key in toggleableKeys) { "Default metrics cannot be hidden" }
        return copy(hiddenBodyMetrics = if (visible) hiddenBodyMetrics - key else hiddenBodyMetrics + key)
    }

    companion object {
        val defaultKeys: Set<String> = setOf("weight", "bodyFat", "visualBodyFat")
        val toggleableKeys: Set<String> =
            BodyMeasurement.entries.map { it.key }.toSet() + setOf("frontPhoto", "sidePhoto", "backPhoto")
    }
}

object DataVisibilityCodec {
    fun encode(value: DataVisibility): JSONObject = JSONObject()
        .put("hiddenBodyMetrics", JSONArray(value.hiddenBodyMetrics.sorted()))
        .put("showPeriod", value.showPeriod)

    fun decode(value: JSONObject?): DataVisibility {
        if (value == null) return DataVisibility()
        val keys = if (value.has("hiddenBodyMetrics")) value.getJSONArray("hiddenBodyMetrics") else JSONArray()
        return DataVisibility(
            (0 until keys.length()).map { keys.getString(it) }.toSet(),
            if (value.has("showPeriod")) value.getBoolean("showPeriod") else false,
        )
    }
}
