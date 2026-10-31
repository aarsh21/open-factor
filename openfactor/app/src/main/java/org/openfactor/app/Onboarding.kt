package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Local continuation of the observed three-stage setup; source-only integrations stay optional. */
data class OnboardingState(
    val step: Int = 0,
    val completed: Boolean = false,
    val deferred: Boolean = false,
    val name: String = "",
    val pounds: Boolean = false,
    val heightCm: String = "",
    val weightKg: String = "",
    val preset: String = "current",
    val gymName: String = "Everything Gym",
    val gymId: String? = null,
    val gender: ProfileGender = ProfileGender.UNSPECIFIED,
    val profileDraft: ProfileSetupDraft? = null,
) {
    fun validBasics() =
        name.isNotBlank() &&
            (heightCm.isBlank() || (heightCm.toDoubleOrNull() ?: 0.0) in 30.0..300.0) &&
            (weightKg.isBlank() || (weightKg.toDoubleOrNull() ?: 0.0) in 1.0..1000.0)

    fun json() =
        JSONObject()
            .put("step", step)
            .put("completed", completed)
            .put("deferred", deferred)
            .put("name", name)
            .put("pounds", pounds)
            .put("heightCm", heightCm)
            .put("weightKg", weightKg)
            .put("preset", preset)
            .put("gymName", gymName)
            .put("gymId", gymId)
            .put("gender", gender.name)
            .put("profileDraft", profileDraft?.json())

    companion object {
        // Old backups/installations are already usable; setup is available through Settings.
        fun decode(j: JSONObject?) =
            if (j == null) OnboardingState(completed = true)
            else
                OnboardingState(
                    j.optInt("step").coerceIn(0, 7),
                    j.optBoolean("completed"),
                    j.optBoolean("deferred"),
                    j.optString("name"),
                    j.optBoolean("pounds"),
                    j.optString("heightCm"),
                    j.optString("weightKg"),
                    j.optString("preset", "current"),
                    j.optString("gymName"),
                    j.optString("gymId").takeIf { it.isNotBlank() && it != "null" },
                    gender = ProfileGender.entries.find { it.name == j.optString("gender") } ?: ProfileGender.UNSPECIFIED,
                    profileDraft = j.optJSONObject("profileDraft")?.let(ProfileSetupDraft::fromJson),
                )
    }
}

