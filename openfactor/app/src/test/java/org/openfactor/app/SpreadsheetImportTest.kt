package org.openfactor.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpreadsheetImportTest {
    private val bench = Exercise("bench", "Barbell Bench Press", "Chest", "Barbell", aliases = listOf("Bench Press"))
    private val pushup = Exercise("pushup", "Push-Up", "Chest", "Bodyweight")
    private val catalog = listOf(bench, pushup)
    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val packageRel = "http://schemas.openxmlformats.org/package/2006/relationships"

    private fun cell(ref: String, value: String) = "<c r=\"$ref\" t=\"inlineStr\"><is><t>$value</t></is></c>"
    private fun number(ref: String, value: String) = "<c r=\"$ref\"><v>$value</v></c>"
    private fun formula(ref: String, cache: String) = "<c r=\"$ref\"><f>1+1</f><v>$cache</v></c>"
    private fun row(n: Int, vararg cells: String) = "<row r=\"$n\">${cells.joinToString("")}</row>"
    private fun sheet(content: String) = "<worksheet xmlns=\"$main\"><sheetData>$content</sheetData></worksheet>"
    private fun sampleRows(): String = listOf(
        row(1, cell("B1", "DAY 1 BENCH")),
        row(2, cell("B2", "EXERCISE"), cell("D2", "WEEK"), cell("E2", "WEIGHT"), cell("F2", "REPS"), cell("G2", "RIR")),
        row(3, cell("B3", "Barbell Bench Press"), number("D3", "1.0"), number("E3", "90"), number("F3", "9.0"), number("G3", "2.0"), number("H3", "8")),
        row(4, cell("B4", "2-MIN REST"), number("D4", "2.0"), formula("E4", "95"), cell("F4", "6–8"), number("G4", "1")),
        row(5, cell("B5", "Push-Up"), number("D5", "1"), cell("F5", "AMRAP"), number("G5", "0")),
        row(6, formula("B6", "Video Tutorial"), number("D6", "2"), cell("F6", "AMRAP"), number("G6", "1")),
        row(7, cell("B7", "ESTIMATED 1RM")),
        row(8, formula("D8", "Pending"), cell("F8", "BODY WEIGHT")),
    ).joinToString("")

    private fun workbook(content: String = sampleRows(), extra: Map<String, String> = emptyMap()): ByteArray {
        val members = linkedMapOf(
            "xl/workbook.xml" to "<workbook xmlns=\"$main\" xmlns:r=\"$rel\"><sheets><sheet name=\"Phase 1\" r:id=\"r1\"/><sheet name=\"Algorithms\" r:id=\"r2\"/><sheet name=\"Exercise Videos\" r:id=\"r3\"/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns=\"$packageRel\"><Relationship Id=\"r1\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>",
            "xl/worksheets/sheet1.xml" to sheet(content),
        )
        members.putAll(extra)
        return zip(members)
    }

    private fun zip(members: Map<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip -> members.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
        } }
        output.toByteArray()
    }

    private fun read(bytes: ByteArray = workbook()) = SpreadsheetImport.read(bytes.inputStream())
    private fun rejects(message: String, block: () -> Unit) {
        try { block(); fail("Expected rejection containing '$message'") }
        catch (failure: Exception) { assertTrue("Unexpected error: ${failure.message}", failure.message.orEmpty().contains(message, true)) }
    }

    @Test fun weeklyRowsRemainCyclesAndTargetsNeverBecomeActuals() {
        val preview = read()
        assertEquals(1, preview.phases.size)
        assertEquals(2, preview.phases.single().weekCount)
        assertEquals(2, preview.phases.single().days.single().exercises.size)
        assertTrue(preview.warnings.any { "Algorithms, Exercise Videos" in it })
        assertTrue(preview.warnings.any { "AMRAP" in it })
        val program = preview.materializePrograms(mapOf(bench.name to bench.id, pushup.name to pushup.id), emptySet(), 3, catalog).single()
        assertEquals(ProgramCycleUnit.WEEK, program.cycleUnit)
        assertEquals(2, program.cycleCount)
        assertEquals(3, program.days.single().exercises.first().cycles.first().sets.size)
        val source = program.days.single().exercises.first()
        assertEquals(120, source.cycles[0].restSeconds)
        assertEquals(120, source.cycles[1].restSeconds)
        assertEquals(9, source.cycles[0].sets.first().repsMin)
        assertEquals(6, source.cycles[1].sets.first().repsMin)
        assertEquals(8, source.cycles[1].sets.first().repsMax)
        assertEquals(1, source.cycles[1].sets.first().rir)
        val workout = program.toWorkout(program.days.single().id, 1, 1234L, catalog = catalog)!!
        assertTrue(workout.exercises.flatMap { it.sets }.all { !it.completed && it.weightKg == null && it.reps == null && it.rir == null })
        assertEquals(1, workout.exercises.first().sets.first().targetRir)
        assertEquals(6, workout.exercises.first().sets.first().targetRepsMin)
        assertNull(workout.exercises.last().sets.first().targetRepsMin)
        assertEquals(1, workout.exercises.last().sets.first().targetRir)
    }

    @Test fun appendPreservesEverythingElseAndRejectsCollision() {
        val program = read().materializePrograms(mapOf(bench.name to bench.id, pushup.name to pushup.id), emptySet(), 2, catalog).single()
        val originalProgram = TrainingProgram(name = "Existing")
        val active = Workout(name = "In progress")
        val history = Workout(name = "Saved", finishedAt = 1000L)
        val original = AppData(programs = listOf(originalProgram), activeProgramId = originalProgram.id,
            programDraft = TrainingProgram(name = "Unsaved draft"), active = active, history = listOf(history),
            routines = listOf(Routine(name = "Existing template", exerciseIds = listOf(bench.id))))
        val appended = original.withSpreadsheetPrograms(listOf(program), catalog)
        assertEquals(original, appended.copy(programs = original.programs))
        assertEquals(listOf(originalProgram, program), appended.programs)
        rejects("identities already exist") { appended.withSpreadsheetPrograms(listOf(program), catalog) }
        assertEquals(original, original.copy())
    }

    @Test fun exactMatchingDoesNotPickAnAmbiguousVariantOrFuzzySuggestion() {
        val matcher = SpreadsheetExerciseMatcher(catalog + bench.copy(id = "duplicate", name = "Another Bench", aliases = listOf("Bench Press")))
        assertEquals(bench.id, matcher.automatic("BARBELL BENCH PRESS"))
        assertNull(matcher.automatic("Bench Press"))
        assertNull(matcher.automatic("Barbel Bench Press"))
        assertEquals(bench.id, matcher.suggestions("Barbel Bench Press").first().id)
        assertNull(SpreadsheetExerciseMatcher(listOf(bench.copy(archived = true))).automatic(bench.name))
    }

    @Test fun unresolvedNamesAndEmptyDaysCannotBeSilentlyDropped() {
        val preview = read()
        rejects("Resolve or explicitly skip") { preview.materializePrograms(emptyMap(), emptySet(), 2, catalog) }
        rejects("no resolved exercises") { preview.materializePrograms(emptyMap(), preview.exerciseNames.toSet(), 2, catalog) }
        val programs = preview.materializePrograms(mapOf(bench.name to bench.id), setOf(pushup.name), 2, catalog)
        assertEquals(1, programs.single().days.single().exercises.size)
        rejects("same exercise") { preview.materializePrograms(preview.exerciseNames.associateWith { bench.id }, emptySet(), 2, catalog) }
        rejects("1–30") { preview.materializePrograms(mapOf(bench.name to bench.id), setOf(pushup.name), 0, catalog) }
    }

    @Test fun formulaTargetsUseOnlyValidatedStoredValuesAndWarnAboutStaleness() {
        val preview = read(workbook(sampleRows().replace(number("F3", "9.0"), formula("F3", "9"))))
        assertEquals("9", preview.phases.single().days.single().exercises.first().weeks.first().reps)
        assertTrue(preview.warnings.any { "Stored formula REPS values" in it })
        rejects("has no stored value") { read(workbook(sampleRows().replace(number("F3", "9.0"), formula("F3", "")))) }
        rejects("RIR must be") { read(workbook(sampleRows().replace(number("G3", "2.0"), formula("G3", "Pending")))) }
        rejects("formula-based weeks") { read(workbook(sampleRows().replace(number("D3", "1.0"), formula("D3", "1")))) }
    }

    @Test fun missingDuplicateAndInvalidTargetsAreRejected() {
        rejects("missing weeks") { read(workbook(sampleRows().replace(number("D4", "2.0"), number("D4", "3")))) }
        rejects("duplicate exercise week") { read(workbook(sampleRows().replace(number("D4", "2.0"), number("D4", "1")))) }
        rejects("unsupported rep target") { read(workbook(sampleRows().replace(number("F3", "9.0"), cell("F3", "lots")))) }
        rejects("RIR must be") { read(workbook(sampleRows().replace(number("G3", "2.0"), number("G3", "11")))) }
        rejects("rest must be") { read(workbook(sampleRows().replace("2-MIN REST", "99-MIN REST"))) }
    }

    @Test fun cachedExerciseNamesAreReviewedDataNotExecutedExpressions() {
        val preview = read(workbook(sampleRows().replace(cell("B3", bench.name), formula("B3", bench.name))))
        assertEquals(bench.name, preview.exerciseNames.first())
        assertTrue(preview.warnings.any { "Stored formula exercise names" in it })
        rejects("week has no exercise name") { read(workbook(sampleRows().replace(cell("B3", bench.name), formula("B3", "")))) }
    }

    @Test fun unsupportedTimeAndStepTargetsNeverBecomeRepetitionCounts() {
        val preview = read(workbook(sampleRows().replace("AMRAP", "30 sec")))
        assertTrue(preview.warnings.any { "'30 sec'" in it })
        val duration = pushup.copy(properties = mapOf("Metrics" to "Duration"))
        val programs = preview.materializePrograms(mapOf(bench.name to bench.id, pushup.name to pushup.id), emptySet(), 2, listOf(bench, duration))
        assertTrue(programs.single().days.single().exercises.last().cycles.flatMap { it.sets }.all { it.repsMin == null && it.repsMax == null && it.rir == null })
        val steps = read(workbook(sampleRows().replace("AMRAP", "50 Steps")))
        assertTrue(steps.warnings.any { "'50 Steps'" in it })
        rejects("does not support the numeric rep targets") {
            preview.materializePrograms(preview.exerciseNames.associateWith { duration.id }, emptySet(), 2, listOf(duration))
        }
    }

    @Test fun literalSharedStringsAndRichTextAreSupported() {
        val preview = read(workbook(sampleRows().replace(cell("B3", bench.name), "<c r=\"B3\" t=\"s\"><v>0</v></c>"), mapOf(
            "xl/sharedStrings.xml" to "<sst xmlns=\"$main\"><si><r><t>Barbell </t></r><r><t>Bench Press</t></r></si></sst>")))
        assertEquals(bench.name, preview.exerciseNames.first())
    }

    @Test fun externalEntitiesAndNonUtf8XmlAreRejectedBeforeParsing() {
        val malicious = "<!DOCTYPE worksheet [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>" + sheet(sampleRows())
        rejects("without DTDs") { read(workbook(extra = mapOf("xl/worksheets/sheet1.xml" to malicious))) }
        rejects("Only UTF-8") { read(workbook(extra = mapOf("xl/worksheets/sheet1.xml" to "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + sheet(sampleRows())))) }
        rejects("without DTDs") { read(workbook(extra = mapOf("xl/worksheets/sheet1.xml" to "\u0000" + sheet(sampleRows())))) }
    }

    @Test fun archivePathsExternalWorksheetsAndSizeAreBounded() {
        rejects("Unsafe workbook member") { read(workbook(extra = mapOf("../outside" to "not extracted"))) }
        rejects("safe size limits") { read(workbook(extra = mapOf("bomb.txt" to "x".repeat(2 * 1024 * 1024 + 1)))) }
        rejects("safe size limits") { read(ByteArray(SpreadsheetImport.MAX_FILE_BYTES + 1)) }
        val external = "<Relationships xmlns=\"$packageRel\"><Relationship Id=\"r1\" Type=\"$rel/worksheet\" TargetMode=\"External\" Target=\"https://invalid.test/worksheet\"/></Relationships>"
        rejects("External worksheets") { read(workbook(extra = mapOf("xl/_rels/workbook.xml.rels" to external))) }
        val traversal = external.replace("TargetMode=\"External\" ", "").replace("https://invalid.test/worksheet", "../worksheet.xml")
        rejects("Unsafe worksheet") { read(workbook(extra = mapOf("xl/_rels/workbook.xml.rels" to traversal))) }
    }

    @Test fun cellsStringsRowsEntriesAndXmlDepthAreBounded() {
        rejects("4096") { read(workbook(sampleRows().replace(bench.name, "x".repeat(4097)))) }
        rejects("Too many columns") { read(workbook(sampleRows().replace("B3", "ZZ3"))) }
        rejects("duplicate worksheet cell") { read(workbook(sampleRows().replace(number("F3", "9.0"), number("F3", "9.0") + number("F3", "9.0")))) }
        rejects("worksheet rows") { read(workbook((1..2001).joinToString("") { row(it) })) }
        rejects("too many ZIP entries") { read(workbook(extra = (1..126).associate { "extra$it" to "" })) }
        rejects("complexity") { read(workbook(extra = mapOf("xl/worksheets/sheet1.xml" to "<x>".repeat(33) + "</x>".repeat(33)))) }
    }

    @Test fun unsupportedDocumentsDoNotBecomeRoutines() {
        rejects("unencrypted .xlsx") { read("exercise,sets,reps\nBench,3,8".toByteArray()) }
        rejects("No supported") { read(workbook(extra = mapOf("xl/workbook.xml" to "<workbook xmlns=\"$main\"><sheets><sheet name=\"Algorithms\"/></sheets></workbook>"))) }
        rejects("empty or unsupported") { read(workbook(row(1, cell("B1", "DAY 1 BENCH")))) }
    }

    /** Optional private attachment smoke test; the workbook itself never enters the repository. */
    @Test fun suppliedWorkbookSmokeTestWhenExplicitlyProvided() {
        val path = System.getenv("OPENFACTOR_SPREADSHEET_SAMPLE")
        assumeTrue("No private sample requested", path != null)
        val preview = File(path!!).inputStream().use(SpreadsheetImport::read)
        assertEquals(listOf("Phase 1", "Phase 2", "Phase 3"), preview.phases.map { it.name })
        assertTrue(preview.phases.all { it.days.size == 5 && it.weekCount == 5 })
        assertEquals(75, preview.phases.sumOf { p -> p.days.sumOf { it.exercises.size } })
        assertTrue(preview.warnings.any { "AMRAP" in it })
        val syntheticCatalog = preview.exerciseNames.mapIndexed { i, name -> Exercise("sample-$i", name, "Test", "Test") }
        val programs = preview.materializePrograms(syntheticCatalog.associate { it.name to it.id }, emptySet(), 3, syntheticCatalog)
        assertEquals(3, programs.size)
        assertEquals(15, programs.sumOf { it.days.size })
        assertEquals(1125, programs.sumOf { p -> p.days.sumOf { d -> d.exercises.sumOf { e -> e.cycles.sumOf { it.sets.size } } } })
        val imported = AppData().withSpreadsheetPrograms(programs, syntheticCatalog)
        assertEquals(programs, imported.programs)
        assertTrue(imported.history.isEmpty())
        assertNull(imported.active)
        assertNull(imported.activeProgramId)
    }
}
