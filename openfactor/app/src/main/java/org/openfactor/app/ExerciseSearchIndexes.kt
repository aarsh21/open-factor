package org.openfactor.app

import java.lang.ref.WeakReference

/** Navigation reuses the same immutable catalog list; edits receive a new list and a new index. */
internal object ExerciseSearchIndexes {
    private data class Entry(val source: WeakReference<List<Exercise>>, val index: ExerciseSearchIndex)
    private val recent = ArrayDeque<Entry>()
    @Synchronized fun forExercises(exercises: List<Exercise>): ExerciseSearchIndex {
        recent.removeAll { it.source.get() == null }
        val existing = recent.firstOrNull { it.source.get() === exercises }
        if (existing != null) {
            recent.remove(existing); recent.addFirst(existing)
            return existing.index
        }
        val index = ExerciseSearchIndex(exercises)
        recent.addFirst(Entry(WeakReference(exercises), index))
        while (recent.size > 4) recent.removeLast()
        return index
    }
}
