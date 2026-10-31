package org.openfactor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Local product guidance. No network, account, or source-app support service is required. */
private val helpArticles = linkedMapOf(
    "Keep a complete backup" to
        "1. Open Settings → Export Backup.\n2. Choose a private folder in Android's file picker.\n3. Save the JSON file.\n\nA complete backup includes saved training, preferences, body records and original progress-photo bytes. Treat the file like the original photos. No account or cloud upload is required. Copy it to another device or private storage before uninstalling the app or clearing app data.",
    "Restore on another device" to
        "Restore replaces local data. Records absent from the backup are removed. An older backup without photos also removes local photo records.\n\n1. Copy a complete backup to the other device.\n2. Open Settings → Restore Backup.\n3. Select the JSON file and review the replacement warning.\n4. Choose Restore only if you intend to replace the device's data.\n\nThe app validates the document and photo attachments before replacement. Cancel or Back leaves your saved data unchanged. Scoped Data Export files are not restore backups.",
    "Export only the data you need" to
        "Open Settings → Data Export. Quick Export includes muscle-group set records, your active program and completed workout history. Granular Export lets you select categories first, then a time frame. Choose Export to open Android's Save dialog. Cancel or Back creates no export in the app.\n\nDate boundaries are inclusive in the device time zone. Last 7 Days means today and the previous six dates. This Month ends today. Last Month includes the whole previous month. All Time includes all saved dates, including future-dated records. Libraries and settings are current snapshots. Photos and drafts are excluded. Hidden measurements still export. These files are for analysis, not Restore Backup.",
    "Import Fitbod strength history" to
        "Open Settings → Import Fitbod CSV and choose a local strength-sets CSV. Clear compatible names are matched automatically; uncertain names are preserved as custom exercises instead of blocking import. Review or change any match, keep its original name or explicitly skip it. Review the limits and source sets if needed, check the pinned acknowledgement and import to history. Preparation can be cancelled, and slow provider reads time out with an error rather than retrying forever. Back or Cancel before confirmation changes no saved data. This is separate from Import spreadsheet (program targets) and Restore Backup (replacement).\n\nThe supported format has 18 columns including date_local, date_utc, workout_id, reps, weight_kg and set_id. Weights remain kilograms; no per-side conversion is inferred. AMRAP and varying rest values are retained in notes. Workout duration is unavailable, so imported workouts show zero minutes rather than a guessed duration. Duration/distance sets and other CSV formats are rejected.\n\nRepeated Fitbod workout IDs are skipped, preserving local edits. Reimporting does not merge new sets into an already imported workout. Skipped exercises can leave workouts partially imported or entirely omitted; review before saving. Imported sets contribute to training statistics. Keep the original CSV and make a complete backup before importing.",
    "Hide a metric without losing it" to
        "Open Settings → Data Visibility. Turn off an unused measurement or photo position. Visibility changes the Dashboard and new-entry editors, not history, charts, exports or original photo files. Editing an existing record preserves hidden values. Turn the field on again to use it in new entries.\n\nScale weight and visual body fat remain available by default.",
    "Read body charts" to
        "Open Body Data, then Body Charts. Choose a metric and a calendar range. Tap a saved entry or a dot to inspect its exact date and value. Each reading remains separate, including readings on the same date. Blank fields are omitted, not plotted as zero. A recorded 0% body-fat value is retained.\n\nCharts keep the original readings. Weight trend adds a trailing seven-day mean of available readings; missing dates are not zero-filled. Body-fat values are recorded, never estimated here. Use the help icon on Body Charts at any time. Settings → Tutorials → Reset Tutorials makes the chart introduction appear on the next chart visit.",
    "Log a workout offline" to
        "Start an empty workout or start a saved routine from Workouts. Add exercises, enter the actual values for each set and mark completed sets. Target values are separate from performed values. You can pause a session and continue later. Finish the workout to add it to history.\n\nProgress photos imported through Android's picker are copied into private app storage. The app keeps the original bytes; it does not upload them. Reference exercise media is separate from your private photos.",
    "Use exercise notes" to
        "A workout can show up to three kinds of note under an exercise. The pin is the exercise's own note and shows every time you do it; set it in the exercise's settings. The list icon is a template note: it shows only when the workout was started from that template, so Upper A and Upper B can say different things. Edit it in the template's targets or from the exercise menu → Template note during a workout started from that template; saving updates the template.\n\nWhen today's exercise has no note yet, the clock line shows your most recent note for it from history, labelled Last time with its date. It is not copied into today's workout. Tap it to write today's note (it starts empty). Swipe it aside or use its close button to hide it for this workout; the old note stays in its workout. Long notes show a few lines; tap to read the rest.",
    "Troubleshoot files and missing data" to
        "If Save fails, choose another local folder and check free storage. A document provider may require its own network access; local app features do not. If Restore rejects a file, keep the original and check that it came from Export Backup, not Data Export. Do not edit backup attachment hashes.\n\nIf a field disappeared, check Data Visibility. If a chart is empty, choose a wider range and check the record date. Hiding a metric never deletes its saved history.\n\nIf the problem persists, include the app version, Android version, steps and error text in a repository issue using Settings → Source Code. Review everything before sharing. Never attach a complete backup, progress photos or private logs to a public issue.",
)

@Composable
internal fun OfflineHelpScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(query, { query = it }, label = { Text("Search help") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val matches = helpArticles.filter { (title, body) -> title.contains(query.trim(), true) || body.contains(query.trim(), true) }
        if (matches.isEmpty()) Text("No matching articles. Try backup, photos, export, visibility or charts.")
        matches.forEach { (title, body) ->
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expanded = if (expanded == title) "" else title },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (expanded == title) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (expanded == title) "Collapse article" else "Expand article")
                }
                if (expanded == title) Text(body, Modifier.padding(bottom = 16.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun TutorialsScreen(p: Preferences, vm: WorkoutViewModel, snackbar: SnackbarHostState) {
    var reset by remember { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Review the body-chart introduction here or use the help icon on Body Charts. Reset makes it appear again on the next chart visit.")
        Text(if ("bodyCharts" in p.dismissedTutorials) "Body Charts introduction: already dismissed" else "Body Charts introduction: appears on the next visit", color = OF.muted)
        OutlinedButton({ reading = !reading }, Modifier.fillMaxWidth()) { Text(if (reading) "Close Introduction" else "Read Body Charts Introduction") }
        if (reading) Text(helpArticles.getValue("Read body charts"))
        Button({ reset = true }, Modifier.fillMaxWidth()) { Text("Reset Tutorials") }
        Text("Reset changes only tutorial dismissal. It does not reset workouts, onboarding, preferences, visibility or body records. This introduction explains reading and inspecting your saved body data.", color = OF.muted)
    }
    if (reset) ConfirmDialog(
        "Reset Tutorials?", "The body-chart introduction will appear on your next chart visit. Your saved data and other preferences will not change.",
        "Reset", { reset = false }, {
            vm.settings { it.copy(dismissedTutorials = it.dismissedTutorials - "bodyCharts") }
            reset = false
            scope.launch { snackbar.showSnackbar("Tutorials reset · open Body Charts to see the introduction") }
        },
    )
}
