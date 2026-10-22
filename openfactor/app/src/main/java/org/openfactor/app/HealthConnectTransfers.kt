package org.openfactor.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

enum class HealthConnectAvailability { AVAILABLE, UNAVAILABLE, UPDATE_REQUIRED }

enum class HealthConnectAction(val label: String, val permissions: Set<String>) {
    IMPORT_SCALE("Import weight and scale body fat", setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
    )),
    READ_STEPS("Read daily steps", setOf(HealthPermission.getReadPermission(StepsRecord::class))),
    EXPORT_SCALE("Export weight and scale body fat", setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
    )),
    EXPORT_WORKOUTS("Export finished workouts", setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )),
}

sealed interface HealthConnectResult<out T> {
    data class Success<T>(val value: T) : HealthConnectResult<T>
    data class ProviderUnavailable(val availability: HealthConnectAvailability) : HealthConnectResult<Nothing>
    data class PermissionRequired(val missing: Set<String>) : HealthConnectResult<Nothing>
    data object PermissionRevoked : HealthConnectResult<Nothing>
    data object Failed : HealthConnectResult<Nothing>
}

data class HealthConnectStepDay(val date: LocalDate, val count: Long)

fun healthConnectAvailability(context: Context): HealthConnectAvailability =
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

/** Only explicit panel actions call this class. It never asks for permissions or schedules work. */
class HealthConnectTransfers(
    private val availability: () -> HealthConnectAvailability,
    private val client: () -> HealthConnectClient,
) {
    constructor(context: Context) : this(
        { healthConnectAvailability(context) }, { HealthConnectClient.getOrCreate(context) },
    )

    suspend fun <T> withAccess(
        action: HealthConnectAction,
        operation: suspend (HealthConnectClient) -> T,
    ): HealthConnectResult<T> {
        return try {
            val state = availability()
            if (state != HealthConnectAvailability.AVAILABLE) {
                HealthConnectResult.ProviderUnavailable(state)
            } else {
                val connection = client()
                val missing = action.permissions - connection.permissionController.getGrantedPermissions()
                if (missing.isNotEmpty()) HealthConnectResult.PermissionRequired(missing)
                else HealthConnectResult.Success(operation(connection))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            // Covers revocation between the permission check and the provider operation.
            HealthConnectResult.PermissionRevoked
        } catch (_: Exception) {
            HealthConnectResult.Failed
        }
    }

    suspend fun importScale(first: LocalDate, last: LocalDate, zone: ZoneId, ownPackage: String) =
        withAccess(HealthConnectAction.IMPORT_SCALE) { connection ->
            val range = TimeRangeFilter.between(
                first.atStartOfDay(zone).toInstant(), last.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            val weights = mutableListOf<WeightRecord>()
            var token: String? = null
            do {
                val page = connection.readRecords(ReadRecordsRequest(WeightRecord::class,
                    timeRangeFilter = range, pageToken = token))
                weights += page.records
                token = page.pageToken
            } while (token != null)
            val fat = mutableListOf<BodyFatRecord>()
            token = null
            do {
                val page = connection.readRecords(ReadRecordsRequest(BodyFatRecord::class,
                    timeRangeFilter = range, pageToken = token))
                fat += page.records
                token = page.pageToken
            } while (token != null)
            // Nothing is merged into local data until both paged reads finish successfully.
            healthConnectScaleWeights(weights, fat, zone, ownPackage)
        }

    suspend fun readSteps(first: LocalDate, last: LocalDate) =
        withAccess(HealthConnectAction.READ_STEPS) { connection ->
            connection.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(first.atStartOfDay(), last.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            )).mapNotNull { day ->
                day.result[StepsRecord.COUNT_TOTAL]?.let { HealthConnectStepDay(day.startTime.toLocalDate(), it) }
            }
        }

    suspend fun export(action: HealthConnectAction, records: () -> List<Record>) =
        withAccess(action) { connection ->
            require(action == HealthConnectAction.EXPORT_SCALE || action == HealthConnectAction.EXPORT_WORKOUTS)
            val batch = records()
            // Validate the entire batch before the single atomic provider insert. No range deletes.
            require(batch.size <= 1000) { "Export at most 1000 records at once" }
            if (batch.isNotEmpty()) connection.insertRecords(batch)
            batch.size
        }
}

/** Versions survive restart and backward clock changes in this installation, outside user backups. */
class HealthConnectVersions(context: Context) {
    private val preferences = context.getSharedPreferences("health_connect", Context.MODE_PRIVATE)

    fun reserve(): Long = synchronized(lock) {
        val version = nextHealthConnectVersion(preferences.getLong("version", 0), System.currentTimeMillis())
        check(preferences.edit().putLong("version", version).commit()) { "Could not reserve export version" }
        version
    }

    private companion object { val lock = Any() }
}

fun nextHealthConnectVersion(previous: Long, clockMillis: Long): Long {
    require(previous >= 0 && previous < Long.MAX_VALUE) { "Export version exhausted" }
    return maxOf(previous + 1, clockMillis.coerceAtLeast(0))
}
