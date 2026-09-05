package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.ui.theme.ambientPanelTint

/**
 * The app grid's own background once the hero collapses (grid gains focus) —
 * a flat, theme-aware lifted tint over `LauncherScreen`'s own
 * `ambientBackground()`. Earlier versions carried a frozen, position-cropped
 * blur of the last hero artwork here; that was dropped with the move to a
 * live `RenderEffect` capture (`BackdropBlur.kt`) — the blurred-hero-behind-
 * the-grid effect was only ever visible for the ~350ms collapse transition
 * and didn't justify a permanent full-screen blur pass. The grid reads for
 * long stretches, so a calm flat backdrop suits it better than the dock's
 * live glass anyway.
 */
@Composable
fun GridBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(gridBackdropTint()),
    )
}

@Composable
private fun gridBackdropTint(): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDarkTheme) GridBackdropTintAlphaDark else GridBackdropTintAlphaLight
    return baseTint.copy(alpha = alpha)
}

private const val GridBackdropTintAlphaDark = 0.45f
private const val GridBackdropTintAlphaLight = 0.55f
