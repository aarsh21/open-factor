package org.openfactor.app

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PersistenceActionBoundaryTest {
    @Test fun successfulActionRunsItsContinuation() {
        var continued = false
        assertTrue(runPersistenceAction { continued = true })
        assertTrue(continued)
    }

    @Test fun typedWriteFailureRejectsActionAndSkipsSuccessContinuation() {
        var continued = false
        val write: () -> Unit = { throw PersistenceWriteException(IOException("disk full")) }
        assertFalse(runPersistenceAction {
            write()
            continued = true
        })
        assertFalse(continued)
    }

    @Test fun cancellationValidationAndProgrammingFailuresPropagateUnchanged() {
        listOf(
            CancellationException("cancelled"),
            IllegalArgumentException("invalid input"),
            IllegalStateException("broken invariant"),
            IOException("not classified by the saver"),
            AssertionError("programming failure"),
        ).forEach { failure ->
            val thrown = assertThrows(failure.javaClass) {
                runPersistenceAction { throw failure }
            }
            assertSame(failure, thrown)
        }
    }
}
