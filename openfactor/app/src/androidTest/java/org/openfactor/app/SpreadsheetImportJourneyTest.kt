package org.openfactor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpreadsheetImportJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun documentActionClearsTheFloatingNavigationAndBackDoesNotChangeTraining() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        var activity: ActivityScenario<MainActivity>? = null
        try {
            store.save(AppData(onboarding = OnboardingState(completed = true)))
            activity = ActivityScenario.launch(MainActivity::class.java)
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Settings").onLast().performClick()
            compose.onNodeWithText("Import spreadsheet").performScrollTo().performClick()
            val button = compose.onNodeWithText("Choose .xlsx document").assertIsDisplayed().getUnclippedBoundsInRoot()
            val navigation = compose.onNodeWithContentDescription("Quick actions").getUnclippedBoundsInRoot()
            assertTrue("Document action must be above floating navigation", button.bottom <= navigation.top)
            compose.onNodeWithContentDescription("Back").performClick()
            val after = store.load()
            assertTrue(after.history.isEmpty())
            assertTrue(after.programs.isEmpty())
            assertNull(after.active)
        } finally {
            activity?.close()
            store.save(before)
        }
    }

    @Test fun platformXmlParserReadsTargetsAndAppendsWithoutCreatingHistory() {
        val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val members = mapOf(
            "xl/workbook.xml" to """<workbook xmlns="$main" xmlns:r="$rel"><sheets><sheet name="Phase 1" r:id="r1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="r1" Type="$rel/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$main"><sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>DAY 1 BENCH</t></is></c></row>
                <row r="2"><c r="A2" t="inlineStr"><is><t>EXERCISE</t></is></c><c r="B2" t="inlineStr"><is><t>WEEK</t></is></c><c r="C2" t="inlineStr"><is><t>REPS</t></is></c><c r="D2" t="inlineStr"><is><t>RIR</t></is></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>Bench Press</t></is></c><c r="B3"><v>1</v></c><c r="C3"><f>9+0</f><v>9</v></c><c r="D3"><v>2</v></c></row>
                </sheetData></worksheet>""",
        )
        val bytes = ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            members.forEach { (name, xml) -> zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
        } }.toByteArray()
        val preview = SpreadsheetImport.read(bytes.inputStream())
        val catalog = listOf(Exercise("bench", "Bench Press", "Chest", "Barbell"))
        val programs = preview.materializePrograms(mapOf("Bench Press" to "bench"), emptySet(), 3, catalog)
        val original = AppData(active = Workout(name = "Keep active"))
        val imported = original.withSpreadsheetPrograms(programs, catalog)
        assertEquals(original.active, imported.active)
        assertTrue(imported.history.isEmpty())
        assertNull(imported.activeProgramId)
        assertEquals(9, imported.programs.single().days.single().exercises.single().cycles.single().sets.first().repsMin)
        assertEquals(3, imported.programs.single().days.single().exercises.single().cycles.single().sets.size)
        assertTrue(preview.warnings.any { "Stored formula REPS" in it })
    }
}
