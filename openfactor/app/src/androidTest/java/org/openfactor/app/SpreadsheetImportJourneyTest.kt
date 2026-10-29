package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
            // Scroll farther than the target: merely exposing it at the viewport bottom can
            // leave its tap under the floating navigation or the API 26 system navigation bar.
            compose.onNodeWithText("Restore Backup").performScrollTo()
            val entry = compose.onNodeWithText("Import spreadsheet").assertIsDisplayed().getUnclippedBoundsInRoot()
            val navigation = compose.onNodeWithContentDescription("Quick actions").getUnclippedBoundsInRoot()
            assertTrue("Import entry must be above floating navigation before tapping", entry.bottom <= navigation.top)
            compose.onNodeWithText("Import spreadsheet").performClick()
            compose.onNodeWithContentDescription("Back").assertIsDisplayed()
            val button = compose.onNodeWithText("Choose .xlsx document").assertExists().assertIsDisplayed()
                .assertIsEnabled().getUnclippedBoundsInRoot()
            val importNavigation = compose.onNodeWithContentDescription("Quick actions").getUnclippedBoundsInRoot()
            assertTrue("Document action must be above floating navigation", button.bottom <= importNavigation.top)
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

class SpreadsheetImportLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowLargeTypePreviewKeepsBothActionsAndTargetsAboveKeyboardOrNavigation() {
        var keyboard by mutableStateOf(false)
        var appended = 0
        var cancelled = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                OpenFactorTheme {
                    Column(Modifier.requiredSize(320.dp, 560.dp).testTag("import-viewport")) {
                        PageHeading("Import spreadsheet", {})
                        SpreadsheetImportLayout(
                            navigationBars = WindowInsets(bottom = 48.dp),
                            ime = WindowInsets(bottom = if (keyboard) 240.dp else 0.dp),
                            content = {
                                items(20) { week -> Text("Week ${week + 1}: 8–10 reps · RIR 2") }
                            },
                            actions = {
                                Button({ appended++ }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                    Text("Append 3 new programs")
                                }
                                OutlinedButton({ cancelled++ }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text("Cancel import")
                                }
                            },
                        )
                    }
                }
            }
        }
        val viewport = compose.onNodeWithTag("import-viewport").getUnclippedBoundsInRoot()
        fun assertReachable(bottomInset: Int) {
            val append = compose.onNodeWithText("Append 3 new programs").assertIsDisplayed().getUnclippedBoundsInRoot()
            val cancel = compose.onNodeWithText("Cancel import").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Append and cancel must not overlap", append.bottom <= cancel.top)
            assertEquals("Actions must reserve only the larger bottom obstruction",
                (viewport.bottom - bottomInset.dp).value, cancel.bottom.value, 0.5f)
            val list = compose.onNode(hasScrollToIndexAction()).performScrollToIndex(19).getUnclippedBoundsInRoot()
            val lastTarget = compose.onNodeWithText("Week 20: 8–10 reps · RIR 2").performScrollTo()
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Source targets must remain fully scrollable above the actions",
                lastTarget.top >= list.top && lastTarget.bottom <= list.bottom && list.bottom <= append.top)
        }
        assertReachable(48 + 108)
        compose.runOnIdle { keyboard = true }
        assertReachable(240)
        compose.onNodeWithText("Append 3 new programs").performClick()
        compose.onNodeWithText("Cancel import").performClick()
        compose.runOnIdle { assertEquals(1, appended); assertEquals(1, cancelled) }
    }
}
