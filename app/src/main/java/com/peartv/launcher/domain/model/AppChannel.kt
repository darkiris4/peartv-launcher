package com.peartv.launcher.domain.model

/**
 * PRODUCT_SPEC.md §3.1.1 "Content Rows" (Tier 3) — a focused app's own
 * published Home Screen Channel (`android.media.tv.TvContract`), real data
 * the app chose to publish itself, not a curated guess. A package can
 * publish more than one channel — `ChannelsRepository.fetchChannels`
 * surfaces all of them (Decisions Log: "Multi-channel Content Rows").
 */
data class AppChannel(
    val displayName: String,
    val programs: List<ChannelProgram>,
)

/**
 * One `PreviewPrograms` row. [posterAspectRatio] is read per-program, never
 * assumed fixed (§3.1.1/§5).
 *
 * §3.1.2 Template 1's metadata stack + action button, built for real for
 * Tier 3 only (Task #25 — Tier 1/2 have no comparably rich, genuinely
 * sourced data to back this with): [shortDescription] (tagline),
 * [contentRating], [durationMinutes], [genres], the episode/season trio, and
 * [intentUri] (the real deep link backing the Play button — `null` falls
 * back to just launching the app, `AppLauncher.launchContent`). All
 * optional — a program that doesn't publish a given field simply omits that
 * part of the metadata line/badge, never a placeholder.
 *
 * [previewVideoUri] backs the full-screen carousel's trailer playback
 * (Decisions Log: "Full-screen Content Rows carousel") — `COLUMN_PREVIEW_VIDEO_URI`,
 * read the same way every other optional column here is: present only when
 * the publishing app actually populated it, never assumed.
 */
data class ChannelProgram(
    val title: String,
    val posterArtUri: String?,
    val posterAspectRatio: Float,
    val shortDescription: String? = null,
    val contentRating: String? = null,
    val durationMinutes: Int? = null,
    val genres: List<String> = emptyList(),
    val seasonNumber: String? = null,
    val episodeNumber: String? = null,
    val episodeTitle: String? = null,
    val intentUri: String? = null,
    val previewVideoUri: String? = null,
)
