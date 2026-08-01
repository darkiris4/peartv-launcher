package com.peartv.launcher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.ui.focus.DpadDirection
import com.peartv.launcher.ui.focus.LocalLastDpadDirection
import com.peartv.launcher.ui.focus.directionFromKey
import kotlin.math.roundToInt

/**
 * PRODUCT_SPEC.md §3.1 — hero (backdrop + metadata) with the top-shelf tray
 * overlapping its lower edge, then the app grid below.
 *
 * [heroBackdropLayer]/[onHeroPositioned] are hoisted up to `MainActivity`
 * rather than owned here, since `StatusBar` (a sibling of this whole screen,
 * not a descendant of it) needs the same recorded hero content for its own
 * blurred backdrop — see `BackdropBlur.kt`. This composable's only jobs with
 * them: report the hero region's real window position on every layout pass,
 * and hand the shared layer to [HeroBanner] (to record into) and
 * [TopShelfRow] (to blur a crop of).
 *
 * Structural hierarchy matches `../hamtv/public/index.html` (the separate,
 * unrenamed sibling web-prototype project this was originally reverse-
 * engineered from — see `PRODUCT_SPEC.md` §3.1.1 — not this project): the hero
 * container ([HeroBanner]) never contains cards — it's purely a backdrop/
 * metadata surface that reacts to the focused app. The tray ([TopShelfRow])
 * is a sibling, positioned via [Alignment.BottomCenter] inside the same
 * [Box] so it visually overlaps the hero's bottom edge without being a child
 * of it.
 *
 * Owns the shared "last D-pad direction" state that [com.peartv.launcher.ui.focus.tvOSFocusable]
 * reads to pick each tile's tilt origin (§1.2) — captured once here via
 * `onPreviewKeyEvent` rather than duplicated per tile.
 *
 * Collapsing-header behavior (Decisions Log: "Hero/tray collapse-on-grid-
 * focus"): [heroHeight] animates between two endpoints — expanded (focus is
 * on a tray tile) and collapsed (focus is in the grid).
 *
 * Grid Reordering & Folders — this composable takes [viewModel] directly
 * (not exploded into ~20 individual state/callback parameters, unlike the
 * rest of this file's usual "props in, callbacks out" convention) purely
 * because of how much this feature adds: Edit Mode, the open-folder modal,
 * and the Options popover each need several pieces of state and several
 * actions, and the resulting parameter list was worse for readability than
 * accepting the ViewModel directly. Root-level `onPreviewKeyEvent` here owns
 * exactly two new things Edit Mode needs that individual tiles can't own
 * themselves: direction-key interception (moving the active tile instead of
 * Compose's normal focus-search) and the Menu key (an alternate way to open
 * the Options popover, kept in case some remote actually sends it, though a
 * long-press already opens it too — see `AppTile`'s doc). This file also
 * tracks two small pieces of transient (non-ViewModel) coordinate state
 * purely so the Options popover can anchor itself next to whichever tile
 * opened it rather than appear as a centered modal — see the
 * `tileWindowPosition` comment below.
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    heroBackdropLayer: GraphicsLayer,
    onHeroPositioned: (Offset) -> Unit,
    settingsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val dockItems by viewModel.dockItems.collectAsStateWithLifecycle()
    val gridItems by viewModel.gridItems.collectAsStateWithLifecycle()
    val focusedApp by viewModel.focusedApp.collectAsStateWithLifecycle()
    val heroBackdrop by viewModel.heroBackdrop.collectAsStateWithLifecycle()
    val tier3Channels by viewModel.tier3Channels.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val openFolder by viewModel.openFolder.collectAsStateWithLifecycle()
    val optionsMenu by viewModel.optionsMenu.collectAsStateWithLifecycle()
    val hasDismissedChannelsPrompt by viewModel.hasDismissedChannelsPrompt.collectAsStateWithLifecycle()
    val gridFolders = remember(gridItems) { gridItems.filterIsInstance<LauncherGridItem.FolderItem>() }

    // First-launch Channels permission prompt (Decisions Log) — only when
    // nothing else is already claiming focus/Back, so it never competes with
    // Edit Mode/the Options popover/an open folder (extremely unlikely to
    // overlap in practice, since this only ever shows once, near cold
    // launch, but defensive rather than assumed).
    val isChannelsPermissionGranted = rememberIsChannelsPermissionGranted()
    val showChannelsPrompt = !hasDismissedChannelsPrompt && !isChannelsPermissionGranted &&
        optionsMenu == null && openFolder == null && !editMode.isActive

    var lastDpadDirection by remember { mutableStateOf<DpadDirection?>(null) }

    // `ContentCarousel`'s explicit `up` target for `TopShelfRow`'s tiles (see
    // that composable's own doc) — Compose's default geometric focus-search
    // failed to route DPAD_UP from a dock tile into the carousel at all,
    // confirmed on-device (one full-screen-sized target overlapping the same
    // hero `Box` the tray sits inside of, unlike the old per-poster
    // `ContentRows`).
    val carouselFocusRequester = remember { FocusRequester() }

    // Options popover anchoring — real window (absolute screen) pixel
    // coordinates, which is exactly the coordinate space `Popup`'s own
    // `PopupPositionProvider` operates in (`OptionsMenu.kt`'s
    // `TileAnchoredPositionProvider`), so no manual conversion against this
    // file's own nested/padded local coordinate spaces is needed.
    var tileWindowPosition by remember { mutableStateOf<Offset?>(null) }
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    val onTilePositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = { coordinates ->
        tileWindowPosition = coordinates.positionInWindow()
        tileSize = coordinates.size
    }

    val dockPackageNames = remember(dockItems) { dockItems.map { it.id }.toSet() }
    // Defaults to expanded (true) before the first focus event lands on cold
    // launch — TopShelfRow's own FocusRequester fires almost immediately, so
    // this is only ever the state for a single initial frame.
    val isTopShelfFocused = focusedApp == null || focusedApp?.packageName in dockPackageNames

    val expansionProgress by animateFloatAsState(
        targetValue = if (isTopShelfFocused) 1f else 0f,
        animationSpec = tween(HeroExpansionMillis),
        label = "heroExpansion",
    )

    BackHandler(enabled = optionsMenu != null || openFolder != null || editMode.isActive || showChannelsPrompt) {
        when {
            optionsMenu != null -> viewModel.closeOptionsMenu()
            openFolder != null -> viewModel.closeFolder()
            editMode.isActive -> viewModel.exitEditMode()
            showChannelsPrompt -> viewModel.dismissChannelsPrompt()
        }
    }

    Box(
        // No top inset reserved for StatusBar here anymore (user-directed:
        // the hero/tray region should reach the true top edge of the
        // screen, not be pushed down/clipped to avoid the clock/settings
        // pill). StatusBar is a separate overlay (MainActivity), drawn
        // after this content in the same Box, so it already floats visually
        // on top with no z-order changes needed — the earlier inset was
        // purely to stop the *collapsed* tray (which can reach flush with
        // the top edge) from visually colliding with the pill; that
        // trade-off is intentionally accepted now in favor of an unclipped
        // hero.
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnCount = columnCount(maxWidth)
            val expandedHeroHeight = maxHeight - HeroGridPeekHeight
            val heroHeight = TopShelfTrayHeight + (expandedHeroHeight - TopShelfTrayHeight) * expansionProgress

    CompositionLocalProvider(LocalLastDpadDirection provides lastDpadDirection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        directionFromKey(event.key)?.let { lastDpadDirection = it }
                    }
                    // Edit Mode's direction-key interception must yield the
                    // instant an overlay (the popover or a folder) is on top
                    // — confirmed on-device that leaving this on
                    // unconditionally (whenever `editMode.isActive`) hijacked
                    // every arrow press for repositioning the *background*
                    // tile instead of letting the popover's own rows navigate.
                    val editModeOwnsDirectionKeys = editMode.isActive && optionsMenu == null && openFolder == null
                    when {
                        editModeOwnsDirectionKeys && directionFromKey(event.key) != null -> {
                            if (event.type == KeyEventType.KeyDown) {
                                directionFromKey(event.key)?.let { viewModel.moveActive(it, columnCount) }
                            }
                            true
                        }
                        // Alternate path into the Options popover, in case some
                        // remote actually sends KEYCODE_MENU (this one, confirmed
                        // on-device, doesn't) — a long-press already opens it too.
                        optionsMenu == null && event.key == Key.Menu && event.type == KeyEventType.KeyDown -> {
                            viewModel.openOptionsMenu()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Column(
                // Deliberately NOT blocking background focus with
                // `focusProperties { canFocus = false }` while an overlay is
                // open, despite the theoretical risk of D-pad focus-search
                // reaching an obscured tile behind it: confirmed on a real
                // device that toggling a subtree's focusability in the same
                // recomposition pass where `OptionsMenu`/`FolderScreen`
                // request focus on themselves crashes with "ActiveParent with
                // no focused child" — a real Compose focus-system ordering
                // hazard, not something worth fighting for a polish item this
                // minor. The overlay's own initial `requestFocus()` already
                // wins focus in practice.
                modifier = Modifier.fillMaxSize(),
            ) {
                val primaryChannel = tier3Channels.firstOrNull()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .onGloballyPositioned { onHeroPositioned(it.positionInWindow()) },
                ) {
                    if (isTopShelfFocused) {
                        if (primaryChannel != null) {
                            // Rapid dock navigation can swap `primaryChannel`
                            // several times within one poster's hold window.
                            // `key()` forces a full dispose/recompose of the
                            // carousel on each channel change instead of
                            // relying on its internal `remember(channel)` to
                            // reset in place — the latter left the auto-scroll
                            // timer permanently orphaned after fast channel
                            // churn (user-reported: "auto-scrolling stopped").
                            androidx.compose.runtime.key(primaryChannel) {
                                ContentCarousel(
                                    channel = primaryChannel,
                                    onProgramClick = viewModel::onProgramClick,
                                    focusRequester = carouselFocusRequester,
                                    upFocusRequester = settingsFocusRequester,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            HeroBanner(
                                activeApp = focusedApp,
                                heroBackdrop = heroBackdrop,
                                backdropSourceLayer = heroBackdropLayer,
                                contentAlpha = expansionProgress,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    if (dockItems.isNotEmpty()) {
                        TopShelfRow(
                            apps = dockItems.filterIsInstance<LauncherGridItem.AppItem>().map { it.app },
                            onAppClick = viewModel::onAppClick,
                            blurSource = heroBackdropLayer,
                            // User-directed: Up from a dock tile should always
                            // reach something — the carousel when this app has
                            // one, straight to Settings otherwise (there's no
                            // "top shelf content" to stop at for a Tier 1/2 app).
                            upFocusRequester = if (primaryChannel != null) carouselFocusRequester else settingsFocusRequester,
                            onAppFocused = viewModel::onAppFocused,
                            editMode = editMode,
                            optionsMenuTargetId = optionsMenu?.targetId,
                            onOpenOptionsMenu = viewModel::openOptionsMenu,
                            onTilePositioned = onTilePositioned,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
                if (gridItems.isNotEmpty()) {
                    AppGrid(
                        items = gridItems,
                        onAppClick = viewModel::onAppClick,
                        onFolderClick = viewModel::openFolder,
                        onAppFocused = viewModel::onAppFocused,
                        onFolderFocused = viewModel::onFolderFocused,
                        editMode = editMode,
                        optionsMenuTargetId = optionsMenu?.targetId,
                        onOpenOptionsMenu = viewModel::openOptionsMenu,
                        onTilePositioned = onTilePositioned,
                        modifier = Modifier.weight(AppGridWeight),
                    )
                }
            }

            if (editMode.isActive) {
                EditModeHint(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
            }

            val openFolderValue = openFolder
            if (openFolderValue != null) {
                FolderScreen(
                    folder = openFolderValue,
                    onRename = { viewModel.renameFolder(openFolderValue.id, it) },
                    onAppClick = viewModel::onAppClick,
                    onAppFocused = viewModel::onAppFocused,
                    optionsMenuTargetId = optionsMenu?.targetId,
                    onOpenOptionsMenu = viewModel::openOptionsMenu,
                    onTilePositioned = onTilePositioned,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val optionsMenuValue = optionsMenu
            if (optionsMenuValue != null) {
                val targetId = optionsMenuValue.targetId
                val isInsideFolder = remember(targetId, gridFolders) {
                    gridFolders.any { folder -> folder.apps.any { it.packageName == targetId } }
                }
                val isFolderTarget = remember(targetId, gridFolders) { gridFolders.any { it.id == targetId } }
                val tileBoundsInWindow = remember(tileWindowPosition, tileSize) {
                    val position = tileWindowPosition ?: Offset.Zero
                    androidx.compose.ui.unit.IntRect(
                        left = position.x.roundToInt(),
                        top = position.y.roundToInt(),
                        right = (position.x + tileSize.width).roundToInt(),
                        bottom = (position.y + tileSize.height).roundToInt(),
                    )
                }
                OptionsMenu(
                    isInsideFolder = isInsideFolder,
                    isFolderTarget = isFolderTarget,
                    folders = gridFolders,
                    tileBoundsInWindow = tileBoundsInWindow,
                    onEditHomeScreen = viewModel::startEditHomeScreen,
                    onMoveToFolder = viewModel::optionsMoveToFolder,
                    onNewFolder = viewModel::optionsNewFolder,
                    onEjectFromFolder = viewModel::optionsEjectFromFolder,
                    onDeleteApp = viewModel::optionsDeleteApp,
                    onDismiss = viewModel::closeOptionsMenu,
                )
            }

            if (showChannelsPrompt) {
                ChannelsPermissionPrompt(
                    onDismiss = viewModel::dismissChannelsPrompt,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
    }
    }
}

/** Mirrors `GridCells.FixedSize(TileWidth)`'s own column-count math (floor of available/cell width) — `AppGrid`'s real layout, not a guess, so Edit Mode's row-based Up/Down movement lines up with what's actually on screen. */
private fun columnCount(maxWidth: androidx.compose.ui.unit.Dp): Int {
    val available = maxWidth - ScreenSafeAreaHorizontal * 2
    return ((available + TileSpacing) / (TileWidth + TileSpacing)).toInt().coerceAtLeast(1)
}

/** Hero collapse/expand transition duration. */
private const val HeroExpansionMillis = 350
