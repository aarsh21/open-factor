package org.openfactor.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in real-provider contract. A separate fixture app must write records to Android Health
 * Connect first, and the real Connect button must have imported them through native consent.
 * This test never injects records directly into Open Factor to manufacture a successful import.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectProviderTest {
    @Test fun oneButtonUiImportMatchesExternalProviderAndSurvivesReload() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an external provider fixture and native Health Connect permission grant",
            arguments.getString("healthProviderFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun required(name: String) = requireNotNull(arguments.getString(name)) { "Missing $name fixture argument" }
        val origin = required("healthFixtureOrigin")
        require(origin != context.packageName)
        val date = LocalDate.parse(required("healthFixtureDate"))
        val kg = required("healthFixtureWeightKg").toDouble()
        val steps = required("healthFixtureSteps").toLong()
        val fat = arguments.getString("healthFixtureBodyFat")?.toDouble()
        assertEquals(HealthConnectAvailability.AVAILABLE, healthConnectAvailability(context))
        val store = WorkoutStore(context)
        val before = store.load()

        // These assertions prove the visible Connect/Sync flow already persisted the actual import.
        val uiWeight = before.scaleWeights.singleOrNull { it.date == date && it.healthConnectOrigin == origin }
        assertNotNull("Connect must save an external-origin weight without another transfer tap", uiWeight)
        assertEquals(kg, uiWeight!!.weightKg, 0.00001)
        if (fat != null) assertEquals(fat, uiWeight.bodyFatPercent!!, 0.00001)
        assertNotNull(before.healthConnect.lastSyncedAtMillis)
        assertEquals(steps, before.healthConnect.dailySteps.single { it.date == date }.count)

        // This is the real platform client and datastore, not a fake or in-memory repository.
        val result = HealthConnectTransfers(context).sync(date, date, ZoneId.systemDefault(), context.packageName)
        assertTrue("Expected a successful provider read, got $result", result is HealthConnectResult.Success)
        val sync = (result as HealthConnectResult.Success).value
        assertEquals(kg, sync.weights.single { it.healthConnectOrigin == origin }.weightKg, 0.00001)
        assertEquals(steps, sync.state.dailySteps.single { it.date == date }.count)
        assertTrue(sync.state.weightAccess)
        assertTrue(sync.state.stepsAccess)
        try {
            val once = before.withHealthConnectSync(sync)
            val twice = once.withHealthConnectSync(sync)
            assertEquals(once, twice)
            assertEquals(uiWeight.id, twice.scaleWeights.single { it.date == date }.id)
            store.save(twice)
            val reloaded = WorkoutStore(context).load()
            assertEquals(twice, reloaded)
            assertEquals(steps, reloaded.healthConnect.dailySteps.single().count)
        } finally {
            store.save(before)
        }
    }
}
