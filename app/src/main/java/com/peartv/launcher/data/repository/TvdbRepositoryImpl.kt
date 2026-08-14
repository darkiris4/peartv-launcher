package com.peartv.launcher.data.repository

import android.util.Log
import com.peartv.launcher.domain.model.ArtworkMatch
import com.peartv.launcher.domain.repository.TvdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val TAG = "TvdbRepository"
private const val BaseUrl = "https://api4.thetvdb.com/v4"

/** Conservative under TheTVDB v4's own documented ~1-month token validity — re-logs in well before actual expiry rather than cutting it close. */
private const val TokenValidityMillis = 25L * 24 * 60 * 60 * 1000

/**
 * Tier 3 poster quality — TheTVDB v4 as a second `ArtworkSource` candidate
 * alongside [TmdbRepositoryImpl]. Same plain `OkHttpClient` + `org.json`
 * proportionality call as TMDB (see that class's own doc) — TheTVDB's own
 * auth is a single `/login` exchange for a bearer token good for weeks, not
 * a per-call handshake, so it doesn't earn a heavier networking layer either.
 *
 * `includesText` — a field TheTVDB publishes directly on every artwork
 * record — is what [ArtworkMatch.isConfirmedClean] is actually built around;
 * see that class's own doc.
 *
 * Exact JSON field/endpoint shapes below were built against TheTVDB v4's
 * published API reference; like the original TMDB integration, treat this as
 * needing on-device confirmation against the real API with a real key before
 * considering it done, not something docs alone can guarantee byte-for-byte.
 */
