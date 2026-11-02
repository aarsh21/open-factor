package org.openfactor.app

/**
 * Android callback boundary for a rejected durable write. The saver has already exposed Failed;
 * keep success-only work (closing, navigation, feedback) inside [action] so it is skipped on failure.
 * Validation, cancellation and programming errors are deliberately not handled here.
 */
internal fun runPersistenceAction(action: () -> Unit): Boolean = try {
    action()
    true
} catch (_: PersistenceWriteException) {
    false
}
