package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import com.peartv.launcher.domain.model.TvApp

/**
 * PRODUCT_SPEC.md §3.1 — the general app grid below the top shelf.
 *
 * `GridCells.FixedSize(TileWidth)` — not `Fixed(N)` — computes however many
 * columns fit at exactly [TileWidth] each, matching the `peartv` reference's
 * `repeat(auto-fill, 200px)` and (crucially) keeping tiles here pixel-
 * identical to the top-shelf tray's tiles, which use the same constant.
 *
 * `FixedSize` only computes *how many* columns fit — it doesn't center the
 * resulting block, so any leftover width (when column-count × tile-width
 * doesn't exactly fill the available space) sat as dead space on the right,
 * reading as off-center. `Arrangement.spacedBy(TileSpacing,
 * Alignment.CenterHorizontally)` centers each row's tiles as a group
 * instead, confirmed on-device (Decisions Log: "Grid centering").
 *
 * Grid Reordering & Folders — renders [LauncherGridItem] (App or Folder), not
 * a raw [TvApp] list, and threads [editMode] through to every tile for the
 * jiggle/dim/active visuals (`AppTile`/`FolderTile`'s own docs). Only the
 * item matching [EditModeState.activeId] is reachable for D-pad navigation
 * while [EditModeState.isActive] is true — see `LauncherScreen`'s root key
 * handling, which consumes every direction press as a move command instead
 * of letting Compose's normal focus-search run.
 *
 * A long-press always opens the Options popover ([onOpenOptionsMenu]) first
 * (user-directed revision — see `LauncherViewModel`'s class doc); Edit Mode
 * is one of *its* actions, not the long-press's own direct effect, so
 * long-press is wired to nothing further once already dragging.
 * [optionsMenuTargetId] drives the plain dim-only treatment on whichever
 * tile the popover is currently open on, and [onTilePositioned] reports that
 * same tile's real screen bounds so the popover knows where to anchor
 * itself (`LauncherScreen` only cares about the coordinates for that one
 * tile at a time).
 */
@Composable
fun AppGrid(
    items: List<LauncherGridItem>,
    onAppClick: (TvApp) -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAppFocused: (TvApp) -> Unit = {},
    onFolderFocused: (String) -> Unit = {},
    editMode: EditModeState = EditModeState(),
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(TileWidth),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TileSpacing, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(TileSpacing),
        contentPadding = PaddingValues(
            horizontal = ScreenSafeAreaHorizontal,
            vertical = ScreenSafeAreaVertical,
        ),
    ) {
        items(items, key = { it.id }) { item ->
            val isActiveDrag = editMode.isActive && editMode.activeId == item.id
            val isDimmed = editMode.isActive && !isActiveDrag
            val isOptionsMenuTarget = optionsMenuTargetId == item.id
            val tileModifier = Modifier.width(TileWidth).aspectRatio(TileAspectRatio)
            val longPress: (() -> Unit)? = if (!editMode.isActive) onOpenOptionsMenu else null
            val positioned: (LayoutCoordinates) -> Unit = if (isOptionsMenuTarget) onTilePositioned else ({})

            when (item) {
                is LauncherGridItem.AppItem -> AppTile(
                    app = item.app,
                    onClick = { if (!editMode.isActive) onAppClick(item.app) },
                    onFocus = { onAppFocused(item.app) },
                    onLongPress = longPress,
                    isEditMode = editMode.isActive,
                    isActiveDrag = isActiveDrag,
                    isDimmed = isDimmed,
                    isOptionsMenuTarget = isOptionsMenuTarget,
                    jigglePhaseSeed = item.id.hashCode(),
                    onPositioned = positioned,
                    modifier = tileModifier,
                )
                is LauncherGridItem.FolderItem -> FolderTile(
                    folder = item,
                    onClick = { if (!editMode.isActive) onFolderClick(item.id) },
                    onFocus = { onFolderFocused(item.id) },
                    onLongPress = longPress,
                    isEditMode = editMode.isActive,
                    isActiveDrag = isActiveDrag,
                    isDimmed = isDimmed,
                    isOptionsMenuTarget = isOptionsMenuTarget,
                    jigglePhaseSeed = item.id.hashCode(),
                    onPositioned = positioned,
                    modifier = tileModifier,
                )
            }
        }
    }
}
