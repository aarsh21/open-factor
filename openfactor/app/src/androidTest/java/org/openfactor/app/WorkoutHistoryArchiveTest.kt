package org.openfactor.app

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryArchiveTest {
    private fun withStore(block: (Context, String, File, WorkoutStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-archive-test-${newId()}"
        val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "workout-history/$hash")
        try { block(context, name, directory, WorkoutStore(context, name)) }
        finally {
            context.deleteSharedPreferences(name)
            context.deleteSharedPreferences("${name}_recovery")
            directory.deleteRecursively()
        }
    }

    private fun history(count: Int) = List(count) { index ->
        Workout(id = "synthetic-$index", name = "Synthetic workout $index", startedAt = 1_700_000_000_000L + index * 86_400_000L,
            finishedAt = 1_700_003_600_000L + index * 86_400_000L,
            exercises = List(5) { exercise -> SessionExercise(id = "e-$index-$exercise", exerciseId = "bench",
                sets = List(4) { set -> LoggedSet(id = "s-$index-$exercise-$set", weightKg = 50.0, reps = 8, completed = true) }) })
    }

    @Test fun largeHistoryIsNotRewrittenForLoggerEditsAndBackupStaysSelfContained() = withStore { context, name, directory, store ->
        var data = AppData(history = history(1_000), active = Workout(name = "Live workout"))
        val initial = System.nanoTime()
        store.save(data)
        val initialMs = (System.nanoTime() - initial) / 1_000_000.0
        val archive = directory.listFiles()!!.single()
        val length = archive.length()
        assertTrue(archive.setLastModified(1_000L))
        val edits = List(5) { index ->
            data = data.copy(active = data.active!!.copy(notes = "Typed value $index"))
            val start = System.nanoTime()
            store.save(data)
            (System.nanoTime() - start) / 1_000_000.0
        }
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("data", null)!!
        assertTrue("Hot snapshot must not grow with saved sets", raw.length < 8_192)
        assertEquals(0, JSONObject(raw).getJSONArray("history").length())
        assertEquals(length, archive.length())
        assertEquals("Unchanged history archive must not be rewritten", 1_000L, archive.lastModified())
        assertEquals(data, WorkoutStore(context, name).load())
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertThrows(IllegalArgumentException::class.java) { DataCodec.decode(raw) }
        Log.i("OpenFactorPerformance", "1000 workouts / 20000 sets: first save=${initialMs}ms; live saves=${edits}ms; hot=${raw.length} bytes; archive=$length bytes")
    }

    @Test fun inlineStorageMigratesWithoutChangingRecordsOrLosingTheCheckpoint() = withStore { context, name, directory, store ->
        val data = AppData(history = history(3), active = Workout(name = "Continue me"))
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("data", DataCodec.encode(data)).commit())
        assertEquals(data, store.load())
        val changed = data.copy(preferences = data.preferences.copy(pounds = true))
        store.save(changed)
        assertEquals(1, directory.listFiles()!!.size)
        assertEquals(changed, WorkoutStore(context, name).load())
        assertTrue(prefs.edit().putString("data", "damaged primary").commit())
        assertEquals(changed, WorkoutStore(context, name).load())
    }

    @Test fun damagedArchiveRecoversStartupCheckpointAndExportsTheExactOriginalAndArchiveBytes() = withStore { context, name, directory, store ->
        val first = AppData(history = history(2))
        store.save(first)
        assertEquals(first, store.load())
        val second = first.copy(history = history(3))
        store.save(second)
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val raw = prefs.getString("data", null)!!
        val key = JSONObject(raw).getString("storageHistory")
        val damaged = "damaged history bytes"
        File(directory, "$key.json").writeText(damaged)
        val recovered = WorkoutStore(context, name)
        assertEquals(first, recovered.load())
        assertTrue(recovered.recovery()!!.restoredPreviousData)
        recovered.save(first.copy(preferences = first.preferences.copy(pounds = true)))
        val zip = ByteArrayOutputStream().also(recovered::exportRecovery).toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = input.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.filterKeys { it.startsWith("original-") }.values.contains(raw))
        assertEquals(damaged, entries["history/$key.json"])
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test fun concurrentStoreReadersAndHistoryPruningNeverReportFalseCorruption() = withStore { context, name, _, writer ->
        val original = AppData(history = history(40))
        writer.save(original)
        val reader = WorkoutStore(context, name)
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val writing = workers.submit {
                repeat(20) { index -> writer.save(original.copy(history = original.history.map { it.copy(notes = "Revision $index") })) }
            }
            val reading = workers.submit {
                repeat(20) {
                    assertEquals(40, reader.load().history.size)
                    assertNull(reader.recovery())
                }
            }
            writing.get(30, java.util.concurrent.TimeUnit.SECONDS)
            reading.get(30, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(40, WorkoutStore(context, name).load().history.size)
        } finally { workers.shutdownNow() }
    }

    @Test fun pruningRetainsCurrentAndStartupHistoriesAndMissingArchiveNeverLoadsAnEmptyHistory() = withStore { context, name, directory, store ->
        val first = AppData(history = history(1))
        store.save(first)
        assertEquals(first, store.load())
        store.save(first.copy(history = history(2)))
        val latest = first.copy(history = history(3))
        store.save(latest)
        assertEquals(2, directory.listFiles()!!.size)
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("data", null)!!
        assertTrue(File(directory, "${JSONObject(raw).getString("storageHistory")}.json").delete())
        assertEquals(first, WorkoutStore(context, name).load())
    }
}
