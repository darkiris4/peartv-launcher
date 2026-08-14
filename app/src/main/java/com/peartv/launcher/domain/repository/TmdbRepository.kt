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
     * @return the most popular movie currently available on [providerId]
     *   (region fixed to `US` — no per-user region setting exists yet), or
     *   `null` if the lookup fails for any reason (bad/missing [apiKey],
     *   network error, no results) — callers treat `null` as "fall through
     *   to Tier 2," never as an error to surface to the user.
     */
    suspend fun fetchTrendingBackdrop(providerId: Int, apiKey: String): TmdbBackdrop?

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
