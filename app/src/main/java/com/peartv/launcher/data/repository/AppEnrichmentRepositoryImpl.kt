package com.peartv.launcher.data.repository

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.peartv.launcher.domain.model.AppEnrichment
import com.peartv.launcher.domain.repository.AppEnrichmentRepository
import org.json.JSONArray

private const val TAG = "AppEnrichmentRepository"
private const val AssetFileName = "app_enrichment.json"

/**
 * PRODUCT_SPEC.md §3.2.1 — loads the bundled curated-app table
 * (`assets/app_enrichment.json`, §3.3.1's provider table in JSON form) once
 * into memory. A plain bundled JSON asset, not Room/DataStore: this is a
 * small, rarely-changing lookup table, and the schema's own description
 * ("local JSON or Room table, bundled + user-editable") doesn't need a
 * database's ceremony at this size.
 *
 * A missing/malformed asset degrades to "no enrichment data for anyone"
 * (empty map), not a crash — per §3.2.1, every field this supplies is
 * optional, so its total absence is just the same as no app having a
 * curated entry.
 */
class AppEnrichmentRepositoryImpl(context: Context) : AppEnrichmentRepository {

    private val byPackageName: Map<String, AppEnrichment> = runCatching {
        context.assets.open(AssetFileName).bufferedReader().use { it.readText() }
    }.mapCatching { parseEnrichmentJson(it) }
        .getOrElse {
            Log.w(TAG, "Failed to load $AssetFileName — enrichment disabled", it)
            emptyMap()
        }

    override fun forPackage(packageName: String): AppEnrichment? = byPackageName[packageName]

    private fun parseEnrichmentJson(json: String): Map<String, AppEnrichment> {
        val entries = JSONArray(json)
        return buildMap {
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val packageName = entry.getString("packageName")
                put(
                    packageName,
                    AppEnrichment(
                        packageName = packageName,
                        displayNameOverride = entry.optString("displayName").ifBlank { null },
                        accentColorArgb = entry.optString("accentColor").ifBlank { null }
                            ?.let { Color.parseColor(it) },
                        tmdbProviderId = if (entry.has("tmdbProviderId")) entry.getInt("tmdbProviderId") else null,
                        pinnedToTopShelf = entry.optBoolean("pinnedToTopShelf", false),
                    ),
                )
            }
        }
    }
}
