package org.openfactor.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Calendar dates are intentional: weigh-ins must not move when the device changes time zone. */
data class ScaleWeightRecord(
    val id: String = newId(),
    val date: LocalDate = LocalDate.now(),
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
) {
    fun validate() {
        require(id.isNotBlank()) { "Missing weight record ID" }
        require(weightKg.isFinite() && weightKg > 0 && weightKg <= 5000) {
            "Weight must be greater than 0 and at most 5000 kg"
        }
        require(
            bodyFatPercent == null || bodyFatPercent.isFinite() && bodyFatPercent in 0.0..100.0
        ) {
            "Body fat must be between 0 and 100%"
        }
    }
}

/** Field inventory observed in the retained Full Body forms; no body-fat estimator is inferred. */
enum class BodyMeasurement(val key: String, val label: String, val group: String) {
    NECK("neck", "Neck", "Upper Body"),
    SHOULDERS("shoulders", "Shoulders", "Upper Body"),
    BUST("bust", "Bust", "Upper Body"),
    CHEST("chest", "Chest", "Upper Body"),
    WAIST("waist", "Waist", "Upper Body"),
    HIPS("hips", "Hips", "Upper Body"),
    LEFT_BICEP("leftBicep", "Left Bicep", "Arms"),
    RIGHT_BICEP("rightBicep", "Right Bicep", "Arms"),
    LEFT_FOREARM("leftForearm", "Left Forearm", "Arms"),
    RIGHT_FOREARM("rightForearm", "Right Forearm", "Arms"),
    LEFT_WRIST("leftWrist", "Left Wrist", "Arms"),
    RIGHT_WRIST("rightWrist", "Right Wrist", "Arms"),
    LEFT_THIGH("leftThigh", "Left Thigh", "Legs"),
    RIGHT_THIGH("rightThigh", "Right Thigh", "Legs"),
    LEFT_CALF("leftCalf", "Left Calf", "Legs"),
    RIGHT_CALF("rightCalf", "Right Calf", "Legs"),
    LEFT_ANKLE("leftAnkle", "Left Ankle", "Legs"),
    RIGHT_ANKLE("rightAnkle", "Right Ankle", "Legs"),
}

data class BodyMeasurementRecord(
    val id: String = newId(),
    val date: LocalDate = LocalDate.now(),
    val centimeters: Map<BodyMeasurement, Double>,
) {
    fun validate() {
        require(id.isNotBlank()) { "Missing measurement record ID" }
        require(centimeters.isNotEmpty()) { "Enter at least one measurement" }
        require(centimeters.values.all { it.isFinite() && it > 0 && it <= 1000 }) {
            "Measurements must be greater than 0 and at most 1000 cm"
        }
    }
}

fun AppData.withScaleWeight(record: ScaleWeightRecord): AppData {
    record.validate()
    return copy(scaleWeights = listOf(record) + scaleWeights.filterNot { it.id == record.id })
}

fun AppData.withBodyMeasurements(record: BodyMeasurementRecord): AppData {
    record.validate()
    return copy(
        bodyMeasurements = listOf(record) + bodyMeasurements.filterNot { it.id == record.id }
    )
}

fun Double.bodyNumber(decimals: Int = 2): String =
    BigDecimal.valueOf(this)
        .setScale(decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

fun Double.bodyWeightNumber(pounds: Boolean, decimals: Int = 2): String =
    (if (pounds) this * 2.2046226218 else this).bodyNumber(decimals)

fun String.bodyDecimal(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

object BodyDataCodec {
    fun weights(records: List<ScaleWeightRecord>) =
        JSONArray(
            records.map {
                it.validate()
                JSONObject()
                    .put("id", it.id)
                    .put("date", it.date.toString())
                    .put("weightKg", it.weightKg)
                    .put("bodyFatPercent", it.bodyFatPercent)
            }
        )

    fun measurements(records: List<BodyMeasurementRecord>) =
        JSONArray(
            records.map {
                it.validate()
                val values = JSONObject()
                it.centimeters.forEach { (field, value) -> values.put(field.key, value) }
                JSONObject()
                    .put("id", it.id)
                    .put("date", it.date.toString())
                    .put("centimeters", values)
            }
        )

    fun readWeights(json: JSONObject): List<ScaleWeightRecord> {
        if (!json.has("scaleWeights")) return emptyList()
        val array = json.getJSONArray("scaleWeights")
        val records =
            (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                ScaleWeightRecord(
                        value.getString("id"),
                        LocalDate.parse(value.getString("date")),
                        value.getDouble("weightKg"),
                        if (value.isNull("bodyFatPercent")) null
                        else value.getDouble("bodyFatPercent"),
                    )
                    .also { it.validate() }
            }
        require(records.map { it.id }.distinct().size == records.size) {
            "Duplicate weight record ID"
        }
        return records
    }

    fun readMeasurements(json: JSONObject): List<BodyMeasurementRecord> {
        if (!json.has("bodyMeasurements")) return emptyList()
        val array = json.getJSONArray("bodyMeasurements")
        val records =
            (0 until array.length()).map { index ->
                val value = array.getJSONObject(index)
                val values = value.getJSONObject("centimeters")
                val fields =
                    values.keys().asSequence().associate { key ->
                        val field =
                            BodyMeasurement.entries.find { it.key == key }
                                ?: error("Unknown body measurement: $key")
                        field to values.getDouble(key)
                    }
                BodyMeasurementRecord(
                        value.getString("id"),
                        LocalDate.parse(value.getString("date")),
                        fields,
                    )
                    .also { it.validate() }
            }
        require(records.map { it.id }.distinct().size == records.size) {
            "Duplicate measurement record ID"
        }
        return records
    }
}
