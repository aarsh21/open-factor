package org.openfactor.app

/** Durability of accepted edits, independent of screen or Activity lifetime. */
sealed interface PersistenceState {
    data object Saved : PersistenceState
    data object Pending : PersistenceState
    data class Failed(val hasUnsavedChanges: Boolean) : PersistenceState
}
