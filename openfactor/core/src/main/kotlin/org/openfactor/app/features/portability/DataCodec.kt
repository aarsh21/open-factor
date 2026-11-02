package org.openfactor.app

import org.json.JSONArray
import org.json.JSONObject

object DataCodec {
    private fun LoggedSet.json() =
        JSONObject()
            .put("id", id)
            .put("weightKg", weightKg ?: JSONObject.NULL)
            .put("reps", reps ?: JSONObject.NULL)
            .put("rir", rir)
            .put("completed", completed)
            .put("warmup", warmup)
            .put("targetRepsMin", targetRepsMin)
            .put("targetRepsMax", targetRepsMax)
            .put("targetRir", targetRir)
            .put("assistanceKg", assistanceKg)
            .put("durationSeconds", durationSeconds)
            .put("distanceMeters", distanceMeters)
            .put("kind", kind.name)
            .put("side", side?.name)
            .put("pairId", pairId)
            .put("segmentOf", segmentOf)

    private fun SessionExercise.json() =
        JSONObject()
            .put("id", id)
            .put("exerciseId", exerciseId)
            .put("sets", JSONArray(sets.map { it.json() }))
            .put("notes", notes)
            .put("restSeconds", restSeconds)
            .put("supersetId", supersetId)
            .put("reviewedEffortSetIds", JSONArray(reviewedEffortSetIds.sorted()))
            .put("lastNoteDismissed", lastNoteDismissed.takeIf { it })

    private fun Workout.json() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("startedAt", startedAt)
            .put("finishedAt", finishedAt)
            .put("restUntil", restUntil)
            .put("programId", programId)
            .put("selectedExerciseId", selectedExerciseId)
            .put("pausedAt", pausedAt)
            .put("pausedDurationMs", pausedDurationMs)
            .put("programDayId", programDayId)
            .put("programCycle", programCycle)
            .put("gymId", gymId)
            .put("routineId", routineId)
            .put("effortPrompts", effortPrompts.takeIf { it.isNotEmpty() }?.let { prompts ->
                JSONArray(prompts.map { JSONObject().put("exerciseId", it.exerciseId).put("afterRest", it.afterRest) })
            })
            .put("exercises", JSONArray(exercises.map { it.json() }))
            .put("notes", notes)

    private fun Exercise.json() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("muscle", muscle)
            .put("equipment", equipment)
            .put("instructions", instructions)
            .put("custom", custom)
            .put("archived", archived)
            .put("secondary", JSONArray(secondary))
            .put("frames", JSONArray(frames))
            .put("video", video)
            .put("muscles", JSONArray(muscles))
            .put("aliases", JSONArray(aliases))
            .put("equipmentOptions", JSONArray(equipmentOptions))
            .put("properties", JSONObject(properties))
            .put("bodyweight", bodyweight)
            .put("searchBoost", searchBoost)
            .put("clips", JSONArray(clips))
            .put("browseCategories", JSONArray(browseCategories))
            .put("resistanceEquipment", JSONArray(resistanceEquipment.map { JSONArray(it.toList()) }))
            .put("supportEquipment", JSONArray(supportEquipment.map { JSONArray(it.toList()) }))
            .also {
                if (generationFacts.descriptorLists.isNotEmpty() || generationFacts.recommendationLevels.isNotEmpty()) {
                    it.put("generationFacts", generationFacts.json())
                }
            }

    fun encodeHistory(history: List<Workout>): String = JSONArray(history.map { it.json() }).toString()

    fun decodeHistory(text: String): List<Workout> = JSONArray(text).objects().map { it.workout() }

    fun encode(d: AppData, pretty: Boolean = true): String =
        document(d).let { if (pretty) it.toString(2) else it.toString() }

    fun workoutDocument(workout: Workout): JSONObject = workout.json()

