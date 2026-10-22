package org.openfactor.app

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DashboardDataTest {
    @Test
    fun orderedSelectionsAndEmptySectionsSurviveBackupWithoutChangingSavedData() {
        val choices =
            DashboardPreferences(
                focuses = listOf("records", "weekly"),
                sections =
                    listOf(
                        DashboardSection.BODY,
                        DashboardSection.EXERCISES,
                        DashboardSection.MUSCLES,
                        DashboardSection.HABITS,
                        DashboardSection.GENERAL,
                        DashboardSection.INSIGHTS,
                    ),
                insights = emptyList(),
                habits = listOf("workouts"),
                bodyMetrics = listOf("waist", "weight", "visualBodyFat"),
                muscles = listOf("Lats", "Chest"),
                exerciseIds = listOf("custom-press", "chin-up"),
                showSteps = false,
            )
        val original =
            AppData(
                preferences =
                    Preferences(
                        dashboard = choices,
                        dataVisibility = DataVisibility().withVisible("waist", false),
                    ),
                active = Workout(id = "live", name = "Unfinished", notes = "Keep this"),
                history =
                    listOf(
                        Workout(
                            id = "saved",
                            name = "Training",
                            exercises =
                                listOf(
                                    SessionExercise(
                                        exerciseId = "custom-press",
                                        sets =
                                            listOf(
                                                LoggedSet(
                                                    weightKg = 31.25,
                                                    reps = 7,
                                                    completed = true,
                                                )
                                            ),
                                    )
                                ),
                        )
                    ),
                favorites = setOf("custom-press"),
                bodyMeasurements =
                    listOf(
                        BodyMeasurementRecord(
                            id = "body",
                            date = LocalDate.of(2026, 2, 3),
                            centimeters = mapOf(BodyMeasurement.WAIST to 81.125),
                        )
                    ),
                progressPhotos =
                    listOf(
                        ProgressPhotoRecord(photos = mapOf(PhotoPosition.FRONT to "b".repeat(64)))
                    ),
            )
        assertEquals(original, DataCodec.decode(DataCodec.encode(original)))
        val sparse =
            original.copy(
                preferences =
                    original.preferences.copy(
                        dashboard =
                            choices.copy(
                                focuses = emptyList(),
                                bodyMetrics = emptyList(),
                                muscles = emptyList(),
                                exerciseIds = emptyList(),
                            )
                    )
            )
        assertEquals(sparse, DataCodec.decode(DataCodec.encode(sparse)))
        val reset =
            original.copy(
                preferences = original.preferences.copy(dashboard = DashboardPreferences())
            )
        assertEquals(reset, DataCodec.decode(DataCodec.encode(reset)))
        val legacy =
            JSONObject(DataCodec.encode(reset)).apply {
                getJSONObject("preferences").remove("dashboard")
            }
        assertEquals(reset, DataCodec.decode(legacy.toString()))
    }
}
