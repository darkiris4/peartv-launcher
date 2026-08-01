package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.TvApp
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for "which leanback apps are installed" (PRODUCT_SPEC.md
 * §3.2). [apps] is a hot, always-current stream — the grid never has to ask
 * "is this stale," it just collects.
 */
interface LauncherAppRepository {
    val apps: StateFlow<List<TvApp>>

    /** Re-queries PackageManager. Called on boot and on package-change broadcasts. */
    fun refresh()
}
