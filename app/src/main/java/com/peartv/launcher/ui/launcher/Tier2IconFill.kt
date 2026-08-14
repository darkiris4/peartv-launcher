package com.peartv.launcher.ui.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/** Tier 2's centered app-icon mark (§3.1.2 Template 4) — a fixed size, same reasoning as AppTile's fixed TileWidth (Dimens.kt): simpler than deriving from the hero's own animating height, and this only ever renders while the hero is at/near full expansion. */
private val Tier2LogoSize = 140.dp

/** Apple's own app-icon corners are a true squircle (continuous-curvature superellipse), not a circular-arc round-rect — Compose has no built-in squircle Shape, and a plain RoundedCornerShape at a squircle-equivalent radius is the standard, already-established approximation elsewhere in this app (AppTile's TileCornerRadius, the tray's TrayCornerRadius, both in Dimens.kt). ~20% of Tier2LogoSize, matching Apple's own icon corner-radius-to-width ratio. */
private val Tier2LogoCornerRadius = 28.dp

/** User-directed: "a very slight drop shadow for a more 3D look" on Tier 2's centered icon — deliberately modest, not the stronger elevation AppTile's own focus shadow uses (that's focus-driven and animated; this is static decoration). */
private val Tier2LogoShadowElevation = 8.dp

/** How far Tier 2's sampled fill color is pulled toward the theme's own background — confirmed on-device that a raw sampled color (no concept of theme) reads fine in dark theme but as a stark, jarring block in light theme when the source icon happens to be dark (e.g. HBO Max). */
private const val Tier2FillBackgroundBlend = 0.45f

/**
 * Tier 2's icon+color treatment — extracted from [HeroBanner]'s own original
 * inline rendering so `ContentCarousel`'s `PosterBackdrop` can reuse it too
 * (a Tier 3 program that resolves to `ResolvedArtwork.UseTier2Icon`, the
 * `ArtworkSource`-gated per-program fallback, not just Tier 2's own real
 * hero). [icon]/[iconPrimaryColorArgb] map directly to [com.peartv.launcher.domain.model.TvApp.icon]/
 * [com.peartv.launcher.domain.model.TvApp.iconPrimaryColorArgb] — both
 * precomputed once at app-list build time, nothing sampled here.
 *
 * `null` [icon] renders nothing (fills with the color alone) — same as
 * [HeroBanner]'s own original behavior, which only ever reached this branch
 * once [icon] was already confirmed non-null; kept permissive here since a
 * Tier 3 program's own [com.peartv.launcher.domain.model.TvApp] reference is
 * threaded in as a plain nullable.
 */
@Composable
fun Tier2IconFill(
    icon: Bitmap?,
    iconPrimaryColorArgb: Int?,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    // Same reasoning as HeroBanner's own original: a sampled color has no
    // concept of theme, so an app with a dark icon (e.g. HBO Max, near-black)
    // reads as a stark, jarring block against light theme's own light
    // background — blending toward backgroundColor pulls it toward whichever
    // theme is active instead of ignoring theme entirely.
    val fillColor = iconPrimaryColorArgb
        ?.let { lerp(Color(it), backgroundColor, Tier2FillBackgroundBlend) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    // Same glow/shadow color AppTile's own tvOSFocusable uses across the
    // whole app (Decisions Log, "Focus-shadow color uniformity") — near-white
    // in dark theme, near-black in light theme, uniform regardless of any
    // per-app accent color.
    val shadowColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fillColor),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = remember(icon) { icon.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(Tier2LogoSize)
                    .shadow(
                        elevation = Tier2LogoShadowElevation,
                        shape = RoundedCornerShape(Tier2LogoCornerRadius),
                        clip = false,
                        ambientColor = shadowColor,
                        spotColor = shadowColor,
                    )
                    .clip(RoundedCornerShape(Tier2LogoCornerRadius)),
            )
        }
    }
}
