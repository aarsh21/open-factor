package org.openfactor.app

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import java.util.concurrent.ConcurrentHashMap

/** Test-only recomposition counters. Release builds compile the hook out via [BuildConfig.DEBUG]. */
@VisibleForTesting
internal object RecompositionTrace {
    @Volatile var enabled = false
    private val counts = ConcurrentHashMap<String, Int>()

    fun count(scope: String, id: String = ""): Int = counts["$scope/$id"] ?: 0
    fun reset() = counts.clear()
    fun record(scope: String, id: String) { counts.merge("$scope/$id", 1, Int::plus) }
}

/** Counts each recomposition of the calling scope while [RecompositionTrace.enabled]. */
@Composable
@NonRestartableComposable
internal fun TraceRecomposition(scope: String, id: String = "") {
    if (BuildConfig.DEBUG) SideEffect { if (RecompositionTrace.enabled) RecompositionTrace.record(scope, id) }
}
