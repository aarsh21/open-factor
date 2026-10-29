package org.openfactor.app

import java.io.InterruptedIOException

internal enum class FitbodMatchKind { EXACT, CLOSEST, PRESERVED }
internal data class FitbodExerciseMatch(val exercise: Exercise, val kind: FitbodMatchKind)
internal data class FitbodMatchPlan(val matches: Map<String, FitbodExerciseMatch>) {
    val choices: Map<String, String> get() = matches.mapValues { it.value.exercise.id }
    val newExercises: List<Exercise> get() = matches.values.filter { it.kind == FitbodMatchKind.PRESERVED }.map { it.exercise }.distinctBy { it.id }
}

internal fun Exercise.supportsFitbodStrength(requiresLoad: Boolean): Boolean {
    val metrics = loggingMetrics()
    return metrics.any { it.isReps } && (!requiresLoad || metrics.any { it.isLoad }) &&
        metrics.all { it.isReps || (it.isLoad && it != SetMetric.ASSISTANCE) }
}

/** Fitbod names can differ from the catalog; uncertain identities stay separate, not guessed. */
internal class FitbodExerciseMatcher(catalog: List<Exercise>) : ImportExerciseSuggestions {
    private data class Candidate(val exercise: Exercise, val phrases: List<Set<String>>, val supportsLoad: Boolean, val equipment: String?)
    private val available = catalog.filter { !it.archived && it.supportsFitbodStrength(false) }.distinctBy { it.id }
    private val index = ExerciseSearchIndexes.forExercises(available)
    private val exact = available.flatMap { exercise -> (exercise.aliases + exercise.name +
        listOfNotNull(exercise.properties["Fitbod source name"].takeIf { exercise.custom && exercise.properties["Import source"] == "Fitbod" })).map {
        literalKey(it) to exercise
    } }.groupBy({ it.first }, { it.second }).mapValues { it.value.distinctBy(Exercise::id) }
    private val candidates = available.map { Candidate(it, (it.aliases + it.name).map(::tokens).distinct(),
        it.loggingMetrics().any { metric -> metric.isLoad }, equipment(it.equipment)) }

    override fun suggestions(name: String): List<Exercise> = index.search(name).take(8)

    fun plan(preview: FitbodPreview): FitbodMatchPlan {
        val sources = preview.workouts.flatMap { it.exercises }.groupBy { it.name }
        require(sources.size <= 1000) { "Import at most 1,000 distinct exercise names at a time. Choose a smaller CSV." }
        val preserved = mutableMapOf<String, Exercise>()
        return FitbodMatchPlan(sources.mapValues { (name, entries) ->
            cancelled()
            val match = match(name, entries.any { e -> e.sets.any { it.weightKg > 0.0 } })
            if (match.kind != FitbodMatchKind.PRESERVED) match else match.copy(exercise = preserved.getOrPut(
                literalKey(name)) { match.exercise })
        })
    }

    internal fun match(name: String, requiresLoad: Boolean): FitbodExerciseMatch {
        val literal = exact[literalKey(name)]
            .orEmpty().filter { it.supportsFitbodStrength(requiresLoad) }.singleOrNull()
        if (literal != null) return FitbodExerciseMatch(literal, FitbodMatchKind.EXACT)
        val query = tokens(name)
        val sourceEquipment = equipment(name)
        val eligible = candidates.filter { candidate ->
            (!requiresLoad || candidate.supportsLoad) &&
                (sourceEquipment == null || candidate.equipment == sourceEquipment)
        }
        if (query.isNotEmpty()) {
            val normalized = eligible.filter { query in it.phrases }
            if (normalized.size == 1) return FitbodExerciseMatch(normalized.single().exercise, FitbodMatchKind.CLOSEST)
            // Do not break semantic ties with search popularity, ID order, or equipment guesses.
            if (normalized.isEmpty()) {
                val ranked = eligible.mapNotNull { candidate ->
                    cancelled()
                    val score = candidate.phrases.maxOf { similarity(query, it) }
                    if (score >= 0.84) candidate.exercise to score else null
                }.sortedByDescending { it.second }
                val best = ranked.firstOrNull()
                if (best != null && (ranked.size == 1 || best.second - ranked[1].second >= 0.06))
                    return FitbodExerciseMatch(best.first, FitbodMatchKind.CLOSEST)
            }
        }
        return FitbodExerciseMatch(preserve(name), FitbodMatchKind.PRESERVED)
    }

    companion object {
        private val legCurl = Regex("\\bleg curl\\b")
        private val shoulderPress = Regex("\\bshoulder press\\b")
        private val skullCrusher = Regex("\\bskull ?crushers?\\b")
        private val vbarPulldown = Regex("\\bv bar(?: cable)?(?: lat)? pulldown\\b")
        private val oneLimb = Regex("\\bone (arm|leg)\\b")
        private val protected = setOf("single", "unilateral", "alternating", "reverse", "pause", "paused", "deficit",
            "assisted", "weighted", "landmine", "close", "wide", "neutral", "sumo", "snatch", "zercher", "front",
            "rear", "underhand", "overhand", "behind", "supported", "offset", "kickstand", "eccentric", "cheat",
            "hammer", "preacher", "spider", "bayesian")

        private fun literalKey(name: String) = ExerciseSearchIndex.normalizedTokens(name).joinToString(" ").ifBlank { name }

        private fun tokens(name: String): Set<String> {
            var text = ExerciseSearchIndex.normalizedTokens(name).joinToString(" ")
                .replace("hamstrings", "hamstring").replace("push down", "pushdown")
            text = legCurl.replace(text, "hamstring curl")
            text = shoulderPress.replace(text, "overhead press")
            text = skullCrusher.replace(text, "tricep extension")
            text = vbarPulldown.replace(text, "neutral close grip cable lat pulldown")
            text = oneLimb.replace(text) { "single ${it.groupValues[1]}" }
            return text.split(' ').filter { it.isNotEmpty() && it !in setOf("the", "a", "and", "with") }.toSet()
        }

        private fun similarity(query: Set<String>, target: Set<String>): Double {
            val remaining = (target - query).toMutableSet()
            val missing = query - target
            if (missing.size > 1) return 0.0
            var penalty = 0.0
            for (token in missing) {
                val fuzzy = remaining.singleOrNull { ExerciseSearchIndex.oneEditApart(token, it) } ?: return 0.0
                remaining.remove(fuzzy)
                penalty += 0.05
            }
            if (remaining.any { it in protected } || remaining.size > 2) return 0.0
            return 2.0 * query.size / (query.size + target.size) - penalty
        }

        private fun equipment(value: String): String? {
            val words = ExerciseSearchIndex.normalizedTokens(value).toSet()
            return when {
                "smith" in words -> "Smith Machine"
                "barbell" in words -> "Barbell"
                "dumbbell" in words -> "Dumbbell"
                "kettlebell" in words -> "Kettlebell"
                "cable" in words -> "Cable"
                "band" in words -> "Band"
                words.any { it in setOf("machine", "loaded", "deck") } -> "Machine"
                else -> null
            }
        }

        fun preserve(name: String): Exercise = Exercise(
            id = newId(), name = name, muscle = "Other", equipment = equipment(name) ?: "Unspecified",
            custom = true, muscles = emptyList(), properties = mapOf("Metrics" to "Weight, Reps", "Import source" to "Fitbod", "Fitbod source name" to name),
            instructions = "Preserved from Fitbod because no unambiguous compatible catalog match was chosen. Edit muscle and equipment details if known.",
        )

        private fun cancelled() {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Import cancelled")
        }
    }
}
