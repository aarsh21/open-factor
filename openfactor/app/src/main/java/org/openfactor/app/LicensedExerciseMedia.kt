package org.openfactor.app

import android.content.Context
import org.json.JSONObject

data class ExerciseMediaCredit(
    val exerciseId: String,
    val exerciseName: String,
    val asset: String,
    val author: String,
    val license: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val sourcePage: String,
)

fun loadExerciseMediaCredits(context: Context): List<ExerciseMediaCredit> {
    val manifest = context.assets.open("exercise-previews.json").bufferedReader().use { JSONObject(it.readText()) }
    val entries = manifest.getJSONArray("entries")
    return (0 until entries.length()).map { index ->
        val entry = entries.getJSONObject(index)
        ExerciseMediaCredit(
            entry.getString("exercise_id"), entry.getString("exercise_name"),
            entry.getString("asset"), entry.getString("author"), entry.getString("license"),
            entry.getString("license_url"), entry.getString("source_url"), entry.getString("source_page"),
        )
    }
}

/** Licensed previews fill only exact reviewed joins; custom/imported media retains its identity. */
fun withLicensedExercisePreviews(context: Context, catalog: List<Exercise>): List<Exercise> {
    val byExercise = loadExerciseMediaCredits(context).groupBy { it.exerciseId }
    return catalog.map { exercise ->
        if (exercise.frames.isNotEmpty()) exercise
        else byExercise[exercise.id]?.let { credits -> exercise.copy(frames = credits.map { it.asset }) } ?: exercise
    }
}
