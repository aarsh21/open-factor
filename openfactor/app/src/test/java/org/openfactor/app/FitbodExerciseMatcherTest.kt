package org.openfactor.app

import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class FitbodExerciseMatcherTest {
    private val bench = Exercise("bench", "Barbell Bench Press", "Chest", "Barbell")
    private val curl = Exercise("curl", "Cable straight bar biceps curl", "Biceps", "Cable", aliases = listOf("Cable biceps curl"))
    private fun preview(vararg names: String): FitbodPreview = FitbodPreview(names.mapIndexed { index, name ->
        FitbodWorkout("workout-$index", "Imported workout", "2026-01-01T00:00:00Z", 1000L + index,
            listOf(FitbodExercise(name, "source-$index", 0, "", listOf(FitbodSet("set-$index", 1, 8, 40.0, false, false, 90, "")))))
    })

    @Test fun commonVendorVocabularyPluralFormsReorderingAndAbbreviationsResolve() {
        val leg = Exercise("leg", "Seated hamstring curl", "Hamstrings", "Machine")
        val rdl = Exercise("rdl", "Dumbbell Romanian deadlift", "Hamstrings", "Dumbbell")
        val pulldown = Exercise("pull", "Neutral close grip cable lat pulldown", "Lats", "Cable")
        val matcher = FitbodExerciseMatcher(listOf(bench, curl, leg, rdl, pulldown))
        listOf("Cable Bicep Curl" to curl.id, "Curls, Cable Biceps" to curl.id, "Seated Leg Curl" to leg.id,
            "DB RDL" to rdl.id, "V-Bar Pulldown" to pulldown.id).forEach { (name, expected) ->
            val match = matcher.match(name, true)
            assertEquals(name, expected, match.exercise.id)
            assertNotEquals(name, FitbodMatchKind.PRESERVED, match.kind)
        }
    }

    @Test fun oneClearTypoCanMatchButDifferentMovementAndEquipmentCannot() {
        val matcher = FitbodExerciseMatcher(listOf(bench, curl))
        assertEquals(bench.id, matcher.match("Barbell Bench Prss", true).exercise.id)
        listOf("Dumbbell Bench Prss", "Barbell Curl", "Cable Squat").forEach { name ->
            assertEquals(name, FitbodMatchKind.PRESERVED, matcher.match(name, true).kind)
        }
    }

    @Test fun ambiguousVariantsDoNotUsePopularityOrIdOrderingAsIdentity() {
        val matcher = FitbodExerciseMatcher(listOf(bench, bench.copy(id = "popular", searchBoost = 1000.0)))
        val match = matcher.match(bench.name, true)
        assertEquals(FitbodMatchKind.PRESERVED, match.kind)
        assertTrue(match.exercise.custom)
        assertEquals(bench.name, match.exercise.name)
        assertTrue(match.exercise.muscles.isEmpty())
    }

    @Test fun missingLateralityAndGripAreNotInventedByClosestMatching() {
        val unilateral = Exercise("one", "Single arm cable row", "Back", "Cable")
        val reverse = Exercise("reverse", "Reverse grip barbell bench press", "Chest", "Barbell")
        val matcher = FitbodExerciseMatcher(listOf(unilateral, reverse))
        assertEquals(FitbodMatchKind.PRESERVED, matcher.match("Cable row", true).kind)
        assertEquals(FitbodMatchKind.PRESERVED, matcher.match("Barbell bench press", true).kind)
        assertEquals(unilateral.id, matcher.match("One arm cable row", true).exercise.id)
    }

    @Test fun incompatibleOrArchivedExercisesArePreservedInsteadOfBlockingOrHidingActuals() {
        listOf("Duration", "Assistance weight, Reps", "Weight, Reps, Duration", "None").forEach { metrics ->
            assertEquals(FitbodMatchKind.PRESERVED,
                FitbodExerciseMatcher(listOf(bench.copy(properties = mapOf("Metrics" to metrics)))).match(bench.name, true).kind)
        }
        assertEquals(FitbodMatchKind.PRESERVED, FitbodExerciseMatcher(listOf(bench.copy(archived = true))).match(bench.name, true).kind)
        val reps = bench.copy(properties = mapOf("Metrics" to "Reps"))
        assertEquals(reps.id, FitbodExerciseMatcher(listOf(reps)).match(reps.name, false).exercise.id)
        assertEquals(FitbodMatchKind.PRESERVED, FitbodExerciseMatcher(listOf(reps)).match(reps.name, true).kind)
    }

    @Test fun preservedExercisesAndHistoryAreSavedTogetherAndReimportDoesNotDuplicateEither() {
        val source = preview(bench.name, "Unlisted press")
        val plan = FitbodExerciseMatcher(listOf(bench)).plan(source)
        assertEquals(2, plan.choices.size)
        assertEquals(1, plan.newExercises.size)
        val original = AppData(active = Workout(name = "Keep active"), programDraft = TrainingProgram(name = "Keep draft"), favorites = setOf(bench.id))
        val imported = original.withFitbodImport(source, plan.choices, emptySet(), plan.newExercises, listOf(bench))
        assertEquals(original, imported.copy(history = original.history, customExercises = original.customExercises))
        assertEquals(2, imported.history.size)
        assertEquals(plan.newExercises, imported.customExercises)
        assertEquals(setOf(bench.id, plan.newExercises.single().id), imported.history.flatMap { it.exercises }.map { it.exerciseId }.toSet())
        assertTrue(imported.history.flatMap { it.exercises }.all { "Fitbod exercise:" in it.notes })
        assertEquals(imported, DataCodec.decode(DataCodec.encode(imported)))
        assertEquals(imported, imported.withFitbodImport(source, plan.choices, emptySet(), plan.newExercises, listOf(bench) + imported.customExercises))
        val next = FitbodExerciseMatcher(listOf(bench) + imported.customExercises).plan(preview("Unlisted press"))
        assertTrue(next.newExercises.isEmpty())
        assertEquals(imported.customExercises.single().id, next.choices["Unlisted press"])
    }

    @Test fun sourceProvenanceReusesACustomExerciseAfterALocalRename() {
        val preserved = FitbodExerciseMatcher.preserve("Unlisted press").copy(name = "My renamed press")
        val plan = FitbodExerciseMatcher(listOf(preserved)).plan(preview("Unlisted press"))
        assertEquals(preserved.id, plan.choices["Unlisted press"])
        assertTrue(plan.newExercises.isEmpty())
    }

    @Test fun equivalentSourceSpellingsShareOnePreservedIdentityInTheSameImport() {
        val plan = FitbodExerciseMatcher(emptyList()).plan(preview("Unlisted Press", "Unlisted-press"))
        assertEquals(1, plan.newExercises.size)
        assertEquals(1, plan.choices.values.toSet().size)
        val symbols = FitbodExerciseMatcher(emptyList()).plan(preview("😀", "🦾"))
        assertEquals(2, symbols.newExercises.size)
        assertEquals(2, symbols.choices.values.toSet().size)
    }

    @Test fun skippedWorkoutsAndOverriddenMatchesDoNotSaveUnusedCustomExercises() {
        val source = preview("Unlisted press")
        val plan = FitbodExerciseMatcher(listOf(bench)).plan(source)
        assertEquals(AppData(), AppData().withFitbodImport(source, plan.choices, setOf("Unlisted press"), plan.newExercises, listOf(bench)))
        val overridden = AppData().withFitbodImport(source, mapOf("Unlisted press" to bench.id), emptySet(), plan.newExercises, listOf(bench))
        assertTrue(overridden.customExercises.isEmpty())
        assertEquals(bench.id, overridden.history.single().exercises.single().exerciseId)
    }

    @Test fun changedCatalogIsRejectedBeforeAnyCustomOrHistoryAppend() {
        val source = preview(bench.name, "Unlisted press")
        val plan = FitbodExerciseMatcher(listOf(bench)).plan(source)
        val original = AppData()
        try {
            original.withFitbodImport(source, plan.choices, emptySet(), plan.newExercises, emptyList())
            fail("The matched exercise disappeared")
        } catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("unavailable")) }
        assertEquals(AppData(), original)
    }

    @Test(timeout = 30_000) fun suppliedCsvResolvesAndImportsEverySetAgainstTheRealCatalogWithoutManualChoices() {
        val path = System.getenv("OPENFACTOR_FITBOD_SAMPLE")
        assumeTrue("No private sample requested", path != null)
        val source = File(path!!).inputStream().use(FitbodImport::read)
        val file = File("src/main/assets/catalog.json").takeIf { it.isFile } ?: File("app/src/main/assets/catalog.json")
        val json = JSONArray(file.readText())
        val catalog = (0 until json.length()).map { index ->
            val value = json.getJSONObject(index)
            val aliases = value.optJSONArray("aliases")
            val properties = value.optJSONObject("properties")
            Exercise(value.getString("id"), value.getString("name"), value.optString("muscle", "Other"), value.optString("equipment", "Other"),
                aliases = aliases?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                properties = properties?.keys()?.asSequence()?.associateWith { properties.getString(it) }.orEmpty())
        }
        val start = System.nanoTime()
        val plan = FitbodExerciseMatcher(catalog).plan(source)
        val milliseconds = (System.nanoTime() - start) / 1_000_000
        assertEquals(59, plan.choices.size)
        assertTrue(plan.matches.values.any { it.kind == FitbodMatchKind.CLOSEST })
        assertTrue(plan.matches.values.any { it.kind == FitbodMatchKind.PRESERVED })
        val imported = AppData().withFitbodImport(source, plan.choices, emptySet(), plan.newExercises, catalog)
        assertEquals(290, imported.history.size)
        assertEquals(5299, imported.history.sumOf { w -> w.exercises.sumOf { it.sets.size } })
        assertEquals(imported, DataCodec.decode(DataCodec.encode(imported)))
        assertEquals(imported, imported.withFitbodImport(source, plan.choices, emptySet(), plan.newExercises, catalog + imported.customExercises))
        println("Fitbod matching: ${plan.matches.values.groupingBy { it.kind }.eachCount()}, ${milliseconds}ms; all 5,299 sets retained")
    }
}
