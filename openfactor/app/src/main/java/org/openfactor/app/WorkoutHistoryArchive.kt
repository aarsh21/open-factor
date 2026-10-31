package org.openfactor.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Keep immutable saved history out of the file synchronously rewritten for every logger edit. */
internal class WorkoutHistoryArchive(context: Context, name: String) {
    private val directory = File(context.filesDir, "workout-history/${digest(name.toByteArray(Charsets.UTF_8))}")
    private var cachedHistory: List<Workout>? = null
    private var cachedDigest: String? = null
    private var lastPruned: Set<String>? = null
    // Identity caches: avoid re-parsing the snapshot just encoded, or an unchanged checkpoint, per save.
    private var encodedRaw: String? = null
    private var encodedKey: String? = null
    private var checkpointRaw: String? = null
    private var checkpointKey: String? = null

    fun decode(raw: String): AppData {
        val key = reference(raw)
        if (key == null) return DataCodec.decode(raw)
        val bytes = AtomicFile(File(directory, "$key.json")).openRead().use { it.readBytes() }
        require(digest(bytes) == key) { "Saved workout history is damaged" }
        val history = DataCodec.decodeHistory(bytes.toString(Charsets.UTF_8))
        return DataCodec.decode(raw, history).also {
            cachedHistory = history
            cachedDigest = key
        }
    }

    fun encode(data: AppData): String {
        if (data.history.isEmpty()) return DataCodec.encode(data, pretty = false).also { encodedRaw = it; encodedKey = null }
        val key = if (data.history === cachedHistory && cachedDigest?.let { File(directory, "$it.json").isFile } == true) requireNotNull(cachedDigest) else {
            val bytes = DataCodec.encodeHistory(data.history).toByteArray(Charsets.UTF_8)
            val hash = digest(bytes)
            check(directory.isDirectory || directory.mkdirs()) { "Could not create workout history archive" }
            val file = File(directory, "$hash.json")
            // Always rewrite when the list changes: an existing archive may itself be damaged.
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (failure: Exception) {
                atomic.failWrite(output)
                throw failure
            }
            cachedHistory = data.history
            cachedDigest = hash
            hash
        }
        // Same compact text as encoding, re-parsing and appending the reference, without the re-parse.
        return DataCodec.document(data.copy(history = emptyList())).put("storageHistory", key).toString()
            .also { encodedRaw = it; encodedKey = key }
    }

    /** Call only after the primary snapshot commits; the startup checkpoint remains recoverable. */
    fun prune(primary: String, checkpoint: String?, preserveOriginals: Boolean) {
        if (preserveOriginals) return
        val primaryKey = if (primary === encodedRaw) encodedKey else reference(primary)
        if (checkpoint !== checkpointRaw) {
            checkpointKey = checkpoint?.let { runCatching { reference(it) }.getOrNull() }
            checkpointRaw = checkpoint
        }
        val keep = listOfNotNull(primaryKey, checkpointKey).toSet()
        if (lastPruned == keep) return
        directory.listFiles()?.filter { it.name.endsWith(".json") && it.name.removeSuffix(".json") !in keep }
            ?.forEach { it.delete() }
        lastPruned = keep
    }

    fun exportRecovery(zip: ZipOutputStream) {
        directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}\\.json")) }?.sortedBy { it.name }?.forEach { file ->
            zip.putNextEntry(ZipEntry("history/${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun reference(raw: String): String? {
        val json = JSONObject(raw)
        if (!json.has("storageHistory")) return null
        return json.getString("storageHistory").also {
            require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid workout history archive identity" }
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
