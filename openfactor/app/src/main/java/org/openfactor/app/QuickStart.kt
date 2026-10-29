package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Date

/** A short, durable introduction to the profile and logging controls the app actually uses. */
@Composable
fun QuickStartScreen(
    data: AppData,
    vm: WorkoutViewModel,
    onConnect: () -> Unit = {},
    onReady: () -> Unit,
) {
    val draft = data.onboarding.profileDraft ?: data.profileSetupDraft()
    val stage = draft.stage.coerceIn(0, 2)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var saveError by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { vm.ensureProfileSetup() }
    LaunchedEffect(stage) { scroll.scrollTo(0) }
    LaunchedEffect(data.healthConnect.lastSyncedAtMillis) {
        if (data.healthConnect.lastSyncedAtMillis != null) vm.editProfileSetup { it.refreshWeight(data.scaleWeights) }
    }
    fun edit(changed: ProfileSetupDraft) { saveError = null; vm.editProfileSetup { changed } }
    fun leave() {
        focus.clearFocus(); keyboard?.hide()
        vm.skipProfileSetup()
        onReady()
    }
    fun back() {
        focus.clearFocus(); keyboard?.hide()
        if (stage > 0) vm.editProfileSetup { it.copy(stage = stage - 1) } else leave()
    }
    BackHandler(onBack = ::back)
    val stageError = when (stage) {
        0 -> draft.nameError()
        1 -> draft.bodyError()
        else -> draft.validationError()
    }
    Surface(color = OF.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(::back) { Icon(if (stage > 0) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close, "Back") }
                Text("Open Factor", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(::leave) { Text("Skip for now") }
            }
            SetupProgress(stage)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Text(
                    when (stage) { 0 -> "Make it yours"; 1 -> "Your starting point"; else -> "Ready, set, log." },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                when (stage) {
                    0 -> ProfileBasicsFields(draft, ::edit)
                    1 -> {
                        SetupHealthConnect(data) {
                            focus.clearFocus(); keyboard?.hide(); onConnect()
                        }
                        ProfileBodyFields(draft, ::edit)
                    }
                    else -> ProfileTrainingFields(draft, ::edit)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                PrimaryAction(
                    if (stage == 2) "Start training" else "Continue",
                    {
                        focus.clearFocus(); keyboard?.hide()
                        if (stage < 2) vm.editProfileSetup { it.copy(stage = stage + 1) }
                        else {
                            saveError = vm.completeProfileSetup()
                            if (saveError == null) onReady()
                        }
                    },
                    enabled = stageError == null,
                )
            }
        }
    }
}

@Composable
private fun SetupProgress(stage: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Setup step ${stage + 1} of 3" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            Box(Modifier.weight(1f).height(3.dp).background(
                if (index <= stage) MaterialTheme.colorScheme.primary else OF.line,
                RoundedCornerShape(50),
            ))
        }
    }
}

@Composable
private fun SetupHealthConnect(data: AppData, onConnect: () -> Unit) {
    val context = LocalContext.current
    var availability by remember { mutableStateOf(healthConnectAvailability(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { availability = healthConnectAvailability(context) }
    Surface(Modifier.fillMaxWidth(), color = OF.card, shape = OF.shape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.HealthAndSafety, null, Modifier.size(24.dp))
                Text("Health Connect", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (data.healthConnect.lastSyncedAtMillis != null) Icon(Icons.Rounded.CheckCircle, "Health Connect has synced", tint = OF.green, modifier = Modifier.size(20.dp))
            }
            if (availability == HealthConnectAvailability.UNAVAILABLE) {
                Text("Unavailable on this device. Manual works too.", color = OF.muted, style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedButton(onConnect, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(when {
                        availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Set up Health Connect"
                        data.healthConnect.lastSyncedAtMillis != null -> "Sync Health Connect"
                        else -> "Connect Health Connect"
                    })
                }
            }
            data.healthConnect.lastSyncedAtMillis?.let { synced ->
                Text("Last synced ${android.text.format.DateFormat.getMediumDateFormat(context).format(Date(synced))}",
                    color = OF.muted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Shared with Profile, so choices remain easy to find and change after first run. */
@Composable
internal fun ProfileTrainingFields(draft: ProfileSetupDraft, onChange: (ProfileSetupDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("Weekly workout goal", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedIconButton({ onChange(draft.copy(weeklyGoal = draft.weeklyGoal - 1)) }, Modifier.size(48.dp), enabled = draft.weeklyGoal > 1) {
                Icon(Icons.Rounded.Remove, "Fewer workouts per week")
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Weekly workout goal: ${draft.weeklyGoal}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${draft.weeklyGoal}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text(if (draft.weeklyGoal == 1) "workout per week" else "workouts per week", style = MaterialTheme.typography.bodyMedium, color = OF.muted, textAlign = TextAlign.Center)
            }
            OutlinedIconButton({ onChange(draft.copy(weeklyGoal = draft.weeklyGoal + 1)) }, Modifier.size(48.dp), enabled = draft.weeklyGoal < 7) {
                Icon(Icons.Rounded.Add, "More workouts per week")
            }
        }
        HorizontalDivider(color = OF.line)
        ProfileSetupSwitch("Rate effort after exercise", draft.rateEffort) { onChange(draft.copy(rateEffort = it)) }
        ProfileSetupSwitch("Automatic rest timer", draft.autoRest) { onChange(draft.copy(autoRest = it)) }
        if (draft.autoRest) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rest duration", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                IconButton({ onChange(draft.copy(restSeconds = (draft.restSeconds - 15).coerceAtLeast(0))) }, enabled = draft.restSeconds > 0) {
                    Icon(Icons.Rounded.Remove, "Shorter rest timer")
                }
                Text("${draft.restSeconds}s", Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                IconButton({ onChange(draft.copy(restSeconds = (draft.restSeconds + 15).coerceAtMost(1800))) }, enabled = draft.restSeconds < 1800) {
                    Icon(Icons.Rounded.Add, "Longer rest timer")
                }
            }
        }
    }
}

@Composable
private fun ProfileSetupSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}
