package org.openfactor.consumer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.openfactor.app.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PersistenceFailureAcceptanceTest {
    @Test fun staleSaveTimersAndLifecycleFlushesCannotAcknowledgeARejectedAction() = runTest {
        var fail = false
        val disk = mutableListOf<Int>()
        val saver = CoalescedSaver<Int>(250, backgroundScope, write = { if (fail) error("disk full") else disk += it })
        saver.saveLater(1)
        saver.saveNow(2)
        fail = true
        assertThrows(PersistenceWriteException::class.java) { saver.saveNow(3) }
        assertEquals(PersistenceState.Failed(false), saver.state.value)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(listOf(2), disk)
        assertEquals(PersistenceState.Failed(false), saver.state.value)
        assertTrue(saver.flushQuietly())
        assertEquals(PersistenceState.Failed(false), saver.state.value)
        assertTrue(saver.dismissFailure())
        assertEquals(PersistenceState.Saved, saver.state.value)
    }

    @Test fun explicitAcknowledgmentCannotDiscardAcceptedPendingEdits() = runTest {
        var fail = true
        var disk = 0
        val saver = CoalescedSaver<Int>(250, backgroundScope, write = { if (fail) error("disk full") else disk = it })
        saver.saveLater(7)
        advanceTimeBy(251)
        runCurrent()
        assertEquals(PersistenceState.Failed(true), saver.state.value)
        assertFalse(saver.dismissFailure())
        assertEquals(PersistenceState.Failed(true), saver.state.value)
        fail = false
        saver.flush()
        assertEquals(7, disk)
        assertEquals(PersistenceState.Saved, saver.state.value)
    }

    @Test fun cancellationIsNotNormalizedIntoAStorageFailure() = runTest {
        val saver = CoalescedSaver<Int>(250, backgroundScope, write = { throw CancellationException("cancelled") })
        assertThrows(CancellationException::class.java) { saver.saveNow(1) }
        assertEquals(PersistenceState.Saved, saver.state.value)
    }
}
