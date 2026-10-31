package org.openfactor.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every palette × variant × foreground/background pair the app actually paints, measured with
 * WCAG 2 relative luminance. Text needs 4.5:1; icons, chart marks and large glyphs need 3:1.
 */
class PaletteContrastTest {
    private val text = 4.5
    private val graphic = 3.0

    private fun pairs(p: PaletteColors) = listOf(
        Triple("content on background", p.content to p.background, text),
        Triple("content on surface", p.content to p.surface, text),
        Triple("content on elevated", p.content to p.elevated, text),
        Triple("muted on background", p.muted to p.background, text),
        Triple("muted on surface", p.muted to p.surface, text),
        Triple("muted on elevated", p.muted to p.elevated, text),
        Triple("primary text/icon on background", p.primary to p.background, text),
        Triple("primary text/icon on surface", p.primary to p.surface, text),
        Triple("onPrimary on primary", p.onPrimary to p.primary, text),
        Triple("complete label on background", p.complete to p.background, text),
        Triple("complete label on surface", p.complete to p.surface, text),
        Triple("check on complete", p.onComplete to p.complete, text),
        Triple("error text on background", p.error to p.background, text),
        Triple("error text on surface", p.error to p.surface, text),
        Triple("error text on elevated", p.error to p.elevated, text),
        Triple("onError on error", p.onError to p.error, text),
        Triple("accent marks on background", p.accent to p.background, graphic),
        Triple("accent marks on surface", p.accent to p.surface, graphic),
        Triple("snackbar text", p.inverseContent to p.inverseSurface, text),
        Triple("warning text", p.onWarning to p.warning, text),
        Triple("Settings title on page", p.content to p.settingsBackground, text),
        Triple("Settings section label", p.settingsMuted to p.settingsBackground, text),
        Triple("Settings row on group", p.content to p.settingsGroup, text),
        Triple("Settings chevron on group", p.muted to p.settingsGroup, text),
        Triple("Settings icon in well", p.content to p.settingsIcon, graphic),
        Triple("navigation selected label", p.navigationSelectedContent to p.navigationSelected, text),
        Triple("navigation label", p.navigationLabel to p.navigation, text),
        Triple("navigation icon", p.navigationIcon to p.navigation, graphic),
        Triple("navigation plus", p.onNavigationAction to p.navigationAction, graphic),
    )

    /** The muscle figures' untouched base tone, in both modes. */
    private val figure = Color(0xFF555555)

