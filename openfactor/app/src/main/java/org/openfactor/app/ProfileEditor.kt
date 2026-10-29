package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal val ProfileSetupDraftSaver = Saver<ProfileSetupDraft, String>(
    save = { it.json().toString() },
    restore = { ProfileSetupDraft.fromJson(JSONObject(it)) },
)

@Composable
internal fun ProfileEditor(data: AppData, vm: WorkoutViewModel, onClose: () -> Unit) {
    var initial by rememberSaveable(stateSaver = ProfileSetupDraftSaver) { mutableStateOf(data.onboarding.profileDraft ?: data.profileSetupDraft()) }
    var draft by rememberSaveable(stateSaver = ProfileSetupDraftSaver) { mutableStateOf(initial) }
    var pendingExit by rememberSaveable { mutableStateOf("") }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val dirty = draft != initial
    fun leave(action: String) {
        focus.clearFocus()
        if (dirty) pendingExit = action
        else if (action == "review") { vm.restartOnboarding(); onClose() } else onClose()
    }
    BackHandler { leave("close") }
    LaunchedEffect(data.scaleWeights) {
        initial = initial.refreshWeight(data.scaleWeights)
        draft = draft.refreshWeight(data.scaleWeights)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading("Profile", { leave("close") }, actions = {
            TextButton({
                focus.clearFocus()
                saveError = vm.saveProfile(draft)
                if (saveError == null) onClose()
            }, enabled = draft.validationError() == null) { Text("Save") }
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp).padding(bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            ProfileBasicsFields(draft) { draft = it; saveError = null }
            HorizontalDivider(color = OF.line)
            ProfileBodyFields(draft) { draft = it; saveError = null }
            HorizontalDivider(color = OF.line)
            ProfileTrainingFields(draft) { draft = it; saveError = null }
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton({ leave("review") }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Review setup") }
        }
    }
    if (pendingExit.isNotEmpty()) ConfirmDialog(
        "Discard profile changes?", "Your saved profile will stay as it is.", "Discard", { pendingExit = "" }, {
            val action = pendingExit
            pendingExit = ""
            if (action == "review") { vm.restartOnboarding(); onClose() } else onClose()
        },
    )
}

@Composable
internal fun ProfileBasicsFields(draft: ProfileSetupDraft, onChange: (ProfileSetupDraft) -> Unit) {
    val focus = LocalFocusManager.current
    val error = draft.nameError()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        OutlinedTextField(
            draft.name, { onChange(draft.copy(name = it)) },
            Modifier.fillMaxWidth().semantics { contentDescription = "Profile name" },
            label = { Text("Name") }, singleLine = true,
            isError = draft.name.isNotEmpty() && error != null,
            supportingText = if (draft.name.isNotEmpty() && error != null) ({ Text(error) }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Gender (optional)", style = MaterialTheme.typography.labelLarge, color = OF.muted)
            ProfileGender.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { gender ->
                        FilterChip(
                            selected = draft.gender == gender,
                            onClick = { focus.clearFocus(); onChange(draft.copy(gender = gender)) },
                            label = { Text(gender.label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .semantics { contentDescription = "Gender ${gender.label}" },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ProfileBodyFields(draft: ProfileSetupDraft, onChange: (ProfileSetupDraft) -> Unit) {
    val focus = LocalFocusManager.current
    val weightError = draft.weightText.isNotBlank() && draft.weightKg() == null
    val heightEntered = draft.heightText.isNotBlank() || (draft.heightInFeet && draft.heightInchesText.isNotBlank())
    val heightError = heightEntered && draft.heightCm() == null
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Kilograms", true to "Pounds").forEachIndexed { index, (pounds, label) ->
                SegmentedButton(draft.pounds == pounds, { focus.clearFocus(); onChange(draft.withPounds(pounds)) },
                    SegmentedButtonDefaults.itemShape(index, 2), enabled = !weightError,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
        OutlinedTextField(
            draft.weightText, { onChange(draft.copy(weightText = it, weightEdited = true)) },
            Modifier.fillMaxWidth().semantics { contentDescription = "Profile weight" },
            label = { Text("Weight (optional)") }, suffix = { Text(if (draft.pounds) "lb" else "kg") },
            isError = weightError, singleLine = true,
            supportingText = if (weightError) ({ Text(draft.bodyError().orEmpty()) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        if (!draft.weightEdited && draft.weightSourceDate != null) {
            Text("Latest reading · ${draft.weightSourceDate}", style = MaterialTheme.typography.labelMedium, color = OF.muted)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Centimeters", true to "Feet and inches").forEachIndexed { index, (feet, label) ->
                SegmentedButton(draft.heightInFeet == feet, { focus.clearFocus(); onChange(draft.withHeightInFeet(feet)) },
                    SegmentedButtonDefaults.itemShape(index, 2), enabled = !heightError,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
        if (draft.heightInFeet) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                draft.heightText, { onChange(draft.copy(heightText = it)) },
                Modifier.weight(1f).semantics { contentDescription = "Profile height feet" },
                label = { Text("Height (optional)") }, suffix = { Text("ft") }, isError = heightError, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                draft.heightInchesText, { onChange(draft.copy(heightInchesText = it)) },
                Modifier.weight(1f).semantics { contentDescription = "Profile height inches" },
                label = { Text("Inches") }, suffix = { Text("in") }, isError = heightError, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
        } else OutlinedTextField(
            draft.heightText, { onChange(draft.copy(heightText = it)) },
            Modifier.fillMaxWidth().semantics { contentDescription = "Profile height" },
            label = { Text("Height (optional)") }, suffix = { Text("cm") }, isError = heightError, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
        if (heightError) Text(if (draft.heightInFeet) "Use whole feet and 0–11.99 inches (30–300 cm total)." else "Enter a height from 30–300 cm.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
