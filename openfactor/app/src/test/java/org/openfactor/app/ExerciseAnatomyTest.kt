package org.openfactor.app

import org.junit.Assert.*
import org.junit.Test

class ExerciseAnatomyTest {
    @Test fun everyPrimaryAndSecondaryPathIsHighlightedWithoutChangingBodyOrUnrelatedMuscles() {
        val original = """<svg><path id="body" fill="#DCDCDC"/><path id="quads" fill="#EEEEEE"/><path id="glutes" fill="#EEEEEE"/><path id="adductors" fill="#EEEEEE"/><path id="upperBack" fill="#EEEEEE"/><path id="chest" fill="#EEEEEE"/></svg>"""
        val highlighted = highlightExerciseAnatomySvg(original, listOf("Quads", "Glutes", "Adductors"), listOf("Upper Back", "Quads"))
        listOf("quads", "glutes", "adductors").forEach { assertTrue(highlighted.contains("id=\"$it\" fill=\"#3982FF\"")) }
        assertTrue(highlighted.contains("id=\"upperBack\" fill=\"#B9D3FF\""))
        assertTrue(highlighted.contains("id=\"chest\" fill=\"#555555\""))
        assertTrue(highlighted.contains("id=\"body\" fill=\"#353535\""))
        assertTrue(original.contains("id=\"quads\" fill=\"#EEEEEE\""))
    }

    @Test fun unknownMuscleDoesNotHighlightArbitraryPathsOrDestroySvgStructure() {
        val original = """<svg viewBox="0 0 20 20"><path id="chest" d="M0 0" fill="#EEEEEE"/></svg>"""
        assertEquals(original.replace("#EEEEEE", "#555555"), highlightExerciseAnatomySvg(original, listOf("Unknown"), emptyList()))
    }
}
