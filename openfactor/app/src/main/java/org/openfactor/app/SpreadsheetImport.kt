package org.openfactor.app

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal data class SpreadsheetWeek(val week: Int, val reps: String, val rir: String)
internal data class SpreadsheetExercise(val name: String, val weeks: List<SpreadsheetWeek>, val restSeconds: Int?)
internal data class SpreadsheetDay(val name: String, val exercises: List<SpreadsheetExercise>)
internal data class SpreadsheetPhase(val name: String, val days: List<SpreadsheetDay>, val weekCount: Int)
internal data class SpreadsheetPreview(val phases: List<SpreadsheetPhase>, val warnings: List<String>) {
    val exerciseNames: List<String> get() = phases.flatMap { it.days }.flatMap { it.exercises }.map { it.name }.distinct()
}

/** Strictly bounded, data-only XLSX reader. ZIP members never touch the filesystem. */
internal object SpreadsheetImport {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 12 * 1024 * 1024
    private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val phaseName = Regex("Phase [1-3]", RegexOption.IGNORE_CASE)
    private val dayName = Regex("DAY\\s+[1-9][0-9]*(?:\\s+.*)?", RegexOption.IGNORE_CASE)
    private val rest = Regex("([0-9]+)\\s*-?\\s*(MIN|SEC)\\s+REST", RegexOption.IGNORE_CASE)

