package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseSearchIndexCacheTest {
    @Test fun stableCatalogReusesIndexButEditedCatalogNeverUsesStaleNames() {
        val original = listOf(Exercise("bench", "Bench press", "Chest", "Barbell"))
        val first = ExerciseSearchIndexes.forExercises(original)
        assertSame(first, ExerciseSearchIndexes.forExercises(original))
        assertEquals("bench", first.search("bb bench").single().id)
        val edited = listOf(original.single().copy(name = "Floor press"))
        val next = ExerciseSearchIndexes.forExercises(edited)
        assertNotSame(first, next)
        assertEquals("bench", next.search("floor press").single().id)
        assertTrue(next.search("bench press").isEmpty())
        assertSame(first.search(""), first.search(""))
    }
    @Test fun cacheEvictsOldCatalogsAndKeepsCurrentOneHot() {
        val catalogs = (0..5).map { listOf(Exercise("$it", "Lift $it", "Other", "Other")) }
        val first = ExerciseSearchIndexes.forExercises(catalogs.first())
        catalogs.drop(1).forEach { ExerciseSearchIndexes.forExercises(it) }
        assertNotSame(first, ExerciseSearchIndexes.forExercises(catalogs.first()))
        val current = ExerciseSearchIndexes.forExercises(catalogs.last())
        assertSame(current, ExerciseSearchIndexes.forExercises(catalogs.last()))
    }
}
