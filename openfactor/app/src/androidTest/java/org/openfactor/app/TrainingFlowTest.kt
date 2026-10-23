package org.openfactor.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device storage/media checks. The end-to-end UI flow uses tooling/android_smoke.py. */
@RunWith(AndroidJUnit4::class)
class TrainingFlowTest {
    @Test
    fun installedAppCannotAccessTheInternet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            ).requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
    }

    @Test
    fun workoutStorageSurvivesNewStoreInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val workout =
                Workout(
                    name = "Device test",
                    exercises =
                        listOf(
                            SessionExercise(
                                exerciseId = "test",
                                sets =
                                    listOf(LoggedSet(weightKg = 42.5, reps = 8, completed = true)),
                            )
                        ),
                )
            val saved = before.copy(active = workout)
            store.save(saved)
            assertEquals(saved, WorkoutStore(context).load())
            assertEquals(saved, DataCodec.decode(DataCodec.encode(saved)))
        } finally {
            store.save(before)
        }
    }

    @Test
    fun deloadPlacementAndOverridesSurviveStoreRestartAndStartThroughViewModel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        var vm = WorkoutViewModel(app)
        val backup = vm.export()
        val before = vm.data.value
        try {
            vm.discard()
            val exercise = vm.catalog.first { it.properties["Metrics"] != null && it.loggingMetrics().any { metric -> metric.isReps } }
            val first = CycleTarget(List(3) { SetTarget("s-$it", 7, 9, 10 - it, SetKind.FAILURE) }, restSeconds = 0)
            val last = CycleTarget(List(6) { SetTarget("l-$it", 3, 8, it) }, restSeconds = 65)
            val entry = PlannedExercise("entry", exercise.id, listOf(first, last))
            val day = ProgramDay("deload-day", "Deload storage", exercises = listOf(entry))
            val program = TrainingProgram("deload-plan", "Deload storage", days = listOf(day), cycleCount = 2, deloadPlacement = ProgramDeload.FIRST)
            vm.beginProgram(program)
            vm.saveProgram(false)
            vm = WorkoutViewModel(app)
            assertEquals(program, vm.data.value.programs.single { it.id == program.id })
            assertFalse(vm.startProgramDay(program.id, day.id, 999))
            assertTrue(vm.startProgramDay(program.id, day.id, -1))
            val active = vm.data.value.active!!
            assertEquals(-1, active.programCycle)
            assertEquals(listOf(13, 12), active.exercises.single().sets.map { it.targetRir })
            assertEquals(listOf(6, 6), active.exercises.single().sets.map { it.targetRepsMin })
            assertEquals(0, active.exercises.single().restSeconds)
            assertTrue(active.exercises.single().sets.all { it.kind == SetKind.STANDARD && it.weightKg == null && it.reps == null })
            assertEquals(active, WorkoutViewModel(app).data.value.active)
            assertEquals(vm.data.value, DataCodec.decode(vm.export()))
            vm.discard()
            val explicit = CycleTarget(listOf(SetTarget("explicit", 17, 23, 13, SetKind.FAILURE)), restSeconds = 0)
            val changed = program.copy(deloadPlacement = ProgramDeload.LAST, days = listOf(day.copy(exercises = listOf(entry.copy(deloadTargets = mapOf(ProgramDeload.LAST to explicit))))))
            vm.beginProgram(changed)
            vm.saveProgram(false)
            vm = WorkoutViewModel(app)
            assertFalse(vm.startProgramDay(program.id, day.id, -1))
            assertTrue(vm.startProgramDay(program.id, day.id, 999))
            val logged = vm.data.value.active!!.exercises.single().sets.single()
            assertEquals(17, logged.targetRepsMin)
            assertEquals(13, logged.targetRir)
            assertEquals(SetKind.FAILURE, logged.kind)
            assertEquals(before.history, vm.data.value.history)
            assertEquals(before.scaleWeights, vm.data.value.scaleWeights)
            assertEquals(before.progressPhotos, vm.data.value.progressPhotos)
            assertEquals(vm.data.value, WorkoutStore(app).load())
        } finally {
            vm.restore(backup)
        }
    }

    @Test
    fun exercisePinsStaySeparateFromWorkoutNotesAndDefaultsSurviveStorageAndBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = WorkoutViewModel(context.applicationContext as android.app.Application)
        val backup = vm.export()
        val before = vm.data.value
        val id = "19f5c6f170d8808bb424e98de4472a7e"
        try {
            vm.discard()
            vm.discardWorkoutDraft()
            vm.saveExerciseDefaults(id, ExerciseDefaults("First default", 75, true))
            vm.prepareWorkout()
            vm.addDraftExercise(id)
            val draftEntry = vm.data.value.workoutDraft!!.exercises.single()
            assertEquals("", draftEntry.notes)
            assertEquals(75, draftEntry.restSeconds)
            vm.saveExerciseDefaults(id, ExerciseDefaults("Next default", 90, false))
            assertEquals(draftEntry, vm.data.value.workoutDraft!!.exercises.single())
            vm.startWorkoutDraft()
            assertEquals(draftEntry, vm.data.value.active!!.exercises.single())
            vm.addExercise(id)
            assertEquals(draftEntry, vm.data.value.active!!.exercises.first())
            assertEquals("", vm.data.value.active!!.exercises.last().notes)
            assertEquals(90, vm.data.value.active!!.exercises.last().restSeconds)
            val logged = vm.data.value.active!!.exercises.last()
            vm.exerciseNotes(logged.id, "Workout entry only")
            assertEquals("Next default", vm.data.value.exerciseDefaults[id]!!.note)
            vm.editSet(logged.id, logged.sets.single().id) { it.copy(weightKg = 37.5, reps = 9, completed = true) }
            assertEquals(vm.data.value, WorkoutViewModel(context.applicationContext as android.app.Application).data.value)
            assertEquals(vm.data.value, DataCodec.decode(vm.export()))
            vm.finish()
            val finished = vm.data.value.history.first()
            assertEquals(listOf("", "Workout entry only"), finished.exercises.map { it.notes })
            assertEquals(before.history, vm.data.value.history.drop(1))
            vm.saveExerciseDefaults(id, ExerciseDefaults("Changed pinned note", 90, false))
            assertEquals(finished, vm.data.value.history.first())
            vm.start("Fresh", listOf(id))
            assertEquals("", vm.data.value.active!!.exercises.single().notes)
            assertEquals(90, vm.data.value.active!!.exercises.single().restSeconds)
            vm.discard()
            val day = ProgramDay(exercises = listOf(PlannedExercise(exerciseId = id)))
            val program = TrainingProgram(name = "Defaults contract", days = listOf(day))
            vm.beginProgram(program)
            vm.saveProgram(false)
            assertTrue(vm.startProgramDay(program.id, day.id, 0))
            assertEquals("", vm.data.value.active!!.exercises.single().notes)
            assertEquals(90, vm.data.value.active!!.exercises.single().restSeconds)
            assertNull(vm.data.value.active!!.exercises.single().sets.first().weightKg)
            assertEquals(listOf(finished) + before.history, vm.data.value.history)
            assertEquals(before.scaleWeights, vm.data.value.scaleWeights)
            assertEquals(before.bodyMeasurements, vm.data.value.bodyMeasurements)
            assertEquals(before.progressPhotos, vm.data.value.progressPhotos)
            vm.saveExerciseDefaults(id, ExerciseDefaults())
            assertFalse(id in vm.data.value.exerciseDefaults)
        } finally {
            vm.restore(backup)
        }
    }

    @Test
    fun customArchiveAndOverrideResetPreserveSavedTrainingAndExerciseIdentity() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val vm = WorkoutViewModel(app)
        val backup = vm.export()
        try {
            vm.discard()
            val id = vm.addCustom(CustomExerciseDraft(name = "Archive contract", metric1 = SetMetric.REPS, bodyPercent = "0"))
            vm.saveExerciseDefaults(id, ExerciseDefaults("Keep pinned", 0, true))
            vm.start("Archive control", listOf(id))
            val entry = vm.data.value.active!!.exercises.single()
            vm.exerciseNotes(entry.id, "Keep history note")
            vm.editSet(entry.id, entry.sets.single().id) { it.copy(reps = 13, completed = true) }
            vm.finish()
            vm.start("Still resolvable", listOf(id))
            val before = vm.data.value
            val original = vm.exercises().single { it.id == id }
            vm.archiveExercise(vm.catalog.first().id, true)
            assertEquals(before, vm.data.value)
            vm.archiveExercise(id, true)
            assertEquals(before.copy(customExercises = before.customExercises.map { if (it.id == id) it.copy(archived = true) else it }), vm.data.value)
            assertEquals(original.copy(archived = true), WorkoutViewModel(app).exercises().single { it.id == id })
            assertEquals(vm.data.value, DataCodec.decode(vm.export()))
            vm.saveExerciseDefaults(id, vm.data.value.exerciseDefaults.getValue(id).withoutOverrides())
            assertEquals(ExerciseDefaults("Keep pinned", doNotRecommend = true), vm.data.value.exerciseDefaults[id])
            assertEquals(before.active, vm.data.value.active)
            assertEquals(before.history, vm.data.value.history)
            assertEquals(before.workoutDraft, vm.data.value.workoutDraft)
            assertEquals(before.programs, vm.data.value.programs)
            assertEquals(before.scaleWeights, vm.data.value.scaleWeights)
            assertEquals(before.bodyMeasurements, vm.data.value.bodyMeasurements)
            assertEquals(before.progressPhotos, vm.data.value.progressPhotos)
            vm.archiveExercise(id, false)
            assertEquals(original, WorkoutViewModel(app).exercises().single { it.id == id })
        } finally {
            vm.restore(backup)
        }
    }

    @Test
    fun fullCatalogHasResolvableLocalMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imported =
            runCatching {
                    context.assets.open("reference/catalog.json").bufferedReader().use {
                        DataCodec.catalog(it.readText())
                    }
                }
                .getOrNull()
        if (imported != null) {
            assertEquals(1345, imported.size)
            assertEquals(imported.size, imported.map { it.id }.distinct().size)
            imported.forEach { e ->
                e.frames.forEach {
                    context.assets.open(it).use { stream -> assertTrue(stream.read() >= 0) }
                }
                if (e.instructions.isNotBlank()) context.assets.open(e.instructions).close()
                e.clips.filter { it.isNotBlank() }.forEach { context.assets.open(it).close() }
            }
        } else {
            val baseline =
                context.assets.open("catalog.json").bufferedReader().use {
                    DataCodec.catalog(it.readText())
                }
            assertEquals(1345, baseline.size)
            assertEquals(1345, baseline.map { it.id }.distinct().size)
            assertTrue(
                baseline.all {
                    it.frames.isEmpty() && it.instructions.isEmpty() && it.video == null
                }
            )
        }
        val equipment = EquipmentCatalog(context)
        assertEquals(297, equipment.items.size)
        assertEquals(89, equipment.prefill("commercialGym").size)
        val bench =
            (imported
                    ?: context.assets.open("catalog.json").bufferedReader().use {
                        DataCodec.catalog(it.readText())
                    })
                .single { it.id == "19f5c6f170d8808bb424e98de4472a7e" }
        assertFalse(equipment.available(bench, GymProfile(name = "Blank")))
        assertTrue(
            equipment.available(
                bench,
                GymProfile(name = "Everything", equipmentIds = equipment.prefill("everything")),
            )
        )
        val bar = equipment.items.single { it.name == "Barbell" }.id
        val plates = equipment.items.single { it.name == "Weight plates" }.id
        val support = equipment.items.single { it.name == "Flat bench" }.id
        val dumbbells = equipment.items.single { it.name == "Dumbbells" }.id
        val bodyweight = equipment.items.single { it.name.equals("Bodyweight only", true) }.id
        assertTrue(equipment.usesResistance(bench, bar))
        assertTrue(equipment.usesResistance(bench, plates))
        assertFalse(equipment.usesResistance(bench, support))
        assertFalse(equipment.usesResistance(bench, dumbbells))
        val (resistance, supports) = equipment.requirementsFor(bench)
        assertEquals(listOf(setOf(bar, plates)), resistance)
        assertEquals(5, supports.size)
        val resolved = WorkoutViewModel(context.applicationContext as android.app.Application).catalog
        assertEquals(1345, resolved.size)
        for ((name, type) in mapOf(
            "Barbell bench press" to CustomExerciseType.COMPOUND_UPPER,
            "Weighted Roman chair hip hinge" to CustomExerciseType.ISOLATION_LOWER,
            "Burpee" to CustomExerciseType.COMPOUND_FULL,
            "Barbell Jefferson curl" to CustomExerciseType.COMPOUND_LOWER,
            "Barbell fixed-hip Jefferson curl" to CustomExerciseType.ISOLATION_LOWER,
        )) {
            assertEquals(name, type, resolved.single { it.name == name }.duplicateDraft().type)
        }
        assertEquals("Core", resolved.single { it.name == "Barbell Jefferson curl" }.properties["Region"])
        assertEquals(listOf("Lower Back Accessory"), resolved.single { it.name == "Barbell Jefferson curl" }.movementPatterns)
        assertEquals("Compound Lower", resolved.single { it.name == "Barbell Jefferson curl" }.infoTypeLabel)
        assertEquals("Lower Isolation", resolved.single { it.name == "Barbell fixed-hip Jefferson curl" }.infoTypeLabel)
        assertEquals("Compound Lower", resolved.single { it.name == "Burpee" }.infoTypeLabel)
        assertEquals(9, resolved.count { it.infoTypeLabel == null })
        assertTrue(resolved.all { it.movementPatterns != null })
        val bundled = context.assets.open("catalog.json").bufferedReader().use {
            DataCodec.catalog(it.readText())
        }.associateBy { it.id }
        imported?.forEach { source ->
            val actual = resolved.single { it.id == source.id }
            assertEquals(source, actual.copy(properties = source.properties, generationFacts = source.generationFacts))
            source.properties.forEach { (key, value) -> assertEquals(value, actual.properties[key]) }
            val fallback = bundled.getValue(source.id).generationFacts
            assertEquals(fallback.descriptorLists + source.generationFacts.descriptorLists, actual.generationFacts.descriptorLists)
            assertEquals(fallback.recommendationLevels + source.generationFacts.recommendationLevels, actual.generationFacts.recommendationLevels)
        }
        val duplicateDraft = resolved.single { it.id == bench.id }.duplicateDraft(resistance, supports)
        val duplicate = duplicateDraft.toExercise(equipment.items)
        assertEquals(.076, duplicate.bodyweight, 0.0)
        assertFalse(equipment.available(duplicate, GymProfile(name = "No support", equipmentIds = setOf(bar, plates))))
        assertTrue(equipment.available(duplicate, GymProfile(name = "Everything", equipmentIds = equipment.prefill("everything"))))
        val custom = bench.copy(custom = true,
            resistanceEquipment = listOf(setOf(bar, plates), setOf(dumbbells)),
            supportEquipment = listOf(setOf(support)))
        assertTrue(equipment.usesResistance(custom, dumbbells))
        assertTrue(equipment.usesResistance(custom, plates))
        assertFalse(equipment.usesResistance(custom, support))
        assertFalse(equipment.usesResistance(custom, bodyweight))
        assertTrue(equipment.usesResistance(custom.copy(resistanceEquipment = listOf(emptySet())), bodyweight))
    }
}
