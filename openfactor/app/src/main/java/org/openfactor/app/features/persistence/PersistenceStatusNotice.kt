package org.openfactor.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/** Separate observer: typed edits do not subscribe the app shell to saver status. */
@Composable
internal fun PersistenceStatusNotice(state: StateFlow<PersistenceState>, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val persistence by state.collectAsState()
    when (val status = persistence) {
        PersistenceState.Saved, PersistenceState.Pending -> Unit
        is PersistenceState.Failed -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (status.hasUnsavedChanges) "Your latest edits aren't saved. Free up storage and retry."
                    else "The last change couldn't be saved. Please try that action again.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                TextButton(if (status.hasUnsavedChanges) onRetry else onDismiss) {
                    Text(if (status.hasUnsavedChanges) "Retry save" else "Dismiss")
                }
            }
        }
    }
}
