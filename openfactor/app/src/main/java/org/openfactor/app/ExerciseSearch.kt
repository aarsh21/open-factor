package org.openfactor.app

import java.text.Normalizer
import java.util.Locale

/** Search identities come from the catalog; a match never creates or substitutes an exercise. */
class ExerciseSearchIndex(exercises: List<Exercise>) {
    private data class Phrase(val text: String, val tokens: List<String>)
    private data class Document(val exercise: Exercise, val name: Phrase, val aliases: List<Phrase>, val metadata: Phrase, val combined: Phrase)
    private val documents = exercises.distinctBy { it.id }.map { exercise ->
        Document(exercise, phrase(exercise.name), exercise.aliases.map(::phrase).distinct(),
            phrase((listOf(exercise.equipment) + exercise.equipmentOptions + exercise.muscles).joinToString(" ")),
            phrase((listOf(exercise.name, exercise.equipment) + exercise.aliases + exercise.equipmentOptions + exercise.muscles).joinToString(" ")))
    }

    fun search(query: String): List<Exercise> {
        val q = phrase(query)
        if (q.tokens.size > 12 || query.length > 160) return emptyList()
        if (q.tokens.isEmpty() && query.isNotBlank()) return emptyList()
        if (q.tokens.isEmpty()) return documents.sortedWith(
            compareByDescending<Document> { it.exercise.searchBoost }.thenBy { it.name.text }.thenBy { it.exercise.id },
        ).map { it.exercise }
        return documents.mapNotNull { doc ->
            val score = maxOf(
                score(q, doc.name, 10_000, 8_500, 7_800, 7_000, 4_500),
                doc.aliases.maxOfOrNull { score(q, it, 9_500, 8_000, 7_500, 6_700, 4_300) } ?: 0,
                // Equipment/muscle words help discovery, but never outrank an exercise-name match.
                score(q, doc.metadata, 3_600, 3_500, 3_400, 3_300, 0),
                score(q, doc.combined, 3_000, 2_900, 2_800, 2_700, 0),
            )
            if (score == 0) null else doc to score
        }.sortedWith(compareByDescending<Pair<Document, Int>> { it.second }
            .thenByDescending { it.first.exercise.searchBoost }
            .thenBy { it.first.name.text }.thenBy { it.first.exercise.id })
            .map { it.first.exercise }
    }

    private fun score(q: Phrase, target: Phrase, exact: Int, prefix: Int, contains: Int, tokens: Int, fuzzy: Int): Int {
        if (target.text.isEmpty()) return 0
        if (target.text == q.text) return exact
        val extra = (target.tokens.size - q.tokens.size).coerceAtLeast(0).coerceAtMost(24)
        if (target.text.startsWith(q.text)) return prefix - extra * 12
        if ((" " + target.text + " ").contains(" " + q.text + " ")) return contains - extra * 12
        val used = BooleanArray(target.tokens.size)
        var fuzzyCount = 0
        for (token in q.tokens) {
            val index = target.tokens.indices.firstOrNull { !used[it] && target.tokens[it] == token }
                ?: target.tokens.indices.firstOrNull { !used[it] && token.length >= 2 && target.tokens[it].startsWith(token) }
                ?: if (fuzzy > 0 && fuzzyCount == 0 && token.length >= 4) {
                    target.tokens.indices.firstOrNull { !used[it] && oneEditApart(token, target.tokens[it]) }
                        ?.also { fuzzyCount++ }
                } else null
            if (index == null) return 0
            used[index] = true
        }
        return (if (fuzzyCount == 0) tokens else fuzzy) - extra * 12
    }

    companion object {
        private val marks = Regex("\\p{M}+")
        private val separators = Regex("[^\\p{L}\\p{N}]+")
        private val compound = Regex("\\b(pull|push|chin|sit) ups?\\b")
        private val pullDown = Regex("\\bpull downs?\\b")
        private val deadLift = Regex("\\bdead lifts?\\b")
        private val abbreviations = mapOf(
            "db" to listOf("dumbbell"), "bb" to listOf("barbell"), "kb" to listOf("kettlebell"),
            "bw" to listOf("bodyweight"), "rdl" to listOf("romanian", "deadlift"),
            "ohp" to listOf("overhead", "press"), "bss" to listOf("bulgarian", "split", "squat"),
            "rdls" to listOf("romanian", "deadlift"), "incl" to listOf("incline"), "inc" to listOf("incline"),
            "decl" to listOf("decline"), "cgbp" to listOf("close", "grip", "bench", "press"),
            "benchpress" to listOf("bench", "press"), "legpress" to listOf("leg", "press"),
            "latpulldown" to listOf("lat", "pulldown"),
        )
        private val singular = mapOf(
            "dumbbells" to "dumbbell", "barbells" to "barbell", "kettlebells" to "kettlebell",
            "pushups" to "pushup", "pullups" to "pullup", "chinups" to "chinup", "situps" to "situp",
            "rows" to "row", "presses" to "press", "raises" to "raise", "curls" to "curl",
            "squats" to "squat", "lunges" to "lunge", "extensions" to "extension", "dips" to "dip",
            "deadlifts" to "deadlift", "flyes" to "fly", "flies" to "fly", "calves" to "calf",
            "pulldowns" to "pulldown", "pushdowns" to "pushdown",
            "biceps" to "bicep", "triceps" to "tricep", "lats" to "lat", "delts" to "delt",
        )

        private fun phrase(value: String): Phrase {
            var text = marks.replace(Normalizer.normalize(value, Normalizer.Form.NFKD), "")
                .lowercase(Locale.ROOT)
            text = separators.replace(text, " ").trim()
            text = compound.replace(text) { it.groupValues[1] + "up" }
            text = pullDown.replace(text, "pulldown")
            text = deadLift.replace(text, "deadlift")
            val tokens = text.split(' ').filter(String::isNotEmpty).flatMap { abbreviations[it] ?: listOf(it) }
                .map { singular[it] ?: it }
            return Phrase(tokens.joinToString(" "), tokens)
        }

        /** Exactly one insertion, deletion, replacement or adjacent transposition; no broad fuzzy fallback. */
        private fun oneEditApart(a: String, b: String): Boolean {
            if (b.length < 4 || kotlin.math.abs(a.length - b.length) > 1) return false
            if (a.length == b.length) {
                val differences = a.indices.filter { a[it] != b[it] }
                return differences.size == 1 || (differences.size == 2 && differences[1] == differences[0] + 1 &&
                    a[differences[0]] == b[differences[1]] && a[differences[1]] == b[differences[0]])
            }
            val shorter = if (a.length < b.length) a else b
            val longer = if (a.length > b.length) a else b
            var offset = 0
            for (i in shorter.indices) {
                if (shorter[i] != longer[i + offset]) {
                    if (offset != 0 || shorter[i] != longer[i + 1]) return false
                    offset = 1
                }
            }
            return true
        }
    }
}

data class ExerciseCatalogCoverage(val exercises: Int, val customExercises: Int, val videoExercises: Int, val archivedExercises: Int)

fun exerciseCatalogCoverage(exercises: List<Exercise>): ExerciseCatalogCoverage {
    val unique = exercises.distinctBy { it.id }
    return ExerciseCatalogCoverage(unique.size, unique.count { it.custom },
        unique.count { it.clips.isNotEmpty() || !it.video.isNullOrBlank() }, unique.count { it.archived })
}
