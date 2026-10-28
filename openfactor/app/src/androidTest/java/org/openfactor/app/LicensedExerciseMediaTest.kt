package org.openfactor.app

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LicensedExerciseMediaTest {
    @Test
    fun everyLicensedPreviewMatchesItsProvenanceAndDecodesAsAnImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("exercise-previews.json").bufferedReader().use { JSONObject(it.readText()) }
        val entries = manifest.getJSONArray("entries")
        assertEquals(44, entries.length())
        val credits = loadExerciseMediaCredits(context)
        assertEquals(21, credits.map { it.exerciseId }.distinct().size)
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val bytes = context.assets.open(entry.getString("asset")).use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(entry.getString("asset"), entry.getString("sha256"), digest)
            assertEquals(entry.getInt("bytes"), bytes.size)
            assertEquals("Everkinetic", entry.getString("author"))
            assertEquals("CC BY-SA 3.0", entry.getString("license"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            assertTrue(entry.getString("asset"), bounds.outWidth > 0 && bounds.outHeight > 0)
        }
        context.assets.open("licenses/CC-BY-SA-3.0.txt").bufferedReader().use {
            assertTrue(it.readText().contains("Attribution-ShareAlike 3.0 Unported"))
        }
    }

    @Test
    fun previewsApplyByReviewedIdAndPreserveExistingMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("catalog.json").bufferedReader().use { DataCodec.catalog(it.readText()) }
        val withPreviews = withLicensedExercisePreviews(context, catalog)
        assertEquals(catalog.size, withPreviews.size)
        assertEquals(21, withPreviews.count { it.frames.isNotEmpty() })
        val bench = catalog.single { it.name == "Barbell bench press" }
        assertEquals(2, withPreviews.single { it.id == bench.id }.frames.size)
        val variant = bench.copy(id = "unreviewed-variant")
        assertTrue(withLicensedExercisePreviews(context, listOf(variant)).single().frames.isEmpty())
        val imported = bench.copy(frames = listOf("own-image.png"))
        assertEquals(imported, withLicensedExercisePreviews(context, listOf(imported)).single())
    }
}
