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
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.domain.model.TvApp

/**
 * PRODUCT_SPEC.md §3.1's top-shelf tray — column-aligned with the grid below
 * it (Decisions Log: "Glass tray width/alignment" — an earlier version
 * shrank to a content-sized "snug" panel for a handful of tiles; that's
 * gone, this always spans the grid's own content width so tile columns line
 * up between the tray and every row beneath it), inset from the screen edge
 * by [TrayOuterMargin] so it reads as a floating card, not a full-bleed bar
 * (Decisions Log: "Glass tray outer margin"). Tiles here carry no per-tile
 * focus label (Decisions Log: "Tray tile focus label") — the tray's own
 * scale/glow motion already reads as "focused" without one.
 *
 * Translucent `colorScheme.surface` panel over a blurred crop of whatever's
 * behind it (`HeroBanner`'s own backdrop, via [blurSource]/`BackdropBlur.kt`)
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
    onAppFocused: (TvApp) -> Unit = {},
    editMode: EditModeState = EditModeState(),
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
) {
    val firstItemFocusRequester = remember { FocusRequester() }
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = TranslucentPanelAlpha))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenSafeAreaHorizontal, vertical = TrayPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
    ) {
        apps.forEachIndexed { index, app ->
            val isActiveDrag = editMode.isActive && editMode.activeId == app.packageName
            val isDimmed = editMode.isActive && !isActiveDrag
            val isOptionsMenuTarget = optionsMenuTargetId == app.packageName
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
                // tile size (both rows use the same TileWidth).
                focusedScale = 1.08f,
                showFocusLabel = false,
                // Same width/aspect as AppGrid's tiles — see Dimens.kt's
                // TileWidth doc for why these must stay identical.
                modifier = Modifier
                    .width(TileWidth)
                    .aspectRatio(TileAspectRatio)
                    .then(
                        if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    )
                    .then(
                        if (upFocusRequester != null) {
                            Modifier.focusProperties { up = upFocusRequester }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }

    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }
}
