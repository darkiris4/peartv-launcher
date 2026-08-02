package com.peartv.launcher.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Android has no direct equivalent of tvOS's "Reduce Motion" accessibility
 * toggle — the closest available system-wide signal is the animator
 * duration scale (`Settings > Developer options > Remove animations` sets
 * this to `0`; this is also the standard signal accessibility-conscious
 * apps already treat as "the user wants animations off," not a
 * developer-only debug flag in practice).
 */
fun Context.isReduceMotionEnabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Read once at app startup (`MainActivity`'s `PearTvLauncherApp`) — Android
 * TV isn't a device class where this gets toggled mid-session the way a
 * phone's accessibility menu might, so a one-time read matches how this app
 * already handles its other one-time startup state (enrichment data,
 * layout seeding). `staticCompositionLocalOf`, not `compositionLocalOf`:
 * this is provided exactly once and never changes for the life of the
 * process, so there's no benefit to the more expensive change-tracking
 * `compositionLocalOf` supports. Defaults to `false` (full motion) for any
 * composable previewing/testing without a real provider above it.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
