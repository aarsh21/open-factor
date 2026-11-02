package org.openfactor.app

import java.time.LocalDate

sealed interface HealthConnectResult<out T> {
    data class Success<T>(val value: T) : HealthConnectResult<T>
    data class ProviderUnavailable(val availability: HealthConnectAvailability) : HealthConnectResult<Nothing>
    data class PermissionRequired(val missing: Set<String>) : HealthConnectResult<Nothing>
    data object PermissionRevoked : HealthConnectResult<Nothing>
    data object Failed : HealthConnectResult<Nothing>
}

data class HealthConnectStepDay(val date: LocalDate, val count: Long)
