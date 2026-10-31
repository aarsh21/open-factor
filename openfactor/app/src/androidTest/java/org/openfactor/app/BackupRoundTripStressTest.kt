package org.openfactor.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Random
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end coverage of the existing local backup foundation, not a cloud protocol.
 *
 * Exercise ProgressPhotoStore's production export/prepare/install and WorkoutStore's durable
 * save/reopen directly. Deliberately do not construct the real application's WorkoutViewModel:
 * its library initialization and notification synchronization have process-wide side effects,
 * especially for an active-workout fixture. These tests never open the user's preference names,
 * history archives, photo directories, or notification state.
 *
 * Images and backup documents live on disk. Only one bitmap/original image is generated, attached,
 * or byte-compared at a time. The stress fixture uses realistic sparse measurements and set
 * actuals, rather than filling every optional metric on every set and exhausting the intentional
 * 64 MiB metadata-node budget. The smaller fixture additionally covers dense body measurements.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripStressTest {
    @Test
    fun richAppDataAndSharedOriginalsSurviveEmptyStoreRestoreAndAnotherExport() {
        roundTrip(
            workoutCount = 12,
            bodyCount = 32,
            imageCount = 12,
            photoRecordCount = 24,
            largeImageCount = 1,
            dense = true,
            secondHop = true,
        )
    }

    @Test
    fun twoThousandWorkoutsAndTenYearsOfBodyDataRestoreTogetherWithOriginalPhotos() {
        roundTrip(
            workoutCount = 2_000,
            bodyCount = 3_650,
            imageCount = 48,
            photoRecordCount = 72,
            largeImageCount = 3,
            dense = false,
            secondHop = false,
        )
    }

    private fun roundTrip(
        workoutCount: Int,
        bodyCount: Int,
        imageCount: Int,
        photoRecordCount: Int,
        largeImageCount: Int,
        dense: Boolean,
        secondHop: Boolean,
    ) {
        val parent = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(parent.cacheDir, "backup-round-trip-${newId()}")
        check(root.mkdirs())
        val source = Sandbox(parent, File(root, "source"))
        val destination = Sandbox(parent, File(root, "destination"))
        val nextDestination = Sandbox(parent, File(root, "next-destination"))
        try {
            val sourcePhotos = ProgressPhotoStore(source.context())
            val originals = File(root, "golden-originals").apply { check(mkdirs()) }
            val assets = List(imageCount) { index ->
                val original = File(originals, "original-$index")
                createImage(original, index, large = index < largeImageCount)
                if (index < largeImageCount) {
                    assertTrue("Large PNG $index must contain at least 1 MiB of actual image data", original.length() >= 1024 * 1024)
                }
                val reference = original.inputStream().use { sourcePhotos.attach(it) }
                assertEquals("Original checksum is the attachment identity", sha256(original), reference)
                Asset(reference, original)
            }
            assertEquals("Each generated image must be unique", imageCount, assets.map { it.reference }.toSet().size)
            val expected = fixture(workoutCount, bodyCount, photoRecordCount, assets, dense)
            assertEquals(workoutCount * 30, expected.history.sumOf { workout -> workout.exercises.sumOf { it.sets.size } })
            assertEquals(imageCount, expected.photoReferences().size)
            assertEquals(photoRecordCount * 2, expected.progressPhotos.sumOf { it.photos.size })
            assertEquals(bodyCount, expected.scaleWeights.size)
            assertEquals(bodyCount, expected.bodyMeasurements.size)
            assertTrue("The calendar fixture includes leap day", expected.scaleWeights.any { it.date == LocalDate.of(2016, 2, 29) })
            assertTrue("Photo identities must be shared across records", expected.progressPhotos.flatMap { it.photos.values }.size > imageCount)

            val backup = File(root, "export.json")
            backup.outputStream().buffered().use { sourcePhotos.export(expected, it) }
            assertTrue(backup.length() > 0)
            // Destroy the entire source store, not merely its metadata or selected records. The
            // golden files are independent oracles; the destination never receives their paths.
            source.close()
            assertFalse(source.root.exists())

            restoreIntoEmptyStore(backup, destination, expected, assets, abandonPreparation = dense)
            if (secondHop) {
                val reopenedContext = destination.context()
                val reopenedData = WorkoutStore(reopenedContext).load()
                assertDataEquals("Before second export", expected, reopenedData)
                val secondBackup = File(root, "reexport.json")
                secondBackup.outputStream().buffered().use {
                    ProgressPhotoStore(reopenedContext).export(reopenedData, it)
                }
                // A second independent destination also proves re-export includes all original
                // assets, rather than relying on anything retained from the first preparation.
                destination.close()
                assertFalse(destination.root.exists())
                restoreIntoEmptyStore(secondBackup, nextDestination, expected, assets, abandonPreparation = false)
            }
            Log.i(
                TAG,
                "history=$workoutCount historySets=${workoutCount * 30} extraSets=120 " +
                    "weights=$bodyCount measurements=$bodyCount " +
                    "measurementValues=${expected.bodyMeasurements.sumOf { it.centimeters.size }} " +
                    "photoRecords=$photoRecordCount references=${photoRecordCount * 2} " +
                    "uniqueImages=$imageCount large1024x1024Png=$largeImageCount " +
                    "originalBytes=${assets.sumOf { it.original.length() }} backupBytes=${backup.length()} " +
                    "firstDate=${expected.scaleWeights.first().date} lastDate=${expected.scaleWeights.last().date}",
            )
        } finally {
            try {
                source.close()
            } finally {
                try {
                    destination.close()
                } finally {
                    try {
                        nextDestination.close()
                    } finally {
                        check(root.deleteRecursively()) { "Could not remove isolated backup fixtures" }
                    }
                }
            }
        }
    }

    private fun restoreIntoEmptyStore(
        backup: File,
        sandbox: Sandbox,
        expected: AppData,
        assets: List<Asset>,
        abandonPreparation: Boolean,
    ) {
        assertTrue("Destination has no persisted files", sandbox.root.walkTopDown().none { it.isFile })
        val context = sandbox.context()
        assertTrue(context.getSharedPreferences("open_factor", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("open_factor_recovery", Context.MODE_PRIVATE).all.isEmpty())
        val store = WorkoutStore(context)
        assertDataEquals("Destination really starts empty", AppData(), store.load())
        val photos = ProgressPhotoStore(context)
        assertTrue(assets.none { photos.file(it.reference).exists() })
        if (abandonPreparation) {
            backup.inputStream().buffered().use { input ->
                photos.prepare(input).use { prepared ->
                    assertDataEquals("Abandoned preparation decodes the complete fixture", expected, prepared.data)
                    assertTrue("Preparation alone must not install originals", assets.none { photos.file(it.reference).exists() })
                    assertDataEquals("Preparation alone must not save metadata", AppData(), store.load())
                }
            }
            assertStagingEmpty(context)
            assertTrue("Closing an uninstalled preparation leaves no originals", assets.none { photos.file(it.reference).exists() })
        }
        backup.inputStream().buffered().use { input ->
            photos.prepare(input).use { prepared ->
                assertNotSame(expected, prepared.data)
                assertDataEquals("Prepared backup", expected, prepared.data)
                assertTrue("Fresh destination still has no installed originals", assets.none { photos.file(it.reference).exists() })
                prepared.install()
                // Same foundation/order used by WorkoutViewModel.restore, without notifications
                // or default-gym enrichment. The fixture already has valid gyms.
                store.save(prepared.data)
            }
        }
        assertStagingEmpty(context)
        assertOriginals(photos, assets)

        // New ContextWrapper and new store instances: this must read preferences plus the durable
        // history archive, not the previous WorkoutStore's in-memory archive/identity caches.
        val reopenedContext = sandbox.context()
        val reopened = WorkoutStore(reopenedContext)
        val actual = reopened.load()
        assertNotSame(expected, actual)
        assertDataEquals("Reopened persistent store", expected, actual)
        assertNull("A normal round trip must not enter storage recovery", reopened.recovery())
        assertFalse(reopened.hasRecoveryPayloads())
        val reopenedPhotos = ProgressPhotoStore(reopenedContext)
        reopenedPhotos.requireFiles(actual)
        assertOriginals(reopenedPhotos, assets)
        assertEquals(
            "The empty destination contains exactly the referenced originals, once per checksum",
            expected.photoReferences(),
            File(reopenedContext.noBackupFilesDir, "progress-photos").listFiles().orEmpty().map { it.name }.toSet(),
        )
        assertTrue(
            "Nonempty history must actually reach the persistent history archive",
            File(reopenedContext.filesDir, "workout-history").walkTopDown().any { it.isFile && it.extension == "json" },
        )
    }

    private fun assertDataEquals(message: String, expected: AppData, actual: AppData) {
        assertEquals("$message: workout count", expected.history.size, actual.history.size)
        assertEquals("$message: weight count", expected.scaleWeights.size, actual.scaleWeights.size)
        assertEquals("$message: measurement count", expected.bodyMeasurements.size, actual.bodyMeasurements.size)
        assertEquals("$message: photo records", expected.progressPhotos, actual.progressPhotos)
        // Do not dump 60,000 sets into instrumentation output on failure. This is structural
        // equality of ALL AppData fields, not a count/sample-based substitute.
        assertTrue("$message: complete AppData structural equality", expected == actual)
    }

    private fun assertOriginals(store: ProgressPhotoStore, assets: List<Asset>) {
        assets.forEach { asset ->
            val restored = store.file(asset.reference)
            assertTrue("Missing original ${asset.reference}", restored.isFile)
            assertEquals("Original byte length ${asset.reference}", asset.original.length(), restored.length())
            assertEquals("Restored SHA-256 ${asset.reference}", asset.reference, sha256(restored))
            // Bounded by one image pair, never all image bytes or the full JSON backup at once.
            assertArrayEquals("Every original byte ${asset.reference}", asset.original.readBytes(), restored.readBytes())
        }
    }

    private fun assertStagingEmpty(context: Context) {
        assertTrue("Prepared staging directories must be removed on close", File(context.noBackupFilesDir, "photo-restore").listFiles().orEmpty().isEmpty())
    }

    private fun fixture(
        workoutCount: Int,
        bodyCount: Int,
        photoRecordCount: Int,
        assets: List<Asset>,
        dense: Boolean,
    ): AppData {
        val history = List(workoutCount) { workout("history-$it", it, active = false, dense = dense) }
        val active = workout("active", workoutCount, active = true, dense = true)
        val draftSession = workout("composition", workoutCount + 1, active = true, dense = true)
        val savedProgram = program("program")
        return AppData(
            active = active,
            history = history,
            routines = listOf(
                Routine(
                    id = "routine", name = "Strength · 日本語", exerciseIds = EXERCISE_IDS,
                    prescriptions = EXERCISE_IDS.mapIndexed { index, id ->
                        TemplateExercise(id, sets = index + 1, repsMin = 6, repsMax = 12, restSeconds = 45 + index * 15, note = "Template $index $TEXT")
                    },
                ),
            ),
            favorites = setOf(EXERCISE_IDS.first(), EXERCISE_IDS.last()),
            customExercises = EXERCISE_IDS.mapIndexed { index, id ->
                Exercise(
                    id = id, name = "Custom $index · Élan", muscle = "Chest", equipment = "Dumbbell",
                    custom = true, instructions = TEXT, secondary = listOf("Triceps"),
                    muscles = listOf("Chest", "Triceps"), aliases = listOf("Alias $index", "日本語 $index"),
                    equipmentOptions = listOf("Dumbbell"),
                    properties = mapOf("Metrics" to when (index) {
                        1 -> "Assistance weight, Reps"
                        2 -> "Duration, Distance long"
                        3 -> "Weight per side, Reps per side"
                        else -> "Weight, Reps"
                    }),
                    bodyweight = if (index == 1) 0.875 else 0.0,
                    searchBoost = index * 0.125, archived = index == 5,
                    resistanceEquipment = listOf(setOf("dumbbell", "barbell")),
                    supportEquipment = listOf(setOf("bench")),
                )
            },
            preferences = preferences(),
            activeProgramId = savedProgram.id,
            gyms = listOf(
                GymProfile(
                    id = "gym", name = "Garage · 東京", icon = "gym",
                    equipmentIds = setOf("dumbbell", "barbell", "bench"),
                    weightsKg = mapOf("dumbbell" to listOf(1.125, 12.3456789, 24.75)),
                    weightSelectionsKg = mapOf("dumbbell" to listOf(EquipmentWeight(12.3456789, quantity = 2), EquipmentWeight(17.5, enabled = false))),
                    weightSelectionsLb = mapOf("barbell" to listOf(EquipmentWeight(45.125, quantity = 1))),
                ),
                GymProfile(id = "travel-gym", name = "Travel", equipmentIds = setOf("bench")),
            ),
            programs = listOf(savedProgram),
            programDraft = program("program-draft"),
            onboarding = OnboardingState(
                step = 2, deferred = true, name = "Renée · 日本語", pounds = true,
                heightCm = "173.456789", weightKg = "67.123456789", preset = "current",
                gymName = "Garage · 東京", gymId = "gym", gender = ProfileGender.NON_BINARY,
                profileDraft = ProfileSetupDraft(
                    stage = 1, name = "Renée", gender = ProfileGender.NON_BINARY, pounds = true,
                    heightInFeet = true, weightText = "148.12345", heightText = "5", heightInchesText = "8.125",
                    weeklyGoal = 5, rateEffort = false, autoRest = false, restSeconds = 77,
                    weightEdited = true, weightSourceDate = FIRST_DATE.toString(), weightSourceId = "weight-0",
                    localWeightId = "profile-local-weight",
                ),
            ),
            scaleWeights = List(bodyCount) { index ->
                ScaleWeightRecord(
                    id = "weight-$index", date = FIRST_DATE.plusDays(index.toLong()),
                    weightKg = 67.123456789 + (index % 1700) * 0.000123457,
                    bodyFatPercent = if (index % 3 == 0) null else 12.123456789 + (index % 100) * 0.012345679,
                    healthConnectOrigin = when (index % 3) {
                        1 -> "com.example.scale"
                        2 -> "com.example.other.scale"
                        else -> null
                    },
                )
            },
            bodyMeasurements = List(bodyCount) { index ->
                BodyMeasurementRecord(
                    id = "measurement-$index", date = FIRST_DATE.plusDays(index.toLong()),
                    centimeters = BodyMeasurement.entries.filter { dense || it.ordinal % 3 == index % 3 }
                        .associateWith { field -> 20.123456789 + field.ordinal * 4.567891234 + (index % 1000) * 0.000123457 },
                    visualBodyFatPercent = if (index % 4 == 0) null else 18.987654321 + (index % 100) * 0.001234567,
                )
            },
            workoutDraft = WorkoutDraft(
                name = "Unstarted composition", exercises = draftSession.exercises, gymId = "travel-gym", notes = TEXT,
            ),
            progressPhotos = List(photoRecordCount) { index ->
                ProgressPhotoRecord(
                    id = "photo-$index",
                    // Multiple records on one date must not be merged; dates straddle leap day.
                    date = FIRST_DATE.plusDays((index / 2).toLong()),
                    photos = PhotoPosition.entries.take(1 + index % 3).mapIndexed { positionIndex, position ->
                        position to assets[(index + positionIndex * 7) % assets.size].reference
                    }.toMap(),
                    notes = "Photo $index $TEXT",
                )
            },
            exerciseDefaults = mapOf(
                EXERCISE_IDS[0] to ExerciseDefaults(note = TEXT, restSeconds = 77, doNotRecommend = true),
                EXERCISE_IDS[1] to ExerciseDefaults(note = "Pinned · é", restSeconds = 0),
            ),
            healthConnect = HealthConnectSyncState(
                lastSyncedAtMillis = START, firstDate = FIRST_DATE, lastDate = FIRST_DATE.plusDays(29),
                dailySteps = List(30) { HealthConnectStepDay(FIRST_DATE.plusDays(it.toLong()), if (it == 0) 0L else 8_000L + it * 113) },
                weightReadings = 20, weightAccess = true, bodyFatAccess = true, stepsAccess = true,
            ),
            pastWorkoutEdit = PastWorkoutEdit(
                original = history.first(),
                edited = history.first().copy(name = "Unsaved edit", notes = "Edited $TEXT"),
            ),
            templateSplits = listOf(TemplateSplit(id = "split", name = "Weekly · é", days = listOf("routine", null, "routine", null, "routine", null, null))),
        )
    }

    /** Six exercises times five stored rows, including valid split-side and Myo relationships. */
    private fun workout(id: String, index: Int, active: Boolean, dense: Boolean): Workout {
        val start = START + index * 86_400_000L
        val exercises = EXERCISE_IDS.mapIndexed { exercise, exerciseId ->
            val prefix = "$id-e$exercise"
            val sets = List(5) { row ->
                val hasTargets = (dense || row == 1) && exercise != 2
                val base = LoggedSet(
                    id = "$prefix-s$row",
                    weightKg = if (exercise in listOf(1, 2) || (active && row == 4)) null else 30.123456789 + (index % 100) * 0.125 + row,
                    reps = if (exercise == 2 || (active && row == 4)) null else 6 + row,
                    rir = if (row == 4 && exercise != 2) 0 else if (dense && exercise != 2) 2 else null,
                    completed = !active || row < 3,
                    warmup = row == 0 && exercise != 2,
                    targetRepsMin = if (hasTargets) 6 else null,
                    targetRepsMax = if (hasTargets) 12 else null,
                    targetRir = if (hasTargets) 2 else null,
                    assistanceKg = if (exercise == 1 && !(active && row == 4)) 12.123456789 + row else null,
                    durationSeconds = if (exercise == 2 && !(active && row == 4)) 60.123456789 + row * 7.25 else null,
                    distanceMeters = if (exercise == 2 && !(active && row == 4)) 321.987654321 + row * 10.125 else null,
                    kind = if (row == 4 && exercise !in listOf(2, 3)) SetKind.FAILURE else SetKind.STANDARD,
                )
                when {
                    exercise == 3 && row > 0 -> base.copy(
                        side = if (row % 2 == 1) SetSide.LEFT else SetSide.RIGHT,
                        pairId = if (row % 2 == 0) "$prefix-s${row - 1}" else null,
                    )
                    exercise == 4 && row in 1..3 -> base.copy(
                        kind = SetKind.MYO, segmentOf = if (row > 1) "$prefix-s1" else null,
                    )
                    else -> base
                }
            }
            SessionExercise(
                id = prefix, exerciseId = exerciseId, sets = sets,
                notes = "Exercise $exercise $TEXT", restSeconds = if (exercise == 2) null else 45 + exercise * 15,
                supersetId = if (exercise < 2) "$id-superset" else null,
                reviewedEffortSetIds = if (exercise == 2) emptySet() else setOf(sets[1].id),
                lastNoteDismissed = exercise % 2 == 0,
            ).validateSetLinks()
        }
        return Workout(
            id = id, name = "Session $index · Élan", startedAt = start,
            finishedAt = if (active) null else start + 3_723_456,
            exercises = exercises, notes = "Workout $index $TEXT",
            restUntil = if (active) start + 1_234_567 else 0,
            programId = if (index % 2 == 0) "program" else null,
            selectedExerciseId = exercises[2].id,
            pausedAt = if (active) start + 1_200_000 else null,
            pausedDurationMs = 12_345L + index,
            programDayId = if (index % 2 == 0) "program-day-0" else null,
            programCycle = if (index % 2 == 0) index % 3 else null,
            gymId = "gym", routineId = if (index % 2 == 1) "routine" else null,
            effortPrompts = if (active) listOf(EffortPrompt(exercises[0].id, afterRest = true), EffortPrompt(exercises[1].id)) else emptyList(),
        )
    }

    private fun program(id: String) = TrainingProgram(
        id = id, name = "Plan $id · 日本語", color = 0xFF1234ABL, cycleCount = 3,
        icon = "rocket", cycleUnit = ProgramCycleUnit.WEEK,
        days = List(2) { day ->
            ProgramDay(
                id = "$id-day-$day", name = if (day == 0) "Training" else "Rest", gymId = "gym",
                exercises = if (day == 1) emptyList() else EXERCISE_IDS.take(2).mapIndexed { index, exerciseId ->
                    PlannedExercise(
                        id = "$id-planned-$index", exerciseId = exerciseId,
                        cycles = List(3) { cycle ->
                            CycleTarget(
                                sets = List(2) { row -> SetTarget(id = "$id-target-$index-$cycle-$row", repsMin = 5 + cycle, repsMax = 10 + cycle, rir = 2, kind = if (row == 1) SetKind.FAILURE else SetKind.STANDARD) },
                                skip = cycle == 2 && index == 1, restSeconds = 60 + cycle * 15,
                            )
                        },
                    )
                },
            )
        },
    )

    private fun preferences() = Preferences(
        pounds = true, restSeconds = 77, autoRest = false, showRir = false, displayName = "Renée · 日本語 🏋️",
        dashboardAllWorkouts = true, showWeeklyWorkouts = false, showInsights = false, showHabits = false,
        exerciseAutoNext = false, keepAlive = true, showWorkoutTimer = false, showBodyweightContribution = false,
        distanceImperial = true, heightInFeet = true, clock24Hour = true,
        dataVisibility = DataVisibility(showPeriod = true).withVisible("sidePhoto", false).withVisible("waist", false),
        dismissedTutorials = setOf("bodyCharts", "workout", "photos"), theme = AppTheme.LIGHT, palette = AppPalette.MOSS,
        shortcuts = ShortcutPreferences(
            order = WorkoutShortcut.entries.filter { it.availableInShortcuts }.reversed(),
            colors = mapOf(WorkoutShortcut.PHOTOS to ShortcutColor.PURPLE, WorkoutShortcut.WEIGHT to ShortcutColor.TEAL),
            showUpNext = false,
        ),
        dashboard = DashboardPreferences(
            focuses = listOf("records", "weekly"), sections = DashboardSection.entries.reversed(),
            insights = listOf("trend", "workouts"), habits = listOf("workouts", "weighIn"),
            bodyMetrics = listOf("waist", "weight", "visualBodyFat"), exerciseIds = EXERCISE_IDS.take(2), showSteps = false,
        ),
        restAfterLastWarmupSet = false, restBetweenExercises = false, supersetAutoNext = false,
        restBetweenSidesPercent = 37, rateEffortAfterExercise = false, effortPromptTiming = EffortPromptTiming.LAST_SET,
        workoutKeypad = false, weeklyWorkoutGoal = 5,
    )

    private fun createImage(file: File, index: Int, large: Boolean) {
        val width = if (large) 1024 else 64 + index
        val height = if (large) 1024 else 96 + index
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val random = Random(0x5EEDBACL + index)
            val row = IntArray(width)
            repeat(height) { y ->
                repeat(width) { x -> row[x] = 0xff000000.toInt() or random.nextInt(0x1000000) }
                bitmap.setPixels(row, 0, width, 0, y, width, 1)
            }
            val format = if (large || index % 2 == 0) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            file.outputStream().buffered().use { check(bitmap.compress(format, 94, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(file.inputStream().buffered(), digest).use { input ->
            val buffer = ByteArray(8192)
            while (input.read(buffer) != -1) { /* Hash without allocating the complete original. */ }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Asset(val reference: String, val original: File)

    /** Fresh paths plus unique preferences names; all preference files are deleted on teardown. */
    private class Sandbox(private val parent: Context, val root: File) : Closeable {
        private val preferencePrefix = "backup_round_trip_${newId().replace('-', '_')}_"
        private val preferenceNames = mutableSetOf<String>()
        private var closed = false

        init { check(root.mkdirs()) }

        fun context(): Context {
            check(!closed)
            return object : ContextWrapper(parent) {
                private fun directory(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
                override fun getApplicationContext(): Context = this
                override fun getFilesDir(): File = directory("files")
                override fun getNoBackupFilesDir(): File = directory("no-backup")
                override fun getCacheDir(): File = directory("cache")
                override fun getCodeCacheDir(): File = directory("code-cache")
                override fun getDir(name: String, mode: Int): File {
                    require('/' !in name && '\\' !in name && name != "." && name != "..")
                    return directory("app_$name")
                }
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    val isolatedName = preferencePrefix + name
                    preferenceNames.add(isolatedName)
                    return parent.getSharedPreferences(isolatedName, mode)
                }
            }
        }

        override fun close() {
            if (closed) return
            try {
                preferenceNames.forEach { name ->
                    check(parent.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
                    check(parent.deleteSharedPreferences(name))
                }
                check(root.deleteRecursively()) { "Could not remove sandbox ${root.name}" }
            } finally {
                closed = true
            }
        }
    }

    companion object {
        private const val TAG = "BackupRoundTripStress"
        private const val START = 1_500_000_000_123L
        private const val TEXT = "Quotes: \"lift\"; slash \\; tab\tline\nbreak · Renée é 日本語 🏋️"
        private val FIRST_DATE: LocalDate = LocalDate.of(2016, 2, 28)
        private val EXERCISE_IDS = List(6) { "custom-backup-$it" }
    }
}
