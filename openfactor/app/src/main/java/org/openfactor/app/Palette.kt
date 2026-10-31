package org.openfactor.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** Curated color systems. Each defines every role in a dark and a light variant. */
enum class AppPalette(val label: String, val intent: String) {
    CHARCOAL("Charcoal", "Neutral graphite with white ink. The original Open Factor look."),
    MIDNIGHT("Midnight", "Blue-black surfaces with a cool periwinkle action color."),
    EMBER("Ember", "Warm graphite. Amber marks actions and finished sets."),
    MOSS("Moss", "Green-grey neutrals with a quiet sage action color."),
    MONO("Mono", "Pure black and white at the highest contrast."),
}

/**
 * Every color the app paints with. Screens read these roles (through [OF] or the Material color
 * scheme built from them), never hex values, so switching palettes re-skins the whole app.
 */
@Immutable
data class PaletteColors(
    val background: Color,
    /** Cards, headers, sheets. */
    val surface: Color,
    /** Raised controls on a surface: wells, chips, tracks, incomplete set buttons. */
    val elevated: Color,
    val content: Color,
    val muted: Color,
    /** Dividers and chart keylines. */
    val line: Color,
    /** Control boundaries such as text field outlines. */
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Finished sets and positive states. Always paired with a check or a label. */
    val complete: Color,
    val onComplete: Color,
    val error: Color,
    val onError: Color,
    /** Charts and training data on this variant's surfaces. */
    val accent: Color,
    val inverseSurface: Color,
    val inverseContent: Color,
    val warning: Color,
    val onWarning: Color,
    val settingsBackground: Color,
    val settingsGroup: Color,
    val settingsBorder: Color,
    val settingsDivider: Color,
    val settingsIcon: Color,
    val settingsMuted: Color,
    /** The floating navigation material. It stays dark in light mode in every palette. */
    val navigation: Color,
    val navigationRim: Color,
    val navigationSelected: Color,
    val navigationSelectedContent: Color,
    val navigationIcon: Color,
    val navigationLabel: Color,
    val navigationAction: Color,
    val onNavigationAction: Color,
    /**
     * Anatomy heat ramp, strongest to softest. The muscle figures are dark artwork in both modes,
     * so the ramp is shared by both variants and tuned against the figure, not the page.
     */
    val highlight: Color,
    val highlightMid: Color,
    val highlightSoft: Color,
) {
    val isDark: Boolean get() = background.luminance() < .5f

    fun colorScheme(): ColorScheme =
        if (isDark) darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = elevated, onPrimaryContainer = content,
            secondary = accent,
            secondaryContainer = primary, onSecondaryContainer = onPrimary,
            background = background, onBackground = content,
            surface = surface, onSurface = content,
            surfaceVariant = elevated, onSurfaceVariant = muted,
            outline = outline, outlineVariant = outlineVariant,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = background, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surface,
            surfaceContainerHighest = elevated,
            error = error, onError = onError,
            // Snackbars (e.g. "Swapped for … · Undo") stay in the palette, not Material lavender.
            inverseSurface = inverseSurface, inverseOnSurface = inverseContent,
            inversePrimary = inverseContent,
        ) else lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = elevated, onPrimaryContainer = content,
            secondary = accent,
            secondaryContainer = primary, onSecondaryContainer = onPrimary,
            background = background, onBackground = content,
            surface = surface, onSurface = content,
            surfaceVariant = elevated, onSurfaceVariant = muted,
            outline = outline, outlineVariant = outlineVariant,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = background, surfaceContainerLow = surface,
            surfaceContainer = surface, surfaceContainerHigh = surface,
            surfaceContainerHighest = elevated,
            error = error, onError = onError,
            inverseSurface = inverseSurface, inverseOnSurface = inverseContent,
            inversePrimary = inverseContent,
        )
}

