package org.openfactor.app

/** Manual dates win; a repeated import updates only the imported reading and retains its ID. */
fun AppData.withHealthConnectWeights(incoming: List<ScaleWeightRecord>): AppData {
    require(incoming.all { it.healthConnectOrigin != null })
    incoming.forEach { it.validate() }
    var records = scaleWeights
    for (record in incoming) {
        val day = records.filter { it.date == record.date }
        if (day.any { it.healthConnectOrigin == null }) continue
        val existing = day.firstOrNull()
        val replacement = record.copy(id = existing?.id ?: record.id)
        records = records.filterNot { it.date == record.date } + replacement
    }
    return copy(scaleWeights = records)
}
