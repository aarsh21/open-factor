package org.openfactor.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.util.JsonReader
import android.util.JsonWriter
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No app preferences, original attachments, or camera captures are shared with the real user. */
@RunWith(AndroidJUnit4::class)
class BackupFailureSafetyTest {
    private class Fixture {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "backup-failure-test-${newId()}"
        val root = File(base.cacheDir, prefix).apply { check(mkdirs()) }
        val preferenceNames = mutableSetOf<String>()
        var failPrimaryCommit = false
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            // Notification sync is best-effort in production. Do not cancel a real user's
            // workout notification or alarm as a side effect of an isolated ViewModel restore.
            override fun getSystemService(name: String): Any? =
                if (name == Context.NOTIFICATION_SERVICE || name == Context.ALARM_SERVICE) null
                else super.getSystemService(name)
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = "$prefix-$name"
                synchronized(preferenceNames) { preferenceNames += isolatedName }
                val real = base.getSharedPreferences(isolatedName, mode)
                return object : SharedPreferences by real {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = real.edit()
                        return object : SharedPreferences.Editor by editor {
                            // Fluent calls must keep returning this wrapper, not the real editor.
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }
                            override fun commit(): Boolean {
                                if (name == "open_factor" && failPrimaryCommit) {
                                    throw IOException("Injected primary preference write failure")
                                }
                                return editor.commit()
                            }
                        }
                    }
                }
            }
        }
        val application = object : Application() {
            init { attachBaseContext(context) }
            override fun getApplicationContext(): Context = this
        }
        val models = ViewModelStore()
        val vm = WorkoutViewModel(application).also { models.put("fixture", it) }
        val store get() = vm.photoStore
        val staging get() = File(context.noBackupFilesDir, "photo-restore")
        val photoDirectory get() = File(context.noBackupFilesDir, "progress-photos")

        fun close() {
            failPrimaryCommit = false
            models.clear()
            synchronized(preferenceNames) { preferenceNames.forEach { base.deleteSharedPreferences(it) } }
            root.deleteRecursively()
        }

        fun seed(): AppData {
            val references = listOf(Color.RED, Color.GREEN, Color.BLUE).map { store.attach(image(it).inputStream()) }
            val first = ProgressPhotoRecord(
                id = "prior-photo", date = LocalDate.of(2026, 1, 2),
                photos = PhotoPosition.entries.zip(references).toMap(), notes = "Keep every original",
            )
            val prior = vm.data.value.copy(
                history = listOf(Workout(id = "prior-workout", name = "Keep history", startedAt = 1_700_000_000_000L,
                    finishedAt = 1_700_000_060_000L, exercises = listOf(SessionExercise(id = "prior-entry", exerciseId = "bench",
                        sets = listOf(LoggedSet(id = "prior-set", weightKg = 42.5, reps = 7, completed = true)))))),
                routines = listOf(Routine(id = "prior-routine", name = "Keep template", exerciseIds = listOf("bench"))),
                favorites = setOf("bench"),
                preferences = vm.data.value.preferences.copy(displayName = "Keep settings · 日本語", pounds = true),
                progressPhotos = listOf(first, first.copy(id = "shared-photo", photos = mapOf(PhotoPosition.FRONT to references.first()))),
            )
            store.prepare(backup(prior).inputStream()).use(vm::restore)
            // Establish a startup checkpoint before taking the byte-for-byte rejection snapshot.
            assertEquals(prior, WorkoutStore(context).load())
            // Even an attachment not yet delivered to its editor must survive a rejected restore.
            store.attach(image(Color.YELLOW).inputStream())
            return prior
        }

        fun backup(data: AppData = vm.data.value): ByteArray =
            ByteArrayOutputStream().also { store.export(data, it) }.toByteArray()

        fun preferences(): Map<String, Map<String, *>> = synchronized(preferenceNames) {
            preferenceNames.associateWith { base.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        }

        fun pictures(): Map<String, ByteArray> = photoDirectory.listFiles().orEmpty().associate { it.name to it.readBytes() }

        fun assertNoStages() {
            assertTrue("Restore staging directory must be empty", staging.listFiles().orEmpty().isEmpty())
        }

        fun rejected(input: InputStream, expected: Class<out Exception> = Exception::class.java): Exception {
            val before = vm.data.value
            val preferences = preferences()
            val originals = pictures()
            val failure = assertThrows(expected) {
                store.prepare(input).use(vm::restore)
            }
            assertEquals("Rejected restore published different data", before, vm.data.value)
            assertEquals("Rejected restore rewrote preferences/checkpoint", preferences, preferences())
            assertEquals("Rejected restore changed stored data", before, WorkoutStore(context).load())
            assertEquals("Rejected restore changed attachment inventory", originals.keys, pictures().keys)
            originals.forEach { (reference, bytes) -> assertArrayEquals("Changed original $reference", bytes, store.file(reference).readBytes()) }
            assertNoStages()
            return failure
        }

        fun rejected(text: String) = rejected(text.byteInputStream())
    }

    private fun withFixture(test: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { test(fixture) } finally { fixture.close() }
    }

    @Test fun missingCorruptAndUnreferencedPhotoAssetsRejectWithoutTouchingPriorData() = withFixture { f ->
        f.seed()
        val backup = f.backup().toString(Charsets.UTF_8)
        val first = f.vm.data.value.photoReferences().sorted().first()
        val variants: List<(JSONObject) -> Unit> = listOf(
            { it.remove("photoAssets") },
            { it.getJSONObject("photoAssets").remove(first) },
            { it.getJSONObject("photoAssets").put(first, "%%%not-base64%%") },
            { it.getJSONObject("photoAssets").put(first, Base64.getEncoder().encodeToString(image(Color.MAGENTA))) },
            { it.getJSONObject("photoAssets").put("../outside", "AA==") },
            { it.getJSONObject("photoAssets").put(first.uppercase(), "AA==") },
            { val bytes = image(Color.MAGENTA); it.getJSONObject("photoAssets").put(hash(bytes), Base64.getEncoder().encodeToString(bytes)) },
            { it.put("photoAssets", JSONArray()) },
        )
        variants.forEach { mutation -> f.rejected(JSONObject(backup).apply(mutation).toString()) }
        // Matching checksums do not make arbitrary bytes (or a PNG header alone) valid images.
        listOf("not an image".toByteArray(), image(Color.MAGENTA).copyOf(24)).forEach { bytes ->
            val reference = hash(bytes)
            val document = JSONObject(backup)
            document.getJSONArray("progressPhotos").getJSONObject(0).getJSONObject("photos").put("side", reference)
            document.getJSONObject("photoAssets").put(reference, Base64.getEncoder().encodeToString(bytes))
            f.rejected(document.toString())
        }
    }

    @Test fun truncatedBackupsRejectAndRemoveAlreadyVerifiedStages() = withFixture { f ->
        f.seed()
        val original = f.backup().toString(Charsets.UTF_8)
        // Put assets first so truncations near the tail have already written verified files.
        val assets = JSONObject(original).getJSONObject("photoAssets")
        val metadata = JSONObject(original).apply { remove("photoAssets") }.toString()
        val text = "{\"photoAssets\":$assets," + metadata.drop(1)
        val cuts = (listOf(0, 1, 7, text.length / 2, text.length - 1, text.length - 2) +
            text.indices.filter { text[it] == ',' }.takeLast(6)).distinct()
        cuts.forEach { cut -> f.rejected(text.take(cut)) }
    }

    @Test fun duplicateRootNestedAssetAndPhotoRecordFieldsAreRejected() = withFixture { f ->
        f.seed()
        val reference = f.vm.data.value.photoReferences().first()
        val encoded = Base64.getEncoder().encodeToString(f.store.file(reference).readBytes())
        val metadata = DataCodec.encode(f.vm.data.value, pretty = false)
        val duplicates = listOf(
            "{\"schemaVersion\":1,\"schemaVersion\":1}",
            "{\"schemaVersion\":1,\"preferences\":{\"displayName\":\"one\",\"displayName\":\"two\"}}",
            "{\"photoAssets\":{\"$reference\":\"$encoded\",\"$reference\":\"$encoded\"},\"schemaVersion\":1}",
            "{\"schemaVersion\":1,\"progressPhotos\":[{\"id\":\"p\",\"date\":\"2026-01-01\",\"photos\":{\"front\":\"$reference\",\"front\":\"$reference\"}}]}",
        )
        duplicates.forEach { f.rejected(it) }
        val duplicateRecords = JSONObject(metadata)
        val records = duplicateRecords.getJSONArray("progressPhotos")
        records.put(records.getJSONObject(0))
        f.rejected(duplicateRecords.toString())
    }

    @Test fun invalidVersionsContainersAndInternalSnapshotsCannotReplaceUserData() = withFixture { f ->
        f.seed()
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.remove("schemaVersion") },
            { it.put("schemaVersion", 0) },
            { it.put("schemaVersion", 999_999) },
            { it.put("schemaVersion", JSONObject.NULL) },
            { it.put("schemaVersion", JSONObject()) },
            { it.put("storageHistory", "a".repeat(64)) },
            { it.put("history", JSONObject()) },
            { it.put("active", "not a workout") },
            { it.getJSONObject("preferences").put("dashboard", JSONArray()) },
            { it.getJSONArray("progressPhotos").getJSONObject(0).put("date", "2026-02-30") },
        )
        val backup = f.backup().toString(Charsets.UTF_8)
        mutations.forEach { f.rejected(JSONObject(backup).apply(it).toString()) }
    }

    @Test fun fractionalSchemaVersionCannotSilentlyRestoreAnEmptyDataset() = withFixture { f ->
        f.seed()
        // Regression expectation: 1.5 is not schema version 1. If coerced with optInt(),
        // this otherwise empty document can replace every stored record and original photo.
        f.rejected("{\"schemaVersion\":1.5}")
    }

    @Test fun nonIntegerAndOverflowingVersionsRejectWithoutTouchingPriorData() = withFixture { f ->
        f.seed()
        listOf("1.0", "1e0", "\"1\"", "\"1.5\"", "true", "4294967297", "-4294967295").forEach { version ->
            f.rejected("{\"schemaVersion\":$version}")
        }
    }

    @Test fun checkpointPicturesSurviveDeletionAndAreCleanedAfterCheckpointAdvances() = withFixture { f ->
        val prior = f.seed()
        val originals = prior.photoReferences().associateWith { f.store.file(it).readBytes() }
        prior.progressPhotos.forEach { f.vm.deleteProgressPhotos(it.id) }
        assertTrue(f.vm.data.value.progressPhotos.isEmpty())
        originals.forEach { (reference, bytes) -> assertArrayEquals(bytes, f.store.file(reference).readBytes()) }
        // Once a new startup checkpoint no longer references these pictures, they may be pruned.
        assertEquals(f.vm.data.value, WorkoutStore(f.context).load())
        f.vm.cleanPhotos()
        assertTrue(f.pictures().isEmpty())
    }

    @Test fun unreadableCheckpointsPreventDestructivePhotoCleanup() = withFixture { f ->
        val prior = f.seed()
        val originals = f.pictures()
        val replacement = AppData(gyms = prior.gyms)
        val recovery = f.context.getSharedPreferences("open_factor_recovery", Context.MODE_PRIVATE)
        listOf("{damaged checkpoint", "{\"schemaVersion\":999999}",
            "{\"schemaVersion\":1,\"progressPhotos\":{}}").forEach { checkpoint ->
            assertTrue(recovery.edit().putString("data_last_good", checkpoint).commit())
            f.vm.restore(DataCodec.encode(replacement))
            assertEquals(originals.keys, f.pictures().keys)
            originals.forEach { (reference, bytes) -> assertArrayEquals(bytes, f.store.file(reference).readBytes()) }
        }
        assertEquals(replacement, WorkoutStore(f.context).load())
        f.vm.cleanPhotos()
        assertTrue(f.pictures().isEmpty())
    }

    @Test fun preservedRecoveryPayloadsProtectPicturesEvenAfterCheckpointRefreshAndDismissal() = withFixture { f ->
        val prior = f.seed()
        val originals = f.pictures()
        assertTrue(f.context.getSharedPreferences("open_factor", Context.MODE_PRIVATE)
            .edit().putString("data", "{damaged primary").commit())
        val recovering = WorkoutStore(f.context)
        assertEquals(prior, recovering.load())
        assertTrue(recovering.hasRecoveryPayloads())
        val replacement = AppData(gyms = prior.gyms)
        f.vm.restore(DataCodec.encode(replacement))
        assertEquals(replacement, WorkoutStore(f.context).load())
        assertTrue(recovering.dismissRecovery())
        f.vm.cleanPhotos()
        assertEquals(originals.keys, f.pictures().keys)
        originals.forEach { (reference, bytes) -> assertArrayEquals(bytes, f.store.file(reference).readBytes()) }
    }

    @Test fun startupRecoveryCheckpointRemainsPhotoCompleteAfterAReplacingRestore() = withFixture { f ->
        val prior = f.seed() // Also establishes the startup recovery checkpoint.
        val replacement = AppData(gyms = prior.gyms, preferences = prior.preferences.copy(displayName = "Replacement"))
        f.vm.restore(DataCodec.encode(replacement))
        assertEquals(replacement, f.vm.data.value)
        // Reproduce a subsequent interrupted/damaged primary write without touching real prefs.
        assertTrue(f.context.getSharedPreferences("open_factor", Context.MODE_PRIVATE)
            .edit().putString("data", "{interrupted primary").commit())
        val recovered = WorkoutStore(f.context).load()
        assertEquals("Startup checkpoint should recover the prior dataset", prior, recovered)
        // Recovery metadata alone is insufficient if restore cleanup deleted its original photos.
        f.store.requireFiles(recovered)
        f.assertNoStages()
    }

    @Test fun trailingDataRejectsEvenAfterAllPhotosHaveBeenStaged() = withFixture { f ->
        f.seed()
        val text = f.backup().toString(Charsets.UTF_8)
        listOf("{}", "[]", "null", "garbage", "\u0000", "\n{\"schemaVersion\":1}").forEach { f.rejected(text + it) }
        // Legal whitespace is not confused with a second JSON document.
        f.store.prepare((text + " \r\n\t").byteInputStream()).use { assertEquals(f.vm.data.value, it.data) }
        f.assertNoStages()
    }

    @Test fun deepMetadataAndOversizedNumericTokensRejectAfterStagingPhotos() = withFixture { f ->
        f.seed()
        val assets = JSONObject(f.backup().toString(Charsets.UTF_8)).getJSONObject("photoAssets")
        val prefix = "{\"photoAssets\":$assets,\"schemaVersion\":1,\"unknown\":"
        val deep = prefix + "[".repeat(65) + "0" + "]".repeat(65) + "}"
        assertTrue(f.rejected(deep).message.orEmpty().contains("nesting is too deep"))
        val token = prefix + "1".repeat(1025) + "}"
        assertTrue(f.rejected(token).message.orEmpty().contains("token is too large"))
    }

    @Test fun oversizedSingleStringIsRejectedWithoutAllocatingALargeFixture() = withFixture { f ->
        f.seed()
        val input = RepeatedStringInput("{\"schemaVersion\":1,\"unknown\":\"", 28 * 1024 * 1024 + 1, "\"}")
        val failure = f.rejected(input, IllegalArgumentException::class.java)
        assertTrue(failure.message.orEmpty().contains("string is too large"))
        assertTrue("Preparation owns and closes its input", input.closed)
    }

    @Test fun metadataReaderAndWriterEnforceSmallNodeAndStringBudgets() {
        // Exercise the production guards with a small budget rather than allocating 64 MiB of nodes.
        listOf("[0,0,0,0,0,0,0,0,0]", "[\"${"x".repeat(513)}\"]").forEach { value ->
            JsonReader(StringReader(value)).use { reader ->
                assertThrows(IllegalArgumentException::class.java) { BackupMetadataReader(reader, maxBudget = 512).value() }
            }
        }
        listOf<Any>(JSONArray(List(9) { 0 }), "x".repeat(513)).forEach { value ->
            val writer = JsonWriter(StringWriter())
            writer.beginObject()
            assertThrows(IllegalArgumentException::class.java) { BackupMetadataWriter(writer, maxBudget = 512).field("value", value) }
            // An exhausted budget leaves an incomplete document. The in-memory writer owns no
            // resource, and close() would throw a second exception for that incomplete document.
        }
    }

    @Test fun providerReadFailuresPreserveAllPicturesAndPersistedRecords() = withFixture { f ->
        f.seed()
        val bytes = f.backup()
        listOf(0, bytes.size / 2, bytes.size - 1, bytes.size).distinct().forEach { failAt ->
            val input = FailingInput(bytes, failAt)
            f.rejected(input, IOException::class.java)
            assertTrue("Failed import must close its provider input", input.closed)
        }
    }

    @Test fun providerWriteFailuresAreReportedWithoutClosingOutputOrChangingSourceData() = withFixture { f ->
        val prior = f.seed()
        val complete = f.backup()
        val assets = complete.toString(Charsets.UTF_8).indexOf("\"photoAssets\"")
        listOf(0, 32, assets + 100, complete.size - 1).distinct().forEach { failAt ->
            val preferences = f.preferences()
            val originals = f.pictures()
            val output = FailingOutput(failAt)
            assertThrows(IOException::class.java) { f.vm.export(output) }
            assertFalse("Export output belongs to the caller", output.closed)
            assertEquals(prior, f.vm.data.value)
            assertEquals(preferences, f.preferences())
            assertEquals(originals.keys, f.pictures().keys)
            originals.forEach { (reference, bytes) -> assertArrayEquals(bytes, f.store.file(reference).readBytes()) }
            f.assertNoStages()
        }
        val output = FailingOutput(Int.MAX_VALUE, failFlush = true)
        assertThrows(IOException::class.java) { f.vm.export(output) }
        assertFalse(output.closed)
        f.store.prepare(f.backup().inputStream()).use { assertEquals(prior, it.data) }
    }

    @Test fun failedPhotoInstallationPreservesPriorStateAndCleansItsStagingDirectory() = withFixture { f ->
        val prior = f.seed()
        val originals = f.pictures()
        val preferences = f.preferences()
        val bytes = image(Color.MAGENTA)
        val reference = hash(bytes)
        val replacement = prior.copy(progressPhotos = listOf(ProgressPhotoRecord(
            id = "new-photo", date = LocalDate.of(2026, 2, 3), photos = mapOf(PhotoPosition.FRONT to reference),
        )))
        val document = JSONObject(DataCodec.encode(replacement)).put("photoAssets",
            JSONObject().put(reference, Base64.getEncoder().encodeToString(bytes)))
        // A file cannot be renamed over a directory. Unlike permissions/free-space tricks,
        // this deterministically fails installation even on emulators running as root.
        val blocker = f.store.file(reference)
        assertTrue(blocker.mkdir())
        try {
            assertThrows(IllegalStateException::class.java) {
                f.store.prepare(document.toString().byteInputStream()).use(f.vm::restore)
            }
            assertEquals(prior, f.vm.data.value)
            assertEquals(preferences, f.preferences())
            assertEquals(prior, WorkoutStore(f.context).load())
            originals.forEach { (oldReference, oldBytes) -> assertArrayEquals(oldBytes, f.store.file(oldReference).readBytes()) }
            f.assertNoStages()
        } finally { assertTrue(blocker.delete()) }
        assertEquals(originals.keys, f.pictures().keys)
    }

    @Test fun failedMetadataCommitAfterPhotoInstallKeepsPriorDataAndOriginals() = withFixture { f ->
        f.seed()
        val prior = f.vm.data.value
        // Reuse identical assets: installation may rewrite a hash, but must not damage prior bytes.
        val replacement = prior.copy(history = emptyList(), preferences = prior.preferences.copy(displayName = "Must not publish"))
        val bytes = f.backup(replacement)
        f.failPrimaryCommit = true
        try { f.rejected(bytes.inputStream(), IOException::class.java) }
        finally { f.failPrimaryCommit = false }
    }

    @Test fun missingAndEmptyOriginalsAbortExportWithoutMutatingRemainingFiles() = withFixture { f ->
        f.seed()
        val reference = f.vm.data.value.photoReferences().first()
        val file = f.store.file(reference)
        val original = file.readBytes()
        try {
            assertTrue(file.delete())
            assertThrows(IllegalArgumentException::class.java) { f.vm.export(ByteArrayOutputStream()) }
            assertFalse(file.exists())
            file.writeBytes(byteArrayOf())
            assertThrows(IllegalArgumentException::class.java) { f.vm.export(ByteArrayOutputStream()) }
            assertEquals(0L, file.length())
            file.writeBytes(original + byteArrayOf(0))
            assertThrows(IllegalArgumentException::class.java) { f.vm.export(ByteArrayOutputStream()) }
            assertArrayEquals(original + byteArrayOf(0), file.readBytes())
        } finally { file.writeBytes(original) }
        f.store.prepare(f.backup().inputStream()).use { assertEquals(f.vm.data.value, it.data) }
        f.assertNoStages()
    }

    private class FailingInput(private val bytes: ByteArray, private val failAt: Int) : InputStream() {
        private var position = 0
        var closed = false
        override fun read(): Int {
            if (position >= failAt) throw IOException("Injected provider read failure")
            return if (position == bytes.size) -1 else bytes[position++].toInt() and 0xff
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= failAt) throw IOException("Injected provider read failure")
            if (position == bytes.size) return -1
            val count = minOf(length, failAt - position, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun close() { closed = true }
    }

    private class FailingOutput(private val failAt: Int, private val failFlush: Boolean = false) : OutputStream() {
        private var written = 0
        var closed = false
        override fun write(value: Int) {
            if (written >= failAt) throw IOException("Injected provider write failure")
            written++
        }
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (length > failAt - written) throw IOException("Injected provider write failure")
            written += length
        }
        override fun flush() { if (failFlush) throw IOException("Injected provider flush failure") }
        override fun close() { closed = true }
    }

    /** Supplies the over-limit string incrementally, with no 28 MiB test byte array. */
    private class RepeatedStringInput(prefix: String, private val repeats: Int, suffix: String) : InputStream() {
        private val prefixBytes = prefix.toByteArray()
        private val suffixBytes = suffix.toByteArray()
        private var position = 0
        var closed = false
        override fun read(): Int {
            val index = position++
            return when {
                index < prefixBytes.size -> prefixBytes[index].toInt() and 0xff
                index < prefixBytes.size + repeats -> 'x'.code
                index < prefixBytes.size + repeats + suffixBytes.size -> suffixBytes[index - prefixBytes.size - repeats].toInt() and 0xff
                else -> -1
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            var count = 0
            while (count < length) {
                val next = read()
                if (next < 0) return if (count == 0) -1 else count
                buffer[offset + count++] = next.toByte()
            }
            return count
        }
        override fun close() { closed = true }
    }

    companion object {
        private fun image(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(19, 23, Bitmap.Config.ARGB_8888)
            return try {
                bitmap.eraseColor(color)
                ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
            } finally { bitmap.recycle() }
        }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