    @Test
    fun everyTextAndIconRolePassesWcagAaInEveryPaletteAndVariant() {
        val failures = AppPalette.entries.flatMap { palette ->
            listOf(true, false).flatMap { dark ->
                pairs(palette.colors(dark)).mapNotNull { (name, colors, minimum) ->
                    val ratio = contrast(colors.first, colors.second)
                    if (ratio < minimum) "${palette.label} ${if (dark) "dark" else "light"}: $name ${"%.2f".format(ratio)} < $minimum" else null
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun anatomyRampStepsStayDistinctOnTheFigure() {
        AppPalette.entries.forEach { palette ->
            val p = palette.colors(dark = true)
            val steps = listOf(figure, p.highlightSoft, p.highlightMid, p.highlight)
            steps.zipWithNext().forEach { (a, b) ->
                assertTrue("${palette.label} ramp step ${a.hex()}→${b.hex()} is too close", deltaE(a, b) >= 8)
            }
        }
    }

    /**
     * Completion and error are told apart by more than hue: a check glyph or a written message
     * always comes with them. Their colors still separate under simulated deuteranopia and
     * protanopia (Machado 2009, severity 1, OKLab ΔE×100 ≥ 8). Charcoal dark is the shipped look
     * and keeps its values; it relies on that secondary encoding (ΔE ≈ 4 under deuteranopia).
     */
    @Test
    fun completeAndErrorSurviveColorVisionDeficiency() {
        AppPalette.entries.forEach { palette ->
            listOf(true, false).forEach { dark ->
                if (palette == AppPalette.CHARCOAL && dark) return@forEach
                val p = palette.colors(dark)
                val name = "${palette.label} ${if (dark) "dark" else "light"}"
                assertTrue("$name normal vision", deltaE(p.complete, p.error) >= 15)
                assertTrue("$name deuteranopia ${deltaE(p.complete, p.error, deuteranopia)}", deltaE(p.complete, p.error, deuteranopia) >= 8)
                assertTrue("$name protanopia ${deltaE(p.complete, p.error, protanopia)}", deltaE(p.complete, p.error, protanopia) >= 8)
            }
        }
    }

    @Test
    fun charcoalDarkIsTheShippedLookValueForValue() {
        val p = AppPalette.CHARCOAL.colors(dark = true)
        assertEquals(
            listOf(0xFF131313, 0xFF202020, 0xFF353535, 0xFFFFFFFF, 0xFFB8B8BC, 0xFF414141, 0xFFFFFFFF, 0xFF000000,
                0xFF91D8AD, 0xFFFF9696, 0xFF3982FF, 0xFF1C1C1E, 0xFF414143, 0xFF393939, 0xFF92969C, 0xFFA1A4AA,
                0xFF0D0D0D, 0xFF1A1A1A, 0xFF272727, 0xFF252525, 0xFF343434, 0xFF96999F, 0xFF49454F).map { it.toInt() },
            listOf(p.background, p.surface, p.elevated, p.content, p.muted, p.line, p.primary, p.onPrimary,
                p.complete, p.error, p.accent, p.navigation, p.navigationRim, p.navigationSelected, p.navigationIcon,
                p.navigationLabel, p.settingsBackground, p.settingsGroup, p.settingsBorder, p.settingsDivider,
                p.settingsIcon, p.settingsMuted, p.outlineVariant).map { it.toArgb() },
        )
        assertEquals("#3982FF", muscleWorkloadColor(12.0))
        assertEquals("#79ACFF", muscleWorkloadColor(6.0))
        assertEquals("#B9D3FF", muscleWorkloadColor(1.0))
    }

    @Test
    fun everyVariantDeclaresItsOwnTone() {
        AppPalette.entries.forEach { palette ->
            assertTrue(palette.colors(dark = true).isDark)
            assertTrue(!palette.colors(dark = false).isDark)
        }
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

    private fun channel(value: Float): Double =
        if (value <= 0.04045f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun linear(c: Color) = doubleArrayOf(channel(c.red), channel(c.green), channel(c.blue))

    private fun luminance(c: Color) = linear(c).let { .2126 * it[0] + .7152 * it[1] + .0722 * it[2] }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + .05) / (lo + .05)
    }

    private val deuteranopia = arrayOf(
        doubleArrayOf(.367322, .860646, -.227968), doubleArrayOf(.280085, .672501, .047413),
        doubleArrayOf(-.011820, .042940, .968881))
    private val protanopia = arrayOf(
        doubleArrayOf(.152286, 1.052583, -.204868), doubleArrayOf(.114503, .786281, .099216),
        doubleArrayOf(-.003882, -.048116, 1.051998))

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val l = cbrt(.4122214708 * rgb[0] + .5363325363 * rgb[1] + .0514459929 * rgb[2])
        val m = cbrt(.2119034982 * rgb[0] + .6806995451 * rgb[1] + .1073969566 * rgb[2])
        val s = cbrt(.0883024619 * rgb[0] + .2817188376 * rgb[1] + .6299787005 * rgb[2])
        return doubleArrayOf(
            .2104542553 * l + .7936177850 * m - .0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + .4505937099 * s,
            .0259040371 * l + .7827717662 * m - .8086757660 * s,
        )
    }

    private fun deltaE(a: Color, b: Color, simulation: Array<DoubleArray>? = null): Double {
        fun lab(c: Color): DoubleArray {
            val rgb = linear(c)
            val seen = simulation?.let { m ->
                DoubleArray(3) { i -> (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0) }
            } ?: rgb
            return oklab(seen)
        }
        val x = lab(a)
        val y = lab(b)
        return 100 * sqrt((x[0] - y[0]).pow(2) + (x[1] - y[1]).pow(2) + (x[2] - y[2]).pow(2))
    }
}
