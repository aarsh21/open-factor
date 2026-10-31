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
    private fun withPhotoStore(test: (ProgressPhotoStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "photo-storage-test-${newId()}").apply { check(mkdirs()) }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir() = directory
        }
        try { test(ProgressPhotoStore(isolated)) }
        finally { directory.deleteRecursively() }
    }

    @Test fun exportRejectsDamagedOriginalsAndReattachingRepairsTheSameReference() = withPhotoStore { store ->
        val bitmap = Bitmap.createBitmap(29, 43, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val reference = store.attach(bytes.inputStream())
        val data = AppData(progressPhotos = listOf(ProgressPhotoRecord(photos = mapOf(PhotoPosition.FRONT to reference))))
        val damaged = bytes + byteArrayOf(1)
        store.file(reference).writeBytes(damaged)
        assertThrows(IllegalArgumentException::class.java) { store.export(data, ByteArrayOutputStream()) }
        assertArrayEquals(damaged, store.file(reference).readBytes())
        assertEquals(reference, store.attach(bytes.inputStream()))
        assertArrayEquals(bytes, store.file(reference).readBytes())
        val backup = ByteArrayOutputStream().also { store.export(data, it) }.toByteArray()
        store.prepare(backup.inputStream()).use { prepared ->
            assertEquals(data, prepared.data)
            prepared.install()
            assertArrayEquals(bytes, store.file(reference).readBytes())
        }
        store.file(reference).writeBytes(byteArrayOf())
        assertThrows(IllegalArgumentException::class.java) { store.export(data, ByteArrayOutputStream()) }
    }

    @Test fun malformedUtf8BackupIsRejectedInsteadOfChangingSavedText() = withPhotoStore { store ->
        val prefix = "{\"schemaVersion\":1,\"preferences\":{\"displayName\":\"".toByteArray()
        val suffix = "\"}}".toByteArray()
        val malformed = prefix + byteArrayOf(0xc3.toByte(), 0x28) + suffix
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { store.prepare(malformed.inputStream()) }
        val valid = DataCodec.encode(AppData(preferences = Preferences(displayName = "Renée · 日本語"))).toByteArray()
        store.prepare(valid.inputStream()).use { assertEquals("Renée · 日本語", it.data.preferences.displayName) }
    }

    @Test
    fun cameraCapturesUsePrivateFullResolutionUrisAndCleanCancelledOrInvalidOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ProgressPhotoStore(context)
        val cancelled = store.createCapture()
        val cancelledUri = store.captureUri(cancelled)
        assertEquals("content", cancelledUri.scheme)
        assertEquals("${context.packageName}.files", cancelledUri.authority)
        store.discardCapture(cancelled)
        assertFalse(java.io.File(context.cacheDir, "photo-captures/$cancelled").exists())
        val empty = store.createCapture()
        assertThrows(IllegalArgumentException::class.java) { store.attachCapture(empty) }
        assertFalse(java.io.File(context.cacheDir, "photo-captures/$empty").exists())
        val name = store.createCapture()
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        bitmap.recycle()
        context.contentResolver.openOutputStream(store.captureUri(name))!!.use { it.write(bytes) }
        val reference = store.attachCapture(name)
        try {
            assertArrayEquals(bytes, store.file(reference).readBytes())
            assertFalse(java.io.File(context.cacheDir, "photo-captures/$name").exists())
            assertThrows(IllegalArgumentException::class.java) { store.captureUri("../exports/private.jpg") }
        } finally { store.file(reference).delete() }
    }

    @Test
    fun interruptedCameraImportCanReplayFromRetainedStageBeforeReferenceDelivery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstStore = ProgressPhotoStore(context)
        val name = firstStore.createCapture()
        val stage = java.io.File(context.cacheDir, "photo-captures/$name")
        val bitmap = Bitmap.createBitmap(79, 113, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it) }.toByteArray()
        bitmap.recycle()
        context.contentResolver.openOutputStream(firstStore.captureUri(name))!!.use { it.write(bytes) }
        val undeliveredReference = firstStore.importCapture(name)
        try {
            assertTrue("An imported but undelivered camera result must retain its source", stage.exists())
            // A recreated editor may clean an orphaned import before replaying its saveable
            // pendingCapture + captureReady result. The original camera output is independent.
            firstStore.file(undeliveredReference).delete()
            val recreatedStore = ProgressPhotoStore(context)
            val deliveredReference = recreatedStore.importCapture(name)
            assertEquals(undeliveredReference, deliveredReference)
            assertArrayEquals(bytes, recreatedStore.file(deliveredReference).readBytes())
            assertArrayEquals(bytes, stage.readBytes())
            recreatedStore.discardCapture(name)
            assertFalse(stage.exists())
            assertArrayEquals(bytes, recreatedStore.file(deliveredReference).readBytes())
        } finally {
            firstStore.discardCapture(name)
            firstStore.file(undeliveredReference).delete()
        }
    }

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
            rejected { it.put("history", JSONObject()) }
            rejected { it.put("active", "damaged session") }
            rejected { it.getJSONObject("preferences").put("dashboard", "damaged settings") }
            rejected { it.getJSONObject("photoAssets").remove(references.first()) }
            rejected { it.getJSONObject("photoAssets").put(references.first(), "bm90IGEgcGhvdG8=") }
            rejected {
                it.getJSONArray("progressPhotos").getJSONObject(0).put("date", "2026-02-30")
            }
            fun refreshCheckpointAndClean() {
                assertEquals(reopened.data.value, WorkoutStore(context).load())
                reopened.cleanPhotos()
            }
            reopened.deleteProgressPhotos(record.id)
            // The startup checkpoint must remain photo-complete until a later load advances it.
            assertTrue(references.all { reopened.photoStore.file(it).exists() })
            refreshCheckpointAndClean()
            assertTrue(reopened.photoStore.file(references.first()).exists())
            assertFalse(reopened.photoStore.file(references[1]).exists())
            assertFalse(reopened.photoStore.file(references[2]).exists())
            reopened.deleteProgressPhotos(shared.id)
            assertEquals(training, reopened.data.value)
            assertTrue(reopened.photoStore.file(references.first()).exists())
            refreshCheckpointAndClean()
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
            assertTrue(reopened.photoStore.file(references[1]).exists())
            refreshCheckpointAndClean()
            assertFalse(reopened.photoStore.file(references[1]).exists())
            val legacy =
                JSONObject(DataCodec.encode(training)).apply { remove("progressPhotos") }.toString()
            reopened.restore(legacy)
            assertEquals(training, reopened.data.value)
            refreshCheckpointAndClean()
            assertTrue(references.none { reopened.photoStore.file(it).exists() })
            assertThrows(IllegalArgumentException::class.java) {
                reopened.photoStore.attach(ByteArrayInputStream("not a photo".toByteArray()))
            }
        } finally {
            vm.restore(originalBackup)
        }
    }
}
