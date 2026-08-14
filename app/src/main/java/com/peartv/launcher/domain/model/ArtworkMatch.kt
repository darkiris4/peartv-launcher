package com.peartv.launcher.domain.model

/**
 * One Tier 3 swap-in candidate from an external metadata provider (TMDB or
 * TheTVDB), provider-agnostic so `LauncherViewModel`'s `ArtworkSource` policy
 * can compare/rank candidates from either source uniformly. [isConfirmedClean]
 * is `true` when the provider itself declares the image textless — TheTVDB's
 * own `includesText == false`, or TMDB's `xx`/`null` textless-language
 * convention — a claim this app trusts rather than re-verifies by inspecting
 * pixels, since detecting a baked-in logo/title from the image itself isn't
 * feasible.
 *
 * [sourceTmdbId] identifies exactly which verified TMDB record this match
 * came from — confirmed on-device this is load-bearing, not informational:
 * title-string-only matching against an *unverified* top search result
 * produced real wrong-content matches (e.g. TVDB's own "Sugar" search ranked
 * an unrelated 2002 Food Network show above the real 2024 Apple TV+ series).
 * `TmdbRepositoryImpl` populates this once it's confirmed a candidate is
 * actually available on the expected app's own streaming provider
 * (`TvApp.tmdbProviderId`); `TvdbRepositoryImpl` then only accepts one of
 * *its own* candidates if it cross-references back to this same verified id
 * (via TVDB's own `remote_ids`), rather than trusting its own, less reliable
 * relevance ranking. `null` when no such TMDB verification happened (no
 * curated `tmdbProviderId` for the app, or TMDB found nothing at all) — both
 * repositories fall back to their prior best-effort, unverified matching in
 * that case.
 */
data class ArtworkMatch(
    val backdropUrl: String,
    val isConfirmedClean: Boolean,
    val width: Int,
    val height: Int,
    val sourceTmdbId: Int? = null,
)
