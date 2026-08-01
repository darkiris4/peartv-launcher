package com.peartv.launcher.domain.model

/**
 * PRODUCT_SPEC.md §3.1.1 Tier 1 — the currently-trending title standing in
 * for a curated provider's hero backdrop. [title] is the actual movie's
 * title (not the app's name — that's §1.4's per-tile focus label's job) —
 * §3.1.2's authoritative Top Shelf reference calls for a title/logo overlay
 * naming *the specific content shown in the backdrop*, not the app hosting
 * it.
 */
data class TmdbBackdrop(
    val backdropUrl: String,
    val title: String,
)
