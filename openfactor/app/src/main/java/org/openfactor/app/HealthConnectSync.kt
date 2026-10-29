package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A dated snapshot of provider reads. An absent day is unknown, never an invented zero. */
data class HealthConnectSyncState(
    val lastSyncedAtMillis: Long? = null,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    val dailySteps: List<HealthConnectStepDay> = emptyList(),
    val weightReadings: Int = 0,
    val weightAccess: Boolean = false,
    val bodyFatAccess: Boolean = false,
    val stepsAccess: Boolean = false,
) {
    fun validate() {
        if (lastSyncedAtMillis == null) {
            require(firstDate == null && lastDate == null && dailySteps.isEmpty() && weightReadings == 0)
            require(!weightAccess && !bodyFatAccess && !stepsAccess)
            return
        }
        require(lastSyncedAtMillis >= 0 && firstDate != null && lastDate != null)
        require(!lastDate.isBefore(firstDate) && ChronoUnit.DAYS.between(firstDate, lastDate) < 30)
        require(weightAccess || stepsAccess)
        require(!bodyFatAccess || weightAccess)
        require(weightReadings in 0..30 && (weightAccess || weightReadings == 0))
        require(dailySteps.size <= 30 && dailySteps.map { it.date }.distinct().size == dailySteps.size)
        require(stepsAccess || dailySteps.isEmpty())
        require(dailySteps.all { it.date in firstDate..lastDate && it.count >= 0 })
    }
}

data class HealthConnectSyncData(
    val weights: List<ScaleWeightRecord>,
    val state: HealthConnectSyncState,
)

/** Persist body readings and their step snapshot in one durable app-store update. */
fun AppData.withHealthConnectSync(sync: HealthConnectSyncData): AppData {
    sync.state.validate()
    require(sync.weights.size == sync.state.weightReadings)
    require(sync.weights.all { it.date in sync.state.firstDate!!..sync.state.lastDate!! })
    return withHealthConnectWeights(sync.weights).copy(healthConnect = sync.state)
}

object HealthConnectSyncCodec {
    fun encode(state: HealthConnectSyncState): JSONObject {
        state.validate()
        return JSONObject()
            .put("lastSyncedAtMillis", state.lastSyncedAtMillis ?: JSONObject.NULL)
            .put("firstDate", state.firstDate?.toString() ?: JSONObject.NULL)
            .put("lastDate", state.lastDate?.toString() ?: JSONObject.NULL)
            .put("weightReadings", state.weightReadings)
            .put("weightAccess", state.weightAccess)
            .put("bodyFatAccess", state.bodyFatAccess)
            .put("stepsAccess", state.stepsAccess)
            .put("dailySteps", JSONArray().also { array ->
                state.dailySteps.forEach { day ->
                    array.put(JSONObject().put("date", day.date.toString()).put("count", day.count))
                }
            })
    }

    fun decode(json: JSONObject?): HealthConnectSyncState {
        if (json == null) return HealthConnectSyncState()
        val days = json.optJSONArray("dailySteps") ?: JSONArray()
        require(days.length() <= 30)
        return HealthConnectSyncState(
            lastSyncedAtMillis = if (json.isNull("lastSyncedAtMillis")) null else json.getLong("lastSyncedAtMillis"),
            firstDate = if (json.isNull("firstDate")) null else LocalDate.parse(json.getString("firstDate")),
            lastDate = if (json.isNull("lastDate")) null else LocalDate.parse(json.getString("lastDate")),
            dailySteps = List(days.length()) { index ->
                val day = days.getJSONObject(index)
                HealthConnectStepDay(LocalDate.parse(day.getString("date")), day.getLong("count"))
            },
            weightReadings = json.optInt("weightReadings", 0),
            weightAccess = json.optBoolean("weightAccess", false),
            bodyFatAccess = json.optBoolean("bodyFatAccess", false),
            stepsAccess = json.optBoolean("stepsAccess", false),
        ).also { it.validate() }
    }
}