/** `#RRGGBB` for recoloring bundled SVG artwork with palette roles. */
internal fun Color.svgHex(): String = "#%06X".format(java.util.Locale.ROOT, toArgb() and 0xFFFFFF)

internal val LocalPalette = staticCompositionLocalOf { AppPalette.CHARCOAL.colors(dark = true) }

private fun c(argb: Long) = Color(argb)

/**
 * Roles one palette shares across both variants: the navigation material (it stays dark in light
 * mode) and the anatomy ramp (the figures stay dark artwork).
 */
private data class Shared(
    val material: Long, val rim: Long, val selected: Long, val selectedContent: Long,
    val icon: Long, val label: Long, val action: Long, val onAction: Long,
    val highlight: Long, val highlightMid: Long, val highlightSoft: Long,
)

private fun tokens(
    nav: Shared,
    background: Long, surface: Long, elevated: Long, content: Long, muted: Long,
    line: Long, outline: Long, outlineVariant: Long = line,
    primary: Long, onPrimary: Long, complete: Long, onComplete: Long, error: Long, onError: Long,
    accent: Long,
    inverseSurface: Long, inverseContent: Long, warning: Long, onWarning: Long,
    settingsBackground: Long = background, settingsGroup: Long = surface, settingsBorder: Long = elevated,
    settingsDivider: Long = elevated, settingsIcon: Long = elevated, settingsMuted: Long = muted,
) = PaletteColors(
    c(background), c(surface), c(elevated), c(content), c(muted), c(line), c(outline), c(outlineVariant),
    c(primary), c(onPrimary), c(complete), c(onComplete), c(error), c(onError),
    c(accent), c(inverseSurface), c(inverseContent), c(warning), c(onWarning),
    c(settingsBackground), c(settingsGroup), c(settingsBorder), c(settingsDivider), c(settingsIcon), c(settingsMuted),
    c(nav.material), c(nav.rim), c(nav.selected), c(nav.selectedContent), c(nav.icon), c(nav.label),
    c(nav.action), c(nav.onAction), c(nav.highlight), c(nav.highlightMid), c(nav.highlightSoft),
)

private val charcoalNav = Shared(0xFF1C1C1E, 0xFF414143, 0xFF393939, 0xFFFFFFFF, 0xFF92969C, 0xFFA1A4AA, 0xFFFFFFFF, 0xFF000000,
    highlight = 0xFF3982FF, highlightMid = 0xFF79ACFF, highlightSoft = 0xFFB9D3FF)
private val midnightNav = Shared(0xFF121725, 0xFF2D3549, 0xFF283149, 0xFFFFFFFF, 0xFF8792A8, 0xFF9BA5BA, 0xFF9DB8FF, 0xFF0A1226,
    highlight = 0xFF6E97FF, highlightMid = 0xFF9AB7FF, highlightSoft = 0xFFC6D6FF)
private val emberNav = Shared(0xFF1E1B17, 0xFF453E36, 0xFF3B352E, 0xFFFFFFFF, 0xFF9C9488, 0xFFABA397, 0xFFF5A54A, 0xFF1C1206,
    highlight = 0xFFF29A3D, highlightMid = 0xFFF7BC7A, highlightSoft = 0xFFFAD9B2)
private val mossNav = Shared(0xFF161D19, 0xFF35413A, 0xFF2C3830, 0xFFFFFFFF, 0xFF8C9A90, 0xFF9DAAA0, 0xFFCFE6C4, 0xFF0F1A10,
    highlight = 0xFF4DBB93, highlightMid = 0xFF93D6BC, highlightSoft = 0xFFC7EBDC)
private val monoNav = Shared(0xFF121212, 0xFF3A3A3A, 0xFFFFFFFF, 0xFF000000, 0xFFA6A6A6, 0xFFB8B8B8, 0xFFFFFFFF, 0xFF000000,
    highlight = 0xFFFFFFFF, highlightMid = 0xFFD0D0D0, highlightSoft = 0xFF9E9E9E)