@Composable
fun OnboardingScreen(
    data: AppData,
    vm: WorkoutViewModel,
    onManualProgram: () -> Unit,
    onHealthConnect: () -> Unit = {},
) {
    val s = data.onboarding
    fun edit(block: (OnboardingState) -> OnboardingState) = vm.editOnboarding(block)
    fun next() = edit { it.copy(step = (it.step + 1).coerceAtMost(7)) }
    var exit by remember { mutableStateOf(false) }
    BackHandler { if (s.step > 0) edit { it.copy(step = it.step - 1) } else exit = true }
    Surface(color = OF.top, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton({
                    if (s.step > 0) edit { it.copy(step = it.step - 1) } else exit = true
                }) {
                    Icon(
                        if (s.step == 0) Icons.Rounded.Close
                        else Icons.AutoMirrored.Rounded.ArrowBack,
                        "Back",
                    )
                }
                TextButton({ exit = true }) { Text("Set up later") }
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                when (s.step) {
                    0 -> {
                        Spacer(Modifier.height(20.dp))
                        SetupTitle("OPEN FACTOR")
                        Text(
                            "Workouts",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(20.dp))
                        Icon(
                            Icons.Rounded.FitnessCenter,
                            null,
                            Modifier.align(Alignment.CenterHorizontally).size(104.dp),
                        )
                        Text(
                            "Plan your training. Log your workouts. See your progress.",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Your training stays on this device. No account required.",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = OF.muted,
                        )
                    }
                    1,
                    4,
                    6 -> {
                        Spacer(Modifier.height(16.dp))
                        SetupTitle("LET’S GET\nSTARTED")
                        Text(
                            "Your training starts here",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val stage =
                            when (s.step) {
                                1 -> 0
                                4 -> 1
                                else -> 2
                            }
                        val labels = listOf("Basics", "Gym & Equipment", "Program")
                        val descriptions =
                            listOf(
                                "We’ll gather key details like your height and weight for your training profile.",
                                "Choose your gym and the equipment available to you.",
                                "Create a program from scratch, or set one up later.",
                            )
                        Column(Modifier.padding(top = 12.dp)) {
                            labels.forEachIndexed { i, label ->
                                Row(
                                    Modifier.heightIn(min = 92.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(40.dp)
                                                .background(
                                                    if (i == stage) MaterialTheme.colorScheme.primary
                                                    else OF.elevated,
                                                    CircleShape,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (i < stage)
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            else
                                                Text(
                                                    "${i+1}",
                                                    color =
                                                        if (i == stage) MaterialTheme.colorScheme.onPrimary
                                                        else OF.muted,
                                                    style = MaterialTheme.typography.titleLarge,
                                                )
                                        }
                                        if (i < 2)
                                            Box(
                                                Modifier.width(2.dp)
                                                    .height(if (i == stage) 88.dp else 42.dp)
                                                    .background(OF.elevated)
                                            )
                                    }
                                    Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
                                        Text(
                                            label,
                                            color =
                                                if (i <= stage) MaterialTheme.colorScheme.onSurface
                                                else OF.muted,
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                        if (i == stage)
                                            Text(
                                                descriptions[i],
                                                color = OF.muted,
                                                modifier = Modifier.padding(top = 6.dp),
                                            )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        SetupTitle("Basics")
                        Text(
                            "Optionally sync your data",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        OutlinedCard(
                            onClick = onHealthConnect,
                            colors = CardDefaults.outlinedCardColors(containerColor = OF.top),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(Icons.Rounded.HealthAndSafety, null)
                                Column {
                                    Text(
                                        "Health Connect",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        "Steps, weight, scale body fat, and workouts",
                                        color = OF.muted,
                                    )
                                    Text(
                                        "Optional · transfers run only when you choose",
                                        color = OF.muted,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                        Text("You can continue without syncing.", color = OF.muted)
                    }
                    3 -> {
                        SetupTitle("Basics")
                        Text(
                            "Your training profile",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        OutlinedTextField(
                            s.name,
                            { v -> edit { it.copy(name = v) } },
                            Modifier.fillMaxWidth(),
                            label = { Text("Name") },
                            singleLine = true,
                        )
                        Text("Workout weight units", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.selectableGroup()) {
                            listOf(false to "Kilograms (kg)", true to "Pounds (lb)").forEach {
                                (lb, label) ->
                                Row(
                                    Modifier.weight(1f)
                                        .selectable(
                                            s.pounds == lb,
                                            role = Role.RadioButton,
                                            onClick = { edit { it.copy(pounds = lb) } },
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(s.pounds == lb, null)
                                    Text(label)
                                }
                            }
                        }
                        OutlinedTextField(
                            s.heightCm,
                            { v -> edit { it.copy(heightCm = v) } },
                            Modifier.fillMaxWidth(),
                            label = { Text("Height (cm) · optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError =
                                s.heightCm.isNotBlank() &&
                                    (s.heightCm.toDoubleOrNull() ?: 0.0) !in 30.0..300.0,
                            supportingText = { Text("Enter 30–300 cm, or leave empty") },
                        )
                        OutlinedTextField(
                            s.weightKg,
                            { v -> edit { it.copy(weightKg = v) } },
                            Modifier.fillMaxWidth(),
                            label = { Text("Body weight (kg) · optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError =
                                s.weightKg.isNotBlank() &&
                                    (s.weightKg.toDoubleOrNull() ?: 0.0) !in 1.0..1000.0,
                            supportingText = { Text("Enter 1–1000 kg, or leave empty") },
                        )
                        Text(
                            "These details are stored locally. Height and body weight do not yet drive automatic programming.",
                            color = OF.muted,
                        )
                    }
                    5 -> {
                        SetupTitle("Gym & Equipment")
                        Text(
                            "Choose your equipment",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Column(Modifier.selectableGroup()) {
                            val presets =
                                linkedMapOf(
                                    "current" to
                                        (data.gyms.firstOrNull()?.name ?: "Everything Gym"),
                                    "everything" to "Everything Gym",
                                    "blank" to "Blank Gym",
                                ) + EquipmentCatalog.presetLabels
                            presets.forEach { (key, label) ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .selectable(
                                            s.preset == key,
                                            role = Role.RadioButton,
                                            onClick = {
                                                edit { it.copy(preset = key, gymName = label) }
                                            },
                                        )
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(s.preset == key, null)
                                    Text(
                                        if (key == "current") "Keep current gym · $label" else label
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            s.gymName,
                            { v -> edit { it.copy(gymName = v) } },
                            Modifier.fillMaxWidth(),
                            label = { Text("Gym name") },
                            singleLine = true,
                        )
                        Text(
                            "You can customize equipment and weights in Settings → Gym Profiles.",
                            color = OF.muted,
                        )
                    }
                    7 -> {
                        SetupTitle("Program")
                        Text(
                            "How would you like to create your program?",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        OutlinedCard(
                            onClick = {
                                vm.finishOnboarding()
                                onManualProgram()
                            },
                            colors = CardDefaults.outlinedCardColors(containerColor = OF.top),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(Icons.Rounded.EditNote, null)
                                Column {
                                    Text(
                                        "Create from scratch",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text("Build your own days and set targets", color = OF.muted)
                                }
                            }
                        }
                        Text(
                            "Smart generation and XLSX import are not available yet.",
                            color = OF.muted,
                        )
                        if (data.programs.isNotEmpty())
                            Text("Your saved programs will stay in your library.", color = OF.muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryAction(
                when (s.step) {
                    0 -> "Get Started"
                    1 -> "Go to Basics"
                    4 -> "Go to Gym & Equipment"
                    6 -> "Go to Program"
                    7 -> "Set up program later"
                    else -> "Next"
                },
                {
                    when (s.step) {
                        3 -> {
                            vm.applyOnboardingBasics()
                            next()
                        }
                        5 -> {
                            vm.applyOnboardingGym()
                            next()
                        }
                        7 -> vm.finishOnboarding()
                        else -> next()
                    }
                },
                Modifier.fillMaxWidth().padding(18.dp),
                enabled =
                    when (s.step) {
                        3 -> s.validBasics()
                        5 -> s.gymName.isNotBlank()
                        else -> true
                    },
            )
        }
    }
    if (exit)
        AlertDialog(
            onDismissRequest = { exit = false },
            title = { Text("Finish setup later?") },
            text = {
                Text("Your progress is saved. Continue setup from Settings whenever you’re ready.")
            },
            confirmButton = {
                TextButton({
                    edit { it.copy(deferred = true) }
                    exit = false
                }) {
                    Text("Set up later")
                }
            },
            dismissButton = { TextButton({ exit = false }) { Text("Continue setup") } },
        )
}

@Composable
private fun SetupTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineLarge,
        fontFamily = workoutDisplayFont(),
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}
