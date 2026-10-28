package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The first workout needs a unit choice, not a training-profile interview. */
@Composable
fun QuickStartScreen(data: AppData, vm: WorkoutViewModel, onReady: () -> Unit) {
    var pounds by rememberSaveable { mutableStateOf(data.preferences.pounds) }
    Surface(color = OF.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Rounded.FitnessCenter, null, Modifier.size(56.dp))
        Text("Open Factor", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Weight unit", style = MaterialTheme.typography.titleLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Kilograms", true to "Pounds").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = pounds == value,
                    onClick = { pounds = value },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction("Start training", {
            vm.settings { it.copy(pounds = pounds, showRir = false, exerciseAutoNext = false) }
            vm.finishOnboarding()
            onReady()
        })
        Text("Saved on your device", Modifier.align(Alignment.CenterHorizontally), color = OF.muted)
        Spacer(Modifier.height(16.dp))
    }
    }
}
