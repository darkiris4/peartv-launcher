package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
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
 *
 * [columnCount]/[upFocusRequesters] fix a real on-device navigation bug:
 * Compose's default geometric focus search has no notion of this grid's own
 * row-major layout, so `DPAD_UP` from row 0 consistently landed on the
 * dock's *last* tile rather than the column directly above wherever focus
 * actually was — the same "geometric search is unreliable" boundary
 * PRODUCT_SPEC.md §1.3 already documents (`TopShelfRow`'s own `up` wiring to
 * the carousel/settings is the same remedy applied at a different
 * boundary). Only row 0 items (`index < columnCount`, this grid's own
 * row-major order) get an explicit `up` target — every other row already
 * has an unambiguous grid tile directly above it, so default search is left
 * alone there. [upFocusRequesters] is indexed by column, one
 * `FocusRequester` per dock tile, owned by `LauncherScreen` (not created
 * here) since `TopShelfRow` needs the very same instances to point its own
 * tiles' `down` back at this grid's row 0 — see that composable's own doc.
 *
 * [rowZeroFocusRequesters], unlike [upFocusRequesters], must actually be
 * *attached* here via `Modifier.focusRequester` (not just pointed at) —
 * `TopShelfRow`'s own tiles hold the other end, targeting these as their own
 * `down`. A `FocusRequester` a `focusProperties` block merely points at, but
 * that no node ever attaches to, throws `IllegalStateException` the instant
 * focus search actually tries to reach it — confirmed on-device (crashed on
 * every `DPAD_DOWN` from the dock) after an earlier pass wired these into
 * `TopShelfRow` as `down` targets but forgot this half entirely.
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
    columnCount: Int = 0,
    upFocusRequesters: List<FocusRequester> = emptyList(),
    rowZeroFocusRequesters: List<FocusRequester> = emptyList(),
    // Defaults to the shared tray/grid constant (Dimens.kt's own doc on
    // why tray and grid tiles normally match), but `LauncherScreen` passes
    // a larger, screen-height-derived size for its own collapsed layout —
    // user-directed: the dock (its own row) plus exactly 3 grid rows
    // should fill the screen, standard [TileSpacing] between them — grown
    // instead of the tray's fixed size once the two were no longer
    // interleaved on screen together (the tray sits well above the grid
    // now, not sharing a row with it), so there was no longer a strong
    // reason to keep grid tiles pinned to the tray's own smaller size.
    tileWidth: Dp = TileWidth,
) {
    LazyVerticalGrid(
        columns = GridCells.FixedSize(tileWidth),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TileSpacing, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(TileSpacing),
        contentPadding = PaddingValues(
            horizontal = ScreenSafeAreaHorizontal,
            vertical = ScreenSafeAreaVertical,
        ),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isActiveDrag = editMode.isActive && editMode.activeId == item.id
            val isDimmed = editMode.isActive && !isActiveDrag
            val isOptionsMenuTarget = optionsMenuTargetId == item.id
            val isRowZero = index < columnCount
            val upTarget = if (isRowZero) upFocusRequesters.getOrNull(index.coerceAtMost(upFocusRequesters.size - 1)) else null
            val ownFocusRequester = if (isRowZero) rowZeroFocusRequesters.getOrNull(index) else null
            val tileModifier = Modifier.width(tileWidth).aspectRatio(TileAspectRatio)
                .let { base -> if (ownFocusRequester != null) base.focusRequester(ownFocusRequester) else base }
                .let { base -> if (upTarget != null) base.focusProperties { up = upTarget } else base }
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