    fun document(d: AppData): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("activeProgramId", d.activeProgramId)
            .put("programs", JSONArray(d.programs.map { ProgramCodec.encode(it) }))
            .put("programDraft", d.programDraft?.let { ProgramCodec.encode(it) })
            .put("pastWorkoutEdit", d.pastWorkoutEdit?.let {
                JSONObject().put("original", it.original.json()).put("edited", it.edited.json())
            })
            .put("workoutDraft", d.workoutDraft?.let {
                JSONObject()
                    .put("name", it.name)
                    .put("gymId", it.gymId)
                    .put("notes", it.notes)
                    .put("exercises", JSONArray(it.exercises.map { entry -> entry.json() }))
            })
            .put("onboarding", d.onboarding.json())
            .put("scaleWeights", BodyDataCodec.weights(d.scaleWeights))
            .put("healthConnect", HealthConnectSyncCodec.encode(d.healthConnect))
            .put("bodyMeasurements", BodyDataCodec.measurements(d.bodyMeasurements))
            .put("progressPhotos", ProgressPhotoCodec.encode(d.progressPhotos))
            .put("gyms", JSONArray(d.gyms.map { GymCodec.encode(it) }))
            .put("active", d.active?.json())
            .put("history", JSONArray(d.history.map { it.json() }))
            .put(
                "routines",
                JSONArray(
                    d.routines.map { TemplateCodec.encodeRoutine(it) }
                ),
            )
            .put("templateSplits", JSONArray(d.templateSplits.map { TemplateCodec.encodeSplit(it) }))
            .put("favorites", JSONArray(d.favorites.toList()))
            .put("customExercises", JSONArray(d.customExercises.map { it.json() }))
            .put("exerciseDefaults", ExerciseDefaultsCodec.encode(d.exerciseDefaults))
            .put(
                "preferences",
                JSONObject()
                    .put("pounds", d.preferences.pounds)
                    .put("restSeconds", d.preferences.restSeconds)
                    .put("autoRest", d.preferences.autoRest)
                    .put("showRir", d.preferences.showRir)
                    .put("displayName", d.preferences.displayName)
                    .put("dashboardAllWorkouts", d.preferences.dashboardAllWorkouts)
                    .put("showWeeklyWorkouts", d.preferences.showWeeklyWorkouts)
                    .put("showInsights", d.preferences.showInsights)
                    .put("showHabits", d.preferences.showHabits)
                    .put("exerciseAutoNext", d.preferences.exerciseAutoNext)
                    .put("keepAlive", d.preferences.keepAlive)
                    .put("showWorkoutTimer", d.preferences.showWorkoutTimer)
                    .put("showBodyweightContribution", d.preferences.showBodyweightContribution)
                    .put("distanceImperial", d.preferences.distanceImperial)
                    .put("heightInFeet", d.preferences.heightInFeet)
                    .put("clock24Hour", d.preferences.clock24Hour)
                    .put("dataVisibility", DataVisibilityCodec.encode(d.preferences.dataVisibility))
                    .put("dismissedTutorials", JSONArray(d.preferences.dismissedTutorials.sorted()))
                    .put("theme", d.preferences.theme.name)
                    .put("palette", d.preferences.palette.name)
                    .put("shortcuts", d.preferences.shortcuts.json())
                    .put("dashboard", d.preferences.dashboard.json())
                    .put("restAfterLastWarmupSet", d.preferences.restAfterLastWarmupSet)
                    .put("restBetweenExercises", d.preferences.restBetweenExercises)
                    .put("supersetAutoNext", d.preferences.supersetAutoNext)
                    .put("restBetweenSidesPercent", d.preferences.restBetweenSidesPercent)
                    .put("rateEffortAfterExercise", d.preferences.rateEffortAfterExercise)
                    .put("effortPromptTiming", d.preferences.effortPromptTiming.name)
                    .put("workoutKeypad", d.preferences.workoutKeypad)
                    .put("weeklyWorkoutGoal", d.preferences.weeklyWorkoutGoal),
            )

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    // Missing legacy fields have defaults; malformed present fields must not erase saved records.
    private fun JSONObject.array(k: String) = if (has(k)) getJSONArray(k) else JSONArray()

    private fun JSONObject.optionalObject(k: String): JSONObject? =
        if (isNull(k)) null else getJSONObject(k)

    private fun JSONObject.actual(key: String, maximum: Double = Double.MAX_VALUE): Double? =
        if (isNull(key)) null else getDouble(key).also {
            require(it.isFinite() && it in 0.0..maximum) { "Invalid $key actual" }
        }

    private fun JSONObject.sessionExercise(): SessionExercise =
        SessionExercise(
            getString("id"),
            getString("exerciseId"),
            array("sets").objects().map { s ->
                LoggedSet(
                    s.getString("id"),
                    if (!s.has("weightKg")) 0.0 else s.actual("weightKg", 5000.0),
                    if (!s.has("reps")) 8 else s.actual("reps", 9999.0)?.also {
                        require(it % 1.0 == 0.0) { "Reps must be a whole number" }
                    }?.toInt(),
                    if (s.isNull("rir")) null else s.getInt("rir").coerceIn(0, 10),
                    s.optBoolean("completed"),
                    s.optBoolean("warmup"),
                    if (s.isNull("targetRepsMin")) null else s.getInt("targetRepsMin"),
                    if (s.isNull("targetRepsMax")) null else s.getInt("targetRepsMax"),
                    if (s.isNull("targetRir")) null else s.getInt("targetRir"),
                    assistanceKg = s.actual("assistanceKg", 5000.0),
                    durationSeconds = s.actual("durationSeconds"),
                    distanceMeters = s.actual("distanceMeters"),
                    kind = if (!s.has("kind")) SetKind.STANDARD else SetKind.valueOf(s.getString("kind")),
                    side = if (s.isNull("side")) null else SetSide.valueOf(s.getString("side")),
                    pairId = if (s.isNull("pairId")) null else s.getString("pairId"),
                    segmentOf = if (s.isNull("segmentOf")) null else s.getString("segmentOf"),
                )
            },
            optString("notes"),
            if (isNull("restSeconds")) null else getInt("restSeconds"),
            supersetId = if (isNull("supersetId")) null else getString("supersetId").also { require(it.isNotBlank()) },
            reviewedEffortSetIds = array("reviewedEffortSetIds").strings().toSet(),
            lastNoteDismissed = optBoolean("lastNoteDismissed"),
        ).validateSetLinks()

