package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.TmdbBackdrop

/**
 * PRODUCT_SPEC.md §3.1.1 Tier 1 — curated apps' hero backdrop art. TMDB has
 * no "artwork for streaming provider X" endpoint directly (a
 * `tmdb_provider_id` identifies a *service*, e.g. Hulu = 15, not a specific
 * title), so this surfaces the backdrop of whatever's currently popular on
 * that provider instead — real TMDB art, standing in for the provider,
 * rather than a specific diegetic claim about one title.
 */
interface TmdbRepository {
    /**
     * @return the most popular movie currently available on [providerId]
     *   (region fixed to `US` — no per-user region setting exists yet), or
     *   `null` if the lookup fails for any reason (bad/missing [apiKey],
     *   network error, no results) — callers treat `null` as "fall through
     *   to Tier 2," never as an error to surface to the user.
     */
    suspend fun fetchTrendingBackdrop(providerId: Int, apiKey: String): TmdbBackdrop?

    /**
     * Tier 3 poster quality — a specific program's [title] (not a provider)
     * resolved to TMDB's own top search match. There's no
     * IMDb/TMDB ID or release year in the TvContract data this app reads, so
     * matching is title-string-only; callers should treat a `null` result as
     * "this channel's own art is all there is," same as
     * [fetchTrendingBackdrop]'s `null` contract — never surfaced as an error.
     */
    suspend fun searchBackdrop(title: String, apiKey: String): TmdbBackdrop?
}
