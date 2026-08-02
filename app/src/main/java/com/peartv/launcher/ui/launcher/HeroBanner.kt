package com.peartv.launcher.ui.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.peartv.launcher.R
import com.peartv.launcher.domain.model.TmdbBackdrop
import com.peartv.launcher.domain.model.TvApp

private const val HeroCrossfadeMillis = 400

/** Tier 2's centered app-icon mark (§3.1.2 Template 4) — a fixed size, same reasoning as AppTile's fixed TileWidth (Dimens.kt): simpler than deriving from the hero's own animating height, and this only ever renders while the hero is at/near full expansion. */
private val Tier2LogoSize = 140.dp

/** Apple's own app-icon corners are a true squircle (continuous-curvature superellipse), not a circular-arc round-rect — Compose has no built-in squircle Shape, and a plain RoundedCornerShape at a squircle-equivalent radius is the standard, already-established approximation elsewhere in this app (AppTile's TileCornerRadius, the tray's TrayCornerRadius, both in Dimens.kt). ~20% of Tier2LogoSize, matching Apple's own icon corner-radius-to-width ratio. */
private val Tier2LogoCornerRadius = 28.dp

/** User-directed: "a very slight drop shadow for a more 3D look" on Tier 2's centered icon — deliberately modest, not the stronger elevation AppTile's own focus shadow uses (that's focus-driven and animated; this is static decoration). */
private val Tier2LogoShadowElevation = 8.dp

/** How far Tier 2's sampled fill color is pulled toward the theme's own backgroundColor — confirmed on-device that a raw sampled color (no concept of theme) reads fine in dark theme but as a stark, jarring block in light theme when the source icon happens to be dark (e.g. HBO Max). */
private const val Tier2FillBackgroundBlend = 0.45f

