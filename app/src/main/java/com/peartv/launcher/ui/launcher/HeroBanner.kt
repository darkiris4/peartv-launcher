package com.peartv.launcher.ui.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
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
 * - No separate small logo badge — [TvApp] only carries one image (the
 *   16:9 banner), not a distinct square icon, so there's nothing sensible
 *   to show as a badge yet without adding a second asset-loading path. A
 *   blurred-backdrop-plus-centered-logo Tier 2 treatment (§3.1.2 Template 4)
 *   was tried and reverted (see the Decisions Log) — reusing the banner
 *   itself as a stand-in "logo" assumed art designed to read that way, which
 *   isn't a safe assumption across arbitrary installed apps' banners.
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
                Image(
                    bitmap = remember(banner) { banner.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Fades to the page background at the bottom so the tray stays
        // legible regardless of how bright the backdrop art is (§3.1.1) —
        // structural, not a "lighting" effect: without it the artwork would
        // end on a hard seam right above the tray/grid. The darkening scrim
        // this used to also apply (a black gradient stop instead of
        // Transparent below) was removed per the Decisions Log's "§3.1.1
        // 'liquid glass' tray/pill styling — removed" entry.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            backgroundColor,
                        ),
                    ),
                ),
        )

        // §3.1.2 Template 1 calls for a vignette along the bottom **and
        // left** edges — this closes the previously-open gap (§3.1.2's gap
        // table, "Vignette: Bottom-only"). Mirrors the vertical fade above
        // (same solid-to-transparent construction, just horizontal) so the
        // lower-left title text (below) always sits on a legible background
        // regardless of the backdrop art's own brightness there.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            backgroundColor,
                            Color.Transparent,
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
