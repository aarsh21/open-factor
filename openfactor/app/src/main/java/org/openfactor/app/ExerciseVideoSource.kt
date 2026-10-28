package org.openfactor.app

import java.net.URI

/** Original object names are stable identifiers, never guessed exercise-name matches. */
data class ExerciseVideoReference(val exerciseId: String, val suffix: String) {
    init {
        require(exerciseId.matches(Regex("[0-9a-f]{32}"))) { "Invalid exercise video identifier" }
        require(suffix.matches(Regex("[a-z]"))) { "Invalid exercise video camera" }
    }
    val objectPath: String get() = "videos/${exerciseId}_$suffix.mp4"
    val assetPath: String get() = "reference/$objectPath"

    companion object {
        private val objectPattern = Regex("(?:reference/)?videos/([0-9a-f]{32})_([a-z])[.]mp4")
        fun parse(value: String?): ExerciseVideoReference? {
            val match = value?.let(objectPattern::matchEntire) ?: return null
            return ExerciseVideoReference(match.groupValues[1], match.groupValues[2])
        }
    }
}

data class ExerciseVideoLocation(val uri: String, val bundled: Boolean, val cacheKey: String)

/** HTTPS-only configuration; URL credentials, query parameters and traversal are rejected. */
fun normalizedExerciseMediaBase(value: String): String? {
    if (value.isBlank()) return null
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val path = uri.rawPath.orEmpty()
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.normalize().rawPath != uri.rawPath ||
        path.contains('%') || path.contains('\\') || path.split('/').any { it == "." || it == ".." } ||
        (uri.port != -1 && uri.port !in 1..65535)) return null
    return uri.toASCIIString().trimEnd('/') + "/"
}

fun exerciseVideoLocation(reference: ExerciseVideoReference, bundled: Boolean, mediaBase: String): ExerciseVideoLocation? {
    if (bundled) return ExerciseVideoLocation("asset:///${reference.assetPath}", true, reference.assetPath)
    val url = (normalizedExerciseMediaBase(mediaBase) ?: return null) + reference.objectPath
    return ExerciseVideoLocation(url, false, url)
}
