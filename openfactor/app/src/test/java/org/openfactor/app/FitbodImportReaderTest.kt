package org.openfactor.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class FitbodImportReaderTest {
    private val csv = "date_local,date_utc,workout_name,exercise_name,set_number,reps,weight_kg,is_warmup,is_amrap,rest_seconds,set_duration_raw,distance_m,notes,workout_id,exercise_external_resource_id,exercise_position,circuit_position,set_id\n" +
        "2026-01-01T12:00:00Z,2026-01-01T12:00:00Z,Reader check,Press,1,8,40,False,False,90,,,,reader,press,0,,set-1"

    @Test fun aProviderReturningZeroBytesMakesProgressInsteadOfSpinning() {
        val bytes = csv.byteInputStream()
        val input = object : InputStream() {
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
            override fun read(): Int = bytes.read()
        }
        assertEquals(1, FitbodImport.read(input).setCount)
    }

    @Test fun validInputIsParsedAndClosedOffTheCallerThread() = runBlocking {
        val caller = Thread.currentThread()
        val closed = AtomicBoolean(false)
        val parsed = readFitbodInput {
            assertNotSame(caller, Thread.currentThread())
            object : ByteArrayInputStream(csv.toByteArray()) {
                override fun close() { closed.set(true); super.close() }
            }
        }
        assertEquals(1, parsed.setCount)
        assertTrue(closed.get())
    }

    @Test(timeout = 10_000) fun aStalledReadTimesOutAndClosesTheProviderWithoutWaitingForItsRead() = runBlocking {
        val input = BlockingInput()
        val providerCancelled = AtomicBoolean(false)
        try {
            readFitbodInput(timeoutMillis = 1_000, cancelOpen = { providerCancelled.set(true) }) { input }
            fail("Stalled provider must time out")
        } catch (_: TimeoutCancellationException) { }
        assertTrue(input.closed.await(5, TimeUnit.SECONDS))
        assertTrue(providerCancelled.get())
    }

    @Test(timeout = 10_000) fun cancellingAnOpenProviderReleasesLateStreamsWithoutPublishingAResult() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val operation = async {
            readFitbodInput(cancelOpen = { release.countDown() }) {
                entered.countDown()
                release.await()
                object : ByteArrayInputStream(csv.toByteArray()) {
                    override fun close() { closed.countDown(); super.close() }
                }
            }
        }
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        operation.cancelAndJoin()
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertTrue(operation.isCancelled)
    }

    @Test fun providerErrorsAreReportedWithoutRetries() = runBlocking {
        var attempts = 0
        try {
            readFitbodInput { attempts++; throw IOException("Provider unavailable") }
            fail("Provider failure must surface")
        } catch (failure: IOException) { assertEquals("Provider unavailable", failure.message) }
        assertEquals(1, attempts)
    }

    private class BlockingInput : InputStream() {
        val closed = CountDownLatch(1)
        override fun read(): Int { closed.await(); return -1 }
        override fun close() { closed.countDown() }
    }
}
