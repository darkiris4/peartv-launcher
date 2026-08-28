package com.peartv.launcher.data.repository

import android.util.Log
import com.peartv.launcher.domain.model.ArtworkMatch
import com.peartv.launcher.domain.model.TmdbBackdrop
import com.peartv.launcher.domain.repository.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "TmdbRepository"
private const val DiscoverMovieUrl = "https://api.themoviedb.org/3/discover/movie"
private const val DiscoverTvUrl = "https://api.themoviedb.org/3/discover/tv"
private const val SearchTvUrl = "https://api.themoviedb.org/3/search/tv"
private const val SearchMovieUrl = "https://api.themoviedb.org/3/search/movie"
private const val ApiBaseUrl = "https://api.themoviedb.org/3"
private const val BackdropBaseUrl = "https://image.tmdb.org/t/p/w1280"

private const val TrendingBackdropLimit = 5

/**
 * PRODUCT_SPEC.md §3.1.1 Tier 1 — one TMDB Discover API call per provider
 * per content type (movies and TV shows both — the original "movies only
 * for v1" scoping's noted follow-up, now done), each returning up to
 * [TrendingBackdropLimit] candidates, merged and re-sorted by TMDB's own
 * `popularity` score so the final top [TrendingBackdropLimit] reflects
 * what's actually most popular on that provider *overall*, not "top N
 * movies" plus "top N shows" concatenated regardless of relative
 * popularity. `LauncherViewModel.heroBackdrop` rotates through the result
 * (§3.1.2: "Tier 1's backdrop may rotate... every 5–10s"). No Retrofit for
 * a single GET endpoint — a plain `OkHttpClient` + `org.json` (already used
 * the same way in `AppEnrichmentRepositoryImpl`) is proportionate.
 *
 * In-memory cache keyed by `providerId`, successes only: within one launcher
 * session, "what's popular on Hulu right now" doesn't change fast enough to
 * justify a network round-trip on every single focus event for the same
 * curated app. Failures are deliberately never cached — a transient network
 * hiccup should be retried next focus, not permanently disable Tier 1 for
 * that provider until the app restarts. Each content type's call fails
 * independently (its own `runCatching` inside [fetchDiscoverResults], not
 * one shared around both) — a movies-call error shouldn't discard an
 * already-succeeded shows result, or vice versa.
 */
