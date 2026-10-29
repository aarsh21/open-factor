package org.openfactor.app

import android.util.JsonReader
import android.util.JsonWriter
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

class LargeBackupTest {
    @Test fun twoThousandWorkoutBackupPreparesAndReexportsWithoutTruncatingSets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photos = ProgressPhotoStore(context)
        val data = AppData(history = List(2_000) { index ->
            val start = 1_600_000_000_000L + index * 86_400_000L
            Workout(id = "fixture-$index", name = "Synthetic training $index", startedAt = start, finishedAt = start + 3_600_000,
                exercises = List(6) { exercise -> SessionExercise(id = "entry-$index-$exercise", exerciseId = "bench",
                    notes = "Synthetic performance fixture", restSeconds = 90,
                    sets = List(5) { row -> LoggedSet(id = "set-$index-$exercise-$row", weightKg = 50.0, reps = 8, rir = 2,
                        completed = true, warmup = row == 0, targetRepsMin = 6, targetRepsMax = 15, targetRir = 2) }) })
        })
        val input = DataCodec.encode(data, pretty = false).toByteArray()
        photos.prepare(ByteArrayInputStream(input)).use { prepared ->
            assertEquals(data, prepared.data)
            assertEquals(48_000, prepared.data.history.sumOf { it.completedWorkingSets() })
        }
        val exported = ByteArrayOutputStream().also { photos.export(data, it) }.toByteArray()
        photos.prepare(ByteArrayInputStream(exported)).use { prepared -> assertEquals(data, prepared.data) }
    }

    @Test fun streamedBackupEscapesTextAndPreservesLegacyMetadata() {
        val photos = ProgressPhotoStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val data = AppData(history = listOf(Workout(name = "Quotes: \"lift\"", notes = "Line one\nBackslash \\ · 日本語")),
            preferences = Preferences(displayName = "A & B"))
        val exported = ByteArrayOutputStream().also { photos.export(data, it) }.toByteArray()
        assertEquals(data, DataCodec.decode(exported.toString(Charsets.UTF_8)))
        photos.prepare(ByteArrayInputStream(exported)).use { assertEquals(data, it.data) }
    }

    @Test fun streamingMetadataStillRejectsAnExhaustedBudget() {
        val writer = JsonWriter(java.io.StringWriter()).apply { beginObject() }
        assertThrows(IllegalArgumentException::class.java) {
            BackupMetadataWriter(writer, maxBudget = 128).field("array", org.json.JSONArray(listOf(1, 2, 3)))
        }
    }

    @Test fun metadataStillRejectsAnExhaustedNodeBudget() {
        JsonReader(StringReader("[1,2,3]")).use { reader ->
            assertThrows(IllegalArgumentException::class.java) { BackupMetadataReader(reader, maxBudget = 128).value() }
        }
    }
}
