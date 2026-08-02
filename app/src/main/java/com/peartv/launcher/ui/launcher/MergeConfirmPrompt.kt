package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Grid Reordering & Folders §8 (drag-to-merge) — shown once a held Edit Mode
 * direction press lands on a mergeable neighbor ([LauncherViewModel.pendingMerge]),
 * before anything about the layout actually changes. User-directed: the
 * original build merged the instant a hold was detected, with no way back —
 * confirmed on-device this was too easy to trigger by accident while just
 * trying to reposition a tile past a neighbor. Same centered, opaque,
 * no-heavy-scrim treatment as `ChannelsPermissionPrompt` (this app's other
 * confirm-or-dismiss prompt) rather than `OptionsMenu`'s tile-anchored
 * popover — there's no single tile this decision is "about," it's about two.
 *
 * Back and outside-click both cancel via [Popup]'s own `dismissOnBackPress`/
 * `onDismissRequest` (same mechanism `OptionsMenu` already relies on to
 * swallow Back before `LauncherScreen`'s outer `BackHandler` ever sees it) —
 * landing back in Edit Mode with the active tile still picked up, exactly
 * where the hold started, not a half-finished state.
 */
@Composable
fun MergeConfirmPrompt(
    activeLabel: String,
    targetLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmFocusRequester = remember { FocusRequester() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        ) {
            Text(
                text = "Create a Folder?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Combine \"$targetLabel\" and \"$activeLabel\" into a new folder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                ) {
                    Text("Create Folder")
                }
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        confirmFocusRequester.requestFocus()
    }
}
