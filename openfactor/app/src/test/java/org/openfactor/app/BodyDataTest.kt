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
                            visualBodyFatPercent = 24.75,
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
    fun backupKeepsVisualBodyFatSeparateFromScaleBodyFat() {
        val data =
            AppData(
                scaleWeights =
                    listOf(ScaleWeightRecord(date = date, weightKg = 71.25, bodyFatPercent = 18.7)),
                bodyMeasurements =
                    listOf(
                        BodyMeasurementRecord(
                            date = date,
                            centimeters = mapOf(BodyMeasurement.WAIST to 84.5),
                        )
                    ),
            )
        val backup = JSONObject(DataCodec.encode(data))
        backup.getJSONArray("bodyMeasurements").getJSONObject(0).put("visualBodyFatPercent", 24.75)
        val restored = JSONObject(DataCodec.encode(DataCodec.decode(backup.toString())))
        assertEquals(
            24.75,
            restored
                .getJSONArray("bodyMeasurements")
                .getJSONObject(0)
                .getDouble("visualBodyFatPercent"),
            0.0,
        )
        assertEquals(
            18.7,
            restored.getJSONArray("scaleWeights").getJSONObject(0).getDouble("bodyFatPercent"),
            0.0,
        )
    }

    @Test
    fun visualBodyFatCanBeTheOnlyFullBodyValueAndClearingItDoesNotEraseCircumferences() {
        val base =
            AppData(
                scaleWeights =
                    listOf(ScaleWeightRecord(date = date, weightKg = 81.2, bodyFatPercent = 17.3))
            )
        for (percent in listOf(0.0, 24.75, 100.0)) {
            val record =
                BodyMeasurementRecord(id = "visual", date = date, visualBodyFatPercent = percent)
            val saved = base.withBodyMeasurements(record)
            assertEquals(saved, DataCodec.decode(DataCodec.encode(saved)))
            assertEquals(
                percent,
                saved.bodyChartSeries().first { it.key == "visualBodyFat" }.points.single().value,
                0.0,
            )
            assertEquals(
                listOf(17.3),
                saved.bodyChartSeries().first { it.key == "bodyFat" }.points.map { it.value },
            )
            val measured = record.copy(centimeters = mapOf(BodyMeasurement.WAIST to 85.25))
            val cleared = measured.copy(visualBodyFatPercent = null)
            assertEquals(
                listOf(cleared),
                saved.withBodyMeasurements(measured).withBodyMeasurements(cleared).bodyMeasurements,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.withBodyMeasurements(BodyMeasurementRecord())
        }
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
        listOf(-0.01, 100.01, "NaN").forEach { percent ->
            rejected {
                it.getJSONArray("bodyMeasurements")
                    .getJSONObject(0)
                    .put("visualBodyFatPercent", percent)
            }
        }
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

    @Test
    fun chartsKeepSeparateReadingsAndOmitMissingFieldsWithoutLosingZeroPercent() {
        val data =
            AppData(
                scaleWeights =
                    listOf(
                        ScaleWeightRecord("first", date, 53.25, 0.0),
                        ScaleWeightRecord("second", date, 54.0),
                    ),
                bodyMeasurements =
                    listOf(
                        BodyMeasurementRecord(
                            "measure",
                            date,
                            mapOf(
                                BodyMeasurement.LEFT_BICEP to 31.1,
                                BodyMeasurement.RIGHT_BICEP to 34.2,
                            ),
                        )
                    ),
            )
        val metric = data.bodyChartSeries().associateBy { it.key }
        assertEquals(listOf(53.25, 54.0), metric.getValue("weight").points.map { it.value })
        assertEquals(
            listOf("first", "second"),
            metric.getValue("weight").points.map { it.recordId },
        )
        assertEquals(listOf(0.0), metric.getValue("bodyFat").points.map { it.value })
        assertTrue(metric.getValue("waist").points.isEmpty())
        assertEquals(31.1, metric.getValue("leftBicep").points.single().value, 0.0)
        assertEquals(34.2, metric.getValue("rightBicep").points.single().value, 0.0)
        val pounds = data.copy(preferences = Preferences(pounds = true)).bodyChartSeries()
        assertEquals("lb", pounds.first().unit)
        assertEquals(117.39615461, pounds.first().points.first().value, .000001)
        assertEquals(metric.getValue("leftBicep"), pounds.first { it.key == "leftBicep" })
        assertEquals(
            data.bodyChartSeries(),
            DataCodec.decode(DataCodec.encode(data)).bodyChartSeries(),
        )
    }

    @Test
    fun chartCalendarWindowsIncludeBothBoundariesAcrossLeapAndYearTransitions() {
        val today = LocalDate.of(2024, 3, 3)
        val windows =
            mapOf(
                BodyChartRange.WEEK to ("2024-02-26" to "2024-03-03"),
                BodyChartRange.MONTH to ("2024-03-01" to "2024-03-31"),
                BodyChartRange.THREE_MONTHS to ("2024-01-01" to "2024-03-31"),
                BodyChartRange.SIX_MONTHS to ("2023-10-01" to "2024-03-31"),
                BodyChartRange.YEAR to ("2023-04-01" to "2024-03-31"),
            )
        windows.forEach { (range, dates) ->
            val first = LocalDate.parse(dates.first)
            val last = LocalDate.parse(dates.second)
            val points =
                listOf(
                    BodyChartPoint("before", first.minusDays(1), 71.0),
                    BodyChartPoint("last", last, 82.0),
                    BodyChartPoint("after", last.plusDays(1), 63.0),
                    BodyChartPoint("first", first, 94.0),
                )
            val series = BodyChartSeries("weight", "Scale Weight", "kg", points)
            val window = range.window(today, points)
            assertEquals(first..last, window)
            assertEquals(listOf("first", "last"), series.inWindow(window).map { it.recordId })
            assertEquals(
                first.minusDays(1)..last.plusDays(1),
                BodyChartRange.ALL.window(today, points),
            )
        }
        assertEquals(today..today, BodyChartRange.ALL.window(today, emptyList()))
        val single = listOf(BodyChartPoint("one", today.minusYears(3), 72.0))
        assertEquals(
            single.single().date..single.single().date,
            BodyChartRange.ALL.window(today, single),
        )
    }
}
