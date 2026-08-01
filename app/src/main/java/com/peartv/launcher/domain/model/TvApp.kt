package com.peartv.launcher.domain.model

import android.graphics.Bitmap

/**
 * A single leanback-launchable app, as surfaced to the grid.
 *
 * [banner] is pre-decoded (PRODUCT_SPEC.md §2.2 — HARDWARE-config, composed
 * once at app-list build time in the data layer) so rendering it in the grid
 * is never more than a GPU blit, even on the first focus of a cold-launched
 * screen.
 *
 * [accentColorArgb], [pinnedToTopShelf], and [tmdbProviderId] are enrichment
 * fields (§3.2.1) — all default to "no enrichment data available" rather
 * than requiring every installed app to have a curated entry.
 */
data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: Bitmap?,
    val accentColorArgb: Int? = null,
    val pinnedToTopShelf: Boolean = false,
    val tmdbProviderId: Int? = null,
    /** `ApplicationInfo.category`, human-readable — Grid Reordering & Folders' Decision #5 Tier 2 opportunistic folder-naming source; `null` when undeclared (most real installed apps, confirmed — this is why folder auto-naming always has a Tier 3 fallback). */
    val category: String? = null,
)
