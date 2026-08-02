package com.peartv.launcher.ui.launcher

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.peartv.launcher.domain.model.AppChannel
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.ui.focus.FocusGainMillis
import com.peartv.launcher.ui.focus.FocusLossMillis
import kotlinx.coroutines.delay

/** How long a poster holds before either playing its trailer (if published) or advancing — user-directed, raised from the original 5s. */
private const val PosterHoldMillis = 8000L

/** Right-to-left slide transition speed between carousel items — user-directed (replaced the original crossfade). Kept at the same duration [HeroBanner]'s own crossfade used, for a consistent transition speed across both composables even though the shape of the transition itself now differs. */
private const val CarouselTransitionMillis = 400

/** Ambient/background trailer playback — deliberately silent by default (this is a passively-cycling home-screen surface, not something the user opened to watch); flip if that reads wrong on-device. */
private const val TrailerMuted = true

/**
 * Poster quality — confirmed on-device (real published channel data):
 * Apple TV/Hulu's `ASPECT_RATIO_16_9` (1.78) art crops full-bleed fine; Plex
 * publishes portrait movie-poster art (`ASPECT_RATIO_MOVIE_POSTER`, 0.667)
 * that badly crops if forced full-bleed the same way. 1.2 sits cleanly
 * between the two real values seen — comfortably below every landscape
 * ratio this app's aspect-ratio map produces, comfortably above every
 * portrait/square one.
 */
private const val LandscapeAspectRatioThreshold = 1.2f

/** How much of the screen's height a portrait/square poster's own inset art occupies when there's no TMDB backdrop to swap in instead — large enough to read clearly, small enough to leave room for ProgramMetadata below. */
private const val PortraitInsetHeightFraction = 0.55f

private const val TAG = "ContentCarousel"

private enum class CarouselPhase { Poster, Trailer }

/**
 * PRODUCT_SPEC.md §3.1.2 Template 1 (Full-Screen Carousel) — Tier 3's
 * presentation, replacing the previous `ContentRows` (Template 3, a row of
 * small posters) entirely. User-directed: the row-of-posters treatment
 * "take up about half the screen," had no motion, and its posters weren't
 * selectable — none of which matched real tvOS's actual Top Shelf for an app
 * like Apple TV or Hulu.
 *
 * Renders [channel]'s programs one at a time, full-bleed. Each poster holds
 * for [PosterHoldMillis], then — if [ChannelProgram.previewVideoUri] is
 * published — slides into playing that trailer (via `media3`
 * `ExoPlayer`/`PlayerView`, embedded through `AndroidView`) until it ends or
 * errors, at which point the carousel advances to the next program and the
 * cycle repeats. A program with no preview video simply advances after the
 * hold, no video step. D-pad Left/Right manually retreat/advance at any
 * point — during the poster hold *or* mid-trailer — cancelling whatever's
 * currently showing and resetting the hold for the newly-selected program;
 * Center/Enter launches it (`onProgramClick`, same `AppLauncher.launchContent`
 * path `ContentRows` used).
 *
 * Only [AppChannel.programs] from a single channel are shown — multi-channel
 * apps (Decisions Log: "Multi-channel Content Rows") no longer render every
 * channel as a stacked section; the caller (`LauncherScreen`) passes just the
 * first/primary one. A full-screen carousel is inherently one-at-a-time, so
 * simultaneous multi-channel display doesn't carry over into this
 * presentation — see the Decisions Log entry for this trade-off.
 *
 * No real `RenderEffect`/hardware compositing tricks here — trailer playback
 * is real video decode, which is exactly what PRODUCT_SPEC.md §4's original
 * "no live-updating video previews" non-goal was protecting the §0 frame
 * budget against. That non-goal is reopened specifically for this feature
 * (Decisions Log: "Full-screen Content Rows carousel") — on-device frame-
 * pacing verification during real D-pad navigation while a trailer is
 * playing is still outstanding, not assumed safe just because it builds.
 *
 * [focusRequester] is this composable's own attach point (see
 * `TopShelfRow`'s doc for why explicit `FocusRequester`s were needed here at
 * all — default geometric search couldn't route `DPAD_UP` into a full-screen
 * target). [upFocusRequester] is where pressing Up *from* the carousel goes
 * next — the settings gear (`StatusBar`), so Up-Up from a dock tile with a
 * carousel reaches Settings, matching the single-Up path apps without one
 * get (`LauncherScreen` wires both cases). A subtle low-opacity border fades
 * in while the carousel actually holds real focus, so it's visually clear
 * D-pad input has moved off the dock and onto this — user-directed, after
 * confirming Left/Right/Center silently did nothing here (see the Decisions
 * Log — that was a real bug, not by design).
 */
