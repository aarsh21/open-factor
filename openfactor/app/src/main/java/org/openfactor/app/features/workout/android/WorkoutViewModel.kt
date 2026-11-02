package org.openfactor.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Android composition root: owns capabilities and lifetime, not product mutations. */
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkoutStore(application)
    val photoStore = ProgressPhotoStore(application)
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val library = (application as? OpenFactorApplication)?.exerciseLibrary
        ?: ExerciseLibraryRepository().also { it.load(application) }
    val commands: WorkoutCommands = WorkoutCommands(
        initial = store.load(), scope = saveScope, write = store::save,
        catalog = { catalog }, equipmentPreset = { equipmentCatalog.prefill(it) },
        equipmentItems = { equipmentCatalog.items }, requirePhotoFiles = photoStore::requireFiles,
        onPublished = { previous, next ->
            if (previous.active != next.active || (next.active != null && previous.preferences != next.preferences))
                WorkoutNotifications.sync(application, next)
            if (previous.progressPhotos != next.progressPhotos) cleanPhotos()
        },
        onDeferredFailure = { android.util.Log.w("WorkoutStore", "Deferred save failed; edits remain pending", it) },
    )
    val data = commands.data
    val persistence = commands.persistence
    val finishWorkout = WorkoutFinishController(commands, viewModelScope)
    private val _storageRecovery = MutableStateFlow(store.recovery())
    val storageRecovery = _storageRecovery.asStateFlow()
    val hasStorageRecovery: Boolean get() = store.hasRecoveryPayloads()
    private val _libraryReady = MutableStateFlow(false)
    val libraryReady = _libraryReady.asStateFlow()
    internal val libraryLoad = library.state
    val catalog: List<Exercise> get() = library.snapshot?.catalog.orEmpty()
    val equipmentCatalog: EquipmentCatalog get() = requireNotNull(library.snapshot) { "The library is still loading" }.equipment

    fun exercises(d: AppData = data.value) = commands.exercises(d)

    private fun prepareLibrary(snapshot: LoadedExerciseLibrary) {
        runPersistenceAction { commands.ensureDefaultGym(snapshot.equipment.prefill("everything")) }
        _libraryReady.value = true
    }

    fun retryLibrary() {
        library.load(getApplication())
        viewModelScope.launch { runCatching { library.await() }.onSuccess(::prepareLibrary) }
    }

    init {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) retryLibrary()
        else prepareLibrary(runBlocking { library.await() })
        refreshNotifications()
    }

    fun refreshNotifications(): Unit = commands.withSnapshot {
        WorkoutNotifications.sync(getApplication(), it, forceRefresh = true)
    }

    fun flushPendingSaves(): Boolean = commands.flushPendingSaves()

    fun retryPersistence() {
        viewModelScope.launch(Dispatchers.IO) { commands.retryPendingSaves() }
    }

    fun dismissPersistenceFailure() { commands.dismissPersistenceFailure() }

    override fun onCleared() {
        commands.flushPendingSaves()
        saveScope.cancel()
    }

    fun exportStorageRecovery(output: OutputStream) = store.exportRecovery(output)

    fun dismissStorageRecovery() {
        if (store.dismissRecovery()) _storageRecovery.value = null
    }

    fun cleanPhotos(draft: Collection<String> = emptyList()): Unit = commands.withSnapshot { snapshot ->
        val recoveryReferences = store.recoveryPhotoReferences()
        if (recoveryReferences != null) photoStore.clean(snapshot.photoReferences() + recoveryReferences + draft)
    }

    fun restore(text: String) {
        photoStore.prepare(ByteArrayInputStream(text.toByteArray())).use { restore(it) }
    }

    fun restore(backup: PreparedPhotoBackup) {
        val decoded = backup.data
        val restored = if (decoded.gyms.isEmpty()) decoded.copy(gyms = listOf(
            GymProfile(name = "Everything Gym", equipmentIds = equipmentCatalog.prefill("everything")),
        )) else decoded
        commands.replaceData(restored, backup::install)
        cleanPhotos()
    }

    fun export(output: OutputStream) {
        val (snapshot, retained) = commands.withSnapshot { it to photoStore.retainForExport(it) }
        retained.use { photoStore.export(snapshot, output) }
    }

    fun export(): String = ByteArrayOutputStream().also { export(it) }.toString("UTF-8")
}
