package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Grid Reordering & Folders — a small discoverability aid shown only while
 * actively dragging a tile (Edit Mode, entered via the Options popover's
 * "Edit Home Screen" row), so a user mid-rearrange knows how to place the
 * tile and how to stop.
 */
@Composable
fun EditModeHint(modifier: Modifier = Modifier) {
    Text(
        text = "Arrows to move  •  Back to finish",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