/**
 * PRODUCT_SPEC.md §3.1.1's hero — backdrop + vignette, observing whichever
 * app is currently focused (in either the top shelf or the grid). No
 * name/tagline text here anymore (Decisions Log: "Hero name/tagline text") —
 * that's now §1.4's per-tile focus label, which identifies the focused app
 * directly beneath its own tile instead of in a separate hero text block.
 *
 * Backdrop source follows the three-tier model (§2.4/§3.1.1 Decisions Log):
 * [heroBackdrop] (Tier 1 — remote TMDB art, loaded via Coil) takes priority
 * when non-null; otherwise falls back to [activeApp]'s own local banner
 * bitmap (Tier 2). Tier 3 (real Channels data replacing the hero with
 * Content Rows entirely) is a further-out call site's job, not this
 * composable's — this only owns *which backdrop image* to show, not whether
 * to show a backdrop at all.
 *
 * §3.1.2's restored hero title (Template 1's lower-left title/logo
 * treatment) renders [heroBackdrop]'s own `title` — the *specific content*
 * shown in the backdrop, not the app's name (that stays §1.4's per-tile
 * label, not reopened) — only when Tier 1 is actually active. Tier 2 has no
 * per-title identity to show (a static app banner isn't "a movie"), so it
 * renders nothing here, same as before.
 *
 * Deliberately scoped down from the full spec beyond that:
 *
 * - Plain [Crossfade], not §3.1.1's asymmetric staggered-entrance/uniform-
 *   exit reveal choreography. This gets the *structural* separation (hero
 *   owns backdrop, contains no cards) and focus-driven binding right first;
 *   the richer reveal choreography is a follow-up polish pass, not a
 *   correctness requirement.
 * - No literal "mirrored" reflection from §3.1.2 Template 4's description —
 *   scoped down deliberately, same spirit as the rest of this list.
 *
 * Tier 2 (no [heroBackdrop], a [banner] but no real Channels data) fills the
 * hero with [TvApp.iconPrimaryColorArgb] — [TvApp.icon]'s own dominant color
 * (`DrawableBitmap.kt`'s `dominantColorArgb`, Android's `Palette` library,
 * sampled once at app-list build time) — behind a sharp, centered copy of
 * [TvApp.icon] itself (a *separate* asset from the banner, see that field's
 * own doc for why). A flat sampled-color fill, not a blurred banner: two
 * blur approaches were tried first for this same backdrop and abandoned.
 * A live `GraphicsLayer`-based blur (the downscale/upscale technique
 * `TopShelfRow`/`StatusBar` use, `BackdropBlur.kt`) silently produced an
 * empty layer on-device — nesting that recording inside this composable's
 * own outer [recordBackdropSource] capture (below) hit the same class of
 * hazard the Decisions Log's multi-pass-blur entry already flags for
 * chained `GraphicsLayer` operations on this hardware, just failing
 * quietly instead of crashing. A pre-shrunk-bitmap blur (a tiny
 * rasterization stretched back up via ordinary bilinear filtering) worked
 * technically but read as a pixelated, blocky mess rather than a genuine
 * frosted-glass look — user-rejected on sight, not a subtle tuning
 * complaint. An even earlier attempt at this same §3.1.2 Template 4
 * treatment reused the banner itself, shrunk, as a stand-in logo, and got
 * reverted (Decisions Log) — full-bleed banner art isn't designed to read
 * that way as a sharp mark; [TvApp.icon] is. Falls back to the plain sharp
 * banner only if [TvApp.icon] is somehow null (shouldn't happen in
 * practice) — [TvApp.iconPrimaryColorArgb] alone falls back to a neutral
 * theme color, same as [AppTile]'s own [TvApp.accentColorArgb] fallback.
 *
 * [contentAlpha] drives the collapsing-header behavior (tvOS photo
 * reference, `design/IMG_1858.jpeg` vs `IMG_1859.jpeg`): as
 * `LauncherScreen`'s hero region collapses toward the tray's own height
 * (focus moved into the grid), the backdrop fades toward fully transparent
 * rather than just cropping smaller, matching the reference's "artwork
 * disappears entirely" collapsed state rather than a cropped sliver.
 *
 * [backdropSourceLayer] re-records this composable's entire final output
 * (backdrop art, vignettes, title — everything, via
 * [Modifier.recordBackdropSource]) on every draw pass, so `TopShelfRow` and
 * `StatusBar` can each draw a blurred crop of it as their own background
 * (see `BackdropBlur.kt`). This is the return of a mechanism the Decisions
 * Log's "§3.1.1 'liquid glass' tray/pill styling — removed" entry deleted
 * outright — reopened at user request (Decisions Log, "Dock/pill backdrop
 * blur"), rebuilt on the downscale/upscale technique in `BackdropBlur.kt`
 * rather than the real `RenderEffect` blur the original had (SDK-gated to
 * API 31+, so it was never actually active on this project's confirmed API
 * 30 reference hardware to begin with).
 */
