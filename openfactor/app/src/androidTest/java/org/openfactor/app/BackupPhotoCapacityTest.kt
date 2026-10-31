package org.openfactor.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Photo-heavy checks use disk streams, not a giant in-memory backup or the real app store. */
@RunWith(AndroidJUnit4::class)
class BackupPhotoCapacityTest {
    @Test
    fun eightLargeOriginalsSurviveAFileBackedBackupLargerThanOneHundredMiB() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "backup-capacity-${newId()}").apply { check(mkdirs()) }
        fun context(name: String): Context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = File(root, name).apply { check(isDirectory || mkdirs()) }
        }
        val started = System.nanoTime()
        try {
            val source = ProgressPhotoStore(context("source"))
            val originals = (0 until 8).map { index ->
                val original = File(root, "original-$index.png")
                createLargeImage(original, index)
                assertTrue("Fixture must contain at least 12 MiB of actual image bytes", original.length() >= 12L * 1024 * 1024)
                assertTrue(original.length() <= ProgressPhotoStore.MAX_PHOTO_BYTES)
                val reference = original.inputStream().use { source.attach(it) }
                assertEquals(sha256(original), reference)
                reference to original
            }
            val data = AppData(
                history = listOf(Workout(id = "capacity-workout", name = "Photo capacity workout", startedAt = 1_700_000_000_000L, finishedAt = 1_700_003_600_000L,
                    exercises = listOf(SessionExercise(id = "capacity-entry", exerciseId = "bench", sets = listOf(LoggedSet(id = "capacity-set", weightKg = 87.125, reps = 9, completed = true)))))),
                scaleWeights = List(365) { index -> ScaleWeightRecord(id = "capacity-weight-$index", date = LocalDate.of(2024, 1, 1).plusDays(index.toLong()), weightKg = 70.125 + index / 1000.0) },
                progressPhotos = originals.mapIndexed { index, (reference, _) ->
                    ProgressPhotoRecord(id = "capacity-photo-$index", date = LocalDate.of(2024, 1, 1).plusDays(index.toLong()), photos = mapOf(PhotoPosition.FRONT to reference))
                },
            )
            val backup = File(root, "photo-heavy-backup.json")
            backup.outputStream().use { source.export(data, it) }
            assertTrue("Export must exceed 100 MiB", backup.length() > 100L * 1024 * 1024)
            // No source attachment files remain: restoration must rely entirely on the backup.
            assertTrue(File(root, "source").deleteRecursively())
            val destination = ProgressPhotoStore(context("destination"))
            assertTrue(originals.none { destination.file(it.first).exists() })
            backup.inputStream().use { input ->
                destination.prepare(input).use { prepared ->
                    assertTrue("All metadata must survive", data == prepared.data)
                    prepared.install()
                }
            }
            val reopened = ProgressPhotoStore(context("destination"))
            originals.forEach { (reference, original) ->
                val restored = reopened.file(reference)
                assertEquals(original.length(), restored.length())
                assertEquals(reference, sha256(restored))
                // Compare every byte without allocating two ~16 MiB arrays per image.
                original.inputStream().buffered().use { expected ->
                    restored.inputStream().buffered().use { actual ->
                        val expectedBytes = ByteArray(8192)
                        val actualBytes = ByteArray(8192)
                        while (true) {
                            val count = expected.read(expectedBytes)
                            if (count < 0) {
                                assertEquals(-1, actual.read())
                                break
                            }
                            var offset = 0
                            while (offset < count) {
                                val read = actual.read(actualBytes, offset, count - offset)
                                assertTrue("Restored original ended early", read > 0)
                                offset += read
                            }
                            assertTrue("Original byte mismatch", (0 until count).all { expectedBytes[it] == actualBytes[it] })
                        }
                    }
                }
            }
            assertTrue(File(root, "destination/photo-restore").listFiles().orEmpty().isEmpty())
            Log.i("BackupPhotoCapacity", "images=${originals.size} originalBytes=${originals.sumOf { it.second.length() }} backupBytes=${backup.length()} elapsedMs=${(System.nanoTime() - started) / 1_000_000} maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun attachmentOverTwentyMiBIsRejectedWithoutInstallingOrRetainingAnOriginal() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "backup-photo-limit-${newId()}").apply { check(mkdirs()) }
        val isolated = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        try {
            val store = ProgressPhotoStore(isolated)
            val overLimit = object : InputStream() {
                private var remaining = ProgressPhotoStore.MAX_PHOTO_BYTES.toLong() + 1
                override fun read(): Int = if (remaining-- > 0) 1 else -1
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (remaining <= 0) return -1
                    val count = minOf(length.toLong(), remaining).toInt()
                    buffer.fill(1, offset, offset + count)
                    remaining -= count
                    return count
                }
            }
            val failure = assertThrows(IllegalArgumentException::class.java) { store.attach(overLimit) }
            assertTrue(failure.message.orEmpty().contains("20 MiB"))
            assertTrue(File(root, "progress-photos").listFiles().orEmpty().isEmpty())
            assertTrue(File(root, "photo-restore").listFiles().orEmpty().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun photoExportUsesBulkOutputRatherThanOneProviderWritePerEncodedByte() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "backup-output-calls-${newId()}").apply { check(mkdirs()) }
        val isolated = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        try {
            val original = File(root, "original.png")
            createLargeImage(original, 42, edge = 512)
            val store = ProgressPhotoStore(isolated)
            val reference = original.inputStream().use { store.attach(it) }
            var singleByteWrites = 0L
            var bulkWrites = 0L
            var totalBytes = 0L
            val output = object : OutputStream() {
                override fun write(value: Int) { singleByteWrites++; totalBytes++ }
                override fun write(bytes: ByteArray, offset: Int, length: Int) { bulkWrites++; totalBytes += length }
            }
            store.export(AppData(progressPhotos = listOf(ProgressPhotoRecord(photos = mapOf(PhotoPosition.FRONT to reference)))), output)
            assertTrue("Export must actually write the complete encoded image", totalBytes > original.length())
            Log.i("BackupPhotoCapacity", "singleByteWrites=$singleByteWrites bulkWrites=$bulkWrites totalBytes=$totalBytes")
            // A small amount of JSON punctuation may use write(Int); encoded photos must use chunks.
            assertTrue("Encoded image caused $singleByteWrites single-byte provider writes; expected fewer than 1024", singleByteWrites < 1024)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createLargeImage(file: File, seed: Int, edge: Int = 2048) {
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        try {
            val random = Random(9300 + seed)
            val row = IntArray(bitmap.width)
            repeat(bitmap.height) { y ->
                for (x in row.indices) row[x] = random.nextInt()
                bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
