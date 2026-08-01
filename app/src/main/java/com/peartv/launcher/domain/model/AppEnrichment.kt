package com.peartv.launcher.domain.model

/**
 * PRODUCT_SPEC.md §3.2.1 — supplementary, package-name-keyed presentation
 * data `PackageManager` doesn't have. Optional per app: an installed app
 * with no matching entry still renders correctly with `PackageManager`-
 * sourced defaults ([TvApp]'s own field defaults) — this exists to enhance
 * known/curated apps, not to gate what appears in the launcher.
 */
data class AppEnrichment(
    val packageName: String,
    val displayNameOverride: String? = null,
    val accentColorArgb: Int? = null,
    val tmdbProviderId: Int? = null,
    val pinnedToTopShelf: Boolean = false,
)