@Composable
fun HeroBanner(
    activeApp: TvApp?,
    heroBackdrop: TmdbBackdrop?,
    backdropSourceLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .background(backgroundColor)
            .alpha(contentAlpha)
            .recordBackdropSource(backdropSourceLayer),
    ) {
        Crossfade(
            targetState = heroBackdrop?.backdropUrl to activeApp?.banner,
            animationSpec = tween(HeroCrossfadeMillis),
            label = "heroBackdrop",
        ) { (backdropUrl, banner) ->
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (activeApp?.packageName == SystemSettingsPackageName) {
                // Same override as AppTile's tile art — the Tier 2 fallback
                // here was still drawing the real Settings app's own banner
                // bitmap (confirmed on-device: focusing the Settings tile
                // showed its unmodified icon as the hero backdrop, the one
                // place this override was missed).
                val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Image(
                    painter = painterResource(if (isDarkBackground) R.drawable.settings_tile_dark else R.drawable.settings_tile_light),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (banner != null) {
                val icon = activeApp?.icon
                if (icon != null) {
                    // A flat fill sampled from the icon's own dominant color
                    // — falls back to a neutral theme color (not the banner)
                    // if Palette couldn't extract one, same reasoning as
                    // AppTile's own accentColorArgb fallback. Blended toward
                    // backgroundColor (the same target the vignette below
                    // fades to) rather than used raw: a sampled color has no
                    // concept of theme, so an app with a dark icon (e.g.
                    // HBO Max, near-black) rendered a stark, jarring block
                    // against light theme's own light background, confirmed
                    // on-device — dark theme never surfaced this since a
                    // dark sample already sits close to a dark background.
                    // Blending pulls it toward whichever theme is active
                    // instead of ignoring theme entirely.
                    val fillColor = activeApp.iconPrimaryColorArgb
                        ?.let { lerp(Color(it), backgroundColor, Tier2FillBackgroundBlend) }
                        ?: MaterialTheme.colorScheme.surfaceVariant
                    // Same glow/shadow color AppTile's own tvOSFocusable
                    // uses across the whole app (Decisions Log,
                    // "Focus-shadow color uniformity") — near-white in dark
                    // theme, near-black in light theme, uniform regardless
                    // of any per-app accent color. Compose's Modifier.shadow
                    // defaults to a fixed black ambient/spot color, which
                    // reads correctly in dark theme but wrong in light theme
                    // (a black shadow on the light dock's own near-black
                    // shadow standard would clash — the whole point of that
                    // Decisions Log entry).
                    val shadowColor = MaterialTheme.colorScheme.onBackground
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(fillColor),
                        contentAlignment = Alignment.Center,
                    ) {
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
                } else {
                    Image(
                        bitmap = remember(banner) { banner.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Fades to the page background at the bottom so the tray stays
        // legible regardless of how bright the backdrop art is (§3.1.1) —
        // structural, not a "lighting" effect: without it the artwork would
        // end on a hard seam right above the tray/grid. The darkening scrim
        // this used to also apply (a black gradient stop instead of
        // Transparent below) was removed per the Decisions Log's "§3.1.1
        // 'liquid glass' tray/pill styling — removed" entry.
        //
        // Bounded to the bottom [VignetteBottomFraction] of the hero via
        // explicit colorStops — an unbounded two-color Brush.verticalGradient
        // spans the *entire* height (fully transparent at the very top down
        // to fully opaque at the very bottom), which reads as a heavy
        // full-frame wash rather than an edge fade, confirmed on-device: it
        // was washing out Tier 2's centered icon treatment sitting at
        // the hero's vertical center. Bounding it restores this comment's
        // own stated intent — an edge effect, not a tint over everything.
        // [featheredEdgeStops] (not a plain 2-stop fade) — see that
        // function's own doc for the two artifacts it fixes, both confirmed
        // on-device as a visible hard edge where the fade begins.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = featheredEdgeStops(
                            color = backgroundColor,
                            start = 1f - VignetteBottomFraction,
                            end = 1f,
                            maxAlpha = VignetteMaxAlpha,
                        ),
                    ),
                ),
        )

        // §3.1.2 Template 1 calls for a vignette along the bottom **and
        // left** edges — this closes the previously-open gap (§3.1.2's gap
        // table, "Vignette: Bottom-only"). Mirrors the vertical fade above
        // (same solid-to-transparent construction, just horizontal, and same
        // feathered-edge fix — see that fade's own comment) so the
        // lower-left title text (below) always sits on a legible background
        // regardless of the backdrop art's own brightness there.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = featheredEdgeStops(
                            color = backgroundColor,
                            start = 0f,
                            end = VignetteLeftFraction,
                            reversed = true,
                            maxAlpha = VignetteMaxAlpha,
                        ),
                    ),
                ),
        )

        // §3.1.2 Template 1 — title of the specific content shown in the
        // backdrop, lower-left. Tier 1 only (Tier 2's static app banner has
        // no per-title identity to name here); positioned above the tray's
        // own clearance zone, same reasoning as the tray's bottom padding
        // elsewhere in this file.
        if (heroBackdrop != null) {
            Text(
                text = heroBackdrop.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = ScreenSafeAreaHorizontal,
                        bottom = TopShelfTrayHeight + 16.dp,
                    ),
            )
        }
    }
}
