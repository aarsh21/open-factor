package org.openfactor.app

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ExerciseVideoCacheTest {
    @Test
    fun streamingCacheEvictsOldClipsWithinItsByteBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "video-cache-test-${UUID.randomUUID()}")
        val limit = 64L * 1024L
        val chunk = 32 * 1024
        val cache = ExerciseVideoCache.newCache(context, directory, limit)
        try {
            repeat(4) { index ->
                val key = "test-clip-$index"
                val hole = cache.startReadWrite(key, 0, chunk.toLong())
                try {
                    val target = cache.startFile(key, 0, chunk.toLong())
                    target.writeBytes(ByteArray(chunk) { index.toByte() })
                    cache.commitFile(target, chunk.toLong())
                } finally { cache.releaseHoleSpan(hole) }
                assertTrue("Cache exceeded its budget", cache.cacheSpace <= limit)
            }
            assertFalse(cache.isCached("test-clip-0", 0, chunk.toLong()))
            assertTrue(cache.isCached("test-clip-3", 0, chunk.toLong()))
            assertEquals(128L * 1024L * 1024L, ExerciseVideoCache.MAX_BYTES)
        } finally {
            cache.release()
            directory.deleteRecursively()
        }
    }
}
