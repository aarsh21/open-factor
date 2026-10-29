package org.openfactor.app

import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryTest {
    @Test fun chronologyAndDayFilteringFollowRecordedDatesWithoutDuplicateIds() {
        fun workout(id: String, date: String, hour: Int) = Workout(id = id, name = id,
            startedAt = LocalDate.parse(date).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val older = workout("older", "2026-10-26", 9)
        val first = workout("first", "2026-10-27", 9)
        val latest = workout("latest", "2026-10-27", 18)
        val days = workoutHistoryDays(listOf(older, first, latest, latest))
        assertEquals(listOf(LocalDate.parse("2026-10-27"), LocalDate.parse("2026-10-26")), days.map { it.first })
        assertEquals(listOf(latest, first), days.first().second)
        assertEquals(listOf(older), workoutHistoryDays(listOf(latest, older), older.localDate()).single().second)
        assertTrue(workoutHistoryDays(listOf(older), LocalDate.parse("2026-10-28")).isEmpty())
    }

    @Test fun durationKeepsSecondsAndHours() {
        assertEquals("0:00", historyDuration(0))
        assertEquals("21:19", historyDuration(1279))
        assertEquals("2:19:40", historyDuration(8380))
    }

    @Test fun quickCaptureChoosesOnlyVisiblePosesAndSkipsWhenAllAreHidden() {
        val frontHidden = DataVisibility().withVisible("frontPhoto", false)
        assertEquals(PhotoPosition.SIDE, frontHidden.firstVisiblePhotoPosition())
        val allHidden = PhotoPosition.entries.fold(DataVisibility()) { visibility, position ->
            visibility.withVisible(position.key + "Photo", false)
        }
        assertNull(allHidden.firstVisiblePhotoPosition())
        assertEquals(PhotoPosition.FRONT, DataVisibility().firstVisiblePhotoPosition())
    }

    @Test fun photoNotesRoundTripAndLegacyPhotosRemainReadable() {
        val record = ProgressPhotoRecord(id = "photo", photos = mapOf(PhotoPosition.FRONT to "a".repeat(64)), notes = "Week 12\nMorning")
        val original = AppData(progressPhotos = listOf(record))
        assertEquals(original, DataCodec.decode(DataCodec.encode(original)))
        val json = JSONObject(DataCodec.encode(original))
        json.getJSONArray("progressPhotos").getJSONObject(0).remove("notes")
        assertEquals("", DataCodec.decode(json.toString()).progressPhotos.single().notes)
        assertThrows(IllegalArgumentException::class.java) { record.copy(notes = "x".repeat(4001)).validate() }
    }
}
