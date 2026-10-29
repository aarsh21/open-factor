package org.openfactor.app

import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.time.OffsetDateTime

internal data class FitbodSet(
    val id: String,
    val number: Int,
    val reps: Int,
    val weightKg: Double,
    val warmup: Boolean,
    val amrap: Boolean,
    val restSeconds: Int?,
    val notes: String,
)
internal data class FitbodExercise(
    val name: String,
    val externalId: String,
    val position: Int,
    val circuit: String,
    val sets: List<FitbodSet>,
)
internal data class FitbodWorkout(val sourceId: String, val name: String, val date: String, val startedAt: Long, val exercises: List<FitbodExercise>) {
    val id: String get() = "fitbod-workout:$sourceId"
}
internal data class FitbodPreview(val workouts: List<FitbodWorkout>) {
    val exerciseNames: List<String> get() = workouts.flatMap { it.exercises }.map { it.name }.distinct()
    val setCount: Int get() = workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
    fun pending(history: List<Workout>): FitbodPreview {
        val existing = history.map { it.id }.toSet()
        return copy(workouts = workouts.filterNot { it.id in existing })
    }

    val warnings: List<String> get() = buildList {
        add("Imports completed strength sets into history, not programs or templates. Existing data and active workouts are not replaced. Previously imported Fitbod workout IDs are skipped, including locally edited workouts.")
        add("The source supplies one timestamp per workout, not its duration. Start and end use that instant (0 minutes); no workout duration is invented. Dates display in your device time zone.")
        add("Weights are already kilograms. No doubling, per-side conversion or bodyweight contribution is inferred. Review movement, equipment and per-side matches carefully.")
        if (workouts.any { w -> w.exercises.any { e -> e.sets.any { it.amrap } } })
            add("AMRAP flags are retained in exercise notes by source set number, not converted to RIR, failure or a special set kind.")
        if (workouts.any { w -> w.exercises.any { e -> e.sets.map { it.restSeconds }.distinct().size > 1 } })
            add("Rest varies by set in some exercises. Those values are retained in exercise notes; no single rest target is guessed.")
        if (workouts.any { w -> w.exercises.any { it.circuit.isNotEmpty() } })
            add("Circuit positions are retained in notes, not interpreted as supersets.")
    }
}

/** Bounded, strict UTF-8 CSV reader for the Fitbod strength-set export, not arbitrary CSV. */
internal object FitbodImport {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    private val headers = listOf("date_local", "date_utc", "workout_name", "exercise_name", "set_number", "reps", "weight_kg",
        "is_warmup", "is_amrap", "rest_seconds", "set_duration_raw", "distance_m", "notes", "workout_id",
        "exercise_external_resource_id", "exercise_position", "circuit_position", "set_id")

