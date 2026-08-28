package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.ArtworkMatch
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
     * @return up to a handful of the most popular movies *and* TV shows
     *   currently available on [providerId] (region fixed to `US` — no
     *   per-user region setting exists yet), merged into one list and
     *   ordered by popularity across both — callers rotate through these
     *   for Tier 1's backdrop (PRODUCT_SPEC.md §3.1.2: "Tier 1's backdrop may
     *   rotate... every 5–10s"). Empty list if the lookup fails for any
     *   reason (bad/missing [apiKey], network error, no results) — callers
     *   treat that as "fall through to Tier 2," never as an error to
     *   surface to the user.
     */
    suspend fun fetchTrendingBackdrops(providerId: Int, apiKey: String): List<TmdbBackdrop>

    /**
     * Tier 3 poster quality — a specific program's [title] searched against
     * *both* `/search/tv` and `/search/movie` (Tier 3 content skews TV
     * series; searching movies alone silently missed all of it), matched by
     * exact (normalized) name/title — never a blindly-trusted top result;
     * see [ArtworkMatch.sourceTmdbId]'s own doc for why. When
     * [expectedProviderId] is non-null ([com.peartv.launcher.domain.model.TvApp.tmdbProviderId]
     * for the app this program's channel belongs to), an exact-title
     * candidate is only accepted once confirmed available on that provider
     * (`/watch/providers`, US region) — a real gate, not a soft preference:
     * confirmed on-device this is what actually distinguishes the correct
     * title from an unrelated same-named one when both exist. `null`
     * [expectedProviderId] (the app has no curated provider id) falls back
     * to the first exact-title match with no such verification.
     *
     * Once a candidate is accepted, a second lookup against its own images
     * picks the best textless-preferring [ArtworkMatch] (see
     * [ArtworkMatch.isConfirmedClean]'s own doc — TMDB's `xx`/`null`
     * language-tagging convention is a weaker signal than TheTVDB's own
     * explicit `includesText`, but it's what TMDB offers). `null` result
     * means "this channel's own art is all there is," same as
     * [fetchTrendingBackdrop]'s `null` contract — never surfaced as an error.
     */
    suspend fun searchBackdrop(title: String, apiKey: String, expectedProviderId: Int?): ArtworkMatch?
}
