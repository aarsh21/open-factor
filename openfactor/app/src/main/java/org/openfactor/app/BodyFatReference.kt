package org.openfactor.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Captured labels and asset identities only; no range-to-percentage conversion is inferred. */
private val referenceImages =
    listOf(
        "M-3pct.svg" to "3–4%",
        "M-6pct.svg" to "5–7%",
        "M-10pct.svg" to "8–12%",
        "M-15pct.svg" to "13–17%",
        "M-20pct.svg" to "18–23%",
        "M-25pct.svg" to "24–29%",
        "M-30pct.svg" to "30–34%",
        "M-35pct.svg" to "35–39%",
        "M-40pct.svg" to "40% +",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyFatReferenceSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val illustrated =
        remember(context) {
            val available = context.assets.list("body-fat")?.toSet().orEmpty()
            referenceImages.all { it.first in available }
        }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OF.card,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 760.dp)) {
            Row(
                Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Visual Body Fat reference",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClose) { Icon(Icons.Rounded.Close, "Close reference") }
            }
            Text(
                if (illustrated)
                    "Captured male-reference guide. Enter your own percentage; no value is selected or calculated."
                else
                    "Reference illustrations are not bundled in this build. These are the captured male-reference range labels, not a personal estimate.",
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                color = OF.muted,
                style = MaterialTheme.typography.bodySmall,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(referenceImages, key = { it.first }) { (file, label) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OF.card,
                        border = BorderStroke(1.dp, OF.line),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (illustrated) {
                                var failed by remember(file) { mutableStateOf(false) }
                                AsyncImage(
                                    model = "file:///android_asset/body-fat/$file",
                                    contentDescription = "Visual body fat reference $label",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    contentScale = ContentScale.Fit,
                                    onError = { failed = true },
                                )
                                if (failed)
                                    Text(
                                        "Image unavailable",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                            }
                            Text(
                                label,
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
