package org.openfactor.app

/** Working-set kinds recovered from the reference logger; warm-ups retain their legacy flag. */
enum class SetKind(val label: String) {
    STANDARD("Standard"),
    MYO("Myo"),
    FAILURE("Failure"),
}

enum class SetSide(val label: String) { LEFT("Left"), RIGHT("Right") }

val LoggedSet.logicalRootId: String get() = segmentOf ?: pairId ?: id
val LoggedSet.componentId: String get() = pairId ?: id

/** One working-set identity, even when it contains multiple components and two sides. */
data class LogicalSet(val root: LoggedSet, val components: List<List<LoggedSet>>) {
    val logs get() = components.flatten()
    val completed get() = logs.all { it.completed }
}

fun SessionExercise.logicalSets(): List<LogicalSet> =
    sets.filter { it.segmentOf == null && it.pairId == null }.map { root ->
        val components = listOf(root) + sets.filter { it.segmentOf == root.id && it.pairId == null }
        LogicalSet(root, components.map { component -> listOf(component) + sets.filter { it.pairId == component.id } })
    }

/** Direct, local links only: a right side cannot be a parent and an extra cannot be a root. */
fun SessionExercise.validateSetLinks(): SessionExercise = apply {
    val byId = sets.associateBy { it.id }
    require(byId.size == sets.size) { "Duplicate set ID" }
    for (set in sets) {
        require(set.id.isNotBlank()) { "Empty set ID" }
        require((set.side == SetSide.RIGHT) == (set.pairId != null)) { "Invalid side pair" }
        set.pairId?.let { id ->
            val left = requireNotNull(byId[id]) { "Missing left side" }
            require(left.side == SetSide.LEFT && left.pairId == null && left.segmentOf == set.segmentOf) { "Invalid side pair" }
            require(left.kind == set.kind && left.warmup == set.warmup) { "Mismatched side pair" }
        }
        if (set.side == SetSide.LEFT) require(sets.count { it.pairId == set.id } == 1) { "Missing or duplicate right side" }
        set.segmentOf?.let { id ->
            val root = requireNotNull(byId[id]) { "Missing Myo root" }
            require(root.id != set.id && root.segmentOf == null && root.pairId == null && root.kind == SetKind.MYO && !root.warmup) { "Invalid Myo root" }
            require(set.kind == SetKind.MYO && !set.warmup && (set.side == null) == (root.side == null)) { "Invalid Myo component" }
        }
    }
}

fun SessionExercise.withSetKind(setId: String, kind: SetKind, warmup: Boolean = false): SessionExercise {
    val group = logicalSets().find { it.logs.any { log -> log.id == setId } } ?: return this
    val root = group.root
    val retainExtras = kind == SetKind.MYO && !warmup
    val changed = copy(sets = sets.filterNot { it.segmentOf == root.id && !retainExtras }.map {
        if (it.logicalRootId != root.id) it else it.copy(
            kind = kind, warmup = warmup, rir = if (kind == SetKind.FAILURE && !warmup) 0 else it.rir,
        )
    })
    return if (retainExtras && group.components.size == 1) changed.withMyoComponent(root.id) else changed
}

fun SessionExercise.withMyoComponent(rootId: String): SessionExercise {
    val group = logicalSets().find { it.root.id == rootId && it.root.kind == SetKind.MYO && !it.root.warmup } ?: return this
    val left = LoggedSet(kind = SetKind.MYO, segmentOf = rootId, side = group.root.side)
    val added = if (left.side == SetSide.LEFT) listOf(left, left.copy(id = newId(), side = SetSide.RIGHT, pairId = left.id)) else listOf(left)
    val insertion = sets.indexOfLast { it.logicalRootId == rootId } + 1
    return copy(sets = sets.toMutableList().apply { addAll(insertion, added) })
}

/** Source single→split conversion copies the existing actuals, without dividing weight or reps. */
fun SessionExercise.withSplitSides(): SessionExercise = copy(sets = sets.flatMap {
    if (it.side != null) listOf(it) else listOf(it.copy(side = SetSide.LEFT), it.copy(id = newId(), side = SetSide.RIGHT, pairId = it.id))
})

/** Explicit local merge policy; the UI asks before discarding independently edited right logs. */
fun SessionExercise.withLeftSideOnly(): SessionExercise = copy(
    sets = sets.filterNot { it.side == SetSide.RIGHT }.map { it.copy(side = null) },
)

fun SessionExercise.withoutSet(setId: String): SessionExercise {
    val set = sets.find { it.id == setId } ?: return this
    return copy(sets = if (set.segmentOf == null) sets.filterNot { it.logicalRootId == set.logicalRootId }
        else sets.filterNot { it.componentId == set.componentId })
}

fun SessionExercise.withNewSet(warmup: Boolean = false): SessionExercise {
    val row = LoggedSet(warmup = warmup)
    val added = if (sets.any { it.side != null }) listOf(row.copy(side = SetSide.LEFT), row.copy(id = newId(), side = SetSide.RIGHT, pairId = row.id)) else listOf(row)
    val index = if (warmup) sets.indexOfFirst { !it.warmup }.takeIf { it >= 0 } ?: sets.size else sets.size
    return copy(sets = sets.toMutableList().apply { addAll(index, added) })
}

fun Workout.withSuperset(exerciseIds: Set<String>): Workout {
    require(exerciseIds.all { id -> exercises.any { it.id == id } }) { "Unknown superset exercise" }
    require(exerciseIds.size >= 2) { "Select at least two exercises" }
    val id = newId()
    return copy(exercises = exercises.map { if (it.id in exerciseIds) it.copy(supersetId = id) else it }).normalizeSupersets()
}

fun Workout.normalizeSupersets(): Workout {
    val counts = exercises.mapNotNull { it.supersetId }.groupingBy { it }.eachCount()
    return copy(exercises = exercises.map { if (it.supersetId != null && counts[it.supersetId] == 1) it.copy(supersetId = null) else it })
}

/** The first unfinished working ordinal, not a completed-set count, determines the next round. */
fun Workout.nextSupersetExercise(currentId: String): String? {
    val current = exercises.indexOfFirst { it.id == currentId }
    val group = exercises.getOrNull(current)?.supersetId ?: return null
    val positions = exercises.mapIndexedNotNull { index, exercise ->
        if (exercise.supersetId != group) null else exercise.logicalSets().filterNot { it.root.warmup }
            .indexOfFirst { !it.completed }.takeIf { it >= 0 }?.let { index to it }
    }
    val earliest = positions.minOfOrNull { it.second } ?: return null
    val eligible = positions.filter { it.second == earliest && it.first != current }.map { it.first }
    return (eligible.firstOrNull { it > current } ?: eligible.firstOrNull())?.let { exercises[it].id }
}