class TmdbRepositoryImpl(
    private val httpClient: OkHttpClient,
) : TmdbRepository {

    private val cache = mutableMapOf<Int, List<TmdbBackdrop>>()
    private val searchCache = mutableMapOf<String, ArtworkMatch>()

    override suspend fun fetchTrendingBackdrops(providerId: Int, apiKey: String): List<TmdbBackdrop> {
        cache[providerId]?.let { return it }

        return withContext(Dispatchers.IO) {
            val movies = fetchDiscoverResults(DiscoverMovieUrl, apiKey, providerId, "title")
            val shows = fetchDiscoverResults(DiscoverTvUrl, apiKey, providerId, "name")
            (movies + shows)
                .sortedByDescending { it.second }
                .map { it.first }
                .take(TrendingBackdropLimit)
                .also { if (it.isNotEmpty()) cache[providerId] = it }
        }
    }

    /**
     * One `/discover/{movie,tv}` call — [nameField] is `"title"` for movies,
     * `"name"` for TV (TMDB's own inconsistent field naming between the two
     * endpoints). Returns each candidate paired with its raw `popularity`
     * score, not just a bare [TmdbBackdrop] list, so [fetchTrendingBackdrops]
     * can merge-sort movies and shows into one combined ranking.
     */
    private fun fetchDiscoverResults(
        url: String,
        apiKey: String,
        providerId: Int,
        nameField: String,
    ): List<Pair<TmdbBackdrop, Double>> = runCatching {
        val requestUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("with_watch_providers", providerId.toString())
            .addQueryParameter("watch_region", "US")
            .addQueryParameter("sort_by", "popularity.desc")
            .build()
        val request = Request.Builder().url(requestUrl).build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB discover call failed for provider $providerId ($url): HTTP ${response.code}")
                return@use emptyList()
            }
            val body = response.body?.string() ?: return@use emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@use emptyList()
            (0 until minOf(results.length(), TrendingBackdropLimit)).mapNotNull { i ->
                val result = results.getJSONObject(i)
                val backdropPath = result.optString("backdrop_path").ifBlank { null } ?: return@mapNotNull null
                val name = result.optString(nameField).ifBlank { null } ?: return@mapNotNull null
                TmdbBackdrop(backdropUrl = "$BackdropBaseUrl$backdropPath", title = name) to result.optDouble("popularity", 0.0)
            }
        }
    }.getOrElse {
        Log.w(TAG, "TMDB discover call threw for provider $providerId ($url)", it)
        emptyList()
    }

    /**
     * Searches *both* `/search/tv` and `/search/movie` — confirmed on-device
     * that movie-only search silently missed all Tier 3 content that's
     * actually a TV series (e.g. Apple TV+'s "Sugar"/"Lucky"), the common
     * case. Every candidate from either search is filtered to an exact
     * (normalized) name/title match first — never a blindly-trusted top
     * result (see [ArtworkMatch.sourceTmdbId]'s own doc for why that alone
     * still isn't enough). When [expectedProviderId] is given, the first
     * exact-title candidate confirmed available on that provider
     * (`/watch/providers`, US region) wins; otherwise the first exact-title
     * candidate wins with no such verification (graceful degradation for
     * apps with no curated `tmdbProviderId`).
     *
     * Once a candidate is accepted, a second lookup against its own images
     * (`/{type}/{id}/images`, filtered to `include_image_language=xx,null` —
     * the conventional "no language"/untagged bucket uploaders use for
     * textless art, since neither `/search/tv` nor `/search/movie` expose
     * per-image language tagging themselves) picks the largest textless
     * [ArtworkMatch].
     */
    override suspend fun searchBackdrop(title: String, apiKey: String, expectedProviderId: Int?): ArtworkMatch? {
        val cacheKey = "${title.trim().lowercase()}|$expectedProviderId"
        searchCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedTitle = title.trim()
                val candidates = searchExactMatches(SearchTvUrl, apiKey, title, normalizedTitle, "name", "tv") +
                    searchExactMatches(SearchMovieUrl, apiKey, title, normalizedTitle, "title", "movie")

                val accepted = if (expectedProviderId != null) {
                    candidates.firstOrNull { (id, type) -> isAvailableOnProvider(id, type, apiKey, expectedProviderId) }
                } else {
                    candidates.firstOrNull()
                } ?: return@runCatching null

                fetchImages(accepted.first, accepted.second, apiKey)
            }.getOrElse {
                Log.w(TAG, "TMDB search threw for \"$title\"", it)
                null
            }?.also { searchCache[cacheKey] = it }
        }
    }

    /** One `/search/{type}` call, filtered to exact (normalized, case-insensitive) [nameField] matches only — see [searchBackdrop]'s own doc for why a blindly-trusted top result isn't safe here. Returns `(id, type)` pairs, not raw JSON, so [searchBackdrop] can treat TV and movie candidates uniformly. */
    private fun searchExactMatches(
        url: String,
        apiKey: String,
        rawTitle: String,
        normalizedTitle: String,
        nameField: String,
        type: String,
    ): List<Pair<Int, String>> {
        val searchUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", rawTitle)
            .build()
        val request = Request.Builder().url(searchUrl).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB $type search call failed for \"$rawTitle\": HTTP ${response.code}")
                return@use emptyList()
            }
            val body = response.body?.string() ?: return@use emptyList()
            val results = JSONObject(body).optJSONArray("results") ?: return@use emptyList()
            (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { it.optString(nameField).trim().equals(normalizedTitle, ignoreCase = true) }
                .mapNotNull { candidate -> candidate.optInt("id", -1).takeIf { it != -1 } }
                .map { id -> id to type }
        }
    }

    /** `/{type}/{id}/watch/providers`, US region — `true` if [providerId] appears in any of the flatrate/ads/free buckets (any of those counts as "available on this app," not just a paid-subscription flatrate listing). */
    private fun isAvailableOnProvider(id: Int, type: String, apiKey: String, providerId: Int): Boolean {
        val url = "$ApiBaseUrl/$type/$id/watch/providers".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()
        val request = Request.Builder().url(url).build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.string() ?: return@use false
                val us = JSONObject(body).optJSONObject("results")?.optJSONObject("US")
                us != null && listOf("flatrate", "ads", "free").any { bucket ->
                    val arr = us.optJSONArray(bucket)
                    arr != null && (0 until arr.length()).any { i -> arr.getJSONObject(i).optInt("provider_id") == providerId }
                }
            }
        }.getOrElse {
            Log.w(TAG, "TMDB watch/providers call threw for $type/$id", it)
            false
        }
    }

    /** `/{type}/{id}/images`, filtered to the textless-convention language bucket — see [searchBackdrop]'s own doc. Picks the single largest candidate. */
    private fun fetchImages(id: Int, type: String, apiKey: String): ArtworkMatch? {
        val imagesUrl = "$ApiBaseUrl/$type/$id/images".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("include_image_language", "xx,null")
            .build()
        val request = Request.Builder().url(imagesUrl).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TMDB images call failed for $type/$id: HTTP ${response.code}")
                return@use null
            }
            val body = response.body?.string() ?: return@use null
            val backdrops = JSONObject(body).optJSONArray("backdrops") ?: return@use null
            var best: JSONObject? = null
            var bestRank = -1
            for (i in 0 until backdrops.length()) {
                val candidate = backdrops.getJSONObject(i)
                val width = candidate.optInt("width", 0)
                val height = candidate.optInt("height", 0)
                // "Backdrops" are conventionally landscape already, but
                // ranking orientation explicitly rather than assuming it
                // costs nothing and matches TVDB's own ranking shape (see
                // that class's own doc for why it's load-bearing there).
                val isLandscape = width > height
                val rank = (if (isLandscape) 1_000_000_000 else 0) + (width * height)
                if (rank > bestRank) {
                    bestRank = rank
                    best = candidate
                }
            }
            val chosen = best ?: return@use null
            val filePath = chosen.optString("file_path").ifBlank { null } ?: return@use null
            ArtworkMatch(
                backdropUrl = "$BackdropBaseUrl$filePath",
                isConfirmedClean = true,
                width = chosen.optInt("width", 0),
                height = chosen.optInt("height", 0),
                sourceTmdbId = id,
            )
        }
    }
}
