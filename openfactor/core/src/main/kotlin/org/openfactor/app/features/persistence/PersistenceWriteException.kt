package org.openfactor.app

/** A failed durable write, distinct from invalid input or a programming/cancellation failure. */
class PersistenceWriteException(cause: Exception) : IllegalStateException("Could not save workout data", cause)
