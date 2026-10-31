package org.openfactor.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.core.content.FileProvider
import java.io.*
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Original attachments stay private and never depend on a document provider's URI grant. */
class ProgressPhotoStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "progress-photos").apply { mkdirs() }
    private val staging = File(context.noBackupFilesDir, "photo-restore").apply { mkdirs() }

    private val captures = File(context.cacheDir, "photo-captures").apply { mkdirs() }

    /** Full-resolution camera output is private, temporary and separate from saved originals. */
    fun createCapture(): String {
        captures.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
            ?.forEach { it.delete() }
        return File.createTempFile("capture-", ".jpg", captures).name
    }

    private fun captureFile(name: String): File {
        require(name.matches(Regex("capture-[A-Za-z0-9_-]+\\.jpg"))) { "Invalid camera attachment" }
        return File(captures, name)
    }

    fun captureUri(name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", captureFile(name))

    /** Read without consuming: a recreated editor can retry an interrupted result handoff. */
    fun importCapture(name: String): String = captureFile(name).inputStream().use { attach(it) }

    fun attachCapture(name: String): String = try {
        importCapture(name)
    } finally {
        discardCapture(name)
    }

    fun discardCapture(name: String) {
        captureFile(name).delete()
    }

    fun file(reference: String): File {
        require(reference.matches(Regex("[a-f0-9]{64}"))) { "Invalid photo reference" }
        return File(directory, reference)
    }

    fun attach(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { attach(it) }
            ?: error("Could not open photo. Choose a file stored on this device.")

    fun attach(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_PHOTO_BYTES) {
                "Choose a photo smaller than 20 MiB"
            }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        validateImage(bytes)
        val reference = digest(bytes)
        // Replace even an existing hash: reattaching the original must repair damaged stored bytes.
        val temporary = File.createTempFile("import-", ".tmp", directory)
        try {
            temporary.outputStream().use {
                it.write(bytes)
                it.fd.sync()
            }
            check(temporary.renameTo(file(reference))) { "Could not store photo" }
        } finally {
            temporary.delete()
        }
        return reference
    }

    fun requireFiles(data: AppData) {
        require(data.photoReferences().all { file(it).isFile }) {
            "A photo is missing; reattach it before saving"
        }
    }

    fun clean(retained: Set<String>) {
        directory.listFiles()?.filter { it.name !in retained }?.forEach { it.delete() }
    }

    /** Stream one workout and one photo at a time; never duplicate the whole history as JSON trees. */
    fun export(data: AppData, output: OutputStream) {
        requireFiles(data)
        // Document providers may perform an expensive write for every call. Keep punctuation
        // and the platform Base64 encoder's small writes off the destination until buffered.
        val bounded = BoundedBackupOutput(BufferedOutputStream(output))
        val writer = JsonWriter(OutputStreamWriter(bounded, Charsets.UTF_8))
        val values = BackupMetadataWriter(writer)
        writer.beginObject()
        val metadata = DataCodec.document(data.copy(history = emptyList()))
        metadata.keys().forEach { key ->
            if (key == "history") values.workouts(key, data.history)
            else values.field(key, metadata.get(key))
        }
        // Flush the unfinished root before streaming Base64 directly; output belongs to the caller.
        writer.flush()
        val references = data.photoReferences().sorted()
        if (references.isNotEmpty()) {
            bounded.write(",\"photoAssets\":{".toByteArray())
            references.forEachIndexed { index, reference ->
                if (index > 0) bounded.write(','.code)
                bounded.write("\"$reference\":\"".toByteArray())
                val nonClosing = object : FilterOutputStream(bounded) {
                    // API 26's FilterOutputStream.write(byte[]) otherwise loops byte-by-byte.
                    override fun write(bytes: ByteArray) = out.write(bytes, 0, bytes.size)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                    override fun close() = flush()
                }
                val original = file(reference)
                require(original.length() in 1..MAX_PHOTO_BYTES.toLong()) { "Stored photo is too large or empty; reattach the original" }
                val checksum = MessageDigest.getInstance("SHA-256")
                Base64.getEncoder().wrap(nonClosing).use { encoded ->
                    java.security.DigestInputStream(original.inputStream(), checksum).use {
                        require(it.copyTo(encoded) <= MAX_PHOTO_BYTES) { "Stored photo is too large" }
                    }
                }
                require(checksum.digest().joinToString("") { "%02x".format(it) } == reference) {
                    "Stored photo is damaged; reattach the original before exporting"
                }
                bounded.write('"'.code)
            }
            bounded.write('}'.code)
        }
        bounded.write('}'.code)
        bounded.flush()
    }

    /** Stages and verifies the entire backup before the caller can replace app data. */
    fun prepare(input: InputStream): PreparedPhotoBackup {
        val temporary = File(staging, newId()).apply { check(mkdirs()) }
        try {
            val metadata = JSONObject()
            val assets = mutableSetOf<String>()
            val bounded = BoundedJsonText(InputStreamReader(input, Charsets.UTF_8.newDecoder()))
            JsonReader(bounded).use { reader ->
                val values = BackupMetadataReader(reader)
                reader.beginObject()
                val fields = mutableSetOf<String>()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(fields.add(key)) { "Duplicate backup field" }
                    if (key != "photoAssets") metadata.put(key, values.field(key))
                    else {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val reference = reader.nextName()
                            file(reference)
                            require(assets.add(reference)) { "Duplicate photo asset" }
                            val encoded = reader.nextString()
                            require(encoded.length <= ((MAX_PHOTO_BYTES + 2) / 3) * 4) {
                                "Photo is too large"
                            }
                            val bytes = Base64.getDecoder().decode(encoded)
                            require(bytes.size <= MAX_PHOTO_BYTES && digest(bytes) == reference) {
                                "Photo checksum mismatch"
                            }
                            validateImage(bytes)
                            File(temporary, reference).outputStream().use {
                                it.write(bytes)
                                it.fd.sync()
                            }
                        }
                        reader.endObject()
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after backup" }
            }
            val data = DataCodec.decode(metadata)
            require(assets == data.photoReferences()) {
                "Backup photo attachments are missing or unreferenced"
            }
            return PreparedPhotoBackup(data, temporary, directory)
        } catch (error: Throwable) {
            temporary.deleteRecursively()
            throw error
        }
    }

    companion object {
        const val MAX_PHOTO_BYTES = 20 * 1024 * 1024
        const val MAX_BACKUP_BYTES = 1024L * 1024 * 1024
        // Includes per-node overhead, not just JSON bytes: multi-year set histories need headroom.
        internal const val MAX_METADATA_BUDGET = 64 * 1024 * 1024

        private fun digest(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }

        private fun validateImage(bytes: ByteArray) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(
                bounds.outWidth > 0 &&
                    bounds.outHeight > 0 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000
            ) {
                "Choose a valid photo up to 100 megapixels"
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            require(bitmap != null) { "Could not decode photo" }
            bitmap.recycle()
        }
    }
}

