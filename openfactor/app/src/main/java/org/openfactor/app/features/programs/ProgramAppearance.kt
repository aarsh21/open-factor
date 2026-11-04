package org.openfactor.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val programColors =
    listOf(0xFFFF2F4A, 0xFFFF642B, 0xFFFFB500, 0xFF3C9D6A, 0xFF286CF5, 0xFF965FF8, 0xFF0099AA)

// Public vector equivalents; the private source icon font is not redistributed.
private val programIcons =
    linkedMapOf(
        "rocket" to Icons.Rounded.RocketLaunch,
        "apartment" to Icons.Rounded.Apartment,
        "diamond" to Icons.Rounded.Diamond,
        "castle" to Icons.Rounded.Castle,
        "fort" to Icons.Rounded.Fort,
        "trophy" to Icons.Rounded.EmojiEvents,
        "crown" to Icons.Rounded.WorkspacePremium,
        "cake" to Icons.Rounded.Cake,
        "gift" to Icons.Rounded.CardGiftcard,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "tractor" to Icons.Rounded.Agriculture,
        "fan" to Icons.Rounded.Air,
        "glove" to Icons.Rounded.SportsMma,
        "fitness" to Icons.Rounded.FitnessCenter,
        "spa" to Icons.Rounded.Spa,
        "pig" to Icons.Rounded.Savings,
        "horse" to Icons.Rounded.Sports,
        "globe" to Icons.Rounded.Public,
        "plant" to Icons.Rounded.Yard,
        "shoe" to Icons.Rounded.IceSkating,
        "tree" to Icons.Rounded.Park,
        "panda" to Icons.Rounded.Pets,
        "bike" to Icons.Rounded.DirectionsBike,
        "moon" to Icons.Rounded.DarkMode,
        "flower" to Icons.Rounded.LocalFlorist,
        "shirt" to Icons.Rounded.Checkroom,
        "food" to Icons.Rounded.Restaurant,
        "palette" to Icons.Rounded.Palette,
        "hiking" to Icons.Rounded.Hiking,
        "mask" to Icons.Rounded.TheaterComedy,
        "mountain" to Icons.Rounded.Terrain,
        "sailing" to Icons.Rounded.Sailing,
        "soccer" to Icons.Rounded.SportsSoccer,
        "star" to Icons.Rounded.Star,
        "bolt" to Icons.Rounded.Bolt,
        "flag" to Icons.Rounded.Flag,
    )

fun programIcon(id: String) = programIcons[id] ?: Icons.Rounded.RocketLaunch

@Composable
internal fun ProgramHeader(title: String, onBack: () -> Unit, onSettings: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(56.dp).background(OF.top)) {
        IconButton(onBack, Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Rounded.ChevronLeft, "Back")
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            fontSize = 20.sp,
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (onSettings != null)
            IconButton(onSettings, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.Tune, "Program settings")
            }
    }
}

@Composable
internal fun ProgramColorChoices(selected: Long, icon: String, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        programColors.forEachIndexed { index, color ->
            Surface(
                onClick = { onSelect(color) },
                shape = RoundedCornerShape(10.dp),
                color = Color(color),
                border = if (selected == color) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                modifier =
                    Modifier.size(48.dp).semantics {
                        contentDescription =
                            "${listOf("Red", "Orange", "Amber", "Green", "Blue", "Purple", "Teal")[index]} program color"
                        this.selected = selected == color
                        role = Role.RadioButton
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(programIcon(icon), null, tint = Color.Black.copy(alpha = .6f))
                }
            }
        }
    }
}

@Composable
internal fun ProgramIconChoices(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.selectableGroup()) {
        programIcons.entries.chunked(6).forEach { icons ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                icons.forEach { (id, icon) ->
                    Surface(
                        onClick = { onSelect(id) },
                        modifier =
                            Modifier.size(48.dp).semantics {
                                contentDescription = "$id program icon"
                                this.selected = selected == id
                                role = Role.RadioButton
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        border = if (selected == id) BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface) else null,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
                    }
                }
            }
        }
    }
}
