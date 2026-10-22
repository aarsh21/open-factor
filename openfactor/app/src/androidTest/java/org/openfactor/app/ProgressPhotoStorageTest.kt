package org.openfactor.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressPhotoStorageTest {
    @Test
    fun originalsSurviveReopenAndBackupAndSharedImagesAreRemovedOnlyAfterLastRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = WorkoutViewModel(context.applicationContext as Application)
        val originalBackup = vm.export()
        try {
            val training = vm.data.value.copy(
                progressPhotos = emptyList(),
                preferences = vm.data.value.preferences.copy(
                    dataVisibility = DataVisibility().withVisible("sidePhoto", false).withVisible("waist", false),
                    dismissedTutorials = setOf("bodyCharts"),
                ),
            )
            vm.restore(DataCodec.encode(training))
            fun image(color: Int): ByteArray {
                val bitmap = Bitmap.createBitmap(31, 47, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                return ByteArrayOutputStream()
                    .also {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        bitmap.recycle()
                    }
                    .toByteArray()
            }
            val originals = listOf(image(Color.RED), image(Color.GREEN), image(Color.BLUE))
            val references = originals.map { vm.photoStore.attach(ByteArrayInputStream(it)) }
            val record =
                ProgressPhotoRecord(
                    date = LocalDate.of(2026, 10, 19),
                    photos = PhotoPosition.entries.zip(references).toMap(),
                )
            vm.saveProgressPhotos(record)
            val shared =
                ProgressPhotoRecord(
                    date = record.date,
                    photos = mapOf(PhotoPosition.FRONT to references.first()),
                )
            vm.saveProgressPhotos(shared)
            val saved = vm.data.value
            val reopened = WorkoutViewModel(context.applicationContext as Application)
            assertEquals(saved, reopened.data.value)
            references.zip(originals).forEach { (reference, bytes) ->
                assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
            }
            val backup = ByteArrayOutputStream().also { reopened.export(it) }.toString("UTF-8")
            reopened.photoStore.prepare(ByteArrayInputStream(backup.toByteArray())).close()
            assertEquals(saved, reopened.data.value)
            fun rejected(transform: (JSONObject) -> Unit) {
                val invalid = JSONObject(backup).apply(transform).toString()
                assertThrows(Exception::class.java) { reopened.restore(invalid) }
                assertEquals(saved, reopened.data.value)
                assertEquals(saved, WorkoutStore(context).load())
                references.zip(originals).forEach { (reference, bytes) ->
                    assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
                }
            }
            rejected { it.getJSONObject("photoAssets").remove(references.first()) }
            rejected { it.getJSONObject("photoAssets").put(references.first(), "bm90IGEgcGhvdG8=") }
            rejected {
                it.getJSONArray("progressPhotos").getJSONObject(0).put("date", "2026-02-30")
            }
            reopened.deleteProgressPhotos(record.id)
            assertTrue(reopened.photoStore.file(references.first()).exists())
            assertFalse(reopened.photoStore.file(references[1]).exists())
            assertFalse(reopened.photoStore.file(references[2]).exists())
            reopened.deleteProgressPhotos(shared.id)
            assertEquals(training, reopened.data.value)
            assertFalse(reopened.photoStore.file(references.first()).exists())
            // Restore onto an empty store, not onto retained records or attachment files.
            reopened.restore(DataCodec.encode(AppData()))
            assertTrue(reopened.data.value.preferences.dismissedTutorials.isEmpty())
            reopened.restore(backup)
            assertEquals(saved, reopened.data.value)
            assertEquals(saved, WorkoutViewModel(context.applicationContext as Application).data.value)
            assertFalse(reopened.data.value.preferences.dataVisibility.isVisible("sidePhoto"))
            assertTrue("bodyCharts" in reopened.data.value.preferences.dismissedTutorials)
            references.zip(originals).forEach { (reference, bytes) ->
                assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
            }
            val edited =
                record.copy(
                    date = record.date.minusDays(2),
                    photos = record.photos - PhotoPosition.SIDE,
                )
            reopened.saveProgressPhotos(edited)
            assertEquals(listOf(edited, shared), reopened.data.value.progressPhotos)
            assertFalse(reopened.photoStore.file(references[1]).exists())
            val legacy =
                JSONObject(DataCodec.encode(training)).apply { remove("progressPhotos") }.toString()
            reopened.restore(legacy)
            assertEquals(training, reopened.data.value)
            assertTrue(references.none { reopened.photoStore.file(it).exists() })
            assertThrows(IllegalArgumentException::class.java) {
                reopened.photoStore.attach(ByteArrayInputStream("not a photo".toByteArray()))
            }
        } finally {
            vm.restore(originalBackup)
        }
    }
}