@Composable
fun ContentCarousel(
    channel: AppChannel,
    onProgramClick: (ChannelProgram) -> Unit,
    resolveBackdropUrl: suspend (title: String) -> String?,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (channel.programs.isEmpty()) return

    var index by remember(channel) { mutableIntStateOf(0) }
    var phase by remember(channel) { mutableStateOf(CarouselPhase.Poster) }
    // Single-program channels advance() back to the same index, and Compose
    // skips the state write when the value is unchanged — so LaunchedEffect
    // keyed on `index` alone would never restart. `cycle` always changes.
    var cycle by remember(channel) { mutableIntStateOf(0) }
    val program = channel.programs.getOrNull(index) ?: return

    fun advance(delta: Int) {
        index = (index + delta).mod(channel.programs.size)
        cycle++
    }

    LaunchedEffect(index, cycle) {
        Log.d(TAG, "${channel.displayName}: showing index=$index/${channel.programs.size - 1} '${program.title}'")
        phase = CarouselPhase.Poster
        delay(PosterHoldMillis)
        if (program.previewVideoUri != null) {
            Log.d(TAG, "${channel.displayName}: index=$index has a trailer, playing")
            phase = CarouselPhase.Trailer
        } else {
            Log.d(TAG, "${channel.displayName}: index=$index no trailer, advancing")
            advance(1)
        }
    }

    // Poster quality — resolved independently of the hold/advance timer
    // above (its own effect, not folded into the one above) so a slow TMDB
    // lookup never delays the poster hold itself. Keyed on `index` alone,
    // not `cycle`: a network fetch is idempotent and cache-checked below, so
    // re-running it for the single-item edge case the `cycle` counter above
    // exists for is harmless, not incorrect. Landscape art never attempts a
    // lookup at all — Apple TV/Hulu's own art is already the right shape;
    // only portrait/square art (confirmed on real Plex data) tries a swap-in.
    val resolvedBackdrops = remember(channel) { mutableStateMapOf<Int, String?>() }
    LaunchedEffect(index) {
        if (resolvedBackdrops.containsKey(index)) return@LaunchedEffect
        resolvedBackdrops[index] = if (program.posterAspectRatio < LandscapeAspectRatioThreshold) {
            resolveBackdropUrl(program.title)
        } else {
            null
        }
    }

    var isFocused by remember { mutableStateOf(false) }
    val focusIndicatorAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(if (isFocused) FocusGainMillis else FocusLossMillis),
        label = "carouselFocusIndicator",
    )

    val backgroundColor = MaterialTheme.colorScheme.background
    val indicatorColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .focusProperties { up = upFocusRequester }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { advance(-1); true }
                    Key.DirectionRight -> { advance(1); true }
                    Key.DirectionCenter, Key.Enter -> { onProgramClick(program); true }
                    else -> false
                }
            }
            // Subtle indicator that the carousel itself (not the dock) is
            // what D-pad input currently controls — user-directed. A thin,
            // low-opacity inset border rather than anything heavier; this is
            // full-screen content, not a tile, so the scale/glow language
            // §1.1 uses for tiles doesn't apply here.
            .border(
                width = 2.dp,
                color = indicatorColor.copy(alpha = focusIndicatorAlpha * 0.5f),
            ),
    ) {
        AnimatedContent(
            targetState = index to phase,
            transitionSpec = {
                // User-directed: right-to-left slide — new content enters
                // from the right edge, previous content continues on
                // leftward off-screen, replacing the original crossfade.
                slideInHorizontally(animationSpec = tween(CarouselTransitionMillis)) { fullWidth -> fullWidth } togetherWith
                    slideOutHorizontally(animationSpec = tween(CarouselTransitionMillis)) { fullWidth -> -fullWidth }
            },
            label = "carouselContent",
        ) { (crossfadeIndex, crossfadePhase) ->
            val crossfadeProgram = channel.programs.getOrNull(crossfadeIndex) ?: return@AnimatedContent
            when (crossfadePhase) {
                CarouselPhase.Poster -> PosterBackdrop(crossfadeProgram, resolvedBackdrops[crossfadeIndex])
                CarouselPhase.Trailer -> TrailerPlayer(
                    uri = crossfadeProgram.previewVideoUri.orEmpty(),
                    onEnded = { advance(1) },
                    onError = { advance(1) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, backgroundColor))),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(backgroundColor.copy(alpha = 0.5f), Color.Transparent))),
        )

        ProgramMetadata(
            program = program,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = ScreenSafeAreaHorizontal,
                    end = ScreenSafeAreaHorizontal,
                    bottom = TopShelfTrayHeight + 16.dp,
                ),
        )
    }
}