    fun read(input: InputStream): FitbodPreview {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Import cancelled")
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_FILE_BYTES - bytes.size() + 1))
            if (count == -1) break
            if (count == 0) {
                val next = input.read()
                if (next == -1) break
                require(bytes.size() < MAX_FILE_BYTES) { "Fitbod CSV exceeds the 4 MiB size limit." }
                bytes.write(next)
            } else {
                require(bytes.size() + count <= MAX_FILE_BYTES) { "Fitbod CSV exceeds the 4 MiB size limit." }
                bytes.write(buffer, 0, count)
            }
        }
        val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix(0xfeff.toChar().toString())
        require('\u0000' !in text) { "Fitbod CSV must be UTF-8 without NUL characters." }
        val rows = csv(text)
        val header = rows.firstOrNull() ?: throw IllegalArgumentException("Fitbod CSV is empty.")
        require(header.size == headers.size && header.toSet() == headers.toSet()) { "Choose a Fitbod strength-sets CSV with the supported 18-column header." }
        val columns = header.withIndex().associate { it.value to it.index }
        data class Row(val workout: FitbodWorkout, val exercise: FitbodExercise, val set: FitbodSet)
        val sets = mutableSetOf<String>()
        val parsed = rows.drop(1).mapIndexed { index, row ->
            val location = "CSV record ${index + 2}"
            require(row.size == header.size) { "$location: expected ${header.size} columns." }
            fun field(name: String) = row[columns.getValue(name)].trim()
            fun label(name: String, max: Int, blank: Boolean = false): String = field(name).also {
                require(it.length <= max && (blank || it.isNotEmpty())) { "$location: invalid $name." }
            }
            fun token(name: String) = label(name, 128).also {
                require(Regex("[A-Za-z0-9_-]+").matches(it)) { "$location: invalid $name identity." }
            }
            fun integer(name: String, range: IntRange): Int = SpreadsheetImport.integer(field(name), range)
                ?: throw IllegalArgumentException("$location: invalid $name.")
            fun flag(name: String) = when {
                field(name).equals("true", true) -> true
                field(name).equals("false", true) -> false
                else -> throw IllegalArgumentException("$location: $name must be True or False.")
            }
            fun date(name: String): Long = try { OffsetDateTime.parse(field(name)).toInstant().toEpochMilli() }
                catch (_: Exception) { throw IllegalArgumentException("$location: invalid offset timestamp in $name.") }
            val startedAt = date("date_utc")
            require(startedAt == date("date_local")) { "$location: local and UTC timestamps disagree." }
            require(field("set_duration_raw").isEmpty() && field("distance_m").isEmpty()) {
                "$location: duration/distance sets are not supported by this strength-set importer. No data was imported."
            }
            val weight = field("weight_kg").toDoubleOrNull()
            require(weight != null && weight.isFinite() && weight in 0.0..5000.0) { "$location: invalid weight_kg." }
            val id = token("set_id")
            require(sets.add(id)) { "$location: duplicate set_id." }
            val set = FitbodSet(id, integer("set_number", 1..9999), integer("reps", 1..9999), weight,
                flag("is_warmup"), flag("is_amrap"), field("rest_seconds").takeIf { it.isNotEmpty() }?.let {
                    integer("rest_seconds", 0..1800)
                }, label("notes", 4096, blank = true))
            val exercise = FitbodExercise(label("exercise_name", 160), token("exercise_external_resource_id"),
                integer("exercise_position", 0..99), field("circuit_position").also {
                    require(it.isEmpty() || SpreadsheetImport.integer(it, 0..99) != null) { "$location: invalid circuit_position." }
                }, emptyList())
            val workout = FitbodWorkout(token("workout_id"), label("workout_name", 160, blank = true), field("date_local"), startedAt, emptyList())
            Row(workout, exercise, set)
        }
        require(parsed.isNotEmpty()) { "Fitbod CSV contains no strength sets." }
        val grouped = parsed.groupBy { it.workout.sourceId }
        require(grouped.size <= 2000) { "Fitbod CSV exceeds 2,000 workouts." }
        val workouts = grouped.values.map { workoutRows ->
            val workout = workoutRows.first().workout
            require(workoutRows.all { it.workout.name == workout.name && it.workout.startedAt == workout.startedAt }) {
                "Workout ${workout.sourceId}: conflicting name or timestamp."
            }
            val exercises = workoutRows.groupBy { it.exercise.position }.toSortedMap().values.map { exerciseRows ->
                val exercise = exerciseRows.first().exercise
                require(exerciseRows.all { it.exercise == exercise }) { "Workout ${workout.sourceId}: conflicting exercise position ${exercise.position}." }
                require(exerciseRows.map { it.set.number }.distinct().size == exerciseRows.size) { "Workout ${workout.sourceId}: duplicate set_number." }
                require(exerciseRows.size <= 100) { "Workout ${workout.sourceId}: more than 100 sets in an exercise." }
                exercise.copy(sets = exerciseRows.map { it.set }.sortedBy { it.number })
            }
            workout.copy(exercises = exercises)
        }.sortedByDescending { it.startedAt }
        return FitbodPreview(workouts)
    }

    private fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var index = 0
        fun endField() {
            require(row.size < 18) { "Fitbod CSV has too many columns." }
            row += field.toString(); field.setLength(0); closed = false
        }
        fun endRow() {
            endField()
            if (row.size != 1 || row.single().isNotEmpty()) {
                require(rows.size <= 20000) { "Fitbod CSV exceeds 20,000 sets." }
                rows += row
            }
            row = mutableListOf()
        }
        while (index < text.length) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Import cancelled")
            val ch = text[index++]
            when {
                quoted && ch == '"' -> {
                    if (index < text.length && text[index] == '"') { field.append('"'); index++ }
                    else { quoted = false; closed = true }
                }
                quoted -> field.append(ch)
                ch == ',' -> endField()
                ch == '\r' || ch == '\n' -> {
                    if (ch == '\r' && index < text.length && text[index] == '\n') index++
                    endRow()
                }
                ch == '"' -> {
                    require(field.isEmpty() && !closed) { "Malformed CSV quoting." }
                    quoted = true
                }
                else -> { require(!closed) { "Unexpected text after a quoted CSV field." }; field.append(ch) }
            }
            require(field.length <= 4096) { "Fitbod CSV field exceeds 4096 characters." }
        }
        require(!quoted) { "Unterminated quoted CSV field." }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) endRow()
        return rows
    }
}

