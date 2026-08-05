package com.peartv.launcher.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.peartv.launcher.R

/**
 * PRODUCT_SPEC.md §5 #10 — SF Pro itself is licensed to Apple platforms
 * only; this app previously never made a deliberate typeface choice at
 * all, just inheriting `androidx.tv.material3`'s own default (effectively
 * Roboto). Inter is the standard open substitute for system UI faces like
 * SF Pro — extensively tested for on-screen legibility at small sizes,
 * wide weight range, free (SIL OFL). User-directed over a rounded
 * alternative (Nunito Sans, closer to SF Pro *Rounded* specifically) that
 * was also considered.
 *
 * `res/font/inter_variable.ttf` is Inter's own variable font (weight axis
 * `100`–`900`) — one file, not a per-weight static set, with each
 * [FontWeight] below resolved via [FontVariation.Settings] rather than a
 * separate bundled file per weight.
 */
@OptIn(ExperimentalTextApi::class)
private val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/**
 * `androidx.tv.material3`'s own default [Typography] (sizes, line heights,
 * letter spacing, per-style weight) left untouched — only [FontFamily] is
 * overridden, on every style, so this is purely a typeface swap, not a new
 * type scale.
 */
val PearTvTypography: Typography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = InterFontFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = InterFontFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = InterFontFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = InterFontFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = InterFontFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = InterFontFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = InterFontFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = InterFontFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = InterFontFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = InterFontFamily),
    )
}
