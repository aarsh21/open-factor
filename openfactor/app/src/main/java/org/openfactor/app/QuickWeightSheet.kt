package org.openfactor.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A quick weigh-in preserves the tab, scroll position and active workout underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickWeightSheet(pounds: Boolean, onClose: () -> Unit, onSave: (ScaleWeightRecord) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.background,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            ScaleWeightEditor(null, pounds, onClose, onSave, {})
        }
    }
}