private val palettes: Map<Pair<AppPalette, Boolean>, PaletteColors> = mapOf(
    // Charcoal dark is the shipped look, value for value. outlineVariant is Material's baseline,
    // which the weekly card divider has always drawn with.
    (AppPalette.CHARCOAL to true) to tokens(charcoalNav,
        background = 0xFF131313, surface = 0xFF202020, elevated = 0xFF353535, content = 0xFFFFFFFF, muted = 0xFFB8B8BC,
        line = 0xFF414141, outline = 0xFF414141, outlineVariant = 0xFF49454F,
        primary = 0xFFFFFFFF, onPrimary = 0xFF000000, complete = 0xFF91D8AD, onComplete = 0xFF000000,
        error = 0xFFFF9696, onError = 0xFF131313, accent = 0xFF3982FF,
        inverseSurface = 0xFF353535, inverseContent = 0xFFFFFFFF, warning = 0xFF443500, onWarning = 0xFFFFFFFF,
        settingsBackground = 0xFF0D0D0D, settingsGroup = 0xFF1A1A1A, settingsBorder = 0xFF272727,
        settingsDivider = 0xFF252525, settingsIcon = 0xFF343434, settingsMuted = 0xFF96999F),
    (AppPalette.CHARCOAL to false) to tokens(charcoalNav,
        background = 0xFFF5F5F5, surface = 0xFFFFFFFF, elevated = 0xFFE5E5E7, content = 0xFF202020, muted = 0xFF55555B,
        line = 0xFFDCDCE0, outline = 0xFF8A8A90,
        primary = 0xFF202020, onPrimary = 0xFFFFFFFF, complete = 0xFF18785A, onComplete = 0xFFFFFFFF,
        error = 0xFFB3261E, onError = 0xFFFFFFFF, accent = 0xFF2A6FE8,
        inverseSurface = 0xFF202020, inverseContent = 0xFFFFFFFF, warning = 0xFFFFF1C2, onWarning = 0xFF3D2E00),
    (AppPalette.MIDNIGHT to true) to tokens(midnightNav,
        background = 0xFF0A0D14, surface = 0xFF141925, elevated = 0xFF232A3A, content = 0xFFF1F4FA, muted = 0xFFA3ADC2,
        line = 0xFF2B3346, outline = 0xFF3A4459,
        primary = 0xFF9DB8FF, onPrimary = 0xFF0A1226, complete = 0xFF72D7D0, onComplete = 0xFF04201C,
        error = 0xFFF59273, onError = 0xFF2A0E06, accent = 0xFF6E97FF,
        inverseSurface = 0xFF2A3244, inverseContent = 0xFFF1F4FA, warning = 0xFF3D3218, onWarning = 0xFFF7EBCB,
        settingsBackground = 0xFF070A10, settingsGroup = 0xFF111622, settingsBorder = 0xFF1D2433,
        settingsDivider = 0xFF1B2130, settingsIcon = 0xFF252D3F, settingsMuted = 0xFF8E99AF),
    (AppPalette.MIDNIGHT to false) to tokens(midnightNav,
        background = 0xFFEEF1F7, surface = 0xFFFFFFFF, elevated = 0xFFE0E5EF, content = 0xFF141A27, muted = 0xFF4C566C,
        line = 0xFFD5DBE7, outline = 0xFF7F899E,
        primary = 0xFF3451B2, onPrimary = 0xFFFFFFFF, complete = 0xFF0B7366, onComplete = 0xFFFFFFFF,
        error = 0xFFB93A12, onError = 0xFFFFFFFF, accent = 0xFF3366E6,
        inverseSurface = 0xFF1C2333, inverseContent = 0xFFF1F4FA, warning = 0xFFFBEFC9, onWarning = 0xFF3B2E05),
    (AppPalette.EMBER to true) to tokens(emberNav,
        background = 0xFF14120F, surface = 0xFF211E1A, elevated = 0xFF37322C, content = 0xFFF8F3EC, muted = 0xFFBBB1A4,
        line = 0xFF433D35, outline = 0xFF4E473E,
        primary = 0xFFF5A54A, onPrimary = 0xFF1C1206, complete = 0xFFFFC46B, onComplete = 0xFF1F1404,
        error = 0xFFFF96B4, onError = 0xFF2B0812, accent = 0xFFF29A3D,
        inverseSurface = 0xFF3A342D, inverseContent = 0xFFF8F3EC, warning = 0xFF45360F, onWarning = 0xFFFBEFD0,
        settingsBackground = 0xFF0E0C0A, settingsGroup = 0xFF1B1916, settingsBorder = 0xFF2A2621,
        settingsDivider = 0xFF28241F, settingsIcon = 0xFF37322C, settingsMuted = 0xFFA39A8E),
    (AppPalette.EMBER to false) to tokens(emberNav,
        background = 0xFFF6F2EC, surface = 0xFFFFFDFA, elevated = 0xFFECE5DA, content = 0xFF241E17, muted = 0xFF62574A,
        line = 0xFFE2D9CC, outline = 0xFF968A7B,
        primary = 0xFFB0500C, onPrimary = 0xFFFFFFFF, complete = 0xFF9A6200, onComplete = 0xFFFFFFFF,
        error = 0xFFA3165E, onError = 0xFFFFFFFF, accent = 0xFFC2600F,
        inverseSurface = 0xFF2B241D, inverseContent = 0xFFF8F3EC, warning = 0xFFFBEAC4, onWarning = 0xFF3D2C05),
    (AppPalette.MOSS to true) to tokens(mossNav,
        background = 0xFF0E1210, surface = 0xFF18201B, elevated = 0xFF29332D, content = 0xFFEEF4EF, muted = 0xFFA4B2A8,
        line = 0xFF323D36, outline = 0xFF3F4B43,
        primary = 0xFFCFE6C4, onPrimary = 0xFF0F1A10, complete = 0xFF87DFC1, onComplete = 0xFF06201A,
        error = 0xFFF7866A, onError = 0xFF2A0C05, accent = 0xFF6CC7A4,
        inverseSurface = 0xFF2D3831, inverseContent = 0xFFEEF4EF, warning = 0xFF3E3714, onWarning = 0xFFF5EECB,
        settingsBackground = 0xFF0A0D0B, settingsGroup = 0xFF151C17, settingsBorder = 0xFF212A24,
        settingsDivider = 0xFF1F2822, settingsIcon = 0xFF2A342E, settingsMuted = 0xFF90A095),
    (AppPalette.MOSS to false) to tokens(mossNav,
        background = 0xFFF0F3EF, surface = 0xFFFFFFFF, elevated = 0xFFE2E8E1, content = 0xFF16201A, muted = 0xFF4E5C52,
        line = 0xFFD6DED5, outline = 0xFF7F8C82,
        primary = 0xFF2F5A3A, onPrimary = 0xFFFFFFFF, complete = 0xFF1F7A5E, onComplete = 0xFFFFFFFF,
        error = 0xFF992317, onError = 0xFFFFFFFF, accent = 0xFF1F7F65,
        inverseSurface = 0xFF1E2922, inverseContent = 0xFFEEF4EF, warning = 0xFFF6EDC6, onWarning = 0xFF3A3005),
    (AppPalette.MONO to true) to tokens(monoNav,
        background = 0xFF000000, surface = 0xFF121212, elevated = 0xFF262626, content = 0xFFFFFFFF, muted = 0xFFC4C4C4,
        line = 0xFF333333, outline = 0xFF5C5C5C,
        primary = 0xFFFFFFFF, onPrimary = 0xFF000000, complete = 0xFFFFFFFF, onComplete = 0xFF000000,
        error = 0xFFFF8A80, onError = 0xFF000000, accent = 0xFFFFFFFF,
        inverseSurface = 0xFFFFFFFF, inverseContent = 0xFF000000, warning = 0xFF3A3000, onWarning = 0xFFFFFFFF,
        settingsBackground = 0xFF000000, settingsGroup = 0xFF121212, settingsBorder = 0xFF2A2A2A,
        settingsDivider = 0xFF262626, settingsIcon = 0xFF2A2A2A, settingsMuted = 0xFFB0B0B0),
    (AppPalette.MONO to false) to tokens(monoNav,
        background = 0xFFFAFAF8, surface = 0xFFFFFFFF, elevated = 0xFFEDEDEA, content = 0xFF0A0A0A, muted = 0xFF454545,
        line = 0xFFDADAD6, outline = 0xFF6B6B6B,
        primary = 0xFF0A0A0A, onPrimary = 0xFFFFFFFF, complete = 0xFF0A0A0A, onComplete = 0xFFFFFFFF,
        error = 0xFFB00020, onError = 0xFFFFFFFF, accent = 0xFF0A0A0A,
        inverseSurface = 0xFF0A0A0A, inverseContent = 0xFFFFFFFF, warning = 0xFFFFF0B8, onWarning = 0xFF2E2400,
        settingsBorder = 0xFFE2E2DE),
)

