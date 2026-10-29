package org.openfactor.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** Every target remains visible together; the two original figures supply front/back context. */
@Composable
fun ExerciseAnatomy(exercise: Exercise) {
    val primary = exercise.muscles.ifEmpty { listOf(exercise.muscle) }.filter(String::isNotBlank).distinct()
    val secondary = exercise.secondary.filter { it.isNotBlank() && it !in primary }.distinct()
    var figure by rememberSaveable { mutableStateOf("male") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val figureSelector: @Composable () -> Unit = {
            SingleChoiceSegmentedButtonRow {
                listOf("male" to "Male", "female" to "Female").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = figure == value,
                        onClick = { figure = value },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        modifier = Modifier.semantics { contentDescription = "$label anatomy figure" },
                    ) { Text(label) }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 380.dp || LocalDensity.current.fontScale > 1.15f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Target muscles", style = MaterialTheme.typography.titleLarge)
                    figureSelector()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target muscles", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    figureSelector()
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (primary + secondary).forEach { muscle ->
                val isPrimary = muscle in primary
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "$muscle, ${if (isPrimary) "Primary" else "Secondary"} target muscle"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val color = if (isPrimary) OF.blue else Color(0xFFB9D3FF)
                    Canvas(Modifier.size(8.dp)) { drawCircle(color) }
                    Text(muscle, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(if (isPrimary) "Primary" else "Secondary", color = OF.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = OF.card, shape = OF.shape) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(false, true).forEach { back ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetMuscleFigure(primary, secondary, figure, back, Modifier.fillMaxWidth().aspectRatio(.49f))
                        Text(if (back) "Back" else "Front", color = OF.muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetMuscleFigure(
    primary: List<String>, secondary: List<String>, figure: String, back: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val art by produceState<ByteBuffer?>(null, context, primary, secondary, figure, back) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val xml = context.assets.open("reference/body/avatar_${figure}_${if (back) "back" else "front"}.svg")
                    .bufferedReader().use { it.readText() }
                ByteBuffer.wrap(highlightExerciseAnatomySvg(xml, primary, secondary).toByteArray())
            }.getOrNull()
        }
    }
    val description = "${if (back) "Back" else "Front"} target muscles: ${(primary + secondary).joinToString(", ")}"
    Box(modifier, contentAlignment = Alignment.Center) {
        if (art != null) AsyncImage(art, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Icon(Icons.Rounded.AccessibilityNew, description, Modifier.size(48.dp), tint = OF.muted)
    }
}

/** Only recolor named original paths; never mutate the packaged anatomy source. */
internal fun highlightExerciseAnatomySvg(xml: String, primary: List<String>, secondary: List<String>): String {
    fun String.key() = filter(Char::isLetterOrDigit).lowercase()
    val primaryIds = primary.map { it.key() }.toSet()
    val secondaryIds = secondary.map { it.key() }.toSet()
    val themed = xml.replace("#DCDCDC", "#353535").replace("#EEEEEE", "#555555")
    return Regex("<path\\b[^>]*>").replace(themed) { match ->
        val id = Regex("id=\"([^\"]+)\"").find(match.value)?.groupValues?.get(1)?.key()
        val color = when (id) {
            in primaryIds -> "#3982FF"
            in secondaryIds -> "#B9D3FF"
            else -> null
        }
        if (color == null) match.value else match.value.replace("#555555", color)
    }
}