    fun read(input: InputStream): SpreadsheetPreview {
        val bytes = boundedRead(input, MAX_FILE_BYTES)
        require(bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            "Choose an unencrypted .xlsx workbook, not CSV, XLS or a backup."
        }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 128) { "Workbook has too many ZIP entries (maximum 128)." }
                require(entry.name.length <= 240 && !entry.name.startsWith('/') && '\\' !in entry.name &&
                    entry.name.split('/').none { it == ".." || it == "." }) { "Unsafe workbook member path." }
                require(entry.name !in entries) { "Workbook contains duplicate ZIP members." }
                val content = boundedRead(zip, minOf(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total))
                total += content.size
                entries[entry.name] = content
            }
        }
        fun member(name: String) = entries[name] ?: throw IllegalArgumentException("Workbook is missing $name.")
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { data ->
            var text: StringBuilder? = null
            var inText = false
            xml(data, object : DefaultHandler() {
                override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                    if (uri != MAIN) return
                    if (local == "si") { require(shared.size < 20000) { "Too many shared strings." }; text = StringBuilder() }
                    if (local == "t") inText = true
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText) text?.let { appendText(it, ch, start, length) }
                }
                override fun endElement(uri: String, local: String, q: String) {
                    if (uri != MAIN) return
                    if (local == "t") inText = false
                    if (local == "si") { shared += text.toString(); text = null }
                }
            })
        }
        val relationships = mutableMapOf<String, String>()
        xml(member("xl/_rels/workbook.xml.rels"), object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (uri != PACKAGE_REL || local != "Relationship") return
                val id = a.getValue("Id") ?: return
                require(id !in relationships) { "Duplicate workbook relationship." }
                if (a.getValue("Type") != "$REL/worksheet") return
                require(a.getValue("TargetMode") != "External") { "External worksheets are not supported." }
                val target = a.getValue("Target").orEmpty()
                val path = if (target.startsWith("/xl/")) target.drop(1) else "xl/$target"
                require(Regex("xl/worksheets/[A-Za-z0-9_-]+\\.xml").matches(path)) { "Unsafe worksheet relationship." }
                relationships[id] = path
            }
        })
        val sheets = mutableListOf<Pair<String, String>>()
        val ignored = mutableListOf<String>()
        xml(member("xl/workbook.xml"), object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (uri != MAIN || local != "sheet") return
                require(sheets.size + ignored.size < 20) { "Too many worksheets (maximum 20)." }
                val name = a.getValue("name").orEmpty()
                require(name.length <= 80) { "Worksheet name is too long." }
                if (!phaseName.matches(name)) { ignored += name; return }
                require(sheets.none { it.first.equals(name, ignoreCase = true) }) { "Duplicate phase worksheet." }
                sheets += name to (relationships[a.getValue(REL, "id")]
                    ?: throw IllegalArgumentException("Missing worksheet relationship for $name."))
            }
        })
        require(sheets.isNotEmpty()) { "No supported Phase 1, Phase 2 or Phase 3 worksheets were found." }
        val warnings = linkedSetOf(
            "Weekly rows are target changes, not sets. Choose the working-set count below; it applies to every imported exercise.",
            "Weight, achieved reps, dates and progression algorithms are not imported. Formula expressions are never executed. No training history is created.",
            "Only numeric rep ranges, RIR and literal rest targets fit the program model. RIR applies only to rep-based catalog matches. Keep the source workbook for AMRAP, duration and step targets.",
        )
        if (ignored.isNotEmpty()) warnings += "Ignored non-routine sheets: ${ignored.joinToString(", ")}."
        val phases = sheets.map { (name, path) -> phase(name, rows(member(path), shared), warnings) }
        return SpreadsheetPreview(phases, warnings.toList())
    }

    private data class Cell(val text: String, val formula: Boolean)
    private fun rows(data: ByteArray, shared: List<String>): List<Map<Int, Cell>> {
        val rows = mutableListOf<Map<Int, Cell>>()
        var row: MutableMap<Int, Cell>? = null
        var column = -1
        var type = ""
        var formula = false
        var capture = false
        var value = StringBuilder()
        var cellCount = 0
        xml(data, object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (uri != MAIN) return
                when (local) {
                    "row" -> { require(rows.size < 2000 && row == null) { "Too many or nested worksheet rows." }; row = linkedMapOf() }
                    "c" -> {
                        require(++cellCount <= 50000 && row != null) { "Too many or misplaced worksheet cells." }
                        val ref = a.getValue("r").orEmpty()
                        require(Regex("[A-Z]{1,3}[1-9][0-9]{0,6}").matches(ref)) { "Invalid worksheet cell reference." }
                        column = ref.takeWhile { it in 'A'..'Z' }.fold(0) { n, c -> n * 26 + c.code - 'A'.code + 1 } - 1
                        require(column < 128 && column !in row!!) { "Too many columns or duplicate worksheet cell." }
                        type = a.getValue("t").orEmpty(); formula = false; value = StringBuilder()
                    }
                    "f" -> formula = true
                    "v", "t" -> capture = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) appendText(value, ch, start, length)
            }
            override fun endElement(uri: String, local: String, q: String) {
                if (uri != MAIN) return
                when (local) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (type == "s") {
                            shared.getOrNull(raw.toIntOrNull() ?: -1)
                                ?: throw IllegalArgumentException("Invalid shared string reference.")
                        } else raw
                        row!![column] = Cell(text.trim(), formula)
                    }
                    "row" -> { rows += row!!; row = null }
                }
            }
        })
        return rows
    }

    private fun phase(name: String, rows: List<Map<Int, Cell>>, warnings: MutableSet<String>): SpreadsheetPhase {
        data class PendingExercise(val name: String, val weeks: MutableList<SpreadsheetWeek>, var rest: Int? = null)
        data class PendingDay(val name: String, val exercises: MutableList<PendingExercise>)
        val days = mutableListOf<PendingDay>()
        var currentDay: PendingDay? = null
        var currentExercise: PendingExercise? = null
        var columns: Map<String, Int>? = null
        for ((index, row) in rows.withIndex()) {
            val location = "$name row ${index + 1}"
            val heading = row.values.firstOrNull { !it.formula && dayName.matches(it.text) }?.text
            if (heading != null) {
                require(days.size < 14) { "$name has too many days." }
                require(days.none { it.name == heading }) { "Duplicate day in $name." }
                currentDay = PendingDay(heading, mutableListOf()).also { days += it }
                currentExercise = null; columns = null
                continue
            }
            val header = row.entries.filter { !it.value.formula }.associate { it.value.text.uppercase(Locale.ROOT) to it.key }
            if ("EXERCISE" in header && "WEEK" in header && "REPS" in header && "RIR" in header) {
                require(currentDay != null) { "$location: exercise table appears before a DAY heading." }
                columns = header
                continue
            }
            val cols = columns ?: continue
            fun cell(key: String) = row[cols[key]] ?: Cell("", false)
            if (cell("EXERCISE").let { !it.formula && it.text.equals("ESTIMATED 1RM", true) }) {
                columns = null; currentExercise = null
                continue
            }
            val weekCell = cell("WEEK")
            if (weekCell.text.isBlank() && !weekCell.formula) continue
            require(!weekCell.formula) { "$location: formula-based weeks are not supported." }
            val week = integer(weekCell.text, 1..52) ?: throw IllegalArgumentException("$location: invalid week number.")
            val label = cell("EXERCISE")
            val restMatch = rest.matchEntire(label.text)
            if (label.text.isNotBlank() && restMatch == null && !label.text.equals("Video Tutorial", true)) {
                if (label.formula) warnings += "Stored formula exercise names are used without recalculation. Review their catalog matches carefully; they may be stale."
                require(week == 1) { "$location: an exercise must begin at week 1." }
                require(label.text.length <= 160) { "$location: exercise name is too long." }
                require(currentDay!!.exercises.size < 100) { "$name has too many exercises in a day." }
                currentExercise = PendingExercise(label.text, mutableListOf()).also { currentDay.exercises += it }
            }
            val exercise = currentExercise ?: throw IllegalArgumentException("$location: week has no exercise name.")
            require(exercise.weeks.none { it.week == week }) { "$location: duplicate exercise week." }
            if (restMatch != null) {
                require(!label.formula) { "$location: rest targets must be literal, not stored formula results." }
                val amount = restMatch.groupValues[1].toIntOrNull() ?: -1
                val seconds = amount.toLong() * if (restMatch.groupValues[2].equals("MIN", true)) 60 else 1
                require(seconds in 0..1800) { "$location: rest must be 0–1800 seconds." }
                require(exercise.rest == null || exercise.rest == seconds.toInt()) { "$location: conflicting rest rows." }
                exercise.rest = seconds.toInt()
            }
            fun target(key: String): String {
                val target = cell(key)
                if (target.formula) {
                    require(target.text.isNotBlank()) { "$location: formula-based $key target has no stored value. Recalculate and save the workbook in your spreadsheet app first." }
                    warnings += "Stored formula $key values are imported as data only, without recalculation. They may be stale; compare the preview with your spreadsheet app before confirming."
                }
                return target.text
            }
            val reps = target("REPS")
            val rir = target("RIR")
            val nonRepTarget = reps.equals("AMRAP", true) || Regex("[1-9][0-9]{0,3}\\s+(sec|steps)", RegexOption.IGNORE_CASE).matches(reps)
            require(reps.isBlank() || nonRepTarget || repRange(reps) != null) { "$location: unsupported rep target '$reps'." }
            require(rir.isBlank() || integer(rir, 0..10) != null) { "$location: RIR must be 0–10." }
            if (nonRepTarget) warnings += "'$reps' cannot be represented as a numeric program rep target: ${exercise.name}. The rep target will stay blank; follow this target from your source workbook."
            exercise.weeks += SpreadsheetWeek(week, reps, rir)
        }
        require(days.isNotEmpty() && days.all { it.exercises.isNotEmpty() }) { "$name contains an empty or unsupported DAY section." }
        val weekCount = days.flatMap { it.exercises }.flatMap { it.weeks }.maxOf { it.week }
        val expected = (1..weekCount).toSet()
        require(days.flatMap { it.exercises }.all { it.weeks.map { w -> w.week }.toSet() == expected }) {
            "$name has missing weeks. Every exercise must have weeks 1–$weekCount; missing targets are not guessed."
        }
        return SpreadsheetPhase(name, days.map { day -> SpreadsheetDay(day.name, day.exercises.map {
            SpreadsheetExercise(it.name, it.weeks.sortedBy { w -> w.week }, it.rest)
        }) }, weekCount)
    }

    internal fun integer(text: String, range: IntRange): Int? = text.toDoubleOrNull()?.takeIf {
        it.isFinite() && it % 1.0 == 0.0 && it >= range.first && it <= range.last
    }?.toInt()

    internal fun repRange(text: String): Pair<Int, Int>? {
        integer(text, 1..9999)?.let { return it to it }
        val parts = text.split(Regex("\\s*[-–]\\s*"))
        if (parts.size != 2) return null
        val min = integer(parts[0], 1..9999) ?: return null
        val max = integer(parts[1], min..9999) ?: return null
        return min to max
    }

    private fun boundedRead(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Import cancelled")
            val count = input.read(buffer, 0, minOf(buffer.size, limit - out.size() + 1))
            if (count == -1) break
            require(out.size() + count <= limit) { "Workbook exceeds the safe size limits (4 MiB file, 2 MiB per member, 12 MiB expanded)." }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun appendText(builder: StringBuilder, ch: CharArray, start: Int, length: Int) {
        require(builder.length + length <= 4096) { "Workbook cell or string exceeds 4096 characters." }
        builder.append(ch, start, length)
    }

    private fun xml(data: ByteArray, handler: DefaultHandler) {
        // Android's SAX implementation does not expose Xerces' disallow-doctype feature.
        // Accept only UTF-8 XML and reject declarations before either platform's parser sees them.
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()
        require('\u0000' !in text && !text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) {
            "Workbook XML must be UTF-8 without DTDs or entity declarations."
        }
        val encoding = Regex("<\\?xml[^?]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        require(encoding == null || encoding.equals("UTF-8", true)) { "Only UTF-8 workbook XML is supported." }
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        val reader = factory.newSAXParser().xmlReader
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw IllegalArgumentException("External XML entities are forbidden.") }
        var depth = 0
        var nodes = 0
        reader.contentHandler = object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                require(++depth <= 32 && ++nodes <= 200000 && a.length <= 32) { "Workbook XML exceeds safe complexity limits." }
                handler.startElement(uri, local, q, a)
            }
            override fun endElement(uri: String, local: String, q: String) { handler.endElement(uri, local, q); depth-- }
            override fun characters(ch: CharArray, start: Int, length: Int) { handler.characters(ch, start, length) }
        }
        reader.errorHandler = object : DefaultHandler() {
            override fun error(e: org.xml.sax.SAXParseException) { throw e }
            override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
        }
        reader.parse(InputSource(ByteArrayInputStream(data)))
    }
}