internal fun FitbodPreview.materializeHistory(choices: Map<String, String>, skipped: Set<String>, catalog: List<Exercise>): List<Workout> {
    require(exerciseNames.all { it in skipped || it in choices }) { "Resolve or explicitly skip every unmatched exercise." }
    val available = catalog.filterNot { it.archived }.associateBy { it.id }
    return workouts.mapNotNull { source ->
        val exercises = source.exercises.filterNot { it.name in skipped }.map { exercise ->
            val match = available[choices[exercise.name]] ?: throw IllegalArgumentException("An exercise is unavailable. Review the import again.")
            val metrics = match.loggingMetrics()
            require(metrics.any { it.isReps } && metrics.none { it == SetMetric.ASSISTANCE } &&
                (exercise.sets.all { it.weightKg == 0.0 } || metrics.any { it.isLoad })) {
                "${match.name} cannot represent these recorded weight/repetition values. Choose a compatible strength exercise or skip it."
            }
            val sets = exercise.sets.map { set ->
                LoggedSet(id = "fitbod-set:${set.id}", weightKg = set.weightKg, reps = set.reps, warmup = set.warmup, completed = true).also {
                    require(it.canComplete(metrics)) { "${match.name} requires metrics absent from this CSV. Choose a compatible strength exercise or skip it." }
                }
            }
            val rests = exercise.sets.map { it.restSeconds }.distinct()
            val notes = buildList {
                add("Fitbod exercise: ${exercise.name}")
                if (exercise.circuit.isNotEmpty()) add("Fitbod circuit position: ${exercise.circuit} (not converted to a superset).")
                exercise.sets.forEach { set ->
                    val details = buildList {
                        if (set.amrap) add("AMRAP")
                        if (rests.size > 1) add("rest ${set.restSeconds?.let { "$it seconds" } ?: "unspecified"}")
                        if (set.notes.isNotEmpty()) add(set.notes)
                    }
                    if (details.isNotEmpty()) add("Fitbod set ${set.number}: ${details.joinToString(" · ")}")
                }
            }.joinToString("\n")
            SessionExercise(id = "fitbod-exercise:${source.sourceId}:${exercise.position}", exerciseId = match.id,
                sets = sets, notes = notes, restSeconds = rests.singleOrNull())
        }
        if (exercises.isEmpty()) null else Workout(id = source.id, name = source.name.ifBlank { "Fitbod workout" },
            startedAt = source.startedAt, finishedAt = source.startedAt, exercises = exercises,
            notes = "Imported from Fitbod. Source date: ${source.date}. Workout duration unavailable; start and end use the source timestamp.")
    }
}

internal fun AppData.withFitbodHistory(workouts: List<Workout>, catalog: List<Exercise>): AppData {
    val existing = history.map { it.id }.toSet()
    val additions = workouts.filterNot { it.id in existing }
    require(additions.size <= 2000 && additions.map { it.id }.distinct().size == additions.size) { "Invalid Fitbod workout identities." }
    val available = catalog.filterNot { it.archived }.associateBy { it.id }
    additions.forEach { workout ->
        require(workout.id.startsWith("fitbod-workout:") && workout.id != active?.id && workout.finishedAt == workout.startedAt && workout.exercises.isNotEmpty()) { "Invalid Fitbod history entry." }
        require(workout.exercises.map { it.id }.distinct().size == workout.exercises.size) { "Duplicate Fitbod exercise identity." }
        val sets = workout.exercises.flatMap { it.sets }
        require(sets.map { it.id }.distinct().size == sets.size) { "Duplicate Fitbod set identity." }
        workout.exercises.forEach { exercise ->
            val match = available[exercise.exerciseId] ?: throw IllegalArgumentException("An exercise is unavailable. Review the import again.")
            val metrics = match.loggingMetrics()
            require(exercise.sets.isNotEmpty() && exercise.sets.all { it.completed && it.canComplete(metrics) }) { "Invalid completed Fitbod sets." }
        }
    }
    return if (additions.isEmpty()) this else copy(history = (history + additions).sortedByDescending { it.startedAt })
}

internal fun AppData.withFitbodImport(
    preview: FitbodPreview,
    choices: Map<String, String>,
    skipped: Set<String>,
    newExercises: List<Exercise>,
    catalog: List<Exercise>,
): AppData {
    val pending = preview.pending(history)
    val used = pending.exerciseNames.filterNot { it in skipped }.mapNotNull { choices[it] }.toSet()
    val additions = newExercises.filter { it.id in used }.distinctBy { it.id }
    val known = catalog.map { it.id }.toSet()
    require(additions.none { it.id in known } && additions.all { it.custom && !it.archived && it.supportsFitbodStrength(true) }) {
        "Preserved exercise identities changed. Preview the import again."
    }
    val completeCatalog = catalog + additions
    val workouts = pending.materializeHistory(choices, skipped, completeCatalog)
    val imported = withFitbodHistory(workouts, completeCatalog)
    return if (additions.isEmpty()) imported else imported.copy(customExercises = customExercises + additions)
}
