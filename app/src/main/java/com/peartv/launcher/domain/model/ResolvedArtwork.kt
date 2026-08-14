package com.peartv.launcher.domain.model

/**
 * The outcome of `LauncherViewModel.resolveArtwork`'s per-program
 * `ArtworkSource` policy decision (`ContentCarousel`'s `PosterBackdrop`
 * renders each variant) — `sealed interface`, not `sealed class` (unlike this
 * package's other sealed hierarchies, e.g. `LauncherGridItem`), since no
 * variant here carries shared state to justify a base class.
 */
sealed interface ResolvedArtwork {
    /**
     * A provider match won. [width]/[height] are the matched image's own
     * real dimensions ([ArtworkMatch.width]/[ArtworkMatch.height]) —
     * `PosterBackdrop` uses them to decide *how* to render [url], not just
     * that it should: full-bleed only when the match is actually
     * landscape-shaped, the same inset/uncropped treatment channel-provided
     * portrait art already gets otherwise. Provider ranking prefers a
     * landscape candidate when one exists (see each repository's own
     * `searchBackdrop` doc), but doesn't require one — confirmed on-device
     * that force-cropping a portrait-shaped match full-bleed (the original
     * behavior) looked badly zoomed-in, effectively still reading as
     * "portrait" despite technically filling a landscape frame.
     */
    data class OnlineMatch(val url: String, val width: Int, val height: Int) : ResolvedArtwork

    /** Use the program's own channel-provided art, exactly as `PosterBackdrop` already renders it (landscape full-bleed or portrait letterboxed by its own aspect ratio). */
    data object UseChannelArt : ResolvedArtwork

    /** Nothing usable — neither the channel's own art nor a provider match cleared the active policy's bar. Renders the focused app's own icon+color card (`Tier2IconFill`) instead of a broken or absent poster. */
    data object UseTier2Icon : ResolvedArtwork
}
