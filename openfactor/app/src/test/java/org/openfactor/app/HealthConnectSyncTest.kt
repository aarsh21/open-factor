package org.openfactor.app

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HealthConnectSyncTest {
    private val date = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.of("Asia/Kolkata")
    private val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)
    private val fatPermission = HealthPermission.getReadPermission(BodyFatRecord::class)
    private val stepPermission = HealthPermission.getReadPermission(StepsRecord::class)

    @Test fun noPermissionRequestsOnlyReadCategoriesAndNeverReadsRecords() = runBlocking {
        val provider = Provider()
        val transfers = provider.transfers()
        assertEquals(HealthConnectResult.PermissionRequired(healthConnectReadPermissions),
            transfers.sync(date, date, zone, "org.openfactor.app", 100))
        assertEquals(0, provider.scaleReads + provider.stepReads)
        provider.grants = setOf(fatPermission)
        assertFalse(canSyncHealthConnect(provider.grants))
        assertTrue(transfers.sync(date, date, zone, "org.openfactor.app", 100) is HealthConnectResult.PermissionRequired)
        assertEquals(0, provider.scaleReads + provider.stepReads)
    }

    @Test fun partialWeightAccessReadsRealWeightWithoutRequiringBodyFatOrSteps() = runBlocking {
        val provider = Provider()
        provider.grants = setOf(weightPermission)
        val result = provider.transfers().sync(date, date, zone, "org.openfactor.app", 100)
        val sync = (result as HealthConnectResult.Success).value
        assertEquals(81.7, sync.weights.single().weightKg, 0.0)
        assertNull(sync.weights.single().bodyFatPercent)
        assertTrue(sync.state.weightAccess)
        assertFalse(sync.state.bodyFatAccess)
        assertFalse(sync.state.stepsAccess)
        assertEquals(0, provider.fatReads)
        assertEquals(0, provider.stepReads)
    }

    @Test fun stepsUseProviderDailyAggregateWithoutOriginFilteringOrInventedEmptyDays() = runBlocking {
        val provider = Provider()
        provider.grants = setOf(stepPermission)
        val sync = (provider.transfers().sync(date.minusDays(1), date, zone, "org.openfactor.app", 100)
            as HealthConnectResult.Success).value
        assertEquals(listOf(HealthConnectStepDay(date, 4321)), sync.state.dailySteps)
        assertTrue(sync.weights.isEmpty())
        assertEquals(0, provider.scaleReads)
        assertEquals(1, provider.stepReads)
        val request = provider.lastAggregateRequest!!
        fun value(name: String) = request.javaClass.methods.single { it.name.startsWith(name) }.invoke(request)
        assertEquals(emptySet<DataOrigin>(), value("getDataOriginFilter"))
        assertEquals(setOf(StepsRecord.COUNT_TOTAL), value("getMetrics"))
        assertEquals(Period.ofDays(1), value("getTimeRangeSlicer"))
        val range = value("getTimeRangeFilter") as TimeRangeFilter
        assertEquals(date.minusDays(1).atStartOfDay(), range.localStartTime)
        assertEquals(date.plusDays(1).atStartOfDay(), range.localEndTime)
        assertNull(range.startTime)
    }

    @Test fun combinedSyncWaitsForAllReadsAndRechecksGrantsEveryTime() = runBlocking {
        val provider = Provider()
        provider.grants = healthConnectReadPermissions
        val transfers = provider.transfers()
        val sync = (transfers.sync(date, date, zone, "org.openfactor.app", 100) as HealthConnectResult.Success).value
        assertEquals(17.4, sync.weights.single().bodyFatPercent!!, 0.0)
        assertEquals(4321L, sync.state.dailySteps.single().count)
        provider.aggregateFailure = SecurityException("Access revoked while reading")
        assertEquals(HealthConnectResult.PermissionRevoked, transfers.sync(date, date, zone, "org.openfactor.app", 101))
        provider.aggregateFailure = IOException("Provider unavailable")
        assertEquals(HealthConnectResult.Failed, transfers.sync(date, date, zone, "org.openfactor.app", 101))
        provider.aggregateFailure = null
        provider.grants = emptySet()
        assertTrue(transfers.sync(date, date, zone, "org.openfactor.app", 102) is HealthConnectResult.PermissionRequired)
    }

    @Test fun snapshotSurvivesBackupAndRepeatedImportPreservesManualDataAndIds() {
        val manual = ScaleWeightRecord("manual", date, 82.4)
        val imported = ScaleWeightRecord("old", date.minusDays(1), 80.2, healthConnectOrigin = "scale.app")
        val state = HealthConnectSyncState(100, date.minusDays(1), date,
            listOf(HealthConnectStepDay(date, 0)), 2, true, false, true)
        val sync = HealthConnectSyncData(listOf(
            ScaleWeightRecord(date = date, weightKg = 81.7, healthConnectOrigin = "scale.app"),
            ScaleWeightRecord(date = date.minusDays(1), weightKg = 81.2, healthConnectOrigin = "scale.app"),
        ), state)
        val data = AppData(scaleWeights = listOf(manual, imported)).withHealthConnectSync(sync)
        assertEquals(manual, data.scaleWeights.first { it.date == date })
        assertEquals("old", data.scaleWeights.first { it.date == date.minusDays(1) }.id)
        assertEquals(data, data.withHealthConnectSync(sync))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertEquals(0L, data.healthConnect.dailySteps.single().count)
        assertEquals(state, HealthConnectSyncCodec.decode(HealthConnectSyncCodec.encode(state)))
        assertEquals(HealthConnectSyncState(), HealthConnectSyncCodec.decode(null))
    }

    @Test fun malformedSnapshotCannotIntroduceNegativeDuplicateOrOutOfRangeStepData() {
        val valid = HealthConnectSyncState(100, date, date,
            listOf(HealthConnectStepDay(date, 10)), 0, false, false, true)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(dailySteps = listOf(HealthConnectStepDay(date, -1))).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(dailySteps = valid.dailySteps + valid.dailySteps).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(dailySteps = listOf(HealthConnectStepDay(date.plusDays(1), 1))).validate()
        }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(stepsAccess = false).validate() }
        val json = HealthConnectSyncCodec.encode(valid)
        json.getJSONArray("dailySteps").getJSONObject(0).put("count", -1)
        assertThrows(IllegalArgumentException::class.java) { HealthConnectSyncCodec.decode(json) }
        assertThrows(IllegalArgumentException::class.java) {
            HealthConnectSyncCodec.decode(JSONObject().put("lastSyncedAtMillis", 100))
        }
    }

    private class Provider {
        val date: LocalDate = LocalDate.of(2026, 10, 29)
        var grants = emptySet<String>()
        var aggregateFailure: Exception? = null
        var scaleReads = 0
        var fatReads = 0
        var stepReads = 0
        var lastAggregateRequest: Any? = null
        private val at = date.atTime(8, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant()
        private fun metadata(): Metadata = Metadata::class.java.constructors.single { it.parameterCount == 7 }
            .newInstance(Metadata.RECORDING_METHOD_MANUAL_ENTRY, "", DataOrigin("scale.app"), Instant.EPOCH, null, 0L, null) as Metadata
        private val permissions = object : PermissionController {
            override suspend fun getGrantedPermissions() = grants
            override suspend fun revokeAllPermissions() { error("Not used") }
        }
        val client = Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader,
            arrayOf(HealthConnectClient::class.java)) { _, method, args ->
            when (method.name) {
                "getPermissionController" -> permissions
                "readRecords" -> {
                    val request = args!![0] as ReadRecordsRequest<*>
                    if (request.recordType == WeightRecord::class) {
                        scaleReads++
                        ReadRecordsResponse(listOf(WeightRecord(at, null, Mass.kilograms(81.7), metadata())), null)
                    } else {
                        fatReads++
                        ReadRecordsResponse(listOf(BodyFatRecord(at, null, Percentage(17.36), metadata())), null)
                    }
                }
                "aggregateGroupByPeriod" -> {
                    stepReads++
                    lastAggregateRequest = args!![0]
                    aggregateFailure?.let { throw it }
                    listOf(
                        AggregationResultGroupedByPeriod(AggregationResult(emptyMap(), emptyMap(), emptySet()),
                            date.minusDays(1).atStartOfDay(), date.atStartOfDay()),
                        AggregationResultGroupedByPeriod(AggregationResult(mapOf(StepsRecord.COUNT_TOTAL.metricKey to 4321L),
                            emptyMap(), setOf(DataOrigin("step.app"))), date.atStartOfDay(), date.plusDays(1).atStartOfDay()),
                    )
                }
                else -> error("Unexpected provider operation: ${method.name}")
            }
        } as HealthConnectClient
        fun transfers() = HealthConnectTransfers({ HealthConnectAvailability.AVAILABLE }, { client })
    }
}
