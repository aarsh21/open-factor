package org.openfactor.app

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BackupExportConcurrencyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withPhoto(test: (WorkoutViewModel, ProgressPhotoRecord) -> Unit) {
        val store = WorkoutStore(context)
        val before = store.load()
        val protection = ProgressPhotoStore(context).retainForExport(before)
        val models = ViewModelStore()
        try {
            store.save(before.copy(active = Workout(name = "Export fixture", notes = "Synthetic note ".repeat(2000)),
                preferences = before.preferences.copy(displayName = "Export snapshot"), progressPhotos = emptyList()))
            val vm = WorkoutViewModel(context.applicationContext as Application).also { models.put("export-fixture", it) }
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val reference = vm.photoStore.attach(ByteArrayInputStream(bytes))
            val record = ProgressPhotoRecord(photos = mapOf(PhotoPosition.FRONT to reference))
            vm.commands.saveProgressPhotos(record)
            test(vm, record)
        } finally {
            models.clear()
            store.save(before)
            protection.close()
        }
    }

    @Test fun slowDocumentProviderDoesNotBlockCommandsAndItsPhotoSnapshotRemainsComplete() = withPhoto { vm, record ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val bytes = ByteArrayOutputStream()
        val output = object : OutputStream() {
            var first = true
            private fun pause() {
                if (!first) return
                first = false
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Test writer was not released" }
            }
            override fun write(value: Int) { pause(); bytes.write(value) }
            override fun write(data: ByteArray, offset: Int, length: Int) { pause(); bytes.write(data, offset, length) }
        }
        val export = executor.submit { vm.export(output) }
        try {
            assertTrue("The provider is actually blocked", entered.await(5, TimeUnit.SECONDS))
            val mutation = executor.submit {
                vm.commands.deleteProgressPhotos(record.id)
                vm.commands.settings { it.copy(displayName = "Still responsive") }
            }
            mutation.get(3, TimeUnit.SECONDS)
            assertEquals("Still responsive", vm.data.value.preferences.displayName)
            assertTrue("The export lease pins an original after its metadata was removed", vm.photoStore.file(record.photos.values.single()).isFile)
            release.countDown()
            export.get(5, TimeUnit.SECONDS)
            vm.photoStore.prepare(ByteArrayInputStream(bytes.toByteArray())).use { backup ->
                assertEquals(listOf(record), backup.data.progressPhotos)
                assertNotEquals("Still responsive", backup.data.preferences.displayName)
            }
            vm.cleanPhotos()
            assertFalse("Closing the lease makes the removed attachment collectible", vm.photoStore.file(record.photos.values.single()).exists())
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test fun failedExportReleasesItsPhotoRetention() = withPhoto { vm, record ->
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Synthetic provider failure") }
            override fun write(data: ByteArray, offset: Int, length: Int) { throw IOException("Synthetic provider failure") }
        }
        assertThrows(IOException::class.java) { vm.export(output) }
        vm.commands.deleteProgressPhotos(record.id)
        vm.cleanPhotos()
        assertFalse(vm.photoStore.file(record.photos.values.single()).exists())
    }
}