fun AppPalette.colors(dark: Boolean): PaletteColors = palettes.getValue(this to dark)

internal fun lerp(from: PaletteColors, to: PaletteColors, t: Float): PaletteColors {
    fun Color.mix(other: Color) = lerp(this, other, t)
    return PaletteColors(
        from.background.mix(to.background), from.surface.mix(to.surface), from.elevated.mix(to.elevated),
        from.content.mix(to.content), from.muted.mix(to.muted), from.line.mix(to.line), from.outline.mix(to.outline),
        from.outlineVariant.mix(to.outlineVariant), from.primary.mix(to.primary), from.onPrimary.mix(to.onPrimary),
        from.complete.mix(to.complete), from.onComplete.mix(to.onComplete), from.error.mix(to.error),
        from.onError.mix(to.onError), from.accent.mix(to.accent), from.inverseSurface.mix(to.inverseSurface),
        from.inverseContent.mix(to.inverseContent), from.warning.mix(to.warning), from.onWarning.mix(to.onWarning),
        from.settingsBackground.mix(to.settingsBackground), from.settingsGroup.mix(to.settingsGroup),
        from.settingsBorder.mix(to.settingsBorder), from.settingsDivider.mix(to.settingsDivider),
        from.settingsIcon.mix(to.settingsIcon), from.settingsMuted.mix(to.settingsMuted),
        from.navigation.mix(to.navigation), from.navigationRim.mix(to.navigationRim),
        from.navigationSelected.mix(to.navigationSelected),
        from.navigationSelectedContent.mix(to.navigationSelectedContent),
        from.navigationIcon.mix(to.navigationIcon), from.navigationLabel.mix(to.navigationLabel),
        from.navigationAction.mix(to.navigationAction), from.onNavigationAction.mix(to.onNavigationAction),
        from.highlight.mix(to.highlight), from.highlightMid.mix(to.highlightMid), from.highlightSoft.mix(to.highlightSoft),
    )
}

/**
 * Palette and light/dark changes are rare and deliberate: every role cross-fades together over
 * [AppMotion.Palette] in perceptual (Oklab) space, starting from whatever is on screen if a change
 * interrupts another. Settled frames show the exact target values. Without motion it is a swap.
 */
@Composable
internal fun rememberCrossfadedPalette(target: PaletteColors, motion: Boolean): PaletteColors {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = if (!motion || progress.value >= 1f) to else lerp(from, to, progress.value)
        to = target
        if (!motion) return@LaunchedEffect progress.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(AppMotion.Palette, easing = AppMotion.EaseOut))
    }
    if (!motion) return target
    val t = progress.value
    return if (t >= 1f) to else lerp(from, to, t)
}
