package com.peartv.launcher.data.repository

import android.content.Context
import android.media.tv.TvContentRating
import android.media.tv.TvContract
import android.util.Log
import com.peartv.launcher.domain.model.AppChannel
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.domain.repository.ChannelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ChannelsRepository"

/**
 * Movie-poster-like fallback (§3.1.1) — used when a program's own aspect
 * ratio column is unrecognized. Confirmed against the real platform jar
 * (`javap` on `android.media.tv.TvContract$PreviewPrograms`, API 35): the
 * framework defines exactly five `ASPECT_RATIO_*` constants (0-4, all
 * present in [AspectRatioByColumnValue] below) — there is no sixth
 * "movie poster" constant. Plex publishes raw column value `5` for most of
 * its catalog anyway (confirmed on-device), which is undocumented/
 * out-of-spec on Plex's part, not a gap in this map. It falls through to
 * this default, which happens to equal Plex's real portrait art's actual
 * ratio (204×306 downloaded and measured on-device) — a fortunate
 * coincidence for that specific value, not something to special-case for.
 */
private const val DefaultPosterAspectRatio = 2f / 3f

private val AspectRatioByColumnValue = mapOf(
    TvContract.PreviewPrograms.ASPECT_RATIO_16_9 to 16f / 9f,
    TvContract.PreviewPrograms.ASPECT_RATIO_3_2 to 3f / 2f,
    TvContract.PreviewPrograms.ASPECT_RATIO_4_3 to 4f / 3f,
    TvContract.PreviewPrograms.ASPECT_RATIO_1_1 to 1f,
    TvContract.PreviewPrograms.ASPECT_RATIO_2_3 to 2f / 3f,
)

/**
 * PRODUCT_SPEC.md §2.4/§3.1.1 Tier 3 — queries the framework's
 * `android.media.tv.TvContract` directly (available since API 26; no
 * `androidx.tvprovider` dependency needed at this project's API 30 floor).
 * No model/builder classes (`PreviewProgram`, `BasePreviewProgram`) exist in
 * the plain platform SDK — confirmed via `javap` against the real
 * `android.jar` rather than assumed — so this reads columns off a raw
 * `Cursor` instead.
 *
 * Reading rows another app published requires `READ_TV_LISTINGS` (§2.4) —
 * without it (or on any other query failure), this degrades to "no channel
 * data," which is exactly Tier 3's "not present" case, not an error path
 * worth surfacing.
 *
 * The Channels table rejects any `selection`/WHERE clause outright
 * (`SecurityException: Selection not allowed for content://android.media.tv/channel`
 * — confirmed on-device, not assumed): there's no supported "channels for
 * package X" query. The real access pattern is an unfiltered query of the
 * whole (READ_TV_LISTINGS-visible) table, filtered by [TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME]
 * client-side instead. Programs, by contrast, use
 * [TvContract.buildPreviewProgramsUriForChannel] — a URI scoped to one
 * already-known channel ID rather than a raw selection — which isn't
 * subject to the same restriction.
 *
 * Only an app's own named channels are read here — a
 * [TvContract.WatchNextPrograms] ("Continue Watching"/"Up Next"-style,
 * system-wide, ungrouped by app) integration was tried and reverted
 * (Decisions Log: "WatchNextPrograms as a second Tier 3 source — reverted"):
 * confirmed on-device it produced multiple stacked Content Rows sections
 * whose second row got visually cut off behind the dock, since
 * `LauncherScreen`'s tray always overlays this region's bottom edge
 * regardless of how much content is stacked above it — a real layout gap in
 * how multiple Content Rows sections coexist with the tray, not something
 * this repository change alone should paper over.
 */
