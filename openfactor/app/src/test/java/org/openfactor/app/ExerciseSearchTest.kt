package org.openfactor.app

import java.io.File
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ExerciseSearchTest {
    private val bench = Exercise("bench", "Barbell bench press", "Chest", "Barbell",
        aliases = listOf("Bench press", "Flat barbell touch-and-go bench press"), searchBoost = 8.0)
    private val dumbbell = Exercise("db", "Dumbbell bench press", "Chest", "Dumbbell", searchBoost = 10.0)
    private val incline = Exercise("incline", "30° incline dumbbell bench press", "Chest", "Dumbbell", searchBoost = 20.0)
    private val rdl = Exercise("rdl", "Barbell Romanian deadlift", "Hamstrings", "Barbell", aliases = listOf("Barbell RDL"))
    private val ohp = Exercise("ohp", "Barbell overhead press", "Front Delts", "Barbell", aliases = listOf("OHP", "Military press"))
    private val pullup = Exercise("pullup", "Overhand-grip pull-up", "Lats", "Bodyweight")
    private val index = ExerciseSearchIndex(listOf(incline, dumbbell, bench, rdl, ohp, pullup))

    @Test fun exactNamesAndAliasesOutrankPopularityAndLongerVariants() {
        assertEquals("bench", index.search("bench press").first().id)
        assertEquals("db", index.search("dumbbell bench press").first().id)
        assertEquals("ohp", index.search("military press").first().id)
        assertEquals(listOf("db", "incline"), index.search("DB bench press").map { it.id })
    }

    @Test fun punctuationAccentsWordOrderPluralAndCommonAbbreviationsAreSearchable() {
        assertEquals("db", index.search("BENCH—PRESSES dumbbells").first().id)
        assertEquals("incline", index.search("30 inc DB bench").first().id)
        assertEquals("rdl", index.search("BB RDL").first().id)
        assertEquals("ohp", index.search("OHP").first().id)
        assertEquals("pullup", index.search("pull ups").first().id)
        assertEquals("bench", index.search("benchpress").first().id)
        val accent = ExerciseSearchIndex(listOf(bench.copy(id = "custom", name = "Développé couché", custom = true)))
        assertEquals("custom", accent.search("developpe couche").single().id)
    }

    @Test fun muscleEquipmentAndMovementCanMatchAcrossCatalogFields() {
        assertEquals("db", index.search("chest press DB").first().id)
        assertEquals("db", index.search("chest prss DB").first().id)
        assertEquals("db", index.search("chset press DB").first().id)
        assertTrue(index.search("quads chest DB press").isEmpty())
    }

    @Test fun templateScreenshotQueryAndItsReorderedVariantsResolveTheSameInclineExercise() {
        listOf("bench press incline", "incline bench press", "press incline bench", "incl DB benhc press")
            .forEach { query -> assertEquals(query, "incline", index.search(query).first().id) }
        assertTrue(index.search("bench press incline").none { it.id == bench.id || it.id == dumbbell.id })
    }

    @Test fun actualCatalogSupportsTemplateSearchAndCommonGymLanguage() {
        val catalog = catalog(File("src/main/assets/catalog.json"))
        val catalogIndex = ExerciseSearchIndex(catalog)
        listOf("bench press incline", "rear foot elevated split squat", "pec deck", "DB RDL", "pull down lat", "dumbell bench press")
            .forEach { query -> assertTrue("Catalog query must return results: $query", catalogIndex.search(query).isNotEmpty()) }
        val inclineMatches = catalogIndex.search("bench press incline")
        assertTrue(inclineMatches.all { exercise -> (listOf(exercise.name) + exercise.aliases).any { it.contains("incline", true) } })
        // Phrase-match scores can differ; membership must not depend on word order.
        assertEquals(inclineMatches.map { it.id }.toSet(), catalogIndex.search("incline bench press").map { it.id }.toSet())
    }

    @Test fun typosAreBoundedAndEveryQueryWordMustBeAccountedFor() {
        assertEquals("bench", index.search("barbell benhc press").first().id)
        assertEquals("db", index.search("DB banch press").first().id)
        assertTrue(index.search("DB banch prss").isEmpty())
        assertTrue(index.search("bench press pineapple").isEmpty())
        assertTrue(index.search("zzzzzzzz").isEmpty())
        assertTrue(index.search("???").isEmpty())
        assertTrue(index.search("x".repeat(200)).isEmpty())
    }

    @Test fun allCatalogIdentitiesRemainReachableAndCustomExercisesJoinTheSameIndex() {
        val source = catalog(File("src/main/assets/catalog.json"))
        assertEquals(1345, source.size)
        assertEquals(1345, source.map { it.id }.distinct().size)
        val custom = Exercise("custom-example", "My supported cable row", "Upper Back", "Cable", custom = true)
        val all = source + custom
        val catalogIndex = ExerciseSearchIndex(all)
        assertEquals(all.map { it.id }.toSet(), catalogIndex.search("").map { it.id }.toSet())
        assertEquals(custom, catalogIndex.search("My supported cable row").first())
        source.filterIndexed { index, _ -> index % 101 == 0 }.forEach { exercise ->
            assertTrue("Full name must resolve its exact catalog identity: ${exercise.name}",
                catalogIndex.search(exercise.name).any { it.id == exercise.id })
        }
        val reference = File("src/main/assets/reference/catalog.json")
        if (reference.isFile) {
            val coverage = exerciseCatalogCoverage(catalog(reference))
            assertEquals(1345, coverage.exercises)
            assertEquals(619, coverage.videoExercises)
        }
    }

    private fun catalog(file: File): List<Exercise> {
        val resolved = if (file.isFile) file else File("app", file.path)
        val array = JSONArray(resolved.readText())
        return (0 until array.length()).map { i ->
            val value = array.getJSONObject(i)
            fun strings(key: String): List<String> = value.optJSONArray(key)?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            }.orEmpty()
            Exercise(value.getString("id"), value.getString("name"), value.optString("muscle", "Other"),
                value.optString("equipment", "Other"), aliases = strings("aliases"), clips = strings("clips"))
        }
    }
}
