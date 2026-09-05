package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.ui.theme.ambientPanelTint
import kotlin.math.roundToInt

/** Live-glass blur radius for the dock and the status pill. */
val DockBlurRadius: Dp = 40.dp

/**
 * Heavier blur for the full-screen Settings backdrop — a still snapshot with
 * no moving art to hold legibility against, so it can read as a more fully
 * frosted panel than the dock's own motion-tuned blur.
 */
val SettingsBlurRadius: Dp = 56.dp

/**
 * Records the content it wraps into [layer] once per draw pass, then draws
 * it normally, so translucent chrome positioned as a *later sibling* of this
 * composable ([TopShelfRow], [StatusBar], [GridBackdrop]) can redraw a live,
 * `RenderEffect`-blurred crop of it via [backdropBlur].
 *
 * Replaces the earlier decoded-bitmap `Toolkit.blur` pipeline
 * (`BlurredArtwork`/`DockBackdrop`, deleted) and its hand-rolled
 * `ContentScale.Crop` geometry (`positionAwareBackdropCrop`, deleted): the
 * API-30 platform limitation that forced blurring a re-decoded copy of a
 * known artwork URL — rather than the composited frame itself — no longer
 * applies now the reference hardware runs Android 15. A live capture also
 * gives every tier real glass, not just Tier 3's rotating channel art.
 *
 * Only the hero/carousel layer is captured, not the whole launcher: the
 * chrome that reads the capture is drawn afterwards and outside it, so there
 * is no layer drawing itself, and the grid/dock tiles behind the panels
 * aren't blur-worthy content anyway.
 */
@Composable
fun BackdropCapture(
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
        content = content,
    )
}

/**
 * Draws a `RenderEffect` Gaussian blur of the region of [source] (recorded
 * by [BackdropCapture]) that sits behind this element on screen, then [tint]
 * over it. The caller composes its own content on top.
 *
 * The crop is done by translating the whole captured layer by this element's
 * negative window origin into a panel-sized scratch layer — no reproduction
 * of `ContentScale.Crop` math against a bitmap, since the capture already is
 * the on-screen composite. [TileMode.Clamp] on the blur avoids a transparent
 * halo where the kernel reaches past the scratch layer's own edges.
 */
fun Modifier.backdropBlur(
    source: GraphicsLayer,
    tint: Color,
    blurRadius: Dp = DockBlurRadius,
): Modifier = composed {
    val scratch = rememberGraphicsLayer()
    val density = LocalDensity.current
    var originInWindow by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { originInWindow = it.positionInWindow() }
        .drawWithCache {
            val radiusPx = with(density) { blurRadius.toPx() }
            scratch.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            scratch.clip = true
            val target = IntSize(size.width.roundToInt(), size.height.roundToInt())
            onDrawBehind {
                if (target.width > 0 && target.height > 0) {
                    scratch.record(size = target) {
                        translate(-originInWindow.x, -originInWindow.y) {
                            drawLayer(source)
                        }
                    }
                    drawLayer(scratch)
                }
                drawRect(tint)
            }
        }
}

/**
 * Fixed translucent material tint for glass chrome drawn over [backdropBlur].
 * The earlier luminance-adaptive tint compensated for a weak downscale/
 * upscale blur; a real Gaussian blur of the live backdrop holds contrast on
 * its own, so this is one fixed value per theme, like a platform material.
 */
@Composable
fun glassTint(alpha: Float = TranslucentPanelAlpha): Color =
    MaterialTheme.ambientPanelTint().copy(alpha = alpha)
