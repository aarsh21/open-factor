package org.openfactor.app

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

object OF {
    val background: Color @Composable get() = MaterialTheme.colorScheme.background
    val top: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val card: Color @Composable get() = MaterialTheme.colorScheme.surface
    val elevated: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    /** Every palette role, including the navigation and Settings materials. */
    val palette: PaletteColors @Composable get() = LocalPalette.current
    val line: Color @Composable get() = LocalPalette.current.line
    val complete: Color @Composable get() = LocalPalette.current.complete
    val onComplete: Color @Composable get() = LocalPalette.current.onComplete
    val accent: Color @Composable get() = LocalPalette.current.accent
    // Original names, kept so existing callers follow the palette as well.
    val blue: Color @Composable get() = accent
    val green: Color @Composable get() = complete
    val shape = RoundedCornerShape(16.dp)
}

@Composable
fun OpenFactorTheme(
    theme: AppTheme = AppTheme.DARK,
    palette: AppPalette = AppPalette.CHARCOAL,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val motionEnabled = rememberAppMotionEnabled()
    val configuration = LocalConfiguration.current
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val appConfiguration = remember(configuration, dark) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    val body = remember { dmSans(context, 400) }
    val medium = remember {
        dmSans(context, 500)
    }
    val bold = remember { dmSans(context, 700) }
    val colors = rememberCrossfadedPalette(palette.colors(dark), motionEnabled)
    val colorScheme = remember(colors) { colors.colorScheme() }

    MaterialTheme(
        colorScheme = colorScheme,
        typography =
            Typography().let { t ->
                t.copy(
                    displaySmall =
                        t.displaySmall.copy(fontFamily = bold, fontWeight = FontWeight.Bold),
                    headlineLarge =
                        t.headlineLarge.copy(fontFamily = bold, fontWeight = FontWeight.Bold),
                    headlineMedium =
                        t.headlineMedium.copy(fontFamily = bold, fontWeight = FontWeight.Bold),
                    titleLarge =
                        t.titleLarge.copy(fontFamily = bold, fontWeight = FontWeight.SemiBold),
                    titleMedium = t.titleMedium.copy(fontFamily = medium),
                    bodyLarge = t.bodyLarge.copy(fontFamily = body),
                    bodyMedium = t.bodyMedium.copy(fontFamily = body),
                    labelLarge = t.labelLarge.copy(fontFamily = medium),
                )
            },
        content = {
            CompositionLocalProvider(LocalConfiguration provides appConfiguration,
                LocalAppMotionEnabled provides motionEnabled, LocalPalette provides colors, content = content)
        },
    )
}

