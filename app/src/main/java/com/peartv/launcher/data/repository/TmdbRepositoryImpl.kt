package com.peartv.launcher.data.repository

import android.util.Log
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
private const val BackdropBaseUrl = "https://image.tmdb.org/t/p/w1280"

/**
 * PRODUCT_SPEC.md §3.1.1 Tier 1 — one-shot TMDB Discover API call (movies
 * only for now; a combined movie+TV query is a follow-up, not a correctness
 * requirement for a first working Tier 1). No Retrofit for a single GET
 * endpoint — a plain `OkHttpClient` + `org.json` (already used the same way
 * in `AppEnrichmentRepositoryImpl`) is proportionate.
 *
 * In-memory cache keyed by `providerId`, successes only: within one launcher
 * session, "what's popular on Hulu right now" doesn't change fast enough to
 * justify a network round-trip on every single focus event for the same
 * curated app. Failures are deliberately never cached — a transient network
 * hiccup should be retried next focus, not permanently disable Tier 1 for
 * that provider until the app restarts.
 */
class TmdbRepositoryImpl(
    private val httpClient: OkHttpClient,
) : TmdbRepository {

    private val cache = mutableMapOf<Int, TmdbBackdrop>()

    override suspend fun fetchTrendingBackdrop(providerId: Int, apiKey: String): TmdbBackdrop? {
        cache[providerId]?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url = DiscoverMovieUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", apiKey)
                    .addQueryParameter("with_watch_providers", providerId.toString())
                    .addQueryParameter("watch_region", "US")
                    .addQueryParameter("sort_by", "popularity.desc")
                    .build()
                val request = Request.Builder().url(url).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "TMDB discover call failed for provider $providerId: HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body?.string() ?: return@use null
                    val results = JSONObject(body).optJSONArray("results") ?: return@use null
                    if (results.length() == 0) return@use null
                    val result = results.getJSONObject(0)
                    val backdropPath = result.optString("backdrop_path").ifBlank { null } ?: return@use null
                    val title = result.optString("title").ifBlank { null } ?: return@use null
                    TmdbBackdrop(backdropUrl = "$BackdropBaseUrl$backdropPath", title = title)
                }
            }.getOrElse {
                Log.w(TAG, "TMDB fetch threw for provider $providerId", it)
                null
            }?.also { cache[providerId] = it }
        }
    }
}
