package org.openfactor.app

import android.app.Application
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GymStorageTest {
    @Test
    fun inventorySurvivesStorageFullBackupAndLegacyRestoreWithoutChangingOtherData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        val vm = WorkoutViewModel(application)
        val original = vm.export()
        try {
            val workout = Workout(name = "Preserved workout", exercises = listOf(SessionExercise(
                exerciseId = "existing", notes = "Entry note", sets = listOf(LoggedSet(weightKg = 37.25, reps = 9, completed = true)))))
            val plan = TrainingProgram(name = "Preserved plan")
            val legacyGym = GymProfile(name = "Inventory gym", equipmentIds = setOf("unknown"), weightsKg = mapOf("unknown" to listOf(0.0, 1.25)))
            val baseline = vm.data.value.copy(
                active = workout, history = listOf(workout.copy(id = "finished")),
                workoutDraft = WorkoutDraft(name = "Preserved draft", exercises = workout.exercises),
                programs = listOf(plan), programDraft = plan.copy(id = "draft-plan"), activeProgramId = plan.id,
                exerciseDefaults = mapOf("existing" to ExerciseDefaults("Pinned", 35, true)),
                scaleWeights = listOf(ScaleWeightRecord(weightKg = 83.125)),
                bodyMeasurements = listOf(BodyMeasurementRecord(centimeters = mapOf(BodyMeasurement.WAIST to 87.5))),
                gyms = listOf(legacyGym),
            )
            WorkoutStore(context).save(baseline)
            val reopened = WorkoutViewModel(application)
            val bitmap = Bitmap.createBitmap(11, 17, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val reference = reopened.photoStore.attach(ByteArrayInputStream(bytes))
            reopened.saveProgressPhotos(ProgressPhotoRecord(photos = mapOf(PhotoPosition.FRONT to reference)))
            val withPhoto = reopened.data.value
            val gym = legacyGym.copy(icon = "cottage", equipmentIds = setOf("unknown", "bar"),
                weightSelectionsKg = mapOf("bar" to listOf(EquipmentWeight(0.0, false, 1), EquipmentWeight(7.25, true, 3))),
                weightSelectionsLb = mapOf("bar" to listOf(EquipmentWeight(33.0, true)), "unknown" to emptyList()))
            reopened.saveGym(gym)
            val expected = withPhoto.copy(gyms = listOf(gym))
            assertEquals(expected, WorkoutStore(context).load())
            assertEquals(expected, WorkoutViewModel(application).data.value)
            val backup = reopened.export()
            reopened.saveGym(legacyGym)
            reopened.restore(backup)
            assertEquals(expected, reopened.data.value)
            assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
            val invalid = JSONObject(backup)
            invalid.getJSONArray("gyms").getJSONObject(0).getJSONObject("weightSelectionsKg").getJSONArray("bar").getJSONObject(0).put("quantity", 0)
            assertThrows(IllegalArgumentException::class.java) { reopened.restore(invalid.toString()) }
            assertEquals(expected, reopened.data.value)
            assertEquals(expected, WorkoutStore(context).load())
            assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
            val legacyBackup = JSONObject(backup)
            val oldGym = GymCodec.encode(legacyGym).apply { remove("weightSelectionsKg"); remove("weightSelectionsLb") }
            legacyBackup.getJSONArray("gyms").put(0, oldGym)
            reopened.restore(legacyBackup.toString())
            assertEquals(withPhoto, reopened.data.value)
            assertArrayEquals(bytes, reopened.photoStore.file(reference).readBytes())
            val catalog = reopened.equipmentCatalog
            val bumper = catalog.items.single { it.id == "25a5c6f170d8805b8c95e7e5b4e8a989" }
            assertEquals(listOf(5.0, 10.0, 15.0, 20.0), legacyGym.weights(bumper, false).map { it.value })
            assertTrue(legacyGym.weights(bumper, true).isEmpty())
            val bar = catalog.items.single { it.name == "Barbell" }
            assertEquals(listOf(20.0, 15.0, 7.0), bar.selectionKg)
            assertEquals(listOf(45.0, 33.0, 15.0), bar.selectionLb)
        } finally {
            WorkoutViewModel(application).restore(original)
        }
    }
}