class PreparedPhotoBackup
internal constructor(val data: AppData, private val staging: File, private val destination: File) :
    Closeable {
    fun install() {
        data.photoReferences().forEach { reference ->
            val source = File(staging, reference)
            val target = File(destination, reference)
            if (source.isFile) check(source.renameTo(target)) { "Could not restore photo" }
            else require(target.isFile) { "Backup preparation expired; select the file again" }
        }
    }

    override fun close() {
        staging.deleteRecursively()
    }
}

private class BoundedBackupOutput(output: OutputStream) : FilterOutputStream(output) {
    private var written = 0L
    override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)
    override fun write(value: Int) {
        require(written + 1 <= ProgressPhotoStore.MAX_BACKUP_BYTES) { "Backup exceeds the 1 GiB safety limit" }
        out.write(value)
        written++
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(written + length <= ProgressPhotoStore.MAX_BACKUP_BYTES) { "Backup exceeds the 1 GiB safety limit" }
        out.write(bytes, offset, length)
        written += length
    }
}

internal class BackupMetadataWriter(
    private val writer: JsonWriter,
    private val maxBudget: Int = ProgressPhotoStore.MAX_METADATA_BUDGET,
) {
    private var size = 64

    fun field(key: String, value: Any, depth: Int = 1) {
        size += key.length
        writer.name(key)
        value(value, depth)
    }

    fun workouts(key: String, history: List<Workout>) {
        size += key.length
        node(1)
        writer.name(key).beginArray()
        history.forEach { value(DataCodec.workoutDocument(it), 2) }
        writer.endArray()
    }

    private fun node(depth: Int) {
        require(depth < 64) { "Backup nesting is too deep" }
        size += 64
        require(size < maxBudget) { "Backup metadata is too large" }
    }

    private fun value(value: Any, depth: Int) {
        node(depth)
        when (value) {
            JSONObject.NULL -> writer.nullValue()
            is JSONObject -> {
                writer.beginObject()
                value.keys().forEach { field(it, value.get(it), depth + 1) }
                writer.endObject()
            }
            is JSONArray -> {
                writer.beginArray()
                repeat(value.length()) { value(value.get(it), depth + 1) }
                writer.endArray()
            }
            is String -> {
                size += value.length
                require(size < maxBudget) { "Backup metadata is too large" }
                writer.value(value)
            }
            is Number -> writer.value(value)
            is Boolean -> writer.value(value)
            else -> error("Invalid backup value")
        }
    }
}

