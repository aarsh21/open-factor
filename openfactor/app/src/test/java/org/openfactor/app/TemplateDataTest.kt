package org.openfactor.app

import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TemplateDataTest {
    private fun catalog(reference: Boolean = false): List<Exercise> {
        val path = if (reference) "src/main/assets/reference/catalog.json" else "src/main/assets/catalog.json"
        val source = File(path).takeIf { it.isFile } ?: File("app", path)
        val array = JSONArray(source.readText())
        return (0 until array.length()).map { index ->
            val p = array.getJSONObject(index)
            fun strings(key: String) = p.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            Exercise(p.getString("id"), p.getString("name"), p.optString("muscle", "Other"), p.optString("equipment", "Other"),
                muscles = strings("muscles"), secondary = strings("secondary"), frames = strings("frames"), clips = strings("clips"))
        }
    }

    @Test fun starterSchedulesResolveTheFullCatalogAndKeepRestExplicit() {
        val catalog = catalog()
        val presets = starterTemplateSplits(catalog)
        assertEquals(listOf("upper-lower-ppl", "upper-lower", "bro-split", "full-body"), presets.map { it.id })
        assertEquals(listOf("Upper", "Lower", null, "Push", "Pull", "Legs", null), presets[0].days.map { it?.name })
        assertEquals(listOf("Upper A", "Lower A", null, "Upper B", "Lower B", null, null), presets[1].days.map { it?.name })
        assertEquals(listOf(5, 4, 5, 3), presets.map { it.days.count { day -> day != null } })
        presets.forEach { preset ->
            assertEquals(7, preset.days.size)
            val materialized = preset.materialize(catalog)
            assertTrue(materialized.routines.all { it.validationError(catalog) == null })
            assertEquals(preset.days.count { it == null }, materialized.split.days.count { it == null })
        }
        assertTrue(starterTemplateSplits(emptyList()).isEmpty())
        val original = File("src/main/assets/reference/catalog.json").takeIf { it.isFile }
            ?: File("app/src/main/assets/reference/catalog.json")
        if (original.isFile) {
            val media = catalog(true).associateBy { it.id }
            presets.flatMap { it.days.filterNotNull() }.flatMap { it.prescriptions }.forEach {
                assertTrue("Starter exercise needs original photos: ${it.exerciseId}", media.getValue(it.exerciseId).frames.isNotEmpty())
                assertTrue("Starter exercise needs original video: ${it.exerciseId}", media.getValue(it.exerciseId).clips.isNotEmpty())
            }
        }
    }

    @Test fun eachImportCreatesIndependentTemplatesAndSevenDayLinks() {
        val catalog = catalog()
        val preset = starterTemplateSplits(catalog)[1]
        val original = AppData(history = listOf(Workout(id = "history", name = "Previous")))
        val first = original.withStarterSplit(preset, catalog)
        val second = first.withStarterSplit(preset, catalog)
        assertEquals(4, first.routines.size)
        assertEquals(8, second.routines.size)
        assertEquals(2, second.templateSplits.size)
        assertEquals(8, second.routines.map { it.id }.distinct().size)
        assertTrue(first.templateSplits.single().days.filterNotNull().all { id -> first.routines.any { it.id == id } })
        assertTrue(second.templateSplits[1].days.filterNotNull().none { it in first.templateSplits.single().days })
        assertEquals(original.history, second.history)
        assertNull(second.active)
        assertTrue(original.routines.isEmpty())
    }

    @Test fun startingTemplateUsesTargetsAndRestWithoutInventingActualsOrTouchingHistory() {
        val catalog = catalog()
        val saved = AppData().withStarterSplit(starterTemplateSplits(catalog)[1], catalog)
        val routine = saved.routines.first()
        val session = saved.withStartedRoutine(routine.id, catalog, 1234).active!!
        assertEquals(1234, session.startedAt)
        assertEquals(routine.exerciseIds, session.exercises.map { it.exerciseId })
        assertEquals(session.exercises.first().id, session.selectedExerciseId)
        routine.prescriptions.zip(session.exercises).forEach { (target, entry) ->
            assertEquals(target.sets, entry.sets.size)
            assertEquals(target.restSeconds, entry.restSeconds)
            entry.sets.forEach { set ->
                assertEquals(target.repsMin, set.targetRepsMin)
                assertEquals(target.repsMax, set.targetRepsMax)
                assertNull(set.weightKg); assertNull(set.reps); assertNull(set.rir)
                assertNull(set.durationSeconds); assertNull(set.distanceMeters)
                assertFalse(set.completed); assertFalse(set.warmup)
            }
        }
        val active = saved.copy(active = session)
        assertThrows(IllegalArgumentException::class.java) { active.withStartedRoutine(routine.id, catalog, 9999) }
        assertEquals(session, active.active)
        assertNull(saved.active)
    }

    @Test fun editingPreservesTemplateAndScheduleIdentityAndPreviousSessions() {
        val catalog = catalog()
        val saved = AppData().withStarterSplit(starterTemplateSplits(catalog)[1], catalog)
        val before = saved.routines.first()
        val performed = before.toWorkout(1, catalog = catalog).copy(finishedAt = 2)
        val edit = before.copy(name = " My Upper ", prescriptions = before.prescriptions.mapIndexed { index, p ->
            if (index == 0) p.copy(sets = 2, repsMin = 5, repsMax = 7, restSeconds = 150) else p
        })
        val changed = saved.copy(history = listOf(performed)).withSavedRoutine(edit, catalog)
        assertEquals(saved.routines.size, changed.routines.size)
        assertEquals("My Upper", changed.routines.first().name)
        assertEquals(before.id, changed.routines.first().id)
        assertEquals(saved.templateSplits, changed.templateSplits)
        assertEquals(performed, changed.history.single())
        assertEquals(2, changed.withStartedRoutine(before.id, catalog, 3).active!!.exercises.first().sets.size)
        assertEquals(3, saved.routines.first().prescriptions.first().sets)
    }

    @Test fun legacyRoutinesRemainOneSetWithNoTargetsAndRoundTripNewPrescriptions() {
        val legacy = """{"schemaVersion":1,"routines":[{"id":"old","name":"Legacy","exerciseIds":["bench"]}]}"""
        val loaded = DataCodec.decode(legacy)
        assertTrue(loaded.templateSplits.isEmpty())
        assertTrue(loaded.routines.single().prescriptions.isEmpty())
        val session = loaded.routines.single().toWorkout(1)
        assertEquals(1, session.exercises.single().sets.size)
        assertNull(session.exercises.single().sets.single().targetRepsMin)
        val duplicatedLegacy = legacy.replace("[\"bench\"]", "[\"bench\",\"bench\"]")
        assertEquals(2, DataCodec.decode(duplicatedLegacy).routines.single().toWorkout(1).exercises.size)
        val catalog = catalog()
        val combined = loaded.withStarterSplit(starterTemplateSplits(catalog)[0], catalog)
        assertEquals(combined, DataCodec.decode(DataCodec.encode(combined)))
        val editorCopy = TemplateCodec.decodeRoutine(TemplateCodec.encodeRoutine(combined.routines[1]))
        assertEquals(combined.routines[1], editorCopy)
    }

    @Test fun invalidTargetsAndMissingLinksCannotBeSavedOrImported() {
        val exercise = Exercise("bench", "Bench", "Chest", "Barbell")
        val valid = Routine(name = "Upper", exerciseIds = listOf("bench"), prescriptions = listOf(TemplateExercise("bench")))
        val bad = listOf(
            valid.copy(name = " "), valid.copy(exerciseIds = emptyList()),
            valid.copy(prescriptions = listOf(TemplateExercise("bench", sets = 0))),
            valid.copy(prescriptions = listOf(TemplateExercise("bench", repsMin = 12, repsMax = 8))),
            valid.copy(prescriptions = listOf(TemplateExercise("bench", restSeconds = 1801))),
            valid.copy(prescriptions = listOf(TemplateExercise("wrong"))),
        )
        bad.forEach { assertNotNull(it.validationError(listOf(exercise))) }
        val original = AppData(routines = listOf(valid))
        assertThrows(IllegalArgumentException::class.java) { original.withSavedRoutine(bad[2], listOf(exercise)) }
        assertEquals(valid, original.routines.single())
        val missing = JSONObject(DataCodec.encode(original)).put("templateSplits", JSONArray().put(JSONObject()
            .put("id", "split").put("name", "Missing").put("days", JSONArray(listOf("absent", JSONObject.NULL, JSONObject.NULL, JSONObject.NULL, JSONObject.NULL, JSONObject.NULL, JSONObject.NULL)))))
        assertThrows(IllegalArgumentException::class.java) { DataCodec.decode(missing.toString()) }
    }

    @Test fun selectedTemplateExportIncludesPrescriptionsAndScheduleButNoUnselectedRecords() {
        val catalog = catalog()
        val data = AppData(preferences = Preferences(displayName = "PRIVATE"), history = listOf(Workout(name = "PRIVATE")))
            .withStarterSplit(starterTemplateSplits(catalog)[2], catalog)
        val payload = ScopedDataExport.document(data, catalog, ScopedExportRequest(false, ExportTimeFrame.ALL_TIME,
            setOf(ExportCategory.WORKOUTS)), LocalDate.of(2026, 10, 29)).getJSONObject("data")
        assertEquals(setOf("workouts", "templateSplits"), payload.keys().asSequence().toSet())
        assertEquals(5, payload.getJSONArray("workouts").length())
        assertEquals(7, payload.getJSONArray("templateSplits").getJSONObject(0).getJSONArray("days").length())
        assertTrue(payload.getJSONArray("workouts").getJSONObject(0).getJSONArray("prescriptions").length() > 0)
        assertFalse(payload.toString().contains("PRIVATE"))
        val other = ScopedDataExport.document(data, catalog, ScopedExportRequest(false, ExportTimeFrame.ALL_TIME,
            setOf(ExportCategory.USER_PROFILE)), LocalDate.of(2026, 10, 29)).getJSONObject("data")
        assertFalse(other.has("templateSplits")); assertFalse(other.has("workouts"))
    }
}
