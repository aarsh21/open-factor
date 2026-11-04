package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProgramSetup(
    program: TrainingProgram,
    vm: WorkoutViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable(program.id) { mutableStateOf(program.name) }
    BackHandler { if (step == 1) step = 0 else onBack() }
    Column(Modifier.fillMaxSize().background(OF.top).imePadding()) {
        ProgramHeader("Create Program", { if (step == 1) step = 0 else onBack() })
        LinearProgressIndicator(
            progress = { if (step == 0) .5f else .85f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = OF.line,
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(
                if (step == 0) "What would you like to name this program?"
                else "What color should we use to display this program?",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(26.dp))
            if (step == 0)
                OutlinedTextField(
                    name,
                    {
                        name = it
                        runPersistenceAction { vm.commands.editProgramDraft { p -> p.copy(name = it) } }
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
            else {
                ProgramColorChoices(program.color, program.icon) { color ->
                    runPersistenceAction { vm.commands.editProgramDraft { it.copy(color = color) } }
                }
                Spacer(Modifier.height(18.dp))
                ProgramIconChoices(program.icon) { icon ->
                    runPersistenceAction { vm.commands.editProgramDraft { it.copy(icon = icon) } }
                }
            }
        }
        PrimaryAction(
            "Next",
            { if (step == 0) step = 1 else onNext() },
            Modifier.padding(18.dp).fillMaxWidth(),
            enabled = name.isNotBlank(),
        )
    }
}
