package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.ui.theme.ambientPanelTint

/**
 * PRODUCT_SPEC.md §3.1's top-shelf tray, inset from the screen edge by
 * [TrayOuterMargin] so it reads as a floating card, not a full-bleed bar
 * (Decisions Log: "Glass tray outer margin"). Tiles here carry no per-tile
 * focus label (Decisions Log: "Tray tile focus label") — the tray's own
 * scale/glow motion already reads as "focused" without one.
 *
 * No longer column-aligned with the grid below it (superseding Decisions
 * Log: "Glass tray width/alignment") — user-directed: this row's own
 * internal padding is symmetric now (the same [TrayPaddingVertical] on
 * every side, not [ScreenSafeAreaHorizontal] left/right and a much smaller
 * [TrayPaddingVertical] top/bottom), which the grid's own contentPadding
 * doesn't match — a deliberate trade, not an oversight.
 *
 * Translucent panel (`MaterialTheme.ambientPanelTint()` — `colorScheme.surface`
 * lifted a touch toward `onSurfaceVariant`, `ui/theme/AmbientBackground.kt`,
 * so this panel doesn't read as an unlit island against `ambientBackground`'s
 * own glow behind it) over a blurred crop of whatever's behind it
 * (`HeroBanner`'s own backdrop, via [blurSource]/`BackdropBlur.kt`)
 * — the earlier "liquid glass" treatment (translucent tint, hairline border,
 * drop shadow, and a real API-31+ backdrop blur) was removed per the
 * Decisions Log's "§3.1.1 'liquid glass' tray/pill styling — removed" entry,
 * confirmed on-device to read as more polished flat than glassy in both
 * themes — then reopened at user request (Decisions Log, "Dock/pill backdrop
 * blur"), rebuilt on a downscale/upscale blur instead of `RenderEffect`
 * (unavailable below API 31, a generation past this project's confirmed API
 * 30 reference floor).
 *
 * Always the first focus target on cold launch (§1.3), which is why item 0
 * owns a [FocusRequester] that fires as soon as this row enters composition.
 *
 * [upFocusRequester], when non-null, is wired as every tile's explicit `up`
 * focus target via `Modifier.focusProperties` — PRODUCT_SPEC.md §1.3's own
 * documented remedy for "geometric search is unreliable" boundaries.
 * Confirmed on-device this boundary specifically needed it: `ContentCarousel`
 * is one full-screen-sized focusable node occupying the same hero `Box` the
 * tray itself sits inside of, and Compose's default nearest-neighbor search
 * failed to route `DPAD_UP` into it at all (unlike the old per-poster
 * `ContentRows`, which had many small, unambiguously-"above" targets to find
 * instead of one giant overlapping one). The caller (`LauncherScreen`) points
 * this at whichever `FocusRequester` is actually correct for the currently-
 * focused app — the carousel's own when it has one, straight to the settings
 * gear otherwise (user-directed: Up should always reach *something*).
 *
 * [focusRequesters]/[downFocusRequesters] fix the same class of bug at the
 * dock/grid boundary (user-reported: `DPAD_UP` from the grid always landed
 * on the dock's rightmost tile instead of the column actually above focus —
 * default geometric search across two separately-laid-out composables
 * again wasn't reliable). [focusRequesters] is one `FocusRequester` per
 * tile, owned and created by `LauncherScreen` rather than here, since
 * `AppGrid`'s own row-0 tiles need those exact same instances to point their
 * `up` back at this row — `TopShelfRow` can't hand out references to a list
 * it created itself for a sibling composable to consume before this one has
 * even composed. [downFocusRequesters], indexed the same way, is this row's
 * own half of the fix — each tile's explicit `down` target, one per grid
 * column, so descending back into the grid lands column-aligned too, not
 * just ascending out of it. Index 0 keeps doubling as the row's initial
 * cold-launch focus target, same as it always has.
 *
 * Grid Reordering & Folders — [apps] threads edit-mode visuals/long-press
 * through same as `AppGrid`; the dock is App-only (Decisions Log "Folders
 * excluded from the dock"), so nothing here ever needs folder-specific
 * handling.
 */
