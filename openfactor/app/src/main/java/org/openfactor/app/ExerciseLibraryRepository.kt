package org.openfactor.app

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal data class LoadedExerciseLibrary(val catalog: List<Exercise>, val equipment: EquipmentCatalog)
internal sealed interface ExerciseLibraryLoad {
    data object Loading : ExerciseLibraryLoad
    data class Ready(val value: LoadedExerciseLibrary) : ExerciseLibraryLoad
    data class Failed(val message: String) : ExerciseLibraryLoad
}

/** One immutable asset snapshot per app process, loaded before any screen needs the catalog. */
internal class ExerciseLibraryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow<ExerciseLibraryLoad>(ExerciseLibraryLoad.Loading)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    val snapshot: LoadedExerciseLibrary? get() = (mutable.value as? ExerciseLibraryLoad.Ready)?.value

    @Synchronized fun load(context: Context) {
        if (job?.isActive == true || snapshot != null) return
        val app = context.applicationContext
        mutable.value = ExerciseLibraryLoad.Loading
        job = scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                val bundled = app.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
                val imported = runCatching {
                    app.assets.open("reference/catalog.json").bufferedReader().use { DataCodec.catalog(it.readText(), bundled) }
                }.getOrDefault(emptyList())
                val exercises = withLicensedExercisePreviews(app, imported.ifEmpty { bundled })
                check(exercises.isNotEmpty()) { "No exercises were found in this installation." }
                mutable.value = ExerciseLibraryLoad.Ready(LoadedExerciseLibrary(exercises, EquipmentCatalog(app)))
                android.util.Log.i("OpenFactorStartup", "Catalog ready: ${exercises.size} exercises in ${SystemClock.elapsedRealtime() - started}ms (background)")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = ExerciseLibraryLoad.Failed("The exercise library couldn’t load. Try again.") }
        }
    }
    suspend fun await(): LoadedExerciseLibrary = when (val result = state.first { it !is ExerciseLibraryLoad.Loading }) {
        is ExerciseLibraryLoad.Ready -> result.value
        is ExerciseLibraryLoad.Failed -> error(result.message)
        ExerciseLibraryLoad.Loading -> error("Library is still loading")
    }
}
