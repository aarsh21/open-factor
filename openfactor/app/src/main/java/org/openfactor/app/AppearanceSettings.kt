package org.openfactor.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Light/Dark/System plus the color palette. Every choice applies the moment it is tapped. */
@Composable
internal fun AppearanceSettings(p: Preferences, vm: WorkoutViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val dark = OF.palette.isDark
    Column(Modifier.fillMaxSize().background(OF.background)) {
        PageHeading("Appearance", onClose)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 112.dp)) {
            SettingsSection("MODE") {
                Column(Modifier.selectableGroup()) {
                    AppTheme.entries.forEach { theme ->
                        PreferenceChoice(theme.label, p.theme == theme) { vm.settings { it.copy(theme = theme) } }
                    }
                }
            }
            SettingsSection("COLOR PALETTE") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 16.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppPalette.entries.forEach { palette ->
                        PaletteSwatch(palette, dark, p.palette == palette, Modifier.weight(1f)) {
                            vm.settings { it.copy(palette = palette) }
                        }
                    }
                }
                Text(
                    p.palette.intent,
                    Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp),
                    color = OF.muted,
                )
            }
        }
    }
}

/** A miniature of the app in that palette: page, card, text, a finished set, the action, the bar. */
@Composable
private fun PaletteSwatch(
    palette: AppPalette,
    dark: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val colors = palette.colors(dark)
    val interactions = remember { MutableInteractionSource() }
    val motion = LocalAppMotionEnabled.current
    val ring by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(if (motion) AppMotion.State else 0, easing = AppMotion.EaseOut),
        label = "palette ring",
    )
    val ink = OF.palette.content
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected, interactions, LocalIndication.current, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.widthIn(max = 72.dp).fillMaxWidth().aspectRatio(.64f).pressFeedback(interactions)
                // A 2dp gap between ring and miniature keeps the palette's own edge visible.
                .border(2.dp, ink.copy(alpha = ring), RoundedCornerShape(14.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.background)
                .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
        ) {
            Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.surface).padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.fillMaxWidth(.75f).height(4.dp).background(colors.content, CircleShape))
                    Box(Modifier.fillMaxWidth(.5f).height(3.dp).background(colors.muted, CircleShape))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(11.dp).background(colors.complete, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(8.dp), tint = colors.onComplete)
                        }
                        Box(Modifier.weight(1f).height(3.dp).background(colors.elevated, CircleShape))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(9.dp).background(colors.primary, RoundedCornerShape(3.dp)))
                Box(Modifier.fillMaxWidth().height(10.dp).background(colors.navigation, CircleShape).padding(1.5.dp)) {
                    Box(Modifier.fillMaxWidth(.34f).fillMaxHeight().graphicsLayer { translationX = size.width * .98f }
                        .background(colors.navigationSelected, CircleShape))
                }
            }
        }
        Text(
            palette.label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) ink else OF.muted,
            // Two lines at large text sizes instead of "Midni…".
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