    private fun JSONObject.workout(): Workout =
        Workout(
            getString("id"),
            getString("name"),
            getLong("startedAt"),
            if (isNull("finishedAt")) null else getLong("finishedAt"),
            array("exercises").objects().map { it.sessionExercise() },
            optString("notes"),
            optLong("restUntil", 0),
            optString("programId").takeIf { it.isNotBlank() && it != "null" },
            optString("selectedExerciseId").takeIf { it.isNotBlank() && it != "null" },
            if (isNull("pausedAt")) null else getLong("pausedAt"),
            optLong("pausedDurationMs", 0).coerceAtLeast(0),
            optString("programDayId").takeIf { it.isNotBlank() && it != "null" },
            if (isNull("programCycle")) null else getInt("programCycle"),
            optString("gymId").takeIf { it.isNotBlank() && it != "null" },
            optString("routineId").takeIf { it.isNotBlank() && it != "null" },
            effortPrompts = array("effortPrompts").objects().map {
                EffortPrompt(it.getString("exerciseId"), it.optBoolean("afterRest"))
            },
        )

    fun decode(text: String, historyOverride: List<Workout>? = null): AppData = decode(JSONObject(text), historyOverride)

    fun requireSupportedVersion(json: JSONObject) {
        val version = json.opt("schemaVersion")
        // JSON readers use either Int or Long. Never truncate fractions, wrap large values,
        // or accept strings/booleans as a supported version.
        require(version == 1 || version == 1L) { "Unsupported backup version" }
    }

    fun decode(j: JSONObject, historyOverride: List<Workout>? = null): AppData {
        requireSupportedVersion(j)
        require(!j.has("storageHistory") || historyOverride != null) { "Internal storage snapshot is not a restore backup" }
        val p = j.optionalObject("preferences") ?: JSONObject()
        val programs = j.array("programs").objects().map { ProgramCodec.decode(it) }
        require(programs.map { it.id }.distinct().size == programs.size) { "Duplicate program" }
        val routines = j.array("routines").objects().map { TemplateCodec.decodeRoutine(it) }
        require(routines.map { it.id }.distinct().size == routines.size) { "Duplicate template" }
        return AppData(
            j.optionalObject("active")?.workout(),
            historyOverride ?: j.array("history").objects().map { it.workout() },
            routines,
            j.array("favorites").strings().toSet(),
            j.array("customExercises").objects().map { it.exercise(custom = true) },
            Preferences(
                p.optBoolean("pounds"),
                p.optInt("restSeconds", 120).coerceIn(0, 1800),
                p.optBoolean("autoRest", true),
                p.optBoolean("showRir", true),
                p.optString("displayName", "Athlete"),
                p.optBoolean("dashboardAllWorkouts"),
                p.optBoolean("showWeeklyWorkouts", true),
                p.optBoolean("showInsights", true),
                p.optBoolean("showHabits", true),
                p.optBoolean("exerciseAutoNext", true),
                keepAlive = p.optBoolean("keepAlive", false),
                showWorkoutTimer = p.optBoolean("showWorkoutTimer", true),
                showBodyweightContribution = p.optBoolean("showBodyweightContribution", true),
                distanceImperial = p.optBoolean("distanceImperial", false),
                heightInFeet = p.optBoolean("heightInFeet", false),
                clock24Hour = p.optBoolean("clock24Hour", false),
                dataVisibility = if (p.has("dataVisibility"))
                    DataVisibilityCodec.decode(p.getJSONObject("dataVisibility")) else DataVisibility(),
                dismissedTutorials = if (p.has("dismissedTutorials"))
                    p.getJSONArray("dismissedTutorials").strings().toSet() else emptySet(),
                theme = AppTheme.valueOf(p.optString("theme", AppTheme.DARK.name)),
                // Cosmetic: a missing or unknown palette (older or newer backup) falls back to Charcoal.
                palette = AppPalette.entries.firstOrNull { it.name == p.optString("palette") } ?: AppPalette.CHARCOAL,
                shortcuts = ShortcutPreferences.decode(p.optionalObject("shortcuts")),
                dashboard = DashboardPreferences.decode(p.optionalObject("dashboard")),
                restAfterLastWarmupSet = p.optBoolean("restAfterLastWarmupSet", true),
                restBetweenExercises = p.optBoolean("restBetweenExercises", true),
                supersetAutoNext = p.optBoolean("supersetAutoNext", true),
                restBetweenSidesPercent = if (!p.has("restBetweenSidesPercent")) 100 else p.get("restBetweenSidesPercent").let {
                    require(it is Number && it.toDouble() % 1.0 == 0.0 && it.toDouble() in 0.0..100.0) { "Side rest must be 0–100%" }
                    it.toInt()
                },
                rateEffortAfterExercise = p.optBoolean("rateEffortAfterExercise", true),
                // Older backups (and unknown future values) get the new on-leave timing.
                effortPromptTiming = EffortPromptTiming.entries.find { it.name == p.optString("effortPromptTiming") }
                    ?: EffortPromptTiming.NEXT_EXERCISE,
                workoutKeypad = p.optBoolean("workoutKeypad", true),
                weeklyWorkoutGoal = if (p.isNull("weeklyWorkoutGoal")) null else p.optInt("weeklyWorkoutGoal").takeIf { it in 1..7 },
            ),
            j.optString("activeProgramId").takeIf { it.isNotBlank() && it != "null" },
            j.array("gyms").objects().map { GymCodec.decode(it) },
            programs,
            j.optionalObject("programDraft")?.let { ProgramCodec.decode(it) },
            OnboardingState.decode(j.optionalObject("onboarding")),
            BodyDataCodec.readWeights(j),
            BodyDataCodec.readMeasurements(j),
            workoutDraft = j.optionalObject("workoutDraft")?.let {
                WorkoutDraft(
                    name = it.getString("name"),
                    exercises = it.array("exercises").objects().map { entry -> entry.sessionExercise() },
                    gymId = it.optString("gymId").takeIf { id -> id.isNotBlank() && id != "null" },
                    notes = it.optString("notes"),
                )
            },
            progressPhotos = ProgressPhotoCodec.decode(j),
            exerciseDefaults = ExerciseDefaultsCodec.decode(j.optionalObject("exerciseDefaults")),
            healthConnect = HealthConnectSyncCodec.decode(j.optionalObject("healthConnect")),
            pastWorkoutEdit = j.optionalObject("pastWorkoutEdit")?.let {
                PastWorkoutEdit(it.getJSONObject("original").workout(), it.getJSONObject("edited").workout())
            },
            templateSplits = TemplateCodec.decodeSplits(j.array("templateSplits"), routines),
        )
    }

