package org.openfactor.app

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

enum class PhotoPosition(val key: String, val label: String) {
    FRONT("front", "Front"),
    SIDE("side", "Side"),
    BACK("back", "Back"),
}

/** Never direct a quick capture into a pose the user hid. */
internal fun DataVisibility.firstVisiblePhotoPosition(): PhotoPosition? =
    PhotoPosition.entries.firstOrNull { isVisible(it.key + "Photo") }

data class ProgressPhotoRecord(
    val id: String = newId(),
    val date: LocalDate = LocalDate.now(),
    val photos: Map<PhotoPosition, String>,
    val notes: String = "",
) {
    fun validate() {
        require(id.isNotBlank()) { "Missing photo record ID" }
        require(notes.length <= 4000) { "Photo note must be 4,000 characters or fewer" }
        require(photos.isNotEmpty()) { "Attach at least one photo" }
        require(photos.values.all { it.matches(Regex("[a-f0-9]{64}")) }) {
            "Invalid photo reference"
        }
    }
}

fun AppData.photoReferences(): Set<String> = progressPhotos.flatMap { it.photos.values }.toSet()

object ProgressPhotoCodec {
    fun encode(records: List<ProgressPhotoRecord>) =
        JSONArray(
            records.map { record ->
                record.validate()
                val photos = JSONObject()
                record.photos.forEach { (position, reference) ->
                    photos.put(position.key, reference)
                }
                JSONObject()
                    .put("id", record.id)
                    .put("date", record.date.toString())
                    .put("photos", photos)
                    .put("notes", record.notes)
            }
        )

    fun decode(json: JSONObject): List<ProgressPhotoRecord> {
        if (!json.has("progressPhotos")) return emptyList()
        val array = json.getJSONArray("progressPhotos")
        val records =
            (0 until array.length()).map { index ->
                val record = array.getJSONObject(index)
                val photos = record.getJSONObject("photos")
                ProgressPhotoRecord(
                        record.getString("id"),
                        LocalDate.parse(record.getString("date")),
                        photos.keys().asSequence().associate { key ->
                            val position =
                                PhotoPosition.entries.find { it.key == key }
                                    ?: error("Unknown photo position: $key")
                            position to photos.getString(key)
                        },
                        record.optString("notes", ""),
                    )
                    .also { it.validate() }
            }
        require(records.map { it.id }.distinct().size == records.size) {
            "Duplicate photo record ID"
        }
        return records
    }
}
