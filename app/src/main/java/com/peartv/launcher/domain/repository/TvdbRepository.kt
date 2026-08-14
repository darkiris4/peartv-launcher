package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.ArtworkMatch

/**
 * Tier 3 poster quality — TheTVDB v4 as an `ArtworkSource` candidate,
 * alongside [TmdbRepository.searchBackdrop]. TheTVDB's own `includesText`
 * field on every artwork record is a provider-declared claim, not a guess —
 * see [ArtworkMatch.isConfirmedClean]'s own doc.
 */
interface TvdbRepository {
    /**
     * @param verifiedTmdbId — a TMDB id ([ArtworkMatch.sourceTmdbId])
     *   already confirmed correct by [TmdbRepository.searchBackdrop]'s own
     *   provider gate. When non-null, a candidate from TVDB's own [title]
     *   search is only accepted if it cross-references back to this exact
     *   id (via TVDB's own `remote_ids` field, matched on id value alone —
     *   not further disambiguated by movie-vs-series, an accepted
     *   simplification given how unlikely a numeric collision across those
     *   two independent TMDB id namespaces is) — confirmed on-device this is
     *   necessary, not defensive: TVDB's own relevance ranking put an
     *   unrelated same-named show above the correct one, with no reliable
     *   network/studio data of its own to disambiguate by (TVDB's own
     *   `originalNetwork`/`companies` fields were empty for the exact
     *   record that ranked highest). `null` (TMDB found nothing to verify
     *   against — no curated provider id for the app, or no TMDB match at
     *   all) falls back to TVDB's own first exact-title match, unverified.
     * @return the best-ranked artwork match for [title] (preferring
     *   `includesText == false`, then largest resolution among whatever
     *   TheTVDB has for the accepted candidate), or `null` if the lookup
     *   fails for any reason (bad/missing [apiKey], network error, no
     *   results, or no candidate cross-references [verifiedTmdbId] when one
     *   was given) — same "never surfaced as an error, just fall through"
     *   contract as [TmdbRepository.searchBackdrop].
     */
    suspend fun searchBackdrop(title: String, apiKey: String, verifiedTmdbId: Int?): ArtworkMatch?
}