    private fun JSONObject.exercise(custom: Boolean = optBoolean("custom")): Exercise {
        val properties = optionalObject("properties")
        val muscles = array("muscles").strings()
        fun requirements(key: String): List<Set<String>> = array(key).let { groups ->
            (0 until groups.length()).map { groups.getJSONArray(it).strings().toSet() }
        }
        return Exercise(
            getString("id"),
            getString("name"),
            optString("muscle", "Other"),
            optString("equipment", "Other"),
            array("secondary").strings(),
            array("frames").strings(),
            optString("instructions"),
            optString("video").takeIf { it.isNotBlank() && it != "null" },
            custom,
            if (custom && has("muscles")) muscles else muscles.ifEmpty { listOf(optString("muscle", "Other")) },
            array("aliases").strings(),
            array("equipmentOptions").strings(),
            properties?.keys()?.asSequence()?.associateWith { properties.getString(it) } ?: emptyMap(),
            optDouble("bodyweight", 0.0).also {
                require(it.isFinite() && it >= 0.0 && (!custom || it <= 1.0)) { "Invalid bodyweight contribution" }
            },
            optDouble("searchBoost", 0.0),
            array("clips").strings(),
            array("browseCategories").strings(),
            requirements("resistanceEquipment"),
            requirements("supportEquipment"),
            optBoolean("archived"),
            ExerciseGenerationFacts.decode(if (has("generationFacts")) getJSONObject("generationFacts") else null),
        ).also { if (custom) it.loggingMetrics() }
    }

    fun catalog(text: String, bundled: List<Exercise> = emptyList()): List<Exercise> {
        val factsById = bundled.associateBy { it.id }
        return JSONArray(text).objects().map { json ->
            val exercise = json.exercise()
            val facts = factsById[exercise.id]
            if (facts == null) exercise else exercise.copy(
                properties = facts.properties.filterKeys { it in listOf("Region", "Movement Patterns") } + exercise.properties,
                generationFacts = ExerciseGenerationFacts(
                    facts.generationFacts.descriptorLists + exercise.generationFacts.descriptorLists,
                    facts.generationFacts.recommendationLevels + exercise.generationFacts.recommendationLevels,
                ),
            )
        }
    }
}
