package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseVideoSourceTest {
    private val id = "19f5c6f170d8808bb424e98de4472a7e"

    @Test fun originalPointersResolveWithoutInventingVideoPaths() {
        val pointer = ExerciseVideoReference.parse("reference/videos/${id}_b.mp4")!!
        assertEquals(id, pointer.exerciseId)
        assertEquals("videos/${id}_b.mp4", pointer.objectPath)
        assertEquals(pointer, ExerciseVideoReference.parse(pointer.objectPath))
        listOf(null, "", "reference/videos/bench-press.mp4", "reference/videos/${id}_bb.mp4", "../videos/${id}_b.mp4", "https://example.com/videos/${id}_b.mp4").forEach {
            assertNull(it, ExerciseVideoReference.parse(it))
        }
    }

    @Test fun bundledVideoAlwaysWinsAndWorksWithoutAConfiguredServer() {
        val pointer = ExerciseVideoReference(id, "a")
        val source = exerciseVideoLocation(pointer, true, "")!!
        assertTrue(source.bundled)
        assertEquals("asset:///reference/videos/${id}_a.mp4", source.uri)
        assertNull(exerciseVideoLocation(pointer, false, ""))
    }

    @Test fun remoteLocationsUseOnlyValidatedHttpsEndpointsAndExactOriginalObjects() {
        val source = exerciseVideoLocation(ExerciseVideoReference(id, "c"), false, "https://media.example.com/media/")!!
        assertEquals("https://media.example.com/media/videos/${id}_c.mp4", source.uri)
        assertFalse(source.bundled)
        assertEquals(source.uri, source.cacheKey)
        listOf("http://media.example.com", "file:///tmp", "https://name:secret@media.example.com/", "https://media.example.com/../media", "https://media.example.com/media?token=foo", "https://media.example.com/media#foo", "https://media.example.com/%2e%2e/", "invalid").forEach {
            assertNull(it, normalizedExerciseMediaBase(it))
        }
    }
}
