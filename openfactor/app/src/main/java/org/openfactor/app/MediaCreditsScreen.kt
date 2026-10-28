package org.openfactor.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun MediaCreditsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val credits = remember(context) { loadExerciseMediaCredits(context) }
    var licenseOpen by remember { mutableStateOf(false) }
    var unavailableLink by remember { mutableStateOf("") }
    fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { unavailableLink = url }
    }
    if (licenseOpen) {
        BackHandler { licenseOpen = false }
        val legalCode = remember(context) {
            context.assets.open("licenses/CC-BY-SA-3.0.txt").bufferedReader().use { it.readText() }
        }
        Column(Modifier.fillMaxSize()) {
            PageHeading("CC BY-SA 3.0", { licenseOpen = false })
            SelectionContainer {
                Text(legalCode, Modifier.verticalScroll(rememberScrollState()).padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PageHeading("Media credits", onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Exercise illustrations by Everkinetic, distributed by wger.", style = MaterialTheme.typography.bodyLarge) }
            item {
                OutlinedButton({ licenseOpen = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Read CC BY-SA 3.0")
                }
            }
            item { Text("The original PNGs are included without changes. Each image retains its original attribution and license. Unmapped exercises use an icon.", style = MaterialTheme.typography.bodyMedium) }
            items(credits.distinctBy { it.exerciseId }, key = { it.exerciseId }) { credit ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(credit.exerciseName, Modifier.weight(1f))
                    TextButton({ open(credit.sourcePage) }, Modifier.semantics { contentDescription = "Source for ${credit.exerciseName}" }) {
                        Text("Source")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = OF.line)
            }
        }
    }
    if (unavailableLink.isNotEmpty()) AlertDialog(
        onDismissRequest = { unavailableLink = "" },
        title = { Text("No browser found") },
        text = { SelectionContainer { Text(unavailableLink) } },
        confirmButton = { TextButton({ unavailableLink = "" }) { Text("Done") } },
    )
}
