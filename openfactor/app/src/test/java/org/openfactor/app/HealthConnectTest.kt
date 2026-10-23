package org.openfactor.app

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthConnectTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val date = LocalDate.of(2026, 10, 21)
    private val time = Instant.parse("2026-10-22T01:00:00Z") // Still October 21 locally.

    // Origin is provider-populated, so the public pre-insert factories cannot construct it.
    private fun metadata(origin: String): Metadata = Metadata::class.java.constructors
        .single { it.parameterCount == 7 }.newInstance(
            Metadata.RECORDING_METHOD_MANUAL_ENTRY, "", DataOrigin(origin), Instant.EPOCH, null, 0L, null,
        ) as Metadata

    private fun weight(kg: Double, at: Instant = time, origin: String = "test.scale") =
        WeightRecord(at, null, Mass.kilograms(kg), metadata(origin))

    private fun fat(percent: Double, at: Instant = time, origin: String = "test.scale") =
        BodyFatRecord(at, null, Percentage(percent), metadata(origin))

    @Test
    fun importUsesLocalCalendarLabelsLatestIndependentSamplesAndFirstTimestampTie() {
        val records = healthConnectScaleWeights(
            listOf(weight(71.2, time.minusSeconds(60)), weight(72.3), weight(99.0),
                weight(88.0, time.plusSeconds(60), "org.openfactor.app")),
            listOf(fat(18.24, time.minusSeconds(90)), fat(19.26, time.minusSeconds(30)),
                fat(45.0, time.plusSeconds(80), "org.openfactor.app"), fat(25.0, time.plusSeconds(86400))),
            zone, "org.openfactor.app",
        )
        assertEquals(1, records.size) // A fat-only day is not an invented weight record.
        assertEquals(date, records.single().date)
        assertEquals(72.3, records.single().weightKg, 0.0)
        assertEquals(19.3, records.single().bodyFatPercent!!, 0.0)
        assertEquals("test.scale", records.single().healthConnectOrigin)
        val utc = healthConnectScaleWeights(listOf(weight(72.3)), emptyList(), ZoneId.of("UTC"), "self")
        assertEquals(date.plusDays(1), utc.single().date)
    }

    @Test
    fun repeatedImportKeepsIdsManualDatesTrainingPhotosAndCompleteBackup() {
        val manual = ScaleWeightRecord("manual", date, 75.7, 17.5)
        val imported = ScaleWeightRecord("old-import", date.minusDays(1), 70.1, 16.2, "old.scale")
        val workout = Workout(name = "Keep training")
        val base = AppData(active = workout, history = listOf(workout.copy(finishedAt = 555)),
            scaleWeights = listOf(manual, imported),
            progressPhotos = listOf(ProgressPhotoRecord(date = date, photos = mapOf(PhotoPosition.SIDE to "ab".repeat(32)))),
            bodyMeasurements = listOf(BodyMeasurementRecord(date = date, visualBodyFatPercent = 24.3)))
        val incoming = listOf(
            ScaleWeightRecord("incoming-manual-day", date, 61.5, 23.6, "test.scale"),
            ScaleWeightRecord("incoming-update", date.minusDays(1), 69.4, null, "test.scale"),
        )
        val merged = base.withHealthConnectWeights(incoming)
        assertEquals(manual, merged.scaleWeights.first { it.date == date })
        assertEquals(imported.copy(weightKg = 69.4, bodyFatPercent = null,
            healthConnectOrigin = "test.scale"), merged.scaleWeights.first { it.date == date.minusDays(1) })
        assertEquals(merged, merged.withHealthConnectWeights(incoming))
        assertEquals(base.copy(scaleWeights = emptyList()), merged.copy(scaleWeights = emptyList()))
        assertEquals(merged, DataCodec.decode(DataCodec.encode(merged)))
        // Moving a manually edited import must not collide with a new import on its old date.
        val moved = ScaleWeightRecord(imported.id, date.minusDays(2), 68.1)
        val newDay = healthConnectScaleWeights(listOf(weight(67.3)), emptyList(), zone, "self").single()
            .copy(date = imported.date)
        val movedData = base.copy(scaleWeights = listOf(moved)).withHealthConnectWeights(listOf(newDay))
        assertEquals(2, movedData.scaleWeights.map { it.id }.distinct().size)
    }

    @Test
    fun selectedScaleExportKeepsImportedAndManualReadingsAndProvenanceInsideBothDateEdges() {
        val first = date.minusDays(6)
        val data = AppData(scaleWeights = listOf(
            ScaleWeightRecord("before", first.minusDays(1), 65.7, healthConnectOrigin = "old.scale"),
            ScaleWeightRecord("import", first, 72.39, 21.7, "test.scale"),
            ScaleWeightRecord("manual", date, 81.26, 18.3),
            ScaleWeightRecord("future", date.plusDays(1), 91.1, healthConnectOrigin = "future.scale"),
        ))
        val payload = ScopedDataExport.document(data, emptyList(), ScopedExportRequest(
            quick = false, timeFrame = ExportTimeFrame.SEVEN_DAYS,
            categories = setOf(ExportCategory.SCALE_WEIGHT),
        ), date, zone).getJSONObject("data").getJSONArray("scaleWeight")
        assertEquals(2, payload.length())
        assertEquals("import", payload.getJSONObject(0).getString("id"))
        assertEquals("test.scale", payload.getJSONObject(0).getString("healthConnectOrigin"))
        assertEquals(72.39, payload.getJSONObject(0).getDouble("weightKg"), 0.0)
        assertEquals("manual", payload.getJSONObject(1).getString("id"))
        assertTrue(payload.getJSONObject(1).isNull("healthConnectOrigin"))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertEquals(listOf(72.39, 81.26), data.bodyChartSeries().first { it.key == "weight" }
            .points.filter { it.date in first..date }.map { it.value })
    }

    @Test
    fun exportUsesLocalMidnightOffsetStableIdsAndOnlyManualScaleFat() {
        val manual = ScaleWeightRecord("a", LocalDate.of(2026, 3, 8), 81.27, 0.0)
        val afterDst = manual.copy(id = "b", date = manual.date.plusDays(1), bodyFatPercent = null)
        val imported = manual.copy(id = "c", healthConnectOrigin = "test.scale")
        fun export(version: Long) = healthConnectScaleExport(listOf(manual, afterDst, imported), zone,
            version, includeWeight = true, includeBodyFat = true)
        val records = export(41)
        assertEquals(3, records.size)
        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), (records[0] as WeightRecord).time)
        assertEquals(Instant.parse("2026-03-09T07:00:00Z"), (records[2] as WeightRecord).time)
        assertEquals(0.0, (records[1] as BodyFatRecord).percentage.value, 0.0)
        assertEquals(listOf("open-factor:weight:a", "open-factor:fat:a", "open-factor:weight:b"),
            records.map { it.metadata.clientRecordId })
        assertEquals(records.map { it.metadata.clientRecordId }, export(42).map { it.metadata.clientRecordId })
        assertTrue(export(42).all { it.metadata.clientRecordVersion == 42L })
        assertTrue(healthConnectScaleExport(listOf(imported), zone, 42, true, true).isEmpty())
    }

    @Test
    fun workoutSegmentsCarryAcrossExercisesExtendShortSessionsAndSkipUnperformedRows() {
        val workout = Workout(id = "workout", name = "Asymmetric", startedAt = time.toEpochMilli(),
            finishedAt = time.plusSeconds(15).toEpochMilli(), pausedDurationMs = 4000,
            exercises = listOf(
                SessionExercise(exerciseId = "one", sets = listOf(
                    LoggedSet(completed = true, warmup = true, reps = 50),
                    LoggedSet(completed = false, reps = 22),
                    LoggedSet(completed = true, reps = 7),
                    LoggedSet(completed = true, rir = 3),
                )),
                SessionExercise(exerciseId = "two", sets = listOf(
                    LoggedSet(completed = true, weightKg = 12.5),
                    LoggedSet(completed = true, durationSeconds = 9.0),
                )),
            ))
        val record = healthConnectWorkoutExport(workout, zone, 1)!!
        assertEquals(time.plusSeconds(30), record.endTime) // MAX, not MIN or wall-clock 15s.
        assertEquals(listOf(time, time.plusSeconds(10), time.plusSeconds(20)), record.segments.map { it.startTime })
        assertEquals(listOf(7, 0, 0), record.segments.map { it.repetitions })
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, record.exerciseType)
        val long = workout.copy(finishedAt = time.plusSeconds(49).toEpochMilli())
        assertEquals(time.plusSeconds(45), healthConnectWorkoutExport(long, zone, 2)!!.endTime)
        val empty = workout.copy(exercises = emptyList(), pausedDurationMs = 0)
        assertTrue(healthConnectWorkoutExport(empty, zone, 3)!!.segments.isEmpty())
        assertNull(healthConnectWorkoutExport(empty.copy(finishedAt = empty.startedAt), zone, 4))
        assertNull(healthConnectWorkoutExport(workout.copy(finishedAt = null), zone, 5))
    }

    @Test
    fun versionsIncreaseAfterBackwardClockAndStopAtOverflow() {
        assertEquals(101L, nextHealthConnectVersion(100, 20))
        assertEquals(500L, nextHealthConnectVersion(100, 500))
        assertEquals(1L, nextHealthConnectVersion(0, -1))
        assertEquals(Long.MAX_VALUE, nextHealthConnectVersion(Long.MAX_VALUE - 1, 100))
        assertThrows(IllegalArgumentException::class.java) { nextHealthConnectVersion(Long.MAX_VALUE, 0) }
    }

    @Test
    fun advancedLogsExportOneLogicalSegmentWithOnlyCompletedActualRepetitions() {
        val entry = SessionExercise(exerciseId = "row", sets = listOf(LoggedSet(id = "root", reps = 7)))
            .withSetKind("root", SetKind.MYO).withSplitSides().let { exercise ->
                exercise.copy(sets = exercise.sets.mapIndexed { index, log ->
                    log.copy(reps = listOf(7, 5, 3, 99)[index], completed = index != 3)
                })
            }
        val workout = Workout(name = "Myo", startedAt = time.toEpochMilli(), finishedAt = time.plusSeconds(2).toEpochMilli(), exercises = listOf(entry))
        val record = healthConnectWorkoutExport(workout, zone, 1)!!
        assertEquals(1, record.segments.size)
        assertEquals(15, record.segments.single().repetitions)
        assertEquals(time.plusSeconds(10), record.endTime)
        assertEquals(4, workout.exercises.single().sets.size)
    }

    @Test
    fun providerDenialRevocationAndPersistenceFailureNeverBecomeSuccessfulExports() = runBlocking {
        val provider = FakeProvider()
        var availability = HealthConnectAvailability.UNAVAILABLE
        val transfers = HealthConnectTransfers({ availability }, { provider.client })
        val export = { healthConnectScaleExport(listOf(ScaleWeightRecord(weightKg = 71.8)), zone, 1, true, true) }
        assertEquals(HealthConnectResult.ProviderUnavailable(availability),
            transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        availability = HealthConnectAvailability.UPDATE_REQUIRED
        assertEquals(HealthConnectResult.ProviderUnavailable(availability),
            transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        availability = HealthConnectAvailability.AVAILABLE
        provider.grants = HealthConnectAction.EXPORT_SCALE.permissions.take(1).toSet()
        assertEquals(HealthConnectResult.PermissionRequired(HealthConnectAction.EXPORT_SCALE.permissions - provider.grants),
            transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        provider.grants = HealthConnectAction.EXPORT_SCALE.permissions
        provider.failure = SecurityException()
        assertEquals(HealthConnectResult.PermissionRevoked, transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        provider.failure = IOException()
        assertEquals(HealthConnectResult.Failed, transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        provider.failure = null
        assertEquals(HealthConnectResult.Failed, transfers.export(HealthConnectAction.EXPORT_SCALE) {
            error("Synchronous version persistence failed")
        })
        assertTrue(provider.written.isEmpty())
        assertEquals(HealthConnectResult.Success(1), transfers.export(HealthConnectAction.EXPORT_SCALE, export))
        provider.grants = emptySet() // A later action must not reuse a successful earlier check.
        assertTrue(transfers.export(HealthConnectAction.EXPORT_SCALE, export) is HealthConnectResult.PermissionRequired)
        assertEquals(1, provider.written.size)
        provider.grants = HealthConnectAction.EXPORT_SCALE.permissions
        provider.failure = CancellationException("Leave panel")
        try {
            transfers.export(HealthConnectAction.EXPORT_SCALE, export)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test
    fun allReadPagesMustFinishBeforeImportCanChangeLocalData() = runBlocking {
        val provider = FakeProvider()
        provider.grants = HealthConnectAction.IMPORT_SCALE.permissions
        val transfers = HealthConnectTransfers({ HealthConnectAvailability.AVAILABLE }, { provider.client })
        provider.weights = listOf(weight(70.2, time.minusSeconds(60)), weight(71.9))
        provider.fat = listOf(fat(21.37))
        val result = transfers.importScale(date, date, zone, "self")
        assertTrue(result is HealthConnectResult.Success)
        val records = (result as HealthConnectResult.Success).value
        assertEquals(71.9, records.single().weightKg, 0.0)
        assertEquals(21.4, records.single().bodyFatPercent!!, 0.0)
        provider.failFat = true
        assertEquals(HealthConnectResult.PermissionRevoked, transfers.importScale(date, date, zone, "self"))
    }

    private class FakeProvider {
        var grants = emptySet<String>()
        var failure: Exception? = null
        var weights = emptyList<WeightRecord>()
        var fat = emptyList<BodyFatRecord>()
        var failFat = false
        val written = mutableListOf<Record>()
        private val permissions = object : PermissionController {
            override suspend fun getGrantedPermissions() = grants
            override suspend fun revokeAllPermissions() { error("Not used") }
        }
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader,
            arrayOf(HealthConnectClient::class.java)) { _, method, args ->
            when (method.name) {
                "getPermissionController" -> permissions
                "insertRecords" -> {
                    failure?.let { throw it }
                    @Suppress("UNCHECKED_CAST")
                    val batch = args!![0] as List<Record>
                    written += batch
                    InsertRecordsResponse(batch.indices.map { "fake-$it" })
                }
                "readRecords" -> {
                    val request = args!![0] as ReadRecordsRequest<*>
                    if (request.recordType == BodyFatRecord::class) {
                        if (failFat) throw SecurityException()
                        ReadRecordsResponse(fat, null)
                    } else {
                        val index = request.pageToken?.toInt() ?: 0
                        val page = weights.drop(index).take(1)
                        ReadRecordsResponse(page, if (index + 1 < weights.size) "${index + 1}" else null)
                    }
                }
                else -> error("Unexpected provider operation: ${method.name}")
            }
        } as HealthConnectClient
    }
}
