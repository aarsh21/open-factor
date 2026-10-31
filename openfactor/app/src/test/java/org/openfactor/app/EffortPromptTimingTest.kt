package org.openfactor.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EffortPromptTimingTest {
    private val metrics = listOf(SetMetric.WEIGHT, SetMetric.REPS)
    private val prefs = Preferences()

    private fun set(id: String, completed: Boolean = true) = LoggedSet(id = id, weightKg = 60.0, reps = 8, completed = completed)
    private fun entry(id: String, vararg sets: LoggedSet, superset: String? = null) =
        SessionExercise(id = id, exerciseId = "bench-$id", sets = sets.toList(), supersetId = superset)

    /** What the view model does for every change: apply it, then the effort transition. */
    private fun Workout.step(preferences: Preferences = prefs, change: Workout.() -> Workout): Workout =
        change().withEffortPromptTransition(this, preferences) { metrics }

    private fun Workout.select(id: String) = copy(selectedExerciseId = id)

    private val done = Workout(
        name = "Upper",
        exercises = listOf(entry("a", set("a1"), set("a2")), entry("b", set("b1", completed = false))),
        selectedExerciseId = "a",
    )

    @Test fun completingTheLastSetAsksNothingUntilTheLifterMovesOn() {
        val start = done.copy(exercises = listOf(entry("a", set("a1"), set("a2", completed = false)), done.exercises[1]))
        val logged = start.step(prefs.copy(exerciseAutoNext = false)) { toggleSetCompletion("a", "a2", prefs.copy(exerciseAutoNext = false), 1_000, metrics) }
        assertEquals("a", logged.selectedExerciseId)
        assertEquals(emptyList<EffortPrompt>(), logged.effortPrompts)
        // Leaving asks about the exercise just left, not the one arrived at.
        val moved = logged.step { select("b") }
        assertEquals(listOf(EffortPrompt("a")), moved.effortPrompts)
    }

    @Test fun leavingWithoutAnUnratedLastLogAsksNothing() {
        val unfinished = done.copy(exercises = listOf(entry("a", set("a1"), set("a2", completed = false)), done.exercises[1]))
        assertTrue(unfinished.step { select("b") }.effortPrompts.isEmpty())
        val rated = done.copy(exercises = listOf(done.exercises[0].withReviewedEffort("a2", 2), done.exercises[1]))
        assertTrue(rated.step { select("b") }.effortPrompts.isEmpty())
        val skipped = done.copy(exercises = listOf(done.exercises[0].withReviewedEffort("a2", null), done.exercises[1]))
        assertTrue(skipped.step { select("b") }.effortPrompts.isEmpty())
        assertTrue(done.step(prefs.copy(rateEffortAfterExercise = false)) { select("b") }.effortPrompts.isEmpty())
        // The previous timing asks at completion instead; nothing is recorded on leave.
        assertTrue(done.step(prefs.copy(effortPromptTiming = EffortPromptTiming.LAST_SET)) { select("b") }.effortPrompts.isEmpty())
        // Staying put (typing, notes) is not leaving.
        assertTrue(done.step { exerciseNotesOf("a", "felt heavy") }.effortPrompts.isEmpty())
    }

    @Test fun automaticAdvanceAfterTheLastSetWaitsForRest() {
        val start = done.copy(exercises = listOf(entry("a", set("a1"), set("a2", completed = false)), done.exercises[1]))
        val advanced = start.step { toggleSetCompletion("a", "a2", prefs, 1_000, metrics) }
        assertEquals("b", advanced.selectedExerciseId)
        assertEquals(listOf(EffortPrompt("a", afterRest = true)), advanced.effortPrompts)
        // Log all sets advances the same way.
        val bulk = start.step { withAllSetsCompleted("a", prefs, 1_000, metrics) }
        assertEquals(listOf(EffortPrompt("a", afterRest = true)), bulk.effortPrompts)
    }

    @Test fun supersetRotationAsksOnlyOnceAnExerciseIsDone() {
        val group = Workout(
            name = "Pair",
            exercises = listOf(
                entry("a", set("a1", completed = false), set("a2", completed = false), superset = "s"),
                entry("b", set("b1", completed = false), set("b2", completed = false), superset = "s"),
            ),
            selectedExerciseId = "a",
        )
        val round1 = group.step { toggleSetCompletion("a", "a1", prefs, 1_000, metrics) }
        assertEquals("b", round1.selectedExerciseId)
        assertTrue(round1.effortPrompts.isEmpty())
        val back = round1.step { toggleSetCompletion("b", "b1", prefs, 2_000, metrics) }
            .step { toggleSetCompletion("a", "a2", prefs, 3_000, metrics) }
        assertEquals("b", back.selectedExerciseId)
        assertEquals(listOf(EffortPrompt("a", afterRest = true)), back.effortPrompts)
        // With nowhere left to go the logger stays on B: B is asked only once it is left (or at Finish).
        val finished = back.step { toggleSetCompletion("b", "b2", prefs, 4_000, metrics) }
        assertEquals("b", finished.selectedExerciseId)
        assertEquals(listOf(EffortPrompt("a", afterRest = true)), finished.effortPrompts)
        // Two owed questions queue in the order the exercises were left.
        val third = finished.copy(exercises = finished.exercises + entry("c", set("c1", completed = false)))
        assertEquals(listOf(EffortPrompt("a", afterRest = true), EffortPrompt("b")), third.step { select("c") }.effortPrompts)
    }

    @Test fun hoppingBackAndForthNeverAsksTwice() {
        val advanced = done.copy(exercises = listOf(entry("a", set("a1"), set("a2", completed = false)), done.exercises[1]))
            .step { toggleSetCompletion("a", "a2", prefs, 1_000, metrics) }
        // Returning before answering withdraws the question: the lifter has not left after all.
        val returned = advanced.step { select("a") }
        assertTrue(returned.effortPrompts.isEmpty())
        val left = returned.step { select("b") }
        assertEquals(listOf(EffortPrompt("a")), left.effortPrompts)
        // Done/Skip retires it; later hops ask nothing.
        val skipped = left.step { copy(exercises = exercises.map { if (it.id == "a") it.withReviewedEffort("a2", null) else it }) }
        assertTrue(skipped.effortPrompts.isEmpty())
        assertTrue(skipped.step { select("a") }.step { select("b") }.step { select("a") }.effortPrompts.isEmpty())
    }

    @Test fun aNewSetAfterRatingAsksAgainForTheNewLastLog() {
        val rated = done.copy(exercises = listOf(done.exercises[0].withReviewedEffort("a2", 1), done.exercises[1]))
        val added = rated.step { copy(exercises = exercises.map { if (it.id == "a") it.withNewSet() else it }) }
        val newId = added.exercises[0].sets.last().id
        assertTrue(added.step { select("b") }.effortPrompts.isEmpty())
        val completed = added.step { editedSet("a", newId) { it.copy(weightKg = 60.0, reps = 6) } }
            .step { toggleSetCompletion("a", newId, prefs.copy(exerciseAutoNext = false), 1_000, metrics) }
        assertEquals(listOf(EffortPrompt("a")), completed.step { select("b") }.effortPrompts)
        // Un-completing the last log before answering retires the owed question.
        val owed = completed.step { select("b") }
        assertTrue(owed.step { toggleSetCompletion("a", newId, prefs, 2_000, metrics) }.effortPrompts.isEmpty())
    }

    @Test fun removedOrSwappedExercisesAreNeverAsked() {
        val removed = done.step { copy(exercises = exercises.filterNot { it.id == "a" }) }
        assertTrue(removed.effortPrompts.isEmpty())
        val owed = done.step { select("b") }
        assertTrue(owed.step { copy(exercises = exercises.filterNot { it.id == "a" }) }.effortPrompts.isEmpty())
        // A different workout (restore, new session) carries nothing over.
        assertEquals(done, done.withEffortPromptTransition(owed.copy(id = "other"), prefs) { metrics })
    }

    @Test fun finishAsksForTheCurrentExerciseAndAnythingStillWaitingForRest() {
        assertEquals(listOf(EffortPrompt("a")), done.withEffortPromptsBeforeFinish(prefs) { metrics }.effortPrompts)
        val waiting = done.copy(exercises = listOf(entry("a", set("a1"), set("a2", completed = false)), entry("b", set("b1", completed = false))))
            .step { toggleSetCompletion("a", "a2", prefs, 1_000, metrics) }
            .step { toggleSetCompletion("b", "b1", prefs.copy(exerciseAutoNext = false), 2_000, metrics) }
        assertEquals(listOf(EffortPrompt("a", afterRest = true)), waiting.effortPrompts)
        assertEquals(listOf(EffortPrompt("a"), EffortPrompt("b")), waiting.withEffortPromptsBeforeFinish(prefs) { metrics }.effortPrompts)
        assertEquals(done, done.withEffortPromptsBeforeFinish(prefs.copy(effortPromptTiming = EffortPromptTiming.LAST_SET)) { metrics })
        assertEquals(done, done.withEffortPromptsBeforeFinish(prefs.copy(rateEffortAfterExercise = false)) { metrics })
        val unfinished = done.select("b")
        assertTrue(unfinished.withEffortPromptsBeforeFinish(prefs) { metrics }.effortPrompts.isEmpty())
    }

    @Test fun timingAndOwedQuestionsRoundTripAndOlderBackupsGetTheNewDefault() {
        assertEquals(EffortPromptTiming.NEXT_EXERCISE, Preferences().effortPromptTiming)
        val data = AppData(
            preferences = Preferences(effortPromptTiming = EffortPromptTiming.LAST_SET),
            active = done.copy(effortPrompts = listOf(EffortPrompt("a", afterRest = true))),
        )
        val encoded = JSONObject(DataCodec.encode(data))
        assertEquals("LAST_SET", encoded.getJSONObject("preferences").getString("effortPromptTiming"))
        assertEquals(data, DataCodec.decode(encoded.toString()))
        encoded.getJSONObject("preferences").remove("effortPromptTiming")
        encoded.getJSONObject("active").remove("effortPrompts")
        val legacy = DataCodec.decode(encoded.toString())
        assertEquals(EffortPromptTiming.NEXT_EXERCISE, legacy.preferences.effortPromptTiming)
        assertTrue(legacy.active!!.effortPrompts.isEmpty())
        encoded.getJSONObject("preferences").put("effortPromptTiming", "SOMETHING_NEWER")
        assertEquals(EffortPromptTiming.NEXT_EXERCISE, DataCodec.decode(encoded.toString()).preferences.effortPromptTiming)
        // Nothing owed writes nothing, so ordinary sessions and history keep their exact shape.
        assertFalse(JSONObject(DataCodec.encode(AppData(active = done))).getJSONObject("active").has("effortPrompts"))
        val scoped = ScopedDataExport.document(
            data, emptyList(), ScopedExportRequest(false, categories = setOf(ExportCategory.WORKOUT_SETTINGS)),
            java.time.LocalDate.of(2026, 10, 31), java.time.ZoneOffset.UTC,
        ).getJSONObject("data").getJSONObject("workoutSettings")
        assertEquals("LAST_SET", scoped.getString("effortPromptTiming"))
    }

    private fun Workout.exerciseNotesOf(id: String, notes: String) =
        copy(exercises = exercises.map { if (it.id == id) it.copy(notes = notes) else it })

    private fun Workout.editedSet(id: String, setId: String, block: (LoggedSet) -> LoggedSet) =
        copy(exercises = exercises.map { e -> if (e.id == id) e.copy(sets = e.sets.map { if (it.id == setId) block(it) else it }) else e })
}
