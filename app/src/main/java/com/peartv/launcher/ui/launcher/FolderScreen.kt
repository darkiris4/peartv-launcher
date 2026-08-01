package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.domain.model.TvApp

/**
 * Grid Reordering & Folders §5 "Open State (Full-Screen Focus)" — a centered
 * modal over a dimmed scrim (real backdrop blur skipped here: this is a
 * genuinely full-screen transient surface, not the tray's small
 * always-visible panel, and it would be invisible on this project's actual
 * API 30 reference hardware regardless — same call as `FolderTile`'s own
 * scope note).
 *
 * The title is the modal's first (and only initially reachable) focus
 * target — "immediately focus the inline text field" (Decision #5's Tier 3
 * fallback-name case) is approximated as "the title already has D-pad focus
 * one Select-press away," rather than auto-invoking the system IME from a
 * non-gesture code path, which is fragile on real Android TV devices and not
 * something this session can iterate on without a live device in front of
 * it. Pressing Down moves focus from the title into the sub-grid.
 *
 * A long-press on any tile here opens the same Options popover as the root
 * grid ([onOpenOptionsMenu]/[optionsMenuTargetId]/[onTilePositioned] mirror
 * `AppGrid`'s identical params) — this is the actual answer to "how do you
 * get an app back out of a folder": open the folder, long-press the app,
 * "Move to… > Home Screen." There's no reordering *within* an open folder in
 * this pass (no jiggle here) — only the popover's own actions apply.
 */
@Composable
fun FolderScreen(
    folder: LauncherGridItem.FolderItem,
    onRename: (String) -> Unit,
    onAppClick: (TvApp) -> Unit,
    onAppFocused: (TvApp) -> Unit,
    modifier: Modifier = Modifier,
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    var titleInput by remember(folder.id) { mutableStateOf(folder.name) }

    // Plain opaque background, not a translucent scrim — the underlying grid
    // is fully hidden either way since the modal itself is opaque; the
    // earlier translucent version was removed per the Decisions Log's
    // "§3.1.1 'liquid glass' tray/pill styling — removed" entry.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(FolderModalWidth)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(FolderModalPadding),
        ) {
            BasicTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it
                    onRename(it)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .focusRequester(titleFocusRequester)
                    // Same DPAD_DOWN trap fix as SettingsScreen's API-key
                    // field — a single-line field has nowhere for cursor-
                    // vertical-movement to go, so redirect it to real focus
                    // movement into the sub-grid below instead.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) return@onPreviewKeyEvent false
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    },
            )

            Spacer(modifier = Modifier.height(FolderTitleSpacing))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                verticalArrangement = Arrangement.spacedBy(TileSpacing),
                // Top-row tiles scale up to 1.15x on focus (AppTile's default
                // focusedScale) — with zero content padding here, that extra
                // height overflowed straight into this grid's own clip
                // bounds and got cut off (confirmed on-device). AppGrid
                // itself never hit this because it already reserves
                // ScreenSafeAreaVertical up top for the same reason.
                contentPadding = PaddingValues(top = FolderGridTopPadding, bottom = FolderGridTopPadding),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(folder.apps, key = { it.packageName }) { app ->
                    val isOptionsMenuTarget = optionsMenuTargetId == app.packageName
                    AppTile(
                        app = app,
                        onClick = { onAppClick(app) },
                        onFocus = { onAppFocused(app) },
                        onLongPress = onOpenOptionsMenu,
                        isOptionsMenuTarget = isOptionsMenuTarget,
                        onPositioned = if (isOptionsMenuTarget) onTilePositioned else ({}),
                        modifier = Modifier.width(TileWidth).aspectRatio(TileAspectRatio),
                    )
                }
            }
        }
    }

    LaunchedEffect(folder.id) {
        titleFocusRequester.requestFocus()
    }
}