internal interface ImportExerciseSuggestions {
    fun suggestions(name: String): List<Exercise>
}

/** Ranked discovery is for explicit choices only; automatic matching requires a unique literal name/alias. */
internal class SpreadsheetExerciseMatcher(catalog: List<Exercise>) : ImportExerciseSuggestions {
    private val exercises = catalog.filterNot { it.archived }.distinctBy { it.id }
    private val index = ExerciseSearchIndex(exercises)
    private val exact = exercises.flatMap { exercise -> (exercise.aliases + exercise.name).map { key(it) to exercise.id } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }
    fun automatic(name: String): String? = exact[key(name)]?.singleOrNull()
    override fun suggestions(name: String): List<Exercise> = index.search(name).take(8)
    companion object {
        private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}

internal fun SpreadsheetPreview.materializePrograms(
    choices: Map<String, String>,
    skipped: Set<String>,
    sets: Int,
    catalog: List<Exercise>,
): List<TrainingProgram> {
    require(sets in 1..30) { "Choose 1–30 working sets per exercise." }
    val targetCount = phases.sumOf { phase -> phase.days.sumOf { day -> day.exercises.filterNot { it.name in skipped }.sumOf { it.weeks.size } } }
    require(targetCount.toLong() * sets <= 20000) { "Import exceeds 20,000 planned sets. Use fewer sets or a smaller workbook." }
    require(exerciseNames.all { it in skipped || choices[it] != null }) { "Resolve or explicitly skip every unmatched exercise." }
    val available = catalog.filterNot { it.archived }.associateBy { it.id }
    return phases.map { phase ->
        val days = phase.days.map { day ->
            val exercises = day.exercises.filterNot { it.name in skipped }.map { source ->
                val exercise = available[choices[source.name]] ?: throw IllegalArgumentException("An exercise is no longer available. Review the import again.")
                val supportsReps = exercise.loggingMetrics().any { it.isReps }
                require(supportsReps || source.weeks.none { SpreadsheetImport.repRange(it.reps) != null }) {
                    "${exercise.name} does not support the numeric rep targets. Choose a rep-based exercise or skip it."
                }
                PlannedExercise(exerciseId = exercise.id, cycles = source.weeks.map { week ->
                    val reps = SpreadsheetImport.repRange(week.reps)
                    CycleTarget(sets = List(sets) { SetTarget(repsMin = reps?.first, repsMax = reps?.second,
                        rir = SpreadsheetImport.integer(week.rir, 0..10).takeIf { supportsReps }) }, restSeconds = source.restSeconds)
                })
            }
            require(exercises.isNotEmpty()) { "${phase.name} / ${day.name} has no resolved exercises. No import was saved." }
            require(exercises.map { it.exerciseId }.distinct().size == exercises.size) { "${phase.name} / ${day.name} maps two entries to the same exercise. Review your choices." }
            ProgramDay(name = day.name, exercises = exercises)
        }
        TrainingProgram(name = "Imported · ${phase.name}", days = days, cycleCount = phase.weekCount, cycleUnit = ProgramCycleUnit.WEEK)
    }
}

internal fun AppData.withSpreadsheetPrograms(programsToAppend: List<TrainingProgram>, catalog: List<Exercise>): AppData {
    require(programsToAppend.size in 1..3) { "Import one to three phases at a time." }
    val ids = programsToAppend.map { it.id }
    require(ids.distinct().size == ids.size && programs.none { it.id in ids } && programDraft?.id !in ids) { "Import identities already exist. Preview the workbook again." }
    val available = catalog.filterNot { it.archived }.map { it.id }.toSet()
    programsToAppend.forEach { program ->
        require(program.days.all { it.exercises.isNotEmpty() && it.exercises.all { exercise -> exercise.exerciseId in available } }) {
            "An imported exercise is unavailable. Review the import again."
        }
        ProgramCodec.decode(ProgramCodec.encode(program))
    }
    return copy(programs = programs + programsToAppend)
}