/**
 * Poster quality (confirmed on real published channel data — see
 * [LandscapeAspectRatioThreshold]'s doc): landscape art (or a [resolvedBackdropUrl]
 * TMDB swap-in, which is always a landscape backdrop) fills full-bleed same
 * as before. Portrait/square art with no swap-in available gets
 * [PortraitPosterBackdrop] instead of being force-cropped into
 * unrecognizability.
 */
@Composable
private fun PosterBackdrop(program: ChannelProgram, resolvedBackdropUrl: String?) {
    val posterUri = program.posterArtUri
    when {
        resolvedBackdropUrl != null -> AsyncImage(
            model = resolvedBackdropUrl,
            contentDescription = program.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        posterUri == null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        program.posterAspectRatio >= LandscapeAspectRatioThreshold -> AsyncImage(
            model = posterUri,
            contentDescription = program.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        else -> PortraitPosterBackdrop(posterUri, program.title, program.posterAspectRatio)
    }
}

/**
 * tvOS's own real treatment for portrait-shaped art in a landscape hero: the
 * actual poster, uncropped, over an ambient blurred/darkened wash of itself
 * — not the same image force-cropped full-bleed, which (confirmed on real
 * Plex 204×306 art) zooms in and crops away most of the poster horizontally.
 *
 * Uses this project's own [blurredBackdrop] (`BackdropBlur.kt`) rather than
 * `Modifier.blur()` — that file's doc already established `Modifier.blur()`
 * needs API 31+ (this app's floor is API 30) and that a multi-pass
 * alternative crashed the renderer on real hardware; this reuses the same
 * single-pass technique already proven stable on-device elsewhere in this
 * screen, rather than re-litigating that constraint here.
 *
 * The blurred layer is declared *before* the sharp inset poster so the
 * inset visually sits in front of it, which means it draws one frame before
 * [recordBackdropSource] below has recorded anything the very first frame —
 * harmless for a static network image (unlike the live/animating content
 * [blurredBackdrop]'s own doc is concerned about), since every frame after
 * the first reads that same unchanging image back.
 */
@Composable
private fun PortraitPosterBackdrop(posterUri: String, title: String, aspectRatio: Float) {
    val sourceLayer = rememberGraphicsLayer()
    val lowResLayer = rememberGraphicsLayer()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blurredBackdrop(source = sourceLayer, lowResLayer = lowResLayer, sourceOffset = { Offset.Zero })
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
        )
        AsyncImage(
            model = posterUri,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(PortraitInsetHeightFraction)
                .aspectRatio(aspectRatio)
                .recordBackdropSource(sourceLayer),
        )
    }
}

/** Ambient, non-interactive trailer playback — `useController = false`, since this is a passively-cycling background surface, not a video the user opened to scrub through. */
@Composable
private fun TrailerPlayer(
    uri: String,
    onEnded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            volume = if (TrailerMuted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // media3-ui's own fix for a well-known Compose/ExoPlayer
                // interop bug: PlayerView's default SurfaceView doesn't
                // composite correctly inside animated Compose content (the
                // Crossfade this sits in) — glitches/flickers during the
                // transition. This flag exists specifically for that case,
                // rather than manually forcing a TextureView (more overhead,
                // no hardware-accelerated fast path).
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        update = { it.player = player },
    )
}

/**
 * §3.1.2 Template 1's metadata stack, applied to the carousel's currently
 * displayed program. Text only — no action button, same reasoning as
 * `ContentRows`' own "Content Rows has no Play button" decision: selecting
 * the poster itself (D-pad center) is the only way to act on it.
 */
@Composable
private fun ProgramMetadata(
    program: ChannelProgram,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val episodeBadge = buildEpisodeBadge(program)
        if (episodeBadge != null) {
            Text(
                text = episodeBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = program.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val description = program.shortDescription
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val metaLine = listOfNotNull(
            program.contentRating,
            program.durationMinutes?.let { "$it min" },
            program.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ).joinToString("   •   ")
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun buildEpisodeBadge(program: ChannelProgram): String? {
    val season = program.seasonNumber
    val episode = program.episodeNumber
    if (season == null && episode == null) return null
    val prefix = buildString {
        if (season != null) append("S$season")
        if (episode != null) {
            if (isNotEmpty()) append(":")
            append("E$episode")
        }
    }
    val episodeTitle = program.episodeTitle
    return if (episodeTitle != null) "$prefix \"$episodeTitle\"" else prefix
}
