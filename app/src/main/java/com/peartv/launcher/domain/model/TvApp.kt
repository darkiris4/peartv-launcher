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
 * [icon] is a *separate* image from [banner] — most apps declare both: the
 * wide 16:9 [banner] for TV surfaces, and a small square everyday launcher
 * icon, built to stay legible at small sizes (unlike [banner], which is
 * full-bleed art not designed to be shrunk). §3.1.2 Template 4's Tier 2
 * hero fallback (`HeroBanner.kt`) uses [icon] as its centered mark over a
 * flat, [iconPrimaryColorArgb]-filled backdrop for exactly that reason — a
 * reverted earlier attempt tried reusing [banner] itself, shrunk, as a
 * stand-in logo, and it read poorly. When an app declares no [banner] at
 * all, [LauncherAppRepository] already falls [banner] back to this same
 * icon — the two fields are simply equal in that case, which still renders
 * correctly.
 *
 * [iconPrimaryColorArgb] is [icon]'s dominant color (`DrawableBitmap.kt`'s
 * `dominantColorArgb`, via Android's `Palette` library), sampled once at
 * app-list build time. `null` when `Palette` couldn't extract one (rare —
 * e.g. a fully transparent icon) — callers fall back to a neutral theme
 * color in that case. A live blur of [banner] was tried first for this same
 * backdrop and abandoned (see `dominantColorArgb`'s own doc for why); a
 * flat sampled-color fill is also a closer match to how [AppTile] already
 * treats its own fallback tiles elsewhere in this app (a solid
 * [accentColorArgb] fill, not a photographic background).
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
    val icon: Bitmap? = null,
    val iconPrimaryColorArgb: Int? = null,
    val accentColorArgb: Int? = null,
    val pinnedToTopShelf: Boolean = false,
    val tmdbProviderId: Int? = null,
    /** `ApplicationInfo.category`, human-readable — Grid Reordering & Folders' Decision #5 Tier 2 opportunistic folder-naming source; `null` when undeclared (most real installed apps, confirmed — this is why folder auto-naming always has a Tier 3 fallback). */
    val category: String? = null,
)
