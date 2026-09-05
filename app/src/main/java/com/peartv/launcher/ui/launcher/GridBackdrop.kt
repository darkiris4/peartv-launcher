package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.ui.theme.ambientPanelTint

/**
 * The app grid's own background once the hero collapses (grid gains focus) —
 * a heavy `RenderEffect` blur of the frozen last hero frame ([backdropLayer],
 * which stops recording when the hero collapses, see [BackdropCapture]) under
 * a theme-aware lifted tint, so the collapsed home surface still shows a soft
 * ghost of the last image the hero was showing rather than a flat fill
 * (user-directed). On a cold start with no hero frame captured yet the layer
 * blurs to nothing and the tint alone covers it.
 */
@Composable
fun GridBackdrop(
    backdropLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.backdropBlur(backdropLayer, gridBackdropTint(), CollapsedBlurRadius),
    )
}

@Composable
private fun gridBackdropTint(): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDarkTheme) GridBackdropTintAlphaDark else GridBackdropTintAlphaLight
    return baseTint.copy(alpha = alpha)
}

/**
 * Enough to keep grid tiles/labels legible over the blurred frozen hero, but
 * light enough that the still clearly reads through. The light value can't
 * just mirror the dark one — a light tint at the same alpha washes the frame
 * out to flat (same reason `glassTint` splits), so it goes much sheerer and
 * leans on the heavy blur itself to keep things calm.
 */
private const val GridBackdropTintAlphaDark = 0.62f
private const val GridBackdropTintAlphaLight = 0.34f
