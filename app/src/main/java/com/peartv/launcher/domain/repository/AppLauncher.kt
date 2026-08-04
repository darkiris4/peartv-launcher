package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.TvApp

/**
 * Screen-space pixel bounds of the tile a launch originated from — the
 * launching tile's real on-screen position/size at the moment it was
 * clicked, captured via Compose's `boundsInWindow()` (`AppTile`'s own
 * click handler). Feeds [android.app.ActivityOptions.makeScaleUpAnimation]/
 * `makeClipRevealAnimation` directly (both take exactly `startX, startY,
 * startWidth, startHeight` as plain ints), which is why this is a bare
 * int rect rather than `android.graphics.Rect` or a Compose geometry
 * type — neither is a natural fit for either layer this crosses.
 * `null` means "no known origin" (a launch path with no tile to capture
 * from) — [AppLauncherImpl][com.peartv.launcher.data.launcher.AppLauncherImpl]
 * falls back to a plain, un-animated launch in that case.
 */
data class LaunchOrigin(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Launches an installed app (PRODUCT_SPEC.md §3.3) — deliberately separate
 * from [LauncherAppRepository], since "what apps exist" and "how we start
 * one" are different concerns with different Android API surfaces backing
 * them (PackageManager query vs. Intent/task-stack handling).
 */
interface AppLauncher {
    fun launch(app: TvApp, origin: LaunchOrigin? = null)

    /**
     * PRODUCT_SPEC.md §3.1.2 — launches a Tier 3 program's own deep link
     * ([intentUri], from `TvContract.PreviewPrograms.COLUMN_INTENT_URI`).
     * Falls back to [launch]ing [fallbackApp] whenever [intentUri] is
     * `null`, malformed, or resolves to nothing installed — a program
     * without a usable deep link still does *something* sensible on click
     * rather than silently no-op'ing.
     */
    fun launchContent(intentUri: String?, fallbackApp: TvApp, origin: LaunchOrigin? = null)

    /** Grid Reordering & Folders' context menu "Delete App" — hands off to the system uninstall confirmation UI (`ACTION_DELETE`); this launcher never uninstalls silently on its own. The grid reconciles itself once [com.peartv.launcher.data.receiver.PackageChangeReceiver] observes the actual removal. */
    fun requestUninstall(packageName: String)
}