@Composable
fun WorkoutNavigation(
    selected: Int,
    scrollEnergy: () -> Float,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val labels = listOf("Dashboard", "Workout", "Exercises", "Settings")
    val motion = LocalAppMotionEnabled.current
    val colors = OF.palette
    val textMeasurer = rememberTextMeasurer()
    val icons =
        listOf(
            Icons.Rounded.Dashboard,
            Icons.Rounded.FitnessCenter,
            Icons.Rounded.Search,
            Icons.Rounded.AccountCircle,
        )
    // Scroll energy changes every scroll frame. Follow it outside composition and read the drift
    // only in graphicsLayer, so scrolling animates the bar in the draw phase without recomposing.
    val scrollDrift = remember { Animatable(0f) }
    val energy by rememberUpdatedState(scrollEnergy)
    LaunchedEffect(motion) {
        snapshotFlow { if (motion) energy() else 0f }.collectLatest { target ->
            // A critically damped spring keeps its velocity as the target moves every scroll frame.
            if (motion) scrollDrift.animateTo(target, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            else scrollDrift.snapTo(target)
        }
    }
    Box(
        modifier.widthIn(max = 480.dp).fillMaxWidth()
            .padding(horizontal = 12.dp).padding(bottom = 14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(66.dp).selectableGroup()
        ) {
            val slot = (maxWidth - 8.dp) / 5
            val offset by
                animateDpAsState(
                    4.dp + slot * (if (selected < 2) selected else selected + 1),
                    tween(if (motion) AppMotion.Step else 0, easing = AppMotion.EaseOut),
                    label = "navigation pill",
                )
            // Only the material responds to scroll. The four touch targets stay at their fixed
            // anchor.
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val drift = scrollDrift.value
                        translationY = drift * 2.dp.toPx()
                        scaleX = 1f - kotlin.math.abs(drift) * .008f
                        shadowElevation = (6.dp.toPx() + kotlin.math.abs(drift) * 3.dp.toPx())
                        shape = RoundedCornerShape(50)
                        clip = true
                    }
                    .background(colors.navigation)
                    .border(
                        1.dp,
                        colors.navigationRim,
                        RoundedCornerShape(50),
                    )
            ) {
                Box(
                    // Layout-phase read: the 300ms pill slide does not recompose the bar per frame.
                    Modifier.offset { IntOffset(offset.roundToPx(), 4.dp.roundToPx()) }
                        .width(slot)
                        .height(58.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.navigationSelected)
                )
            }
            Row(Modifier.fillMaxSize().padding(4.dp)) {
                labels.forEachIndexed { i, label ->
                    if (i == 2)
                        Box(
                            Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val interactions = remember { MutableInteractionSource() }
                            FilledIconButton(
                                onAdd,
                                Modifier.size(48.dp).pressFeedback(interactions),
                                interactionSource = interactions,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = colors.navigationAction,
                                    contentColor = colors.onNavigationAction,
                                ),
                            ) {
                                Icon(Icons.Rounded.Add, "Quick actions", Modifier.size(28.dp))
                            }
                        }
                    val iconScale by
                        animateFloatAsState(
                            if (motion && i == selected) 1.04f else 1f,
                            tween(if (motion) AppMotion.Quick else 0),
                            label = "navigation icon",
                        )
                    val labelStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily =
                            if (i == selected) MaterialTheme.typography.headlineMedium.fontFamily
                            else MaterialTheme.typography.bodyLarge.fontFamily,
                    )
                    val labelWidth = textMeasurer.measure(label, labelStyle, softWrap = false).size.width
                    val labelScale = minOf(1f, with(LocalDensity.current) { (slot - 16.dp).toPx() } / labelWidth)
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .selectable(selected == i, role = Role.Tab, onClick = { onSelect(i) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icons[i],
                            null,
                            Modifier.size(26.dp).graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                            tint = if (i == selected) colors.navigationSelectedContent else colors.navigationIcon,
                        )
                        Text(
                            label,
                            style = labelStyle.copy(fontSize = 12.sp * labelScale, lineHeight = 16.sp * labelScale),
                            color = if (i == selected) colors.navigationSelectedContent else colors.navigationLabel,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    IconButton(onClick, Modifier.size(48.dp).pressFeedback(interactions).clip(RoundedCornerShape(50)).background(OF.elevated),
        interactionSource = interactions) {
        Icon(icon, description, Modifier.size(24.dp))
    }
}

private val dmSansFamilies = java.util.concurrent.ConcurrentHashMap<Int, FontFamily>()

/**
 * One parsed DM Sans instance per weight per process. Building a [Typeface] reads the 240 KB
 * variable font, and a new family instance also misses Compose's typeface cache.
 */
internal fun dmSans(context: android.content.Context, weight: Int): FontFamily = dmSansFamilies.getOrPut(weight) {
    runCatching {
            FontFamily(
                Typeface.Builder(context.applicationContext.assets, "fonts/DM-Sans.ttf")
                    .setWeight(weight)
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            )
        }
        .getOrDefault(FontFamily.SansSerif)
}

@Composable
fun workoutDisplayFont(): FontFamily {
    val context = LocalContext.current
    return remember { dmSans(context, 700) }
}

@Composable
fun PageHeading(
    title: String,
    back: (() -> Unit)? = null,
    displayTitle: Boolean = back == null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val display = workoutDisplayFont()
    Row(
        Modifier.fillMaxWidth().background(OF.top).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (back != null) IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(
            title,
            Modifier.weight(1f),
            fontFamily =
                if (displayTitle) display else MaterialTheme.typography.titleLarge.fontFamily,
            fontSize = if (displayTitle) 25.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (action != null) TextButton(onAction) { Text(action, color = OF.muted) }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    Button(
        onClick,
        modifier.fillMaxWidth().heightIn(min = 52.dp).pressFeedback(interactions, enabled),
        interactionSource = interactions,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors =
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun EmptyContent(
    icon: ImageVector,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = OF.muted)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            color = OF.muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) TextButton(onAction) { Text(action) }
    }
}

/** Compact previews fill their rounded frame; full demonstration media keeps its natural aspect. */
@Composable
fun ExerciseImage(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    frame: Int = 0,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
) {
    val path = exercise.frames.getOrNull(frame.coerceIn(0, exercise.frames.lastIndex.coerceAtLeast(0)))
    var failed by remember(path) { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(
            if (path?.startsWith("previews/") == true && !failed) Color.White else OF.elevated
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) AsyncImage(
            model = "file:///android_asset/$path",
            contentDescription = exercise.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onSuccess = { failed = false },
            onError = { failed = true },
        )
        if (path == null || failed)
            Icon(Icons.Rounded.FitnessCenter, "Exercise image unavailable", Modifier.size(28.dp), tint = OF.muted)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton({ onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MuscleImage(muscle: String, modifier: Modifier = Modifier, figure: String = "male") {
    val path = muscle.lowercase().replace(' ', '_')
    val context = LocalContext.current
    val exists =
        remember(path, figure) {
            runCatching {
                    context.assets.open("reference/muscle_groups/$figure/$path.svg").close()
                    true
                }
                .getOrDefault(false)
        }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(OF.elevated),
        contentAlignment = Alignment.Center,
    ) {
        if (exists) {
            val themed =
                remember(path, figure) {
                    java.nio.ByteBuffer.wrap(
                        context.assets
                            .open("reference/muscle_groups/$figure/$path.svg")
                            .bufferedReader()
                            .use { it.readText() }
                            .replace("#EEEEEE", "#555555")
                            .replace("#DCDCDC", "#3A3A3A")
                            .toByteArray()
                    )
                }
            AsyncImage(themed, muscle, Modifier.fillMaxSize())
        } else Icon(Icons.Rounded.AccessibilityNew, null, Modifier.size(40.dp), tint = OF.muted)
    }
}