class TvdbRepositoryImpl(
    private val httpClient: OkHttpClient,
) : TvdbRepository {

    private val searchCache = mutableMapOf<String, ArtworkMatch>()

    private var cachedToken: String? = null
    private var tokenExpiresAtMillis: Long = 0L

    private fun login(apiKey: String): String? {
        val body = JSONObject().put("apikey", apiKey).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BaseUrl/login")
            .post(body)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "TVDB login failed: HTTP ${response.code}")
                return@use null
            }
            val responseBody = response.body?.string() ?: return@use null
            JSONObject(responseBody).optJSONObject("data")?.optString("token")?.ifBlank { null }
        }
    }

    private fun ensureToken(apiKey: String): String? {
        val token = cachedToken
        if (token != null && System.currentTimeMillis() < tokenExpiresAtMillis) return token

        val fresh = login(apiKey) ?: return null
        cachedToken = fresh
        tokenExpiresAtMillis = System.currentTimeMillis() + TokenValidityMillis
        return fresh
    }

    override suspend fun searchBackdrop(title: String, apiKey: String, verifiedTmdbId: Int?): ArtworkMatch? {
        val cacheKey = "${title.trim().lowercase()}|$verifiedTmdbId"
        searchCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val token = ensureToken(apiKey) ?: return@runCatching null
                val authHeader = "Bearer $token"

                val searchUrl = "$BaseUrl/search".toHttpUrl().newBuilder()
                    .addQueryParameter("query", title)
                    .build()
                val searchRequest = Request.Builder()
                    .url(searchUrl)
                    .header("Authorization", authHeader)
                    .build()

                val (recordId, recordType) = httpClient.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TVDB search call failed for \"$title\": HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body?.string() ?: return@use null
                    val results = JSONObject(body).optJSONArray("data") ?: return@use null
                    // Exact (normalized) title match required, scanned across
                    // *every* result, not just result[0] — confirmed
                    // on-device this matters: TVDB's own relevance ranking is
                    // popularity-driven, not title-similarity-driven, so the
                    // actual title match can sit well below unrelated
                    // higher-ranked series/movies with a similar name (e.g.
                    // "Sugar" the real 2024 series ranked 4th, behind three
                    // unrelated "Sugar"-titled movies). Taking result[0]
                    // unconditionally there matched the wrong content
                    // entirely.
                    val normalizedTitle = title.trim()
                    val exactMatches = (0 until results.length())
                        .map { results.getJSONObject(it) }
                        .filter { candidate ->
                            (candidate.optString("type") == "series" || candidate.optString("type") == "movie") &&
                                candidate.optString("name").trim().equals(normalizedTitle, ignoreCase = true)
                        }
                    // Even among *exact*-title matches, TVDB can still rank
                    // the wrong same-named work first (confirmed on-device:
                    // its own "Sugar" series entry — exact title match — was
                    // a 2002 Food Network show, not the real 2024 Apple TV+
                    // series, and TVDB's own network/company fields were
                    // empty for it, so there was nothing reliable of TVDB's
                    // own to disambiguate with). When [verifiedTmdbId] is
                    // available, only a candidate that cross-references back
                    // to that exact, already-verified TMDB record is
                    // accepted — see [crossReferencesTmdb]'s own doc.
                    val match = if (verifiedTmdbId != null) {
                        exactMatches.firstOrNull { crossReferencesTmdb(it, verifiedTmdbId) }
                    } else {
                        exactMatches.firstOrNull()
                    } ?: return@use null
                    val id = match.optString("tvdb_id").ifBlank { null } ?: return@use null
                    id to match.optString("type")
                } ?: return@runCatching null

                // `/series/{id}/artworks` is a real, dedicated endpoint
                // (confirmed live) — but there is no `/movies/{id}/artworks`
                // equivalent; confirmed on-device every such call returned
                // HTTP 400 "Bad Request", silently forcing every movie match
                // to fall through to TMDB regardless of whether TVDB
                // actually had the right art. A movie's artworks only come
                // back embedded in `/movies/{id}/extended`'s own response,
                // same `data.artworks` shape either way.
                val artworksUrl = if (recordType == "movie") {
                    "$BaseUrl/movies/$recordId/extended"
                } else {
                    "$BaseUrl/series/$recordId/artworks"
                }
                val artworksRequest = Request.Builder()
                    .url(artworksUrl)
                    .header("Authorization", authHeader)
                    .build()

                httpClient.newCall(artworksRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TVDB artworks call failed for $artworksUrl: HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body?.string() ?: return@use null
                    val artworks = JSONObject(body).optJSONObject("data")?.optJSONArray("artworks") ?: return@use null
                    var best: JSONObject? = null
                    var bestRank = -1
                    for (i in 0 until artworks.length()) {
                        val candidate = artworks.getJSONObject(i)
                        val includesText = candidate.optBoolean("includesText", true)
                        val width = candidate.optInt("width", 0)
                        val height = candidate.optInt("height", 0)
                        val area = width * height
                        // TVDB mixes every artwork type together in one list
                        // — icons, posters, banners, clearlogos, backgrounds
                        // — with nothing else distinguishing them here, so
                        // orientation is ranked explicitly rather than
                        // assumed: confirmed on-device that ranking by
                        // textless+area alone could still pick a
                        // portrait/square candidate (a poster or icon) over
                        // a smaller-but-landscape background. Textless still
                        // outranks orientation, which outranks raw size —
                        // three tiers, each encoded as a large, fixed offset
                        // rather than a separate sort pass. A non-landscape
                        // winner isn't rejected outright, just deprioritized
                        // — `PosterBackdrop` renders whatever's actually
                        // chosen according to its own real proportions.
                        val isLandscape = width > height
                        val rank = (if (!includesText) 1_000_000_000 else 0) + (if (isLandscape) 1_000_000 else 0) + area
                        if (rank > bestRank) {
                            bestRank = rank
                            best = candidate
                        }
                    }
                    val chosen = best ?: return@use null
                    val imageUrl = chosen.optString("image").ifBlank { null } ?: return@use null
                    ArtworkMatch(
                        backdropUrl = imageUrl,
                        isConfirmedClean = !chosen.optBoolean("includesText", true),
                        width = chosen.optInt("width", 0),
                        height = chosen.optInt("height", 0),
                        sourceTmdbId = verifiedTmdbId,
                    )
                }
            }.getOrElse {
                Log.w(TAG, "TVDB search threw for \"$title\"", it)
                null
            }?.also { searchCache[cacheKey] = it }
        }
    }

    /** `true` if [candidate]'s own `remote_ids` (TVDB's cross-reference list to other databases) includes a "TheMovieDB.com" entry whose id matches [verifiedTmdbId] — see [searchBackdrop]'s own doc for why this gate exists. */
    private fun crossReferencesTmdb(candidate: JSONObject, verifiedTmdbId: Int): Boolean {
        val remoteIds = candidate.optJSONArray("remote_ids") ?: return false
        for (i in 0 until remoteIds.length()) {
            val ref = remoteIds.getJSONObject(i)
            if (ref.optString("sourceName") == "TheMovieDB.com" && ref.optString("id") == verifiedTmdbId.toString()) {
                return true
            }
        }
        return false
    }
}
