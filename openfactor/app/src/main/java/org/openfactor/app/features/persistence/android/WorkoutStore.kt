package org.openfactor.app

import android.content.Context
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

data class StorageRecovery(val restoredPreviousData: Boolean, val originalCount: Int)

class WorkoutStore(context: Context, preferencesName: String = "open_factor") {
    private val storageLock = storageLocks.computeIfAbsent("${context.filesDir.absolutePath}/$preferencesName") { Any() }
    private inline fun <T> locked(block: () -> T): T = synchronized(storageLock, block)
    private val historyArchive = WorkoutHistoryArchive(context, preferencesName)
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    // SharedPreferences rewrites its whole XML on commit: keep large checkpoints outside the hot file.
    private val recoveryPrefs = context.getSharedPreferences("${preferencesName}_recovery", Context.MODE_PRIVATE)
    private var unreadablePrimary: String? = null
    private var restoredPreviousData = false

    private fun decode(raw: String?): AppData? = raw?.let {
        try { historyArchive.decode(it) } catch (_: Exception) { null }
    }

    fun load(): AppData = locked {
        val raw = prefs.getString("data", null)
        val checkpoint = recoveryPrefs.getString("data_last_good", null)
        if (raw == null && checkpoint == null) return AppData()
        val decoded = decode(raw)
        if (decoded != null) {
            unreadablePrimary = null
            // A startup checkpoint avoids duplicating the full history on each typed digit.
            if (checkpoint != raw)
                recoveryPrefs.edit().putString("data_last_good", raw).commit()
            return decoded
        }
        // Missing primary preferences are not a fresh install when a checkpoint still exists.
        unreadablePrimary = raw ?: checkpoint
        val previous = decode(checkpoint)
        restoredPreviousData = previous != null
        // Keep the original (or surviving checkpoint) before initialization can replace primary data.
        // If this commit fails, save() includes preservation in the same atomic commit as new data.
        if (preserveUnreadable(recoveryPrefs.edit()).commit()) unreadablePrimary = null
        return previous ?: AppData()
    }

    private fun preserveUnreadable(editor: android.content.SharedPreferences.Editor): android.content.SharedPreferences.Editor {
        unreadablePrimary?.let { raw ->
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            editor.putString("recovery_raw_$digest", raw)
                .putBoolean("recovery_pending", true)
                .putBoolean("recovery_restored_previous", restoredPreviousData)
        }
        return editor
    }

    fun save(data: AppData) = locked {
        val raw = historyArchive.encode(data)
        check(preserveUnreadable(prefs.edit()).putString("data", raw).commit()) {
            "Could not save workout data"
        }
        unreadablePrimary = null
        historyArchive.prune(raw, recoveryPrefs.getString("data_last_good", null), hasRecoveryPayloads())
    }

    fun recovery(): StorageRecovery? = locked {
        val pending = when {
            prefs.getBoolean("recovery_pending", false) -> prefs
            recoveryPrefs.getBoolean("recovery_pending", false) -> recoveryPrefs
            else -> return null
        }
        return StorageRecovery(pending.getBoolean("recovery_restored_previous", false), recoveryPayloads().size)
    }

    private fun recoveryPayloads(): Map<String, String> = (recoveryPrefs.all + prefs.all).entries
        .filter { it.key.startsWith("recovery_raw_") && it.value is String }
        .associate { it.key.removePrefix("recovery_raw_") to it.value as String }.toSortedMap()

    /** Archive the exact original strings; damaged/newer JSON must not be decoded and rewritten. */
    fun exportRecovery(output: OutputStream) = locked {
        val originals = recoveryPayloads()
        check(originals.isNotEmpty()) { "No original storage payload is available" }
        ZipOutputStream(output).use { zip ->
            originals.forEach { (digest, raw) ->
                zip.putNextEntry(ZipEntry("original-$digest.json"))
                zip.write(raw.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            historyArchive.exportRecovery(zip)
        }
    }

    /** Acknowledgment hides the notice, while original payloads stay available for later export. */
    fun dismissRecovery(): Boolean = locked {
        val primary = prefs.edit().putBoolean("recovery_pending", false).commit()
        val archive = recoveryPrefs.edit().putBoolean("recovery_pending", false).commit()
        return primary && archive
    }

    fun hasRecoveryPayloads(): Boolean = locked { recoveryPayloads().isNotEmpty() }

    /** Null means cleanup is unsafe: unreadable recovery data may reference any original. */
    internal fun recoveryPhotoReferences(): Set<String>? = locked {
        if (unreadablePrimary != null || hasRecoveryPayloads()) return@locked null
        try {
            val checkpoint = recoveryPrefs.getString("data_last_good", null)
                ?: return@locked emptySet()
            val document = JSONObject(checkpoint)
            DataCodec.requireSupportedVersion(document)
            // Only decode photo metadata, not a potentially multi-year workout history.
            ProgressPhotoCodec.decode(document).flatMap { it.photos.values }.toSet()
        } catch (_: Exception) {
            // Preserve originals rather than guessing what a damaged/newer checkpoint needs.
            null
        }
    }

    private companion object {
        // A reader must finish before another store can prune the archive it just referenced.
        val storageLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    }
}
