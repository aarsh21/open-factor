package org.openfactor.app

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class TypographyContractTest {
    @Test fun everyMaterialRoleUsesTheOwnedFamily() {
        val t = AppTypography
        val display = listOf(t.displayLarge, t.displayMedium, t.displaySmall)
        display.forEach { style ->
            assertEquals(AppFonts.serif, style.fontFamily)
            assertEquals(FontWeight.Normal, style.fontWeight)
            assertEquals(FontSynthesis.None, style.fontSynthesis)
            assertTrue("Serif leading must preserve its upstream vertical extents", style.lineHeight.value >= style.fontSize.value * 1.4f)
        }
        val ui = listOf(t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall)
        ui.forEach { assertEquals(AppFonts.sans, it.fontFamily) }
    }

    @Test fun sansWeightsAreRealVariableInstancesAndSerifStaysGenuineRegular() {
        assertEquals((100..900 step 100).toList(), (AppFonts.sans as FontListFontFamily).map { it.weight.weight })
        assertEquals(listOf(FontWeight.Normal), (AppFonts.serif as FontListFontFamily).map { it.weight })
        assertEquals("tnum", AppTypography.bodyLarge.fontFeatureSettings)
    }
}
