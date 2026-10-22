package org.openfactor.app

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataVisibilityTest {
    @Test
    fun visibilityRoundTripsWithoutChangingRecordsAndDefaultsCannotBeHidden() {
        val visibility = DataVisibility().withVisible("waist", false).withVisible("sidePhoto", false)
        val data = AppData(
            preferences = Preferences(dataVisibility = visibility, dismissedTutorials = setOf("bodyCharts")),
            bodyMeasurements = listOf(BodyMeasurementRecord(
                id = "asymmetric", date = LocalDate.of(2024, 2, 29),
                centimeters = mapOf(BodyMeasurement.WAIST to 81.125, BodyMeasurement.HIPS to 94.75),
                visualBodyFatPercent = 0.0,
            )),
            progressPhotos = listOf(ProgressPhotoRecord(photos = mapOf(PhotoPosition.SIDE to "a".repeat(64)))),
        )
        val decoded = DataCodec.decode(DataCodec.encode(data))
        assertEquals(data, decoded)
        assertFalse(decoded.preferences.dataVisibility.isVisible("waist"))
        assertTrue(decoded.preferences.dataVisibility.isVisible("hips"))
        assertEquals(data.bodyChartSeries(), decoded.bodyChartSeries())
        assertEquals(data.photoReferences(), decoded.photoReferences())
        assertTrue(visibility.withVisible("waist", true).isVisible("waist"))
        val supplied = DataVisibilityCodec.decode(JSONObject().put("hiddenBodyMetrics", JSONArray(listOf("weight", "visualBodyFat", "bodyFat", "waist"))))
        listOf("weight", "bodyFat", "visualBodyFat").forEach { assertTrue(supplied.isVisible(it)) }
        assertFalse(supplied.isVisible("waist"))
        val legacy = JSONObject(DataCodec.encode(data)).apply {
            getJSONObject("preferences").remove("dataVisibility")
            getJSONObject("preferences").remove("dismissedTutorials")
        }
        assertEquals(data.copy(preferences = Preferences()), DataCodec.decode(legacy.toString()))
        assertThrows(Exception::class.java) {
            DataVisibilityCodec.decode(JSONObject().put("hiddenBodyMetrics", JSONArray(listOf("unknown"))))
        }
    }
}
