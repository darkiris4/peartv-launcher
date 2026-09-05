package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GlassTopHighlightHeight = 2.dp
private const val GlassTopHighlightAlpha = 0.35f

/**
 * Liquid Glass's specular highlight along a panel's own top edge — a cheap
 * static near-white gradient, drawn *last* (over the panel's own content) so
 * it reads as light catching the panel edge, not something content occludes.
 * Fixed bright in both themes: a real glass highlight is reflected ambient
 * light, not a theme-flipped surface color.
 *
 * [Modifier.matchParentSize] on the outer Box, not a direct `fillMaxWidth()`:
 * `StatusBar`'s panel stays wrap-content sized around its pill content, and a
 * direct `fillMaxWidth()` child would pull the whole Box out to its parent's
 * max width. `matchParentSize()` is exempt from that sizing pass, so it can
 * only ever match the size the real content already settled on.
 */
@Composable
fun BoxScope.LiquidGlassTopHighlight() {
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(GlassTopHighlightHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = GlassTopHighlightAlpha), Color.Transparent),
                    ),
                ),
        )
    }
}
