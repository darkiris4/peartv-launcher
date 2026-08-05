package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.toSize
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.ui.motion.kenBurnsTransform
import com.peartv.launcher.ui.motion.rememberFreezableKenBurnsProgress
import com.peartv.launcher.ui.theme.ambientPanelTint

/**
 * The app grid's own background once the hero collapses (grid gains focus)
 * — reuses [dockBackdrop]'s own already-blurred bitmap (the same cache
 * `TopShelfRow`'s dock panel reads), position-aware cropped against
 * [heroWindowRect] the same way (`positionAwareBackdropCrop`, `GlassPanel.kt`)
 * rather than a second independent blur pipeline; this composable lives
 * inside the same `Screen.Launcher` composition as the hero (`LauncherScreen`
 * is its caller), so no cross-screen cache hoisting like Settings' own
 * backdrop needed.
 *
 * [active] — true while the hero/tray holds focus (`isTopShelfFocused`,
 * `LauncherScreen`'s own doc), false while focus is in the grid. Both the
 * artwork and the Ken Burns motion freeze the instant [active] goes false
 * and hold static for as long as the grid stays focused (reads as the
 * background settling down, not a jump-cut) rather than continuing to chase
 * whatever the hero does next — real gotcha here: `ContentCarousel` (the
 * actual source of [dockBackdrop]) unmounts entirely once the hero finishes
 * collapsing, and mounts fresh (new bitmap decode, new Ken Burns clock
 * starting at `0f`) the next time the hero expands again, so naively
 * mirroring it live the whole time would make this backdrop's own motion
 * visibly snap on both ends of the cycle. [rememberFreezableKenBurnsProgress]
 * (`KenBurns.kt`) solves the motion half by owning its own paused/resumed
 * clock, seeded from whatever [dockBackdrop] was actually showing at the
 * freeze instant; [frozenArtwork] below solves the bitmap half the same
 * way, by hand — only ever overwritten with a *non-null* live value while
 * [active], so a transient `null` from `ContentCarousel` re-decoding a fresh
 * poster on remount doesn't flash through to this backdrop, which by then is
 * sliding out of view underneath the returning hero anyway.
 *
 * `null` [frozenArtwork] (nothing ever cached yet — cold start or a Tier 1/2
 * app with no rotating-artwork channel to source a blur from) falls back to
 * a flat [gridBackdropTint] fill with no bitmap at all, same fallback
 * principle Settings' own cold-start case uses, rather than drawing nothing
 * or crashing.
 */
@Composable
fun GridBackdrop(
    dockBackdrop: DockBackdrop?,
    heroWindowRect: Rect,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var panelRect by remember { mutableStateOf(Rect.Zero) }

    var frozenArtwork by remember { mutableStateOf<BlurredArtwork?>(null) }
    val liveArtwork = dockBackdrop?.artwork?.value
    if (active && liveArtwork != null) frozenArtwork = liveArtwork

    val progress by rememberFreezableKenBurnsProgress(active) {
        dockBackdrop?.kenBurnsProgress?.value ?: 0f
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { panelRect = Rect(it.positionInWindow(), it.size.toSize()) },
    ) {
        val artwork = frozenArtwork
        if (artwork != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .positionAwareBackdropCrop(
                        bitmap = artwork.bitmap,
                        heroRect = { heroWindowRect },
                        panelRect = { panelRect },
                    )
                    .kenBurnsTransform(progress),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gridBackdropTint(artwork)),
        )
    }
}

/**
 * [GridBackdrop]'s own fixed, theme-aware tint — deliberately its own named
 * constant, not shared with the dock's luminance-adaptive [liquidGlassTint]
 * (tuned for a small panel over fast-rotating live art) or Settings' own
 * [SettingsPanelTintAlphaDark]/[SettingsPanelTintAlphaLight] (a different
 * page, different legibility needs). The grid is read for longer at a
 * stretch than either — tile labels, the focus highlight, sustained
 * browsing — so this leans toward Settings' fixed-per-theme approach rather
 * than the dock's adaptive one (this backdrop is frozen/static for most of
 * the time it's actually visible anyway, per [GridBackdrop]'s own doc, so
 * "adaptive to a live image" isn't even the right model here). Tuned by eye
 * against real grid content, not derived from either sibling value.
 */
@Composable
private fun gridBackdropTint(artwork: BlurredArtwork?): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDarkTheme) GridBackdropTintAlphaDark else GridBackdropTintAlphaLight
    if (artwork == null) return baseTint.copy(alpha = alpha)
    val tintColor = lerp(baseTint, artwork.averageColor, GridBackdropTintColorBiasWeight)
    return tintColor.copy(alpha = alpha)
}

/** [gridBackdropTint]'s own fixed alpha in dark theme. */
private const val GridBackdropTintAlphaDark = 0.45f

/** [gridBackdropTint]'s own fixed alpha in light theme — higher than [GridBackdropTintAlphaDark], same legibility reasoning [liquidGlassTint]/`settingsPanelTint` both already document. */
private const val GridBackdropTintAlphaLight = 0.55f

/** How far [gridBackdropTint]'s own color leans toward the art's average color — same modest-bias concept the dock/Settings tints use, tuned independently. */
private const val GridBackdropTintColorBiasWeight = 0.3f