class ChannelsRepositoryImpl(
    private val context: Context,
) : ChannelsRepository {

    override suspend fun fetchChannels(packageName: String): List<AppChannel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = queryChannels(packageName)
                Log.d(TAG, "$packageName: found ${rows.size} channel row(s): ${rows.map { it.displayName }}")
                rows.mapNotNull { channel ->
                    val programs = queryPrograms(channel.id)
                    Log.d(TAG, "$packageName: channel '${channel.displayName}' (id=${channel.id}) has ${programs.size} program(s)")
                    if (programs.isEmpty()) null else AppChannel(channel.displayName, programs)
                }
            }.getOrElse {
                Log.w(TAG, "Channels lookup failed for $packageName", it)
                emptyList()
            }
        }

    private data class ChannelRow(val id: Long, val displayName: String)

    /**
     * A package can publish more than one channel (e.g. Plex's own
     * "Recommendations" alongside a "My Newscast" channel — confirmed
     * on-device via this project's own diagnostic pass, see the Decisions
     * Log). This used to `return` as soon as the first matching row was
     * found — that silently dropped every channel after the first for any
     * app publishing more than one, which is exactly why only a single
     * row/label ever showed up here even for apps known to publish several.
     */
    private fun queryChannels(packageName: String): List<ChannelRow> {
        val projection = arrayOf(
            TvContract.BaseTvColumns._ID,
            TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
        )
        val channels = mutableListOf<ChannelRow>()
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(TvContract.BaseTvColumns._ID)
            val packageNameIndex = cursor.getColumnIndexOrThrow(TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME)
            val nameIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(packageNameIndex) == packageName) {
                    channels += ChannelRow(
                        id = cursor.getLong(idIndex),
                        displayName = cursor.getString(nameIndex).orEmpty(),
                    )
                }
            }
        }
        return channels
    }

    private fun queryPrograms(channelId: Long): List<ChannelProgram> {
        val projection = arrayOf(
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
            TvContract.PreviewPrograms.COLUMN_CONTENT_RATING,
            TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
            TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE,
            TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
            TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
            TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_PREVIEW_VIDEO_URI,
        )
        var previewVideoCount = 0
        val programs = mutableListOf<ChannelProgram>()
        context.contentResolver.query(
            TvContract.buildPreviewProgramsUriForChannel(channelId),
            projection,
            null,
            null,
            "${TvContract.PreviewPrograms.COLUMN_WEIGHT} DESC",
        )?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_TITLE)
            val posterUriIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
            val aspectRatioIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO)
            val descriptionIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION)
            val ratingIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_CONTENT_RATING)
            val durationIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS)
            val genreIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_CANONICAL_GENRE)
            val seasonIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
            val episodeIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
            val episodeTitleIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE)
            val intentUriIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_INTENT_URI)
            val previewVideoUriIndex = cursor.getColumnIndexOrThrow(TvContract.PreviewPrograms.COLUMN_PREVIEW_VIDEO_URI)
            while (cursor.moveToNext()) {
                val previewVideoUri = cursor.getString(previewVideoUriIndex)
                if (previewVideoUri != null) previewVideoCount++
                programs += ChannelProgram(
                    title = cursor.getString(titleIndex).orEmpty(),
                    posterArtUri = cursor.getString(posterUriIndex),
                    posterAspectRatio = AspectRatioByColumnValue[cursor.getInt(aspectRatioIndex)]
                        ?: DefaultPosterAspectRatio,
                    shortDescription = cursor.getString(descriptionIndex),
                    contentRating = cursor.getString(ratingIndex)?.let(::parseContentRating),
                    durationMinutes = if (cursor.isNull(durationIndex)) {
                        null
                    } else {
                        (cursor.getLong(durationIndex) / 60_000L).toInt()
                    },
                    genres = cursor.getString(genreIndex)?.let { raw ->
                        runCatching { TvContract.Programs.Genres.decode(raw).toList() }.getOrNull()
                    }.orEmpty(),
                    seasonNumber = cursor.getString(seasonIndex),
                    episodeNumber = cursor.getString(episodeIndex),
                    episodeTitle = cursor.getString(episodeTitleIndex),
                    intentUri = cursor.getString(intentUriIndex),
                    previewVideoUri = previewVideoUri,
                )
            }
        }
        // Diagnostic — real trailer availability was an open feasibility
        // question (PRODUCT_SPEC.md Decisions Log: "Full-screen Content Rows
        // carousel"), not something to assume either way.
        if (programs.isNotEmpty()) {
            Log.d(TAG, "channel $channelId: $previewVideoCount/${programs.size} program(s) have a preview video URI")
        }
        return programs
    }

    /** `COLUMN_CONTENT_RATING` is a flattened `TvContentRating` string (e.g. `com.android.tv/US_TV/US_TV_PG`) — this pulls out just the human-readable main rating ("TV-PG"), falling back to the raw column value if it doesn't parse. */
    private fun parseContentRating(raw: String): String? {
        val mainRating = runCatching { TvContentRating.unflattenFromString(raw)?.mainRating }.getOrNull()
        return (mainRating ?: raw).replace('_', '-').ifBlank { null }
    }
}
