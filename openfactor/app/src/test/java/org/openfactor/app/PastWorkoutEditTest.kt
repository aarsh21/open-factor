package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PastWorkoutEditTest {
    private val exercise = Exercise("press", "Press", "Chest", "Barbell")
    private val start = LocalDate.of(2026, 1, 10).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val saved = Workout(id = "saved", name = "Upper", startedAt = start, finishedAt = start + 120000,
        programId = "program", programDayId = "day", programCycle = 2,
        exercises = listOf(SessionExercise(id = "entry", exerciseId = "press",
            sets = listOf(LoggedSet(id = "set", weightKg = 40.0, reps = 8, completed = true)))))

    @Test fun editsAreIsolatedDurableAndCommitRecalculatesFromActuals() {
        val active = saved.copy(id = "active", finishedAt = null)
        val initial = AppData(active = active, history = listOf(saved))
        val opened = initial.beginPastWorkoutEdit(saved.id)
        val changed = saved.copy(name = "Corrected", exercises = saved.exercises.map { e ->
            e.copy(sets = e.sets.map { it.copy(weightKg = 42.5, reps = 9, rir = 2) })
        })
        val editing = opened.copy(pastWorkoutEdit = opened.pastWorkoutEdit!!.copy(edited = changed))
        val restored = DataCodec.decode(DataCodec.encode(editing))
        assertEquals(saved, restored.history.single())
        assertEquals(active, restored.active)
        val committed = restored.commitPastWorkoutEdit(listOf(exercise))
        assertNull(committed.pastWorkoutEdit)
        assertEquals(changed, committed.history.single())
        assertEquals(382.5, committed.history.single().volumeKg(), .001)
        assertEquals(saved.id, committed.history.single().id)
        assertEquals(active, committed.active)
    }

    @Test fun conflictsInvalidActualsAndChangedIdentityCannotOverwriteSavedHistory() {
        val opened = AppData(history = listOf(saved)).beginPastWorkoutEdit(saved.id)
        val conflict = opened.copy(history = listOf(saved.copy(notes = "Newer")))
        assertThrows(IllegalArgumentException::class.java) { conflict.commitPastWorkoutEdit(listOf(exercise)) }
        val bad = saved.copy(exercises = saved.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(reps = null) }) })
        assertThrows(IllegalArgumentException::class.java) { opened.copy(pastWorkoutEdit = PastWorkoutEdit(saved, bad)).commitPastWorkoutEdit(listOf(exercise)) }
        assertThrows(IllegalArgumentException::class.java) { opened.copy(pastWorkoutEdit = PastWorkoutEdit(saved, saved.copy(id = "other"))).commitPastWorkoutEdit(listOf(exercise)) }
        assertEquals(saved, opened.history.single())
    }

    @Test fun dateCorrectionPreservesElapsedDurationAndOldBackupsNeedNoDraft() {
        val next = saved.onDate(LocalDate.of(2026, 1, 11), ZoneOffset.UTC)
        assertEquals(saved.elapsedSeconds(saved.finishedAt!!), next.elapsedSeconds(next.finishedAt!!))
        assertEquals(saved.programId, next.programId)
        assertNull(DataCodec.decode(DataCodec.encode(AppData(history = listOf(saved)))).pastWorkoutEdit)
        assertEquals(LocalDate.of(2026, 1, 11), java.time.Instant.ofEpochMilli(next.startedAt).atZone(ZoneOffset.UTC).toLocalDate())
    }
}
