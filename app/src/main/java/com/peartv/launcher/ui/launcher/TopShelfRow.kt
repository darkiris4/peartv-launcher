package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.LaunchOrigin

/**
 * PRODUCT_SPEC.md §3.1's top-shelf tray, inset from the screen edge by
 * [TrayOuterMargin] so it reads as a floating card, not a full-bleed bar.
 * Tiles here carry no per-tile focus label — the tray's own scale/glow focus
 * motion already reads as "focused" without one.
 *
 * Liquid-Glass-style panel: a live `RenderEffect` Gaussian blur of whatever
 * hero/carousel content sits behind it ([backdropLayer], recorded by
 * [BackdropCapture] in `LauncherScreen`) plus one fixed translucent [glassTint]
 * on top, then [LiquidGlassTopHighlight]'s specular top edge. No content-
 * sourcing tiers or artwork plumbing anymore — the blur simply follows the
 * on-screen composite, so Tier 1/2 backdrops read as glass exactly the same
 * as Tier 3's rotating channel art (Decisions Log: "Live RenderEffect
 * backdrop blur").
 *
 * Always the first focus target on cold launch (§1.3), which is why item 0
 * owns a [FocusRequester] that fires as soon as this row enters composition.
 *
 * [upFocusRequester], when non-null, is wired as every tile's explicit `up`
 * focus target (PRODUCT_SPEC.md §1.3's remedy for unreliable geometric focus
 * search). [focusRequesters]/[downFocusRequesters] fix the same class of bug
 * at the dock/grid boundary — [focusRequesters] is one `FocusRequester` per
 * tile, owned by `LauncherScreen` since `AppGrid`'s row-0 tiles point their
 * own `up` back at these; [downFocusRequesters] is each tile's explicit
 * `down` target, one per grid column.
 *
 * Grid Reordering & Folders — [apps] threads edit-mode visuals/long-press
 * through the same as `AppGrid`; the dock is App-only, so nothing here needs
 * folder-specific handling.
 */
@Composable
fun TopShelfRow(
    apps: List<TvApp>,
    onAppClick: (TvApp, LaunchOrigin?) -> Unit,
    backdropLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    focusRequesters: List<FocusRequester> = emptyList(),
    downFocusRequesters: List<FocusRequester> = emptyList(),
    onAppFocused: (TvApp) -> Unit = {},
    editMode: EditModeState = EditModeState(),
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
    tileWidth: Dp = TileWidth,
    blurRadius: Dp = DockBlurRadius,
) {
    val shape = RoundedCornerShape(TrayCornerRadius)
    val tint = glassTint()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TrayOuterMargin)
            // A soft drop shadow so the glass tray reads as floating above
            // the surface behind it (user-directed) — subtle, and naturally
            // near-invisible on a dark background.
            .shadow(TrayShadowElevation, shape, clip = false)
            .clip(shape)
            .backdropBlur(backdropLayer, tint, blurRadius),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = TrayPaddingVertical, horizontal = TrayPaddingVertical),
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
                    onClick = { origin -> if (!editMode.isActive) onAppClick(app, origin) },
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
                    // tile size (dock and grid tiles always match exactly).
                    focusedScale = 1.08f,
                    showFocusLabel = false,
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

        LiquidGlassTopHighlight()
    }

    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(0)?.requestFocus()
    }
}