@Composable
fun TopShelfRow(
    apps: List<TvApp>,
    onAppClick: (TvApp) -> Unit,
    blurSource: GraphicsLayer,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    focusRequesters: List<FocusRequester> = emptyList(),
    downFocusRequesters: List<FocusRequester> = emptyList(),
    onAppFocused: (TvApp) -> Unit = {},
    editMode: EditModeState = EditModeState(),
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
    // Defaults to the shared constant, but `LauncherScreen` passes its own
    // screen-height-derived size — user-directed: dock and grid tiles
    // should always match exactly, including when the grid's own size
    // grows past [TileWidth] for its collapsed layout (`AppGrid`'s own
    // `tileWidth` doc).
    tileWidth: Dp = TileWidth,
) {
    val shape = RoundedCornerShape(TrayCornerRadius)
    // This tray's own position relative to the shared hero `Box` it and
    // `HeroBanner` are both direct children of — `positionInParent()`
    // already lands in exactly the coordinate space `blurSource` (HeroBanner's
    // recorded output) was captured in, since HeroBanner fills that same
    // `Box` exactly (see `BackdropBlur.kt`'s `blurredBackdrop` doc).
    var offsetInHero by remember { mutableStateOf(Offset.Zero) }
    val trayBlurLayer = rememberGraphicsLayer()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TrayOuterMargin)
            .clip(shape)
            .onGloballyPositioned { offsetInHero = it.positionInParent() }
            .blurredBackdrop(
                source = blurSource,
                lowResLayer = trayBlurLayer,
                sourceOffset = { offsetInHero },
            )
            .background(MaterialTheme.ambientPanelTint().copy(alpha = TranslucentPanelAlpha))
            .horizontalScroll(rememberScrollState())
            // User-directed: the same padding on every side — was
            // [ScreenSafeAreaHorizontal] (48dp) left/right vs
            // [TrayPaddingVertical] (16dp) top/bottom, a 3x mismatch that
            // read as far more empty margin on the sides than above/below.
            .padding(horizontal = TrayPaddingVertical, vertical = TrayPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        apps.forEachIndexed { index, app ->
            val isActiveDrag = editMode.isActive && editMode.activeId == app.packageName
            val isDimmed = editMode.isActive && !isActiveDrag
            val isOptionsMenuTarget = optionsMenuTargetId == app.packageName
            val ownFocusRequester = focusRequesters.getOrNull(index)
            val downTarget = downFocusRequesters.getOrNull(index.coerceAtMost(downFocusRequesters.size - 1))
            AppTile(
                app = app,
                onClick = { if (!editMode.isActive) onAppClick(app) },
                onFocus = { onAppFocused(app) },
                onLongPress = if (!editMode.isActive) onOpenOptionsMenu else null,
                isEditMode = editMode.isActive,
                isActiveDrag = isActiveDrag,
                isDimmed = isDimmed,
                isOptionsMenuTarget = isOptionsMenuTarget,
                jigglePhaseSeed = app.packageName.hashCode(),
                onPositioned = if (isOptionsMenuTarget) onTilePositioned else ({}),
                // 1.08x, not the grid's 1.15x default (PRODUCT_SPEC.md
                // §1.1) — a deliberate re-differentiation, not tied to
                // tile size (dock and grid tiles always match exactly —
                // this composable's own `tileWidth` param doc).
                focusedScale = 1.08f,
                showFocusLabel = false,
                // Same width/aspect as AppGrid's tiles — must stay
                // identical (this composable's own `tileWidth` param doc).
                modifier = Modifier
                    .width(tileWidth)
                    .aspectRatio(TileAspectRatio)
                    .then(
                        if (ownFocusRequester != null) Modifier.focusRequester(ownFocusRequester) else Modifier,
                    )
                    .focusProperties {
                        if (upFocusRequester != null) up = upFocusRequester
                        if (downTarget != null) down = downTarget
                    },
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(0)?.requestFocus()
    }
}
