package org.openfactor.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Bundled, resolver-cached fonts: practical UI type and a restrained display accent. */
@OptIn(ExperimentalTextApi::class)
internal object AppFonts {
    val sans = FontFamily((100..900 step 100).map { weight ->
        Font(
            R.font.geist_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    })
    // This family has shape variants, not a weight axis. Keep its genuine regular drawing.
    val serif = FontFamily(Font(R.font.geist_serif_regular, weight = FontWeight.Normal))
}

// Upstream Serif vertical extents are ~1.405em; Sans leading would clip its real drawing.
internal val AppTypography: Typography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = AppFonts.serif, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineHeight = 82.sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = AppFonts.serif, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineHeight = 64.sp),
        displaySmall = defaults.displaySmall.copy(fontFamily = AppFonts.serif, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, lineHeight = 52.sp),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Bold),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Bold),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.SemiBold),
        titleLarge = defaults.titleLarge.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.SemiBold),
        titleMedium = defaults.titleMedium.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Medium),
        titleSmall = defaults.titleSmall.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Medium),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = AppFonts.sans, fontFeatureSettings = "tnum"),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = AppFonts.sans),
        bodySmall = defaults.bodySmall.copy(fontFamily = AppFonts.sans),
        labelLarge = defaults.labelLarge.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Medium),
        labelMedium = defaults.labelMedium.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Medium),
        labelSmall = defaults.labelSmall.copy(fontFamily = AppFonts.sans, fontWeight = FontWeight.Medium),
    )
}
