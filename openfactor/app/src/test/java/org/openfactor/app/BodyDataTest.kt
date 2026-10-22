package org.openfactor.app

import java.time.LocalDate
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BodyDataTest {
    private val date = LocalDate.of(2026, 10, 20)

    @Test
    fun backupPreservesDatedBodyRecordsAndTrainingAcrossTimeZones() {
        val workout = Workout(name = "Keep my workout", startedAt = 123456L)
        val program = TrainingProgram(name = "Keep my program")
        val data =
            AppData(
                active = workout,
                history = listOf(workout.copy(id = "finished", finishedAt = 234567L)),
                programs = listOf(program),
                activeProgramId = program.id,
                preferences = Preferences(pounds = true),
                scaleWeights =
                    listOf(
                        ScaleWeightRecord(date = date, weightKg = 82.35, bodyFatPercent = 18.7),
                        ScaleWeightRecord(date = date, weightKg = 82.1),
                    ),
                bodyMeasurements =
                    listOf(
                        BodyMeasurementRecord(
                            date = date,
                            centimeters =
                                BodyMeasurement.entries
                                    .mapIndexed { index, field -> field to (20.25 + index) }
                                    .toMap(),
                        )
                    ),
            )
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Calcutta"))
            val backup = DataCodec.encode(data)
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(data, DataCodec.decode(backup))
            assertEquals("181.55", data.scaleWeights.first().weightKg.bodyWeightNumber(true))
            assertEquals("82.35", data.scaleWeights.first().weightKg.bodyWeightNumber(false))
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun editingReplacesOnlyTheRecordWithTheSameId() {
        val first = ScaleWeightRecord(date = date, weightKg = 77.25, bodyFatPercent = 19.1)
        val other = ScaleWeightRecord(date = date, weightKg = 78.5)
        val base =
            AppData(
                active = Workout(name = "In progress"),
                programs = listOf(TrainingProgram(name = "Plan")),
            )
        val saved = base.withScaleWeight(first).withScaleWeight(other)
        val changed = first.copy(date = date.minusDays(2), weightKg = 76.75, bodyFatPercent = null)
        val edited = saved.withScaleWeight(changed)
        assertEquals(listOf(changed, other), edited.scaleWeights)
        assertEquals(base, edited.copy(scaleWeights = emptyList()))
        val measurements =
            BodyMeasurementRecord(
                date = date,
                centimeters =
                    mapOf(BodyMeasurement.LEFT_BICEP to 34.5, BodyMeasurement.RIGHT_BICEP to 35.25),
            )
        val updated = measurements.copy(centimeters = mapOf(BodyMeasurement.RIGHT_BICEP to 36.1))
        val measured = edited.withBodyMeasurements(measurements).withBodyMeasurements(updated)
        assertEquals(listOf(updated), measured.bodyMeasurements)
        assertEquals(edited, measured.copy(bodyMeasurements = emptyList()))
    }

    @Test
    fun oldBackupsRemainReadableWithoutInventingDatedOnboardingRecords() {
        val data =
            AppData(
                history = listOf(Workout(name = "Existing")),
                onboarding = OnboardingState(weightKg = "75.5"),
            )
        val legacy =
            JSONObject(DataCodec.encode(data)).apply {
                remove("scaleWeights")
                remove("bodyMeasurements")
            }
        assertEquals(data, DataCodec.decode(legacy.toString()))
    }

    @Test
    fun invalidBodyDataRejectsTheWholeBackupRatherThanSilentlyDroppingRecords() {
        val good =
            AppData()
                .withScaleWeight(ScaleWeightRecord(date = date, weightKg = 72.5))
                .withBodyMeasurements(
                    BodyMeasurementRecord(
                        date = date,
                        centimeters = mapOf(BodyMeasurement.WAIST to 84.5),
                    )
                )
        fun rejected(change: (JSONObject) -> Unit) {
            val json = JSONObject(DataCodec.encode(good))
            change(json)
            assertThrows(Exception::class.java) { DataCodec.decode(json.toString()) }
        }
        rejected { it.getJSONArray("scaleWeights").getJSONObject(0).put("weightKg", 0) }
        rejected { it.getJSONArray("scaleWeights").getJSONObject(0).put("weightKg", "NaN") }
        rejected { it.getJSONArray("scaleWeights").getJSONObject(0).put("bodyFatPercent", 100.1) }
        rejected { it.getJSONArray("scaleWeights").getJSONObject(0).put("date", "2026-02-30") }
        rejected {
            it.getJSONArray("scaleWeights").put(it.getJSONArray("scaleWeights").getJSONObject(0))
        }
        rejected { it.put("bodyMeasurements", "not an array") }
        rejected {
            it.getJSONArray("bodyMeasurements").getJSONObject(0).put("centimeters", JSONObject())
        }
        rejected {
            it.getJSONArray("bodyMeasurements")
                .getJSONObject(0)
                .getJSONObject("centimeters")
                .put("waist", -1)
        }
        rejected {
            it.getJSONArray("bodyMeasurements")
                .getJSONObject(0)
                .getJSONObject("centimeters")
                .put("unknown", 30)
        }
        val boundaries =
            good.copy(
                scaleWeights =
                    listOf(
                        ScaleWeightRecord(weightKg = 5000.0, bodyFatPercent = 0.0),
                        ScaleWeightRecord(weightKg = 0.1, bodyFatPercent = 100.0),
                    )
            )
        assertEquals(boundaries, DataCodec.decode(DataCodec.encode(boundaries)))
        assertThrows(IllegalArgumentException::class.java) {
            good.withScaleWeight(ScaleWeightRecord(weightKg = Double.POSITIVE_INFINITY))
        }
    }
}
