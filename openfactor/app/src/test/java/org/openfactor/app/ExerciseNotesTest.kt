package org.openfactor.app

import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExerciseNotesTest {
    private fun catalog(): List<Exercise> {
        val source = File("src/main/assets/catalog.json").takeIf { it.isFile } ?: File("app/src/main/assets/catalog.json")
        val array = JSONArray(source.readText())
        return (0 until array.length()).map { index ->
            val p = array.getJSONObject(index)
            Exercise(p.getString("id"), p.getString("name"), p.optString("muscle", "Other"), p.optString("equipment", "Other"))
        }
    }

    private fun entry(exerciseId: String, notes: String = "", warmupOnly: Boolean = false) = SessionExercise(
        exerciseId = exerciseId, notes = notes,
        sets = listOf(LoggedSet(weightKg = 20.0, reps = 5, completed = true, warmup = warmupOnly)),
    )

    private fun done(id: String, startedAt: Long, vararg entries: SessionExercise) =
        Workout(id = id, name = id, startedAt = startedAt, finishedAt = startedAt + 1, exercises = entries.toList())

    @Test fun templateNoteSessionSourceAndDismissalRoundTrip() {
        val routine = Routine("upper", "Upper A", listOf("bench", "row"), listOf(
            TemplateExercise("bench", note = "Go heavy 💪 — top set first"), TemplateExercise("row"),
        ))
        val active = routine.toWorkout(10).let { w ->
            w.copy(exercises = w.exercises.map { if (it.exerciseId == "bench") it.copy(lastNoteDismissed = true) else it })
        }
        val data = AppData(routines = listOf(routine), active = active, history = listOf(active.copy(id = "past", finishedAt = 20)))
        val decoded = DataCodec.decode(DataCodec.encode(data))
        assertEquals(data, decoded)
        assertEquals("upper", decoded.active!!.routineId)
        assertTrue(decoded.active!!.exercises.first().lastNoteDismissed)
        assertFalse(decoded.active!!.exercises.last().lastNoteDismissed)
        assertEquals(routine, TemplateCodec.decodeRoutine(TemplateCodec.encodeRoutine(routine)))
    }

    @Test fun olderDocumentsWithoutNoteFieldsKeepWorkingAndBlankFieldsStayOut() {
        val old = """{"schemaVersion":1,
            "routines":[{"id":"r","name":"Upper","exerciseIds":["bench"],
                "prescriptions":[{"exerciseId":"bench","sets":3,"repsMin":8,"repsMax":12,"restSeconds":120}]}],
            "active":{"id":"w","name":"Upper","startedAt":1,"exercises":[{"id":"e","exerciseId":"bench","sets":[],"notes":""}]}}"""
        val loaded = DataCodec.decode(old)
        assertEquals("", loaded.routines.single().prescriptions.single().note)
        assertNull(loaded.active!!.routineId)
        assertFalse(loaded.active!!.exercises.single().lastNoteDismissed)
        val encoded = JSONObject(DataCodec.encode(loaded))
        assertFalse(encoded.getJSONArray("routines").getJSONObject(0).getJSONArray("prescriptions").getJSONObject(0).has("note"))
        assertFalse(encoded.getJSONObject("active").has("routineId"))
        assertFalse(encoded.getJSONObject("active").getJSONArray("exercises").getJSONObject(0).has("lastNoteDismissed"))
        val explicitNull = old.replace("\"restSeconds\":120}", "\"restSeconds\":120,\"note\":null}")
        assertEquals("", DataCodec.decode(explicitNull).routines.single().prescriptions.single().note)
        val tooLong = old.replace("\"restSeconds\":120}", "\"restSeconds\":120,\"note\":\"${"x".repeat(TEMPLATE_NOTE_LIMIT + 1)}\"}")
        assertThrows(IllegalArgumentException::class.java) { DataCodec.decode(tooLong) }
    }

    @Test fun lastNoteIsTheNewestNonBlankSavedNoteForThatExercise() {
        val history = listOf(
            // Newest-first is the usual order, but dates win: edited workouts can move.
            done("blank", 500, entry("bench", "   ")),
            done("other", 400, entry("row", "Row note")),
            done("older", 100, entry("bench", "Older")),
            done("newest", 300, entry("bench", "  Elbows tucked  ")),
            done("middle", 200, entry("bench", "Middle")),
        )
        assertEquals(LastExerciseNote("Elbows tucked", "newest", 300), lastExerciseNote(history, "bench"))
        assertEquals("Middle", lastExerciseNote(history, "bench", excludeWorkoutId = "newest")!!.text)
        assertEquals("Row note", lastExerciseNote(history, "row")!!.text)
        assertNull(lastExerciseNote(history, "squat"))
        assertNull(lastExerciseNote(emptyList(), "bench"))
    }

    @Test fun lastNoteIgnoresTheCurrentWorkoutAndKeepsWarmupOnlyNotes() {
        val current = done("current", 900, entry("bench", "Typing today"))
        val warmupOnly = done("warmup", 800, entry("bench", "Stopped after warm-up, shoulder", warmupOnly = true))
        val history = listOf(current, warmupOnly, done("old", 100, entry("bench", "Old")))
        assertEquals("warmup", lastExerciseNote(history, "bench", excludeWorkoutId = "current")!!.workoutId)
        // The active workout lives outside history, and the same exercise twice keeps the later entry's note.
        val twice = done("twice", 1000, entry("bench", "First block"), entry("bench", ""), entry("bench", "Second block"))
        assertEquals("Second block", lastExerciseNote(listOf(twice, warmupOnly), "bench")!!.text)
        // Equal dates prefer the earlier (newer-saved) history entry.
        assertEquals("a", lastExerciseNote(listOf(done("a", 5, entry("bench", "A")), done("b", 5, entry("bench", "B"))), "bench")!!.workoutId)
    }

    @Test fun lastNoteDateIsShortAndRelativeNearby() {
        val today = LocalDate.of(2026, 10, 31)
        fun at(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000
        assertEquals("Today", lastNoteDate(at(today), today, ZoneOffset.UTC))
        assertEquals("Yesterday", lastNoteDate(at(today.minusDays(1)), today, ZoneOffset.UTC))
        assertTrue(lastNoteDate(at(LocalDate.of(2026, 10, 12)), today, ZoneOffset.UTC).contains("12"))
        assertTrue(lastNoteDate(at(LocalDate.of(2025, 10, 12)), today, ZoneOffset.UTC).contains("2025"))
    }

    @Test fun templateNoteEditsOnlyThatTemplateExerciseAndStartedSessionsReferenceIt() {
        val upperA = Routine("a", "Upper A", listOf("bench", "row"), listOf(TemplateExercise("bench"), TemplateExercise("row")))
        val upperB = upperA.copy(id = "b", name = "Upper B")
        val data = AppData(routines = listOf(upperA, upperB))
        val noted = data.withTemplateNote("a", "bench", "  Go heavy  ")
        assertEquals("Go heavy", noted.routines[0].prescriptions[0].note)
        assertEquals("", noted.routines[0].prescriptions[1].note)
        assertEquals(upperB, noted.routines[1])
        assertEquals(upperA.prescriptions.map { it.copy(note = "") }, noted.routines[0].prescriptions.map { it.copy(note = "") })
        assertEquals("", noted.withTemplateNote("a", "bench", "   ").routines[0].prescriptions[0].note)
        assertEquals("a", noted.routines[0].toWorkout(1).routineId)
        // Notes stay on the template; the session's own notes start empty.
        assertTrue(noted.routines[0].toWorkout(1).exercises.all { it.notes.isEmpty() })
        assertThrows(IllegalArgumentException::class.java) { data.withTemplateNote("missing", "bench", "x") }
        assertThrows(IllegalArgumentException::class.java) { data.withTemplateNote("a", "squat", "x") }
        assertThrows(IllegalArgumentException::class.java) { data.withTemplateNote("a", "bench", "x".repeat(TEMPLATE_NOTE_LIMIT + 1)) }
    }

    @Test fun legacyTemplatesGainEquivalentPrescriptionsForANote() {
        val legacy = Routine("old", "Legacy", listOf("bench", "row"))
        val noted = AppData(routines = listOf(legacy)).withTemplateNote("old", "row", "Slow tempo").routines.single()
        assertEquals(listOf("bench", "row"), noted.prescriptions.map { it.exerciseId })
        assertEquals("Slow tempo", noted.prescriptions[1].note)
        assertNull(noted.validationError())
        val before = legacy.toWorkout(1).exercises.map { it.sets.size to it.restSeconds }
        assertEquals(before, noted.toWorkout(1).exercises.map { it.sets.size to it.restSeconds })
        val repeated = AppData(routines = listOf(Routine("dup", "Dup", listOf("bench", "bench"))))
        assertThrows(IllegalArgumentException::class.java) { repeated.withTemplateNote("dup", "bench", "x") }
    }

    @Test fun savingAnEditedTemplateKeepsItsNotes() {
        val catalog = listOf(Exercise("bench", "Bench", "Chest", "Barbell"))
        val routine = Routine("r", "Upper", listOf("bench"), listOf(TemplateExercise("bench", note = "Pause reps")))
        val saved = AppData().withSavedRoutine(routine.copy(name = " Upper "), catalog)
        assertEquals("Pause reps", saved.routines.single().prescriptions.single().note)
        assertNotNull(routine.copy(prescriptions = listOf(TemplateExercise("bench", note = "x".repeat(TEMPLATE_NOTE_LIMIT + 1)))).validationError(catalog))
    }

    @Test fun starterSplitsInstallWithoutNotesAndEachCopyKeepsItsOwn() {
        val catalog = catalog()
        val preset = starterTemplateSplits(catalog)[1]
        val installed = AppData().withStarterSplit(preset, catalog).withStarterSplit(preset, catalog)
        assertTrue(installed.routines.flatMap { it.prescriptions }.all { it.note.isEmpty() })
        val first = installed.routines[0]
        val copy = installed.routines[4]
        assertEquals(first.name, copy.name)
        val exercise = first.exerciseIds.first()
        val noted = installed.withTemplateNote(first.id, exercise, "Go heavy on Upper A")
        assertEquals("Go heavy on Upper A", noted.routines[0].prescriptions.first().note)
        assertEquals("", noted.routines[4].prescriptions.first().note)
        val session = noted.withStartedRoutine(first.id, catalog, 5).active!!
        assertEquals(first.id, session.routineId)
        assertEquals(noted.routines, DataCodec.decode(DataCodec.encode(noted)).routines)
    }

    @Test fun scopedWorkoutsExportCarriesTemplateNotes() {
        val routine = Routine("r", "Upper", listOf("bench"), listOf(TemplateExercise("bench", note = "Slow tempo")))
        val payload = ScopedDataExport.document(AppData(routines = listOf(routine)), emptyList(),
            ScopedExportRequest(false, ExportTimeFrame.ALL_TIME, setOf(ExportCategory.WORKOUTS)), LocalDate.of(2026, 10, 31))
            .getJSONObject("data")
        val exported = payload.getJSONArray("workouts").getJSONObject(0).getJSONArray("prescriptions").getJSONObject(0)
        assertEquals("Slow tempo", exported.getString("note"))
    }
}
