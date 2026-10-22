package org.openfactor.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyDataStorageTest {
    @Test
    fun bodyDataSurvivesNewViewModelAndFullRestoreWithoutChangingTraining() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = WorkoutStore(context)
        val before = store.load()
        try {
            val workout =
                Workout(
                    name = "Synthetic preserved session",
                    exercises =
                        listOf(
                            SessionExercise(
                                exerciseId = "test",
                                sets =
                                    listOf(
                                        LoggedSet(
                                            weightKg = 42.5,
                                            reps = 9,
                                            completed = true,
                                            assistanceKg = 12.25,
                                            durationSeconds = 85.5,
                                            distanceMeters = 321.75,
                                        ),
                                        LoggedSet(warmup = true),
                                    ),
                            )
                        ),
                )
            val program = TrainingProgram(name = "Synthetic preserved program")
            val training =
                before.copy(
                    active = workout,
                    history = listOf(workout.copy(id = "finished")),
                    programs = listOf(program),
                    activeProgramId = program.id,
                    programDraft = program.copy(id = "draft"),
                    workoutDraft =
                        WorkoutDraft(
                            name = "Synthetic preserved composition",
                            exercises = workout.exercises,
                        ),
                    gyms = listOf(GymProfile(name = "Synthetic preserved gym")),
                )
            store.save(training)
            val vm = WorkoutViewModel(context.applicationContext as Application)
            val weight =
                ScaleWeightRecord(
                    date = LocalDate.of(2026, 10, 19),
                    weightKg = 83.125,
                    bodyFatPercent = 17.2,
                )
            val measures =
                BodyMeasurementRecord(
                    date = LocalDate.of(2026, 10, 18),
                    centimeters =
                        mapOf(
                            BodyMeasurement.LEFT_BICEP to 35.75,
                            BodyMeasurement.RIGHT_BICEP to 36.25,
                            BodyMeasurement.WAIST to 87.5,
                        ),
                    visualBodyFatPercent = 26.125,
                )
            vm.saveScaleWeight(weight)
            vm.saveBodyMeasurements(measures)
            val saved = training.withScaleWeight(weight).withBodyMeasurements(measures)
            assertEquals(saved, WorkoutStore(context).load())
            val reopened = WorkoutViewModel(context.applicationContext as Application)
            assertEquals(saved, reopened.data.value)
            val backup = reopened.export()
            reopened.deleteScaleWeight(weight.id)
            reopened.deleteBodyMeasurements(measures.id)
            assertEquals(training, reopened.data.value)
            reopened.restore(backup)
            assertEquals(saved, reopened.data.value)
            assertEquals(saved, WorkoutStore(context).load())
            for ((table, field) in
                listOf(
                    "scaleWeights" to "weightKg",
                    "bodyMeasurements" to "visualBodyFatPercent",
                )) {
                val invalid =
                    JSONObject(backup)
                        .apply { getJSONArray(table).getJSONObject(0).put(field, -1) }
                        .toString()
                assertThrows(IllegalArgumentException::class.java) { reopened.restore(invalid) }
                assertEquals(saved, reopened.data.value)
                assertEquals(saved, WorkoutStore(context).load())
            }
        } finally {
            store.save(before)
        }
    }
}
