package org.openfactor.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device storage/media checks. The end-to-end UI flow uses tooling/android_smoke.py. */
@RunWith(AndroidJUnit4::class)
class TrainingFlowTest {
    @Test
    fun installedAppCannotAccessTheInternet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions =
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            ).requestedPermissions.orEmpty()
        assertFalse("android.permission.INTERNET" in permissions)
    }

    @Test
    fun workoutStorageSurvivesNewStoreInstance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val workout =
                Workout(
                    name = "Device test",
                    exercises =
                        listOf(
                            SessionExercise(
                                exerciseId = "test",
                                sets =
                                    listOf(LoggedSet(weightKg = 42.5, reps = 8, completed = true)),
                            )
                        ),
                )
            val saved = before.copy(active = workout)
            store.save(saved)
            assertEquals(saved, WorkoutStore(context).load())
            assertEquals(saved, DataCodec.decode(DataCodec.encode(saved)))
        } finally {
            store.save(before)
        }
    }

    @Test
    fun fullCatalogHasResolvableLocalMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imported =
            runCatching {
                    context.assets.open("reference/catalog.json").bufferedReader().use {
                        DataCodec.catalog(it.readText())
                    }
                }
                .getOrNull()
        if (imported != null) {
            assertEquals(1345, imported.size)
            assertEquals(imported.size, imported.map { it.id }.distinct().size)
            imported.forEach { e ->
                e.frames.forEach {
                    context.assets.open(it).use { stream -> assertTrue(stream.read() >= 0) }
                }
                if (e.instructions.isNotBlank()) context.assets.open(e.instructions).close()
                e.clips.filter { it.isNotBlank() }.forEach { context.assets.open(it).close() }
            }
        } else {
            val baseline =
                context.assets.open("catalog.json").bufferedReader().use {
                    DataCodec.catalog(it.readText())
                }
            assertEquals(1345, baseline.size)
            assertEquals(1345, baseline.map { it.id }.distinct().size)
            assertTrue(
                baseline.all {
                    it.frames.isEmpty() && it.instructions.isEmpty() && it.video == null
                }
            )
        }
        val equipment = EquipmentCatalog(context)
        assertEquals(297, equipment.items.size)
        assertEquals(89, equipment.prefill("commercialGym").size)
        val bench =
            (imported
                    ?: context.assets.open("catalog.json").bufferedReader().use {
                        DataCodec.catalog(it.readText())
                    })
                .single { it.id == "19f5c6f170d8808bb424e98de4472a7e" }
        assertFalse(equipment.available(bench, GymProfile(name = "Blank")))
        assertTrue(
            equipment.available(
                bench,
                GymProfile(name = "Everything", equipmentIds = equipment.prefill("everything")),
            )
        )
        val bar = equipment.items.single { it.name == "Barbell" }.id
        val plates = equipment.items.single { it.name == "Weight plates" }.id
        val support = equipment.items.single { it.name == "Flat bench" }.id
        val dumbbells = equipment.items.single { it.name == "Dumbbells" }.id
        val bodyweight = equipment.items.single { it.name.equals("Bodyweight only", true) }.id
        assertTrue(equipment.usesResistance(bench, bar))
        assertTrue(equipment.usesResistance(bench, plates))
        assertFalse(equipment.usesResistance(bench, support))
        assertFalse(equipment.usesResistance(bench, dumbbells))
        val custom = bench.copy(custom = true,
            resistanceEquipment = listOf(setOf(bar, plates), setOf(dumbbells)),
            supportEquipment = listOf(setOf(support)))
        assertTrue(equipment.usesResistance(custom, dumbbells))
        assertTrue(equipment.usesResistance(custom, plates))
        assertFalse(equipment.usesResistance(custom, support))
        assertFalse(equipment.usesResistance(custom, bodyweight))
        assertTrue(equipment.usesResistance(custom.copy(resistanceEquipment = listOf(emptySet())), bodyweight))
    }
}
