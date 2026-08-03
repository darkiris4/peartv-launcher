package com.peartv.launcher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.ui.focus.DpadDirection
import com.peartv.launcher.ui.focus.LocalLastDpadDirection
import com.peartv.launcher.ui.focus.directionFromKey
import com.peartv.launcher.ui.theme.ambientBackground
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
 * focus"): hero, tray, and the grid each animate independently off
 * [expansionProgress] — expanded (focus is on a tray tile) and collapsed
 * (focus is in the grid) — see the hero `Box`'s own doc, below, for the
 * three-layer mechanics.
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
    val focusedItemId by viewModel.focusedItemId.collectAsStateWithLifecycle()
    val heroBackdrop by viewModel.heroBackdrop.collectAsStateWithLifecycle()
    val tier3Channels by viewModel.tier3Channels.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val openFolder by viewModel.openFolder.collectAsStateWithLifecycle()
    val openFolderRenameMode by viewModel.openFolderRenameMode.collectAsStateWithLifecycle()
    val optionsMenu by viewModel.optionsMenu.collectAsStateWithLifecycle()
    val pendingMerge by viewModel.pendingMerge.collectAsStateWithLifecycle()
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

    // Dock/grid column-aligned focus routing (user-reported: DPAD_UP from
    // the grid always landed on the dock's rightmost tile, not the column
    // actually above focus) — same "geometric search is unreliable" remedy
    // as `carouselFocusRequester` above, just at a different boundary. Both
    // lists are owned here, not by `TopShelfRow`/`AppGrid` individually,
    // since each composable needs the *other's* instances to point its own
    // `up`/`down` at (see each composable's own doc). `rowZeroCount` guards
    // against ever creating a `FocusRequester` that no tile will actually
    // attach to — a grid with fewer items than columns has empty slots in
    // its own row 0, and a `focusProperties` target that's never composed
    // throws when focus search actually tries to reach it.
    val dockApps = remember(dockItems) { dockItems.filterIsInstance<LauncherGridItem.AppItem>().map { it.app } }
    val dockFocusRequesters = remember(dockApps.size) { List(dockApps.size) { FocusRequester() } }

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
    // Keyed on `focusedItemId`, not `focusedApp` — user-reported bug fix:
    // `onFolderFocused` (unlike `onAppFocused`) never touches `focusedApp`,
    // since a folder has no single `TvApp` to give the hero, so it kept
    // whatever app was last genuinely focused. Landing on a folder in the
    // grid after leaving the dock left that stale value pointing at a dock
    // app, which this check read as "still in the dock" and never
    // collapsed the hero — confirmed on-device this only ever happened
    // dock→folder, never dock→app, since `onAppFocused` always overwrites
    // it correctly. `focusedItemId` (`LauncherViewModel`'s own doc: "App or
    // Folder alike") updates for both, so it's the one this needs.
    // Defaults to expanded (true) before the first focus event lands on cold
    // launch — TopShelfRow's own FocusRequester fires almost immediately, so
    // this is only ever the state for a single initial frame.
    val isTopShelfFocused = focusedItemId == null || focusedItemId in dockPackageNames

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
            .fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Captured into a plain local — `maxHeight` is a
            // `BoxWithConstraintsScope` receiver property, out of reach via
            // an implicit receiver from as deep as the grid/tray offset
            // modifiers below sit (nested inside `CompositionLocalProvider`
            // and two more `Box`es, each introducing their own `BoxScope`
            // receiver first) — a plain captured local has no such limit.
            val screenHeight = maxHeight

            // User-directed: dock and grid tiles are one uniform grid — same
            // size, same spacing, throughout. Tile size is solved *first*,
            // as if laying out 4 plain, identical rows (dock's own row
            // included) across the available height — the dock's own
            // container is what adapts to that afterward (below, at its own
            // call site), wrapping row 0's tiles in a background panel with
            // equal padding on every side, rather than the container
            // dictating a size the tiles have to fit. [TopShelfItemHeight]'s
            // reserved §1.4 focus-label space is only real for grid rows —
            // the dock shows no label — but every row still budgets for it
            // here so the underlying grid math treats all 4 rows the same;
            // the dock's own panel just doesn't use the full slot it's
            // given, its own doc explains why.
            val collapsedGridRows = 3
            val totalRows = collapsedGridRows + 1
            val availableForRows = screenHeight - ScreenSafeAreaVertical * 2
            val collapsedRowHeight = (availableForRows - TileSpacing * (totalRows - 1)) / totalRows
            val collapsedTileHeight = (collapsedRowHeight - FocusLabelSpacing - FocusLabelHeight).coerceAtLeast(TileHeight)
            val collapsedTileWidth = collapsedTileHeight * TileAspectRatio
            // The dock's own real container height at this tile size —
            // equal [TrayPaddingVertical] padding on every side (this
            // composable's own tray-positioning doc), not the full
            // [collapsedRowHeight] slot every row's math above assumes —
            // replaces the old, static `TopShelfTrayHeight` constant
            // everywhere below.
            val trayHeight = collapsedTileHeight + TrayPaddingVertical * 2
            // `trayHeight`, not `ScreenSafeAreaVertical + trayHeight` —
            // `AppGrid`'s own top `contentPadding` already adds
            // [ScreenSafeAreaVertical] again before its first row of tiles
            // actually renders, so including it here too would stack into
            // a ~43dp gap after the dock instead of the same ~24dp
            // [TileSpacing] every other row transition uses. This still
            // starts the dock itself [ScreenSafeAreaVertical] from the true
            // top (that offset is applied at the tray's own call site,
            // below) — only the *grid's* box position accounts for its own
            // padding canceling out here.
            val gridCollapsedTop = trayHeight + TileSpacing
            val collapsedGridHeight = screenHeight - gridCollapsedTop

            val columnCount = columnCount(maxWidth, collapsedTileWidth)
            // Only ever as many as the grid's own row 0 actually has tiles
            // for (see the `dockFocusRequesters` doc above) — a grid with
            // fewer items than columns can't fill a whole row 0.
            val rowZeroCount = minOf(columnCount, gridItems.size)
            val gridRowZeroFocusRequesters = remember(rowZeroCount) { List(rowZeroCount) { FocusRequester() } }
            // Don't reserve the full [collapsedGridRows]-row height when
            // there isn't enough content to need it — confirmed on-device
            // this read as dead space below the actual last row, not
            // "filling the screen." Caps at [collapsedGridRows]; scrolls
            // (unchanged `AppGrid` behavior) for anything beyond that.
            val actualGridRows = if (columnCount > 0) {
                ((gridItems.size + columnCount - 1) / columnCount).coerceIn(1, collapsedGridRows)
            } else {
                collapsedGridRows
            }
            val actualGridHeight = (
                ScreenSafeAreaVertical * 2 +
                    collapsedRowHeight * actualGridRows +
                    TileSpacing * (actualGridRows - 1).coerceAtLeast(0)
                ).coerceAtMost(collapsedGridHeight)

            // Tray's own expanded-position endpoint only — see the hero
            // Box's own doc below for why grid no longer uses this (it
            // now animates all the way to `maxHeight`, off-screen, instead
            // of stopping [HeroGridPeekHeight] short of it).
            val expandedHeroHeight = maxHeight - HeroGridPeekHeight

    CompositionLocalProvider(LocalLastDpadDirection provides lastDpadDirection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .ambientBackground()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        directionFromKey(event.key)?.let { lastDpadDirection = it }
                    }
                    // Edit Mode's direction-key interception must yield the
                    // instant an overlay (the popover, a folder, or the
                    // merge-confirm prompt) is on top — confirmed on-device
                    // that leaving this on unconditionally (whenever
                    // `editMode.isActive`) hijacked every arrow press for
                    // repositioning the *background* tile instead of letting
                    // the overlay's own rows/buttons navigate.
                    val editModeOwnsDirectionKeys = editMode.isActive && optionsMenu == null && openFolder == null && pendingMerge == null
                    when {
                        editModeOwnsDirectionKeys && directionFromKey(event.key) != null -> {
                            if (event.type == KeyEventType.KeyDown) {
                                directionFromKey(event.key)?.let { direction ->
                                    // Grid Reordering §8 (drag-to-merge) — a
                                    // tap (repeatCount 0, the OS's own signal
                                    // for "not held yet") always repositions,
                                    // exactly as before; only once the OS
                                    // starts auto-repeating the same held key
                                    // does continuing to aim at whatever's now
                                    // adjacent raise the merge-confirm prompt
                                    // instead of swapping through it — see
                                    // `LauncherViewModel.requestMerge`'s own
                                    // doc for why this split exists (a D-pad
                                    // press has no continuous hover state to
                                    // hang tvOS's own hold-to-merge gesture
                                    // on the way a touch-drag does).
                                    if (event.nativeKeyEvent.repeatCount == 0) {
                                        viewModel.moveActive(direction, columnCount)
                                    } else {
                                        viewModel.requestMerge()
                                    }
                                }
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
            // Collapsing-header behavior (Decisions Log: "Hero/tray collapse-
            // on-grid-focus"), reworked from a height-animating hero Box +
            // Column reflow into three independently-offset layers sharing
            // one Box — user-directed: the previous version's hero Box
            // shrank only down to `expandedHeroHeight` (= maxHeight minus
            // [HeroGridPeekHeight]), always leaving a thin sliver of grid
            // visible even at full expansion, and that sliver's own
            // `ambientBackground`-lit tone read as a hard seam against
            // hero's own vignette fading to a flat tone right above it —
            // confirmed on-device, survived even after fading the vignette
            // toward `ambientPanelTint()` instead of flat background (still
            // two independently-animated regions meeting at a boundary).
            // Hero's backdrop art now always fills the full screen (fading
            // its own opacity via [expansionProgress] as `contentAlpha`,
            // already wired for this) instead of shrinking away — no more
            // boundary for a seam to form at, because there's no more grid
            // sliver peeking out from under hero at all. Tray and grid each
            // animate their own Y offset directly instead of relying on
            // Column reflow (grid) / [Alignment.BottomCenter] of a resizing
            // parent (tray) to reposition them as hero's height changed.
            //
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
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                val primaryChannel = tier3Channels.firstOrNull()

                val trayOffsetY = lerp(ScreenSafeAreaVertical, expandedHeroHeight - trayHeight, expansionProgress)
                // How far above hero's own (now-static) bottom edge the
                // tray currently sits — passed down so the hero/carousel's
                // own title text can clear it correctly at every point in
                // the animation, not just at the two endpoints. See
                // `HeroBanner`'s `trayClearance` param doc.
                val trayClearance = screenHeight - trayOffsetY

                // Layer 1 (bottom): hero backdrop, always full-screen — see
                // this Box's own doc above for why this no longer animates
                // its own height. Composed for as long as any of its own
                // fade-out is still visible ([expansionProgress] > 0), not
                // gated on the discrete [isTopShelfFocused] boolean — that
                // flips the instant focus leaves the tray, which previously
                // unmounted this composable on the very same frame, before
                // its own `contentAlpha` fade (already wired below) ever
                // got to animate — confirmed on-device as a hard cut, not a
                // fade, despite `contentAlpha` being correctly threaded
                // through this whole time.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { onHeroPositioned(it.positionInWindow()) },
                ) {
                    if (expansionProgress > 0f) {
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
                                    resolveBackdropUrl = viewModel::resolveTmdbBackdropUrl,
                                    focusRequester = carouselFocusRequester,
                                    upFocusRequester = settingsFocusRequester,
                                    trayClearance = trayClearance,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(expansionProgress),
                                )
                            }
                        } else {
                            HeroBanner(
                                activeApp = focusedApp,
                                heroBackdrop = heroBackdrop,
                                backdropSourceLayer = heroBackdropLayer,
                                contentAlpha = expansionProgress,
                                trayClearance = trayClearance,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // Layer 2 (middle): grid, slides up from fully below the
                // fold (expanded, [expansionProgress] = 1) into its resting
                // position right below where the tray sits when collapsed
                // ([expansionProgress] = 0) — same resting position/height
                // the old `Modifier.weight(AppGridWeight)` inside a Column
                // gave it, just computed directly now that this isn't a
                // Column anymore.
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
                        columnCount = columnCount,
                        upFocusRequesters = dockFocusRequesters,
                        rowZeroFocusRequesters = gridRowZeroFocusRequesters,
                        tileWidth = collapsedTileWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(actualGridHeight)
                            .offset(y = lerp(screenHeight, gridCollapsedTop, 1f - expansionProgress)),
                    )
                }

                // Layer 3 (top): tray — always fully opaque/visible, drawn
                // last so grid tiles sliding up under it (previous layer)
                // never cover it mid-transition. Collapsed position is
                // [ScreenSafeAreaVertical] from the top, the same margin the
                // grid's own contentPadding uses; expanded position is the
                // same [Alignment.BottomCenter]-of-hero spot the old height-
                // animating hero Box used to produce.
                if (dockItems.isNotEmpty()) {
                    TopShelfRow(
                        apps = dockApps,
                        onAppClick = viewModel::onAppClick,
                        blurSource = heroBackdropLayer,
                        // User-directed: Up from a dock tile should always
                        // reach something — the carousel when this app has
                        // one, straight to Settings otherwise (there's no
                        // "top shelf content" to stop at for a Tier 1/2 app).
                        upFocusRequester = if (primaryChannel != null) carouselFocusRequester else settingsFocusRequester,
                        focusRequesters = dockFocusRequesters,
                        downFocusRequesters = gridRowZeroFocusRequesters,
                        onAppFocused = viewModel::onAppFocused,
                        editMode = editMode,
                        optionsMenuTargetId = optionsMenu?.targetId,
                        onOpenOptionsMenu = viewModel::openOptionsMenu,
                        onTilePositioned = onTilePositioned,
                        tileWidth = collapsedTileWidth,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = trayOffsetY),
                    )
                }
            }

            if (editMode.isActive && pendingMerge == null) {
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
                    enterRenameMode = openFolderRenameMode,
                    onRename = { viewModel.renameFolder(openFolderValue.id, it) },
                    onAppClick = viewModel::onAppClick,
                    onAppFocused = viewModel::onAppFocused,
                    optionsMenuTargetId = optionsMenu?.targetId,
                    onOpenOptionsMenu = viewModel::openOptionsMenu,
                    onTilePositioned = onTilePositioned,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val pendingMergeValue = pendingMerge
            if (pendingMergeValue != null) {
                MergeConfirmPrompt(
                    activeLabel = pendingMergeValue.activeLabel,
                    targetLabel = pendingMergeValue.targetLabel,
                    onConfirm = viewModel::confirmMerge,
                    onCancel = viewModel::cancelMerge,
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

/** Mirrors `GridCells.FixedSize(tileWidth)`'s own column-count math (floor of available/cell width) — `AppGrid`'s real layout, not a guess, so Edit Mode's row-based Up/Down movement lines up with what's actually on screen. [tileWidth] defaults to the shared tray/grid constant, but the collapsed grid's own larger, screen-height-derived size (`LauncherScreen`'s own doc on that) must be passed here explicitly once it diverges from that default — this is real column math, not cosmetic, and Edit Mode's movement breaks silently if it drifts from whatever `AppGrid` is actually laying out. */
private fun columnCount(maxWidth: androidx.compose.ui.unit.Dp, tileWidth: androidx.compose.ui.unit.Dp = TileWidth): Int {
    val available = maxWidth - ScreenSafeAreaHorizontal * 2
    return ((available + TileSpacing) / (tileWidth + TileSpacing)).toInt().coerceAtLeast(1)
}

/** Hero collapse/expand transition duration — not `private`: `MainActivity` reuses this so the status pill's own fade (see its own doc) settles in lockstep with the hero it's tracking, not some independently-guessed duration. */
const val HeroExpansionMillis = 350
