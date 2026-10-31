package org.openfactor.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

class PersistenceConcurrencyTest {
    private fun withViewModel(test: (WorkoutViewModel, Context) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "persistence-concurrency-test-${newId()}"
        val directory = File(context.cacheDir, prefix).apply { check(mkdirs()) }
        val preferences = mutableSetOf<String>()
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = directory
            override fun getNoBackupFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolatedName = "$prefix-$name"
                synchronized(preferences) { preferences += isolatedName }
                return context.getSharedPreferences(isolatedName, mode)
            }
        }
        val application = object : Application() {
            init { attachBaseContext(isolated) }
        }
        val models = ViewModelStore()
        try {
            val vm = WorkoutViewModel(application)
            models.put("test", vm)
            test(vm, isolated)
        } finally {
            models.clear()
            synchronized(preferences) { preferences.forEach { context.deleteSharedPreferences(it) } }
            directory.deleteRecursively()
        }
    }

    @Test fun restoreWaitsForAnAcceptedUpdateAndPublishesTheSameStateAsStorage() = withViewModel { vm, context ->
        val restored = vm.data.value.copy(
            history = listOf(Workout(name = "Synthetic restored history")),
            preferences = vm.data.value.preferences.copy(displayName = "Restored", pounds = false),
        )
        vm.photoStore.prepare(DataCodec.encode(restored).byteInputStream()).use { prepared ->
            val workers = Executors.newFixedThreadPool(2)
            val updateEntered = CountDownLatch(1)
            val releaseUpdate = CountDownLatch(1)
            val restoreStarted = CountDownLatch(1)
            try {
                val update = workers.submit {
                    vm.settings { previous ->
                        updateEntered.countDown()
                        check(releaseUpdate.await(10, TimeUnit.SECONDS))
                        previous.copy(displayName = "Update before restore", pounds = true)
                    }
                }
                assertTrue(updateEntered.await(10, TimeUnit.SECONDS))
                val restore = workers.submit {
                    restoreStarted.countDown()
                    vm.restore(prepared)
                }
                assertTrue(restoreStarted.await(10, TimeUnit.SECONDS))
                try {
                    assertThrows(TimeoutException::class.java) { restore.get(250, TimeUnit.MILLISECONDS) }
                } finally {
                    releaseUpdate.countDown()
                }
                update.get(10, TimeUnit.SECONDS)
                restore.get(10, TimeUnit.SECONDS)
                assertEquals(restored, vm.data.value)
                assertEquals(restored, WorkoutStore(context).load())
            } finally {
                releaseUpdate.countDown()
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }
}
