package org.openfactor.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ScopedDataExportTest {
    private val today = LocalDate.of(2024, 3, 1)
    private val zone = ZoneId.of("America/Los_Angeles")
    private fun JSONArray.ids() = (0 until length()).map { getJSONObject(it).getString("id") }

    @Test
    fun advancedExportKeepsRawLinksButCountsLogicalRootsOnly() {
        val entry = SessionExercise(exerciseId = "row", sets = listOf(LoggedSet(id = "root", reps = 7, completed = true)))
            .withSetKind("root", SetKind.MYO).withSplitSides()
        val data = AppData(history = listOf(Workout(name = "Myo", exercises = listOf(entry), finishedAt = 1)))
        val export = ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false, ExportTimeFrame.ALL_TIME,
            setOf(ExportCategory.MUSCLE_GROUPS, ExportCategory.EXERCISES)), today, zone).getJSONObject("data")
        val roots = export.getJSONArray("muscleGroups").getJSONObject(0).getJSONArray("completedWorkingSetIds")
        assertEquals(1, roots.length())
        assertEquals("root", roots.getString(0))
        val rows = export.getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
        assertEquals(4, rows.length())
        assertEquals("root", rows.getJSONObject(1).getString("pairId"))
        assertEquals("root", rows.getJSONObject(3).getString("segmentOf"))
        assertEquals("RIGHT", rows.getJSONObject(3).getString("side"))
    }

    @Test
    fun calendarScopesIncludeBothBoundariesLeapDayAndAllTimeFutureWithoutChangingHiddenValues() {
        val dates = listOf("2024-01-31", "2024-02-01", "2024-02-16", "2024-02-17", "2024-02-23", "2024-02-24", "2024-02-29", "2024-03-01", "2024-03-02")
        val data = AppData(
            scaleWeights = dates.mapIndexed { index, date -> ScaleWeightRecord(date, LocalDate.parse(date), 70.125 + index, if (index == 6) 0.0 else null) },
            bodyMeasurements = dates.mapIndexed { index, date -> BodyMeasurementRecord(date, LocalDate.parse(date), mapOf(BodyMeasurement.WAIST to 81.25 + index, BodyMeasurement.HIPS to 93.75 - index)) },
            preferences = Preferences(dataVisibility = DataVisibility().withVisible("waist", false)),
        )
        val expected = mapOf(
            ExportTimeFrame.SEVEN_DAYS to listOf("2024-02-24", "2024-02-29", "2024-03-01"),
            ExportTimeFrame.FOURTEEN_DAYS to listOf("2024-02-17", "2024-02-23", "2024-02-24", "2024-02-29", "2024-03-01"),
            ExportTimeFrame.THIRTY_DAYS to dates.subList(1, 8),
            ExportTimeFrame.THIS_MONTH to listOf("2024-03-01"),
            ExportTimeFrame.LAST_MONTH to dates.subList(1, 7),
            ExportTimeFrame.ALL_TIME to dates,
        )
        for ((frame, ids) in expected) {
            val document = ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false, frame, setOf(ExportCategory.SCALE_WEIGHT, ExportCategory.BODY_METRICS)), today, zone)
            assertEquals(setOf("scaleWeight", "bodyMetrics"), document.getJSONObject("data").keys().asSequence().toSet())
            assertEquals(ids, document.getJSONObject("data").getJSONArray("scaleWeight").ids())
            assertEquals(ids, document.getJSONObject("data").getJSONArray("bodyMetrics").ids())
            assertFalse(document.has("schemaVersion"))
            assertThrows(IllegalArgumentException::class.java) { DataCodec.decode(document.toString()) }
        }
        val all = ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false, ExportTimeFrame.ALL_TIME, setOf(ExportCategory.BODY_METRICS)), today, zone)
        assertEquals(81.25, all.getJSONObject("data").getJSONArray("bodyMetrics").getJSONObject(0).getJSONObject("centimeters").getDouble("waist"), 0.0)
        assertEquals(93.75, all.getJSONObject("data").getJSONArray("bodyMetrics").getJSONObject(0).getJSONObject("centimeters").getDouble("hips"), 0.0)
        assertEquals("2024-01-31", data.scaleWeights.first().id)
        assertFalse(data.preferences.dataVisibility.isVisible("waist"))
    }

    @Test
    fun quickUsesOnlyActiveProgramAndLocalStartDatesGranularSeparatesNotesAndNeverExportsPhotosOrDrafts() {
        val entry = SessionExercise("entry", "hold", listOf(
            LoggedSet("working", assistanceKg = 17.25, durationSeconds = 83.5, completed = true),
            LoggedSet("warmup", weightKg = 13.0, reps = 3, completed = true, warmup = true),
            LoggedSet("unfinished", reps = 11),
        ), notes = "PRIVATE entry note")
        fun workout(id: String, time: String) = Workout(id, id, Instant.parse(time).toEpochMilli(), Instant.parse(time).toEpochMilli() + 90000, listOf(entry), "PRIVATE workout note")
        val inside = workout("inside", "2024-02-24T08:00:00Z")
        val data = AppData(
            history = listOf(workout("future", "2024-03-02T08:00:00Z"), inside, workout("utc-is-24-local-is-23", "2024-02-24T07:59:59Z"), workout("end", "2024-03-02T07:59:59Z")),
            programs = listOf(TrainingProgram(id = "inactive", name = "Inactive"), TrainingProgram(id = "active", name = "Active")), activeProgramId = "active",
            active = workout("PRIVATE active", "2024-02-26T10:00:00Z"), workoutDraft = WorkoutDraft("PRIVATE draft"),
            progressPhotos = listOf(ProgressPhotoRecord(photos = mapOf(PhotoPosition.SIDE to "a".repeat(64)))),
            preferences = Preferences(displayName = "PRIVATE name"),
        )
        val catalog = listOf(Exercise("hold", "Supported Hold", "Lats", "Machine", secondary = listOf("Biceps"), frames = listOf("PRIVATE.png"), muscles = listOf("Lats", "Upper Back")))
        val quick = ScopedDataExport.document(data, catalog, ScopedExportRequest(), today, zone)
        val payload = quick.getJSONObject("data")
        assertEquals(setOf("muscleGroups", "trainingPrograms", "workoutLog"), payload.keys().asSequence().toSet())
        assertEquals(listOf("active"), payload.getJSONArray("trainingPrograms").ids())
        assertEquals(listOf("inside", "end"), payload.getJSONArray("workoutLog").ids())
        assertEquals("PRIVATE workout note", payload.getJSONArray("workoutLog").getJSONObject(0).getString("notes"))
        val group = payload.getJSONArray("muscleGroups").getJSONObject(0)
        assertEquals(listOf("Lats", "Upper Back"), (0..1).map { group.getJSONArray("primaryMuscles").getString(it) })
        assertEquals("Biceps", group.getJSONArray("secondaryMuscles").getString(0))
        assertEquals(1, group.getJSONArray("completedWorkingSetIds").length())
        assertEquals("working", group.getJSONArray("completedWorkingSetIds").getString(0))
        val selected = setOf(ExportCategory.WORKOUT_LOG, ExportCategory.EXERCISES)
        val granular = ScopedDataExport.document(data, catalog, ScopedExportRequest(false, categories = selected), today, zone)
        val text = granular.toString()
        assertFalse(text.contains("PRIVATE"))
        assertFalse(text.contains("a".repeat(64)))
        val sets = granular.getJSONObject("data").getJSONArray("exercises").getJSONObject(0).getJSONArray("sets")
        assertEquals(17.25, sets.getJSONObject(0).getDouble("assistanceKg"), 0.0)
        assertEquals(83.5, sets.getJSONObject(0).getDouble("durationSeconds"), 0.0)
        assertTrue(sets.getJSONObject(0).isNull("weightKg"))
        assertEquals(3, sets.length())
        val notes = ScopedDataExport.document(data, catalog, ScopedExportRequest(false, categories = setOf(ExportCategory.WORKOUT_LOG_NOTES)), today, zone).getJSONObject("data")
        assertEquals(setOf("workoutLogNotes"), notes.keys().asSequence().toSet())
        assertEquals("PRIVATE entry note", notes.getJSONArray("workoutLogNotes").getJSONObject(0).getJSONArray("exercises").getJSONObject(0).getString("notes"))
        assertEquals(data, DataCodec.decode(DataCodec.encode(data)))
    }

    @Test
    fun categorySnapshotsAreExplicitAndUnavailableCategoriesCannotPretendToExport() {
        val data = AppData(
            preferences = Preferences(pounds = true, restSeconds = 73, autoRest = false, displayName = "Renée", dismissedTutorials = setOf("bodyCharts"),
                keepAlive = true, showWorkoutTimer = false, showBodyweightContribution = false, distanceImperial = true,
                restAfterLastWarmupSet = false, restBetweenExercises = true, supersetAutoNext = false, restBetweenSidesPercent = 37),
            routines = listOf(Routine("routine", "Library routine", listOf("custom"))),
            programs = listOf(TrainingProgram(id = "plan", name = "Manual plan")),
            gyms = listOf(GymProfile(id = "gym", name = "Garage")),
            exerciseDefaults = mapOf("custom" to ExerciseDefaults("Global exercise note", 81, true)),
            customExercises = listOf(Exercise("custom", "My Hold", "Lats", "Machine", custom = true)),
        )
        val selected = setOf(ExportCategory.USER_PROFILE, ExportCategory.WORKOUT_SETTINGS, ExportCategory.WORKOUTS, ExportCategory.TRAINING_PROGRAMS, ExportCategory.GYM_PROFILES, ExportCategory.EXERCISE_SETTINGS, ExportCategory.CUSTOM_EXERCISES)
        val payload = ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false, ExportTimeFrame.LAST_MONTH, selected), today, zone).getJSONObject("data")
        assertEquals(setOf("userProfile", "workoutSettings", "workouts", "trainingPrograms", "gymProfiles", "exerciseSettings", "customExercises"), payload.keys().asSequence().toSet())
        assertEquals("Renée", payload.getJSONObject("userProfile").getString("displayName"))
        assertEquals(setOf("pounds", "restSeconds", "autoRest", "showRir", "exerciseAutoNext", "keepAlive", "showWorkoutTimer", "showBodyweightContribution", "distanceImperial", "restAfterLastWarmupSet", "restBetweenExercises", "supersetAutoNext", "restBetweenSidesPercent", "rateEffortAfterExercise", "workoutKeypad", "weeklyWorkoutGoal"), payload.getJSONObject("workoutSettings").keys().asSequence().toSet())
        assertEquals(73, payload.getJSONObject("workoutSettings").getInt("restSeconds"))
        assertTrue(payload.getJSONObject("workoutSettings").getBoolean("keepAlive"))
        assertFalse(payload.getJSONObject("workoutSettings").getBoolean("showWorkoutTimer"))
        assertFalse(payload.getJSONObject("workoutSettings").getBoolean("showBodyweightContribution"))
        assertTrue(payload.getJSONObject("workoutSettings").getBoolean("distanceImperial"))
        assertFalse(payload.getJSONObject("workoutSettings").getBoolean("restAfterLastWarmupSet"))
        assertTrue(payload.getJSONObject("workoutSettings").getBoolean("restBetweenExercises"))
        assertFalse(payload.getJSONObject("workoutSettings").getBoolean("supersetAutoNext"))
        assertEquals(37, payload.getJSONObject("workoutSettings").getInt("restBetweenSidesPercent"))
        assertEquals(listOf("routine"), payload.getJSONArray("workouts").ids())
        assertEquals(listOf("plan"), payload.getJSONArray("trainingPrograms").ids())
        assertEquals(listOf("gym"), payload.getJSONArray("gymProfiles").ids())
        assertEquals(listOf("custom"), payload.getJSONArray("customExercises").ids())
        assertEquals(81, payload.getJSONObject("exerciseSettings").getJSONObject("custom").getInt("restSeconds"))
        assertThrows(IllegalArgumentException::class.java) { ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false), today, zone) }
        for (category in listOf(ExportCategory.WEIGHT_TREND, ExportCategory.STEPS, ExportCategory.PERIOD)) {
            assertThrows(IllegalArgumentException::class.java) { ScopedDataExport.document(data, emptyList(), ScopedExportRequest(false, categories = setOf(category)), today, zone) }
        }
    }
}
