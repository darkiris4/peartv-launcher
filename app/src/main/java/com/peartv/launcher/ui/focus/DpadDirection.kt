package com.peartv.launcher.ui.focus

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key

/** Which D-pad direction was last pressed — drives tilt origin, not continuous pointer tracking (PRODUCT_SPEC.md §1.2). */
enum class DpadDirection {
    Up, Down, Left, Right,
}

/**
 * Hoisted at the launcher screen root (see LauncherScreen's onPreviewKeyEvent)
 * so any tvOSFocusable tile below can read "which direction did focus just
 * move," without every tile independently tracking key events.
 */
val LocalLastDpadDirection = compositionLocalOf<DpadDirection?> { null }

fun directionFromKey(key: Key): DpadDirection? = when (key) {
    Key.DirectionUp -> DpadDirection.Up
    Key.DirectionDown -> DpadDirection.Down
    Key.DirectionLeft -> DpadDirection.Left
    Key.DirectionRight -> DpadDirection.Right
    else -> null
}