internal class BackupMetadataReader(
    private val reader: JsonReader,
    private val maxBudget: Int = ProgressPhotoStore.MAX_METADATA_BUDGET,
) {
    private var size = 0

    fun field(key: String, depth: Int = 0): Any {
        size += key.length
        return value(depth)
    }

    fun value(depth: Int = 0): Any {
        require(depth < 64) { "Backup nesting is too deep" }
        size += 64
        require(size < maxBudget) { "Backup metadata is too large" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT ->
                JSONObject().apply {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        require(!has(key)) { "Duplicate backup field" }
                        put(key, field(key, depth + 1))
                    }
                    reader.endObject()
                }
            JsonToken.BEGIN_ARRAY ->
                JSONArray().apply {
                    reader.beginArray()
                    while (reader.hasNext()) put(value(depth + 1))
                    reader.endArray()
                }
            JsonToken.STRING ->
                reader.nextString().also {
                    size += it.length
                    require(size < maxBudget) { "Backup metadata is too large" }
                }
            JsonToken.NUMBER ->
                reader.nextString().let {
                    it.toLongOrNull() ?: it.toDouble().also { number -> require(number.isFinite()) }
                }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            else -> error("Invalid backup value")
        }
    }
}

/** Bounds untrusted JSON before JsonReader allocates an individual string. */
private class BoundedJsonText(private val source: Reader) : Reader() {
    private var total = 0L
    private var inString = false
    private var escaped = false
    private var stringLength = 0
    private var tokenLength = 0

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count <= 0) return count
        total += count
        require(total <= ProgressPhotoStore.MAX_BACKUP_BYTES) { "Backup exceeds 1 GiB" }
        for (index in offset until offset + count) {
            val c = buffer[index]
            if (inString) {
                stringLength++
                require(stringLength <= 28 * 1024 * 1024) { "Backup string is too large" }
                if (escaped) escaped = false
                else if (c == '\\') escaped = true else if (c == '"') inString = false
            } else if (c == '"') {
                inString = true
                stringLength = 0
                tokenLength = 0
            } else if (c.isWhitespace() || c in "{}[]:,") tokenLength = 0
            else {
                tokenLength++
                require(tokenLength <= 1024) { "Backup token is too large" }
            }
        }
        return count
    }

    override fun close() = source.close()
}
