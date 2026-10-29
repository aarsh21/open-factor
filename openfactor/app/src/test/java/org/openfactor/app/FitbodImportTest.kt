package org.openfactor.app

import java.io.File
import java.time.OffsetDateTime
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FitbodImportTest {
    private val bench = Exercise("bench", "Barbell Bench Press", "Chest", "Barbell")
    private val row = linkedMapOf(
        "date_local" to "2026-10-19T19:22:42+05:30", "date_utc" to "2026-10-19T13:52:42+00:00",
        "workout_name" to "Strength", "exercise_name" to bench.name, "set_number" to "1", "reps" to "10",
        "weight_kg" to "25", "is_warmup" to "False", "is_amrap" to "False", "rest_seconds" to "75",
        "set_duration_raw" to "", "distance_m" to "", "notes" to "", "workout_id" to "123",
        "exercise_external_resource_id" to "29", "exercise_position" to "0", "circuit_position" to "", "set_id" to "set-1",
    )
    private fun entry(vararg changes: Pair<String, String>) = row + changes.toMap()
    private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    private fun csv(rows: List<Map<String, String>> = listOf(row), columns: List<String> = row.keys.toList()): String =
        (listOf(columns.joinToString(",")) + rows.map { source -> columns.joinToString(",") { quote(source.getValue(it)) } }).joinToString("\r\n")
    private fun read(vararg entries: Map<String, String>) = FitbodImport.read(csv(entries.toList().ifEmpty { listOf(row) }).byteInputStream())
    private fun materialize(preview: FitbodPreview = read(), catalog: List<Exercise> = listOf(bench)) =
        preview.materializeHistory(mapOf(bench.name to bench.id), emptySet(), catalog)
    private fun rejects(message: String, block: () -> Unit) {
        try { block(); fail("Expected rejection containing '$message'") }
        catch (failure: Exception) { assertTrue("Unexpected error: ${failure.message}", failure.message.orEmpty().contains(message, true)) }
    }

    @Test fun importsActualCompletedStrengthSetsWithOffsetDateAndNoInventedDuration() {
        val preview = read(entry("is_warmup" to "True"), entry("set_id" to "set-2", "set_number" to "2", "reps" to "8", "weight_kg" to "27.5"))
        assertEquals(2, preview.setCount)
        val workout = materialize(preview).single()
        val instant = OffsetDateTime.parse(row.getValue("date_utc")).toInstant().toEpochMilli()
        assertEquals(instant, workout.startedAt)
        assertEquals(instant, workout.finishedAt)
        assertEquals(0L, workout.elapsedSeconds(instant + 10000))
        assertTrue(workout.notes.contains("duration unavailable"))
        assertTrue(workout.notes.contains(row.getValue("date_local")))
        val sets = workout.exercises.single().sets
        assertEquals(25.0, sets[0].weightKg!!, 0.0)
        assertEquals(27.5, sets[1].weightKg!!, 0.0)
        assertEquals(10, sets[0].reps)
        assertTrue(sets[0].warmup)
        assertFalse(sets[1].warmup)
        assertTrue(sets.all { it.completed && it.rir == null && it.targetRepsMin == null && it.targetRir == null })
        assertEquals(75, workout.exercises.single().restSeconds)
        assertNull(workout.programId)
    }

    @Test fun csvSupportsBomQuotedCommasEscapedQuotesNewlinesAndReorderedColumns() {
        val notes = "Steady, \"controlled\"\r\n=not a formula"
        val source = csv(listOf(entry("workout_name" to "Upper, \"A\"", "notes" to notes)), row.keys.reversed())
        val preview = FitbodImport.read((0xfeff.toChar().toString() + source + "\r\n\r\n").byteInputStream())
        assertEquals("Upper, \"A\"", preview.workouts.single().name)
        assertEquals(notes, preview.workouts.single().exercises.single().sets.single().notes)
        assertTrue(materialize(preview).single().exercises.single().notes.contains(notes))
    }

    @Test fun plainCsvAndLfAndEofAreSupported() {
        val source = row.keys.joinToString(",") + "\n" + row.values.joinToString(",")
        assertEquals(1, FitbodImport.read(source.byteInputStream()).setCount)
    }

    @Test fun workoutExerciseAndSetOrderUsesIdentitiesAndPositionsNotCsvOrder() {
        val preview = read(
            entry("exercise_position" to "3", "set_id" to "set-3", "set_number" to "2"),
            entry("exercise_position" to "3", "set_id" to "set-2"),
            entry("exercise_position" to "0", "set_id" to "set-1"),
            entry("workout_id" to "older", "set_id" to "set-4", "date_utc" to "2025-01-01T00:00:00Z", "date_local" to "2025-01-01T05:30:00+05:30"),
        )
        assertEquals(listOf("123", "older"), preview.workouts.map { it.sourceId })
        assertEquals(listOf(0, 3), preview.workouts.first().exercises.map { it.position })
        assertEquals(listOf(1, 2), preview.workouts.first().exercises.last().sets.map { it.number })
        val workouts = materialize(preview)
        assertEquals(2, workouts.first().exercises.size)
        assertEquals(2, workouts.first().exercises.map { it.id }.distinct().size)
    }

    @Test fun amrapVariableRestNotesAndCircuitArePreservedWithoutInventingRirOrGroups() {
        val preview = read(entry("is_amrap" to "True", "rest_seconds" to "45", "circuit_position" to "0", "notes" to "Warm-up note"),
            entry("set_id" to "set-2", "set_number" to "2", "circuit_position" to "0", "rest_seconds" to ""))
        val exercise = materialize(preview).single().exercises.single()
        assertNull(exercise.restSeconds)
        assertNull(exercise.supersetId)
        assertTrue(exercise.sets.all { it.rir == null && it.kind == SetKind.STANDARD })
        assertTrue(exercise.notes.contains("Fitbod set 1: AMRAP · rest 45 seconds · Warm-up note"))
        assertTrue(exercise.notes.contains("Fitbod set 2: rest unspecified"))
        assertTrue(exercise.notes.contains("circuit position: 0"))
        assertTrue(preview.warnings.any { "AMRAP" in it })
        assertTrue(preview.warnings.any { "Rest varies" in it })
        assertTrue(preview.warnings.any { "Circuit" in it })
    }

    @Test fun blankWorkoutNamesAndZeroWeightAreValid() {
        val workout = materialize(read(entry("workout_name" to "", "weight_kg" to "0"))).single()
        assertEquals("Fitbod workout", workout.name)
        assertEquals(0.0, workout.exercises.single().sets.single().weightKg!!, 0.0)
    }

    @Test fun stableIdentitiesSurviveCodecRoundTripAndRepeatImportsPreserveLocalEdits() {
        val imported = AppData().withFitbodHistory(materialize(), listOf(bench))
        val restored = DataCodec.decode(DataCodec.encode(imported))
        assertEquals(imported, restored)
        assertEquals(materialize(), materialize())
        val edited = restored.copy(history = restored.history.map { it.copy(name = "Locally corrected", notes = "Keep this") })
        assertEquals(edited, edited.withFitbodHistory(materialize(), listOf(bench)))
        assertTrue(read().pending(edited.history).workouts.isEmpty())
    }

    @Test fun appendIsHistoryOnlyAndDeduplicatesByWorkoutIdNotDateOrName() {
        val imported = materialize().single()
        val previous = Workout(name = imported.name, startedAt = imported.startedAt, finishedAt = imported.finishedAt)
        val original = AppData(active = Workout(name = "In progress"), history = listOf(previous),
            routines = listOf(Routine(name = "Keep template", exerciseIds = listOf(bench.id))),
            programs = listOf(TrainingProgram(name = "Keep program")), activeProgramId = "keep",
            programDraft = TrainingProgram(name = "Keep draft"), workoutDraft = WorkoutDraft(),
            pastWorkoutEdit = PastWorkoutEdit(previous), customExercises = listOf(bench.copy(custom = true)), favorites = setOf(bench.id))
        val appended = original.withFitbodHistory(listOf(imported), listOf(bench))
        assertEquals(original, appended.copy(history = original.history))
        assertEquals(2, appended.history.size)
        assertTrue(previous in appended.history)
        assertEquals(1, appended.history.count { it.id == imported.id })
        val overlap = read(row, entry("workout_id" to "another", "set_id" to "set-2"))
        assertEquals(listOf("another"), overlap.pending(appended.history).workouts.map { it.sourceId })
    }

    @Test fun choicesMustBeExplicitAndSkippingMayOmitWholeWorkouts() {
        rejects("Resolve or explicitly skip") { read().materializeHistory(emptyMap(), emptySet(), listOf(bench)) }
        assertTrue(read().materializeHistory(emptyMap(), setOf(bench.name), listOf(bench)).isEmpty())
        val preview = read(row, entry("exercise_name" to "Unknown movement", "exercise_external_resource_id" to "999", "exercise_position" to "1", "set_id" to "set-2"))
        val workouts = preview.materializeHistory(mapOf(bench.name to bench.id), setOf("Unknown movement"), listOf(bench))
        assertEquals(1, workouts.single().exercises.size)
        rejects("unavailable") { materialize(catalog = listOf(bench.copy(archived = true))) }
    }

    @Test fun incompatibleMetricsCannotSilentlyHideOrReinterpretActuals() {
        listOf("Duration", "Assistance weight, Reps", "Reps", "Weight, Reps, Duration", "None").forEach { metrics ->
            rejects(if (metrics == "Weight, Reps, Duration") "requires metrics" else "cannot represent") {
                materialize(catalog = listOf(bench.copy(properties = mapOf("Metrics" to metrics))))
            }
        }
        val perSide = materialize(catalog = listOf(bench.copy(properties = mapOf("Metrics" to "Weight per side, Reps per side")))).single()
        assertEquals(25.0, perSide.exercises.single().sets.single().weightKg!!, 0.0)
    }

    @Test fun appendRevalidatesCatalogAndRejectsInvalidOrCollidingEntriesAtomically() {
        val workout = materialize().single()
        rejects("unavailable") { AppData().withFitbodHistory(listOf(workout), emptyList()) }
        rejects("identities") { AppData().withFitbodHistory(listOf(workout, workout), listOf(bench)) }
        rejects("history entry") { AppData(active = workout).withFitbodHistory(listOf(workout), listOf(bench)) }
        rejects("history entry") { AppData().withFitbodHistory(listOf(workout.copy(finishedAt = null)), listOf(bench)) }
        rejects("completed") {
            AppData().withFitbodHistory(listOf(workout.copy(exercises = workout.exercises.map { it.copy(sets = listOf(LoggedSet())) })), listOf(bench))
        }
    }

    @Test fun malformedQuotingHeadersAndColumnCountsAreRejected() {
        rejects("18-column header") { FitbodImport.read("exercise,sets,reps\nBench,3,8".byteInputStream()) }
        rejects("18-column header") { FitbodImport.read(csv(columns = row.keys.toList().dropLast(1)).byteInputStream()) }
        rejects("18-column header") { FitbodImport.read(csv().replace("date_local", "date_utc").byteInputStream()) }
        rejects("Unterminated") { FitbodImport.read((csv() + "\n\"oops").byteInputStream()) }
        rejects("quoting") { FitbodImport.read("abc\"oops".byteInputStream()) }
        rejects("after a quoted") { FitbodImport.read("\"abc\"oops".byteInputStream()) }
        rejects("expected 18 columns") { FitbodImport.read((row.keys.joinToString(",") + "\na,b").byteInputStream()) }
        rejects("too many columns") { FitbodImport.read((csv() + ",extra").byteInputStream()) }
        rejects("no strength sets") { FitbodImport.read(row.keys.joinToString(",").byteInputStream()) }
    }

    @Test fun invalidDatesNumbersFlagsAndIdentitiesAreRejected() {
        listOf("weight_kg" to "NaN", "weight_kg" to "Infinity", "weight_kg" to "-1", "weight_kg" to "5001",
            "reps" to "0", "reps" to "2.5", "reps" to "10000", "reps" to "", "rest_seconds" to "1801",
            "rest_seconds" to "-1", "set_number" to "0", "exercise_position" to "100", "circuit_position" to "text",
            "is_warmup" to "maybe", "is_amrap" to "", "workout_id" to "", "set_id" to "a:b", "exercise_name" to "",
            "date_utc" to "2026-02-30T13:52:42Z", "date_local" to "not-a-date").forEach { change ->
            rejects(change.first) { read(entry(change)) }
        }
        rejects("timestamps disagree") { read(entry("date_local" to "2026-10-19T19:22:43+05:30")) }
        rejects("duration/distance") { read(entry("set_duration_raw" to "00:30")) }
        rejects("duration/distance") { read(entry("distance_m" to "100")) }
    }

    @Test fun duplicateAndConflictingSourceRowsAreNeverSilentlyDiscarded() {
        rejects("duplicate set_id") { read(row, row) }
        rejects("duplicate set_number") { read(row, entry("set_id" to "set-2")) }
        rejects("conflicting name or timestamp") { read(row, entry("set_id" to "set-2", "workout_name" to "Other")) }
        rejects("conflicting name or timestamp") { read(row, entry("set_id" to "set-2", "date_utc" to "2025-01-01T00:00:00Z", "date_local" to "2025-01-01T00:00:00Z")) }
        rejects("conflicting exercise position") { read(row, entry("set_id" to "set-2", "exercise_name" to "Other")) }
        rejects("conflicting exercise position") { read(row, entry("set_id" to "set-2", "exercise_external_resource_id" to "other")) }
    }

    @Test fun fileFieldSetWorkoutAndEncodingBoundsAreEnforced() {
        rejects("4 MiB") { FitbodImport.read(ByteArray(FitbodImport.MAX_FILE_BYTES + 1).inputStream()) }
        rejects("4096") { read(entry("notes" to "x".repeat(4097))) }
        rejects("NUL") { read(entry("notes" to "\u0000")) }
        rejects("Input length") { FitbodImport.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream()) }
        rejects("100 sets") { read(*(1..101).map { entry("set_id" to "s$it", "set_number" to "$it") }.toTypedArray()) }
        rejects("2,000 workouts") { read(*(1..2001).map { entry("set_id" to "s$it", "workout_id" to "w$it") }.toTypedArray()) }
        val short = entry("date_local" to "2026-01-01T00:00:00Z", "date_utc" to "2026-01-01T00:00:00Z", "workout_name" to "", "exercise_name" to "A")
        val tooMany = row.keys.joinToString(",") + "\n" + (short.values.joinToString(",") + "\n").repeat(20001)
        rejects("20,000 sets") { FitbodImport.read(tooMany.byteInputStream()) }
    }

    /** Private attachment remains outside the repository; opt in explicitly to exercise the real export. */
    @Test fun suppliedFitbodCsvSmokeTestWhenExplicitlyProvided() {
        val path = System.getenv("OPENFACTOR_FITBOD_SAMPLE")
        assumeTrue("No private sample requested", path != null)
        val preview = File(path!!).inputStream().use(FitbodImport::read)
        assertEquals(290, preview.workouts.size)
        assertEquals(5299, preview.setCount)
        assertEquals(59, preview.exerciseNames.size)
        val catalog = preview.exerciseNames.mapIndexed { i, name -> Exercise("sample-$i", name, "Test", "Test") }
        val choices = catalog.associate { it.name to it.id }
        val workouts = preview.materializeHistory(choices, emptySet(), catalog)
        assertEquals(5299, workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } })
        assertTrue(workouts.all { it.startedAt == it.finishedAt && it.exercises.all { e -> e.sets.all { s -> s.completed } } })
        val data = AppData().withFitbodHistory(workouts, catalog)
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
        assertEquals(data, data.withFitbodHistory(workouts, catalog))
        assertTrue(preview.pending(data.history).workouts.isEmpty())
    }
}
