package com.peartv.launcher.ui.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.ui.focus.tvOSFocusable
import kotlinx.coroutines.delay

/**
 * Grid Reordering & Folders — the small anchored "liquid glass" popover a
 * long-press opens on whatever tile currently has focus
 * (`design/editHomeScreen.png`, user-directed model replacing the original
 * double-long-press design). Two top-level rows — Edit Home Screen / Move
 * to… — plus Delete App (red, per user direction) as a third; "Move to…"
 * drills into a folder list + "+ New Folder" in place, going back to the
 * root two/three rows on Back rather than closing the whole popover in one
 * step (see [onDismiss] below) — there's no explicit Cancel row, matching
 * the design reference.
 *
 * Real backdrop blur (matching the tray's SDK-gated approach) isn't used
 * here either, same reasoning as `FolderTile`'s own scope note — a small
 * popover that can appear anywhere in a scrolling grid, on hardware that
 * can't render the blur anyway. Plain translucent glass tint instead.
 *
 * [tileBoundsInWindow] is the long-pressed tile's real on-screen bounds, in
 * absolute window pixels (`LauncherScreen` captures this via
 * `onGloballyPositioned`/`positionInWindow()` on that one tile). Positioning
 * itself is a real [Popup] with a custom [PopupPositionProvider]
 * ([TileAnchoredPositionProvider]) rather than a plain `Modifier.offset` —
 * needed so a tile near the *bottom* of the screen (or in the dock, which
 * this project's own collapsing-hero design can put anywhere from near the
 * top to near the bottom of the screen) still gets a popover that's
 * fully on-screen: `calculatePosition` receives the popover's real measured
 * size and the window's real size in the same pass, so both the right edge
 * and the bottom edge can be clamped correctly, not just the one a simpler
 * `Modifier.offset` approach could account for.
 *
 * Back is handled via [Popup]'s own `dismissOnBackPress`/`onDismissRequest`,
 * not `LauncherScreen`'s outer `BackHandler` — confirmed on-device that a
 * `Popup` on Android owns its own window and swallows the Back key inside
 * it when `dismissOnBackPress = false`, meaning the outer `BackHandler`
 * (registered against the *activity's* dispatcher) never even sees the
 * press while this is showing. [onDismiss] steps back out of "Move to…"
 * first, matching the design's drill-down, and only closes the popover
 * entirely from the root two/three rows.
 */
@Composable
fun OptionsMenu(
    isInsideFolder: Boolean,
    isFolderTarget: Boolean,
    folders: List<LauncherGridItem.FolderItem>,
    tileBoundsInWindow: IntRect,
    onEditHomeScreen: () -> Unit,
    onMoveToFolder: (String) -> Unit,
    onNewFolder: () -> Unit,
    onEjectFromFolder: () -> Unit,
    onDeleteApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showMoveTo by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val positionProvider = remember(tileBoundsInWindow, density) {
        val marginPx = with(density) { 16.dp.roundToPx() }
        TileAnchoredPositionProvider(tileBoundsInWindow, marginPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { if (showMoveTo) showMoveTo = false else onDismiss() },
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        // Plain opaque surface, no tint/border — the earlier translucent
        // "liquid glass" popover treatment was removed per the Decisions
        // Log's "§3.1.1 'liquid glass' tray/pill styling — removed" entry.
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
        ) {
            if (!showMoveTo) {
                OptionRow(text = "Edit Home Screen", onClick = onEditHomeScreen, requestInitialFocus = true)
                // Folders can't nest into another folder or be deleted the
                // same way an app is (Decisions Log-equivalent, user-
                // directed) — a folder target only ever gets to rearrange.
                if (!isFolderTarget) {
                    OptionRow(text = "Move to…", trailingChevron = true, onClick = { showMoveTo = true })
                    OptionRow(text = "Delete App", textColor = MaterialTheme.colorScheme.error, onClick = onDeleteApp)
                }
            } else {
                if (isInsideFolder) {
                    OptionRow(text = "Home Screen", onClick = onEjectFromFolder, requestInitialFocus = true)
                }
                folders.forEachIndexed { index, folder ->
                    OptionRow(
                        text = folder.name,
                        onClick = { onMoveToFolder(folder.id) },
                        requestInitialFocus = !isInsideFolder && index == 0,
                    )
                }
                OptionRow(
                    text = "+ New Folder",
                    onClick = onNewFolder,
                    requestInitialFocus = !isInsideFolder && folders.isEmpty(),
                )
            }
        }
    }
}

private class TileAnchoredPositionProvider(
    private val tileBounds: IntRect,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val fitsRight = tileBounds.right + marginPx + popupContentSize.width <= windowSize.width
        val x = if (fitsRight) {
            tileBounds.right + marginPx
        } else {
            (tileBounds.left - marginPx - popupContentSize.width).coerceAtLeast(0)
        }
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(0)
        val y = tileBounds.top.coerceIn(0, maxY)
        return IntOffset(x, y)
    }
}

/** A single row — background flips to a solid highlight on focus (no scale/tilt, unlike `tvOSFocusable`'s usual grid-tile treatment: matches the flat, list-style focus indication in `design/editHomeScreen.png`). */
@Composable
private fun OptionRow(
    text: String,
    modifier: Modifier = Modifier,
    trailingChevron: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // onSurface/surface, not hardcoded White/Black — confirmed on-device
    // this row was invisible in light theme (a solid white highlight is no
    // contrast at all against light theme's own already-light surface).
    // onSurface/surface are opposite-luminance in *both* schemes by
    // definition (`Theme.kt`), so this pairing reads correctly either way.
    val focusedBackground = MaterialTheme.colorScheme.onSurface
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) focusedBackground else Color.Transparent,
        label = "optionRowBackground",
    )
    val contentColor = if (isFocused) MaterialTheme.colorScheme.surface else textColor

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .tvOSFocusable(
                focusedScale = 1f,
                cornerRadius = 18.dp,
                // onSurface, not the Color.White default — same reasoning as
                // focusedBackground above: a white glow reads fine against
                // dark theme's near-black popover but disappears against
                // light theme's near-white one.
                glowColor = MaterialTheme.colorScheme.onSurface,
                onFocusChange = { isFocused = it },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailingChevron) {
            Text(text = "›", color = contentColor, style = MaterialTheme.typography.titleMedium)
        }
    }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            // A long-press fires its callback the instant the hold threshold
            // elapses (see tvOSFocusable's doc) — the key that opened this
            // popover may still be physically down for a moment after that.
            // This short buffer gives it time to actually release before
            // this row claims real focus, so that release doesn't land here
            // and misfire as an accidental click on whatever's focused first
            // (confirmed on-device without this: "New Folder" fired itself
            // the instant the menu appeared).
            delay(InitialFocusGraceMillis)
            focusRequester.requestFocus()
        }
    }
}

private const val InitialFocusGraceMillis = 350L
