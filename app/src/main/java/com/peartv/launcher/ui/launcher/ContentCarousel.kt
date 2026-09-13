package com.peartv.launcher.ui.launcher

import android.content.Context
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import coil.imageLoader
import coil.request.ImageRequest
import com.peartv.launcher.domain.model.AppChannel
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.domain.model.ResolvedArtwork
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.ArtworkSource
import com.peartv.launcher.ui.focus.FocusGainMillis
import com.peartv.launcher.ui.focus.FocusLossMillis
import com.peartv.launcher.ui.motion.kenBurnsTransform
import com.peartv.launcher.ui.motion.rememberKenBurnsProgress
import com.peartv.launcher.ui.theme.ambientPanelTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long a poster holds before either playing its trailer (if published) or advancing — user-directed, raised from the original 5s. */
private const val PosterHoldMillis = 8000L

/** Right-to-left slide transition speed between carousel items — user-directed (replaced the original crossfade). Kept at the same duration [HeroBanner]'s own crossfade used, for a consistent transition speed across both composables even though the shape of the transition itself now differs. */
private const val CarouselTransitionMillis = 400

/** Ambient/background trailer playback — deliberately silent by default (this is a passively-cycling home-screen surface, not something the user opened to watch); flip if that reads wrong on-device. */
private const val TrailerMuted = true

/** How many programs *ahead* of the currently-shown one get their artwork prefetched in the background — see the `resolvedBackdrops` cache's own doc for why. 2 (3 total including the current item) comfortably resolves within [PosterHoldMillis]'s 8s hold at this network's real observed per-item latency (~0.4-1.1s), without firing a large burst of concurrent requests for programs that may never actually be reached. */
private const val PrefetchWindowSize = 2

/**
 * Poster quality — confirmed on-device (real published channel data):
 * Apple TV/Hulu's `ASPECT_RATIO_16_9` (1.78) art crops full-bleed fine; Plex
 * publishes portrait movie-poster art (`ASPECT_RATIO_MOVIE_POSTER`, 0.667)
 * that badly crops if forced full-bleed the same way. 1.2 sits cleanly
 * between the two real values seen — comfortably below every landscape
 * ratio this app's aspect-ratio map produces, comfortably above every
 * portrait/square one.
 */
/** Not `private` — `LauncherViewModel.resolveArtwork`'s `Automatic` policy reuses this same threshold for its own reactive aspect-ratio check, same package. */
const val LandscapeAspectRatioThreshold = 1.2f

/** How much of the screen's height a portrait/square poster's own inset art occupies when there's no TMDB backdrop to swap in instead — large enough to read clearly, small enough to leave room for ProgramMetadata below. */
private const val PortraitInsetHeightFraction = 0.55f

private const val TAG = "ContentCarousel"

private enum class CarouselPhase { Poster, Trailer }

/**
 * `LauncherViewModel.resolveArtwork`'s `Automatic` policy needs the channel
 * poster's *real* decoded pixel dimensions (see that function's own doc for
 * why [ChannelProgram.posterAspectRatio] alone isn't enough) — a plain
 * Coil `ImageRequest`/`imageLoader.execute` reading `Drawable.intrinsicWidth`/
 * `intrinsicHeight`, leaving Coil's normal hardware-bitmap decode path alone.
 */
private suspend fun decodeImageDimensions(context: Context, uri: String): Pair<Int, Int>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context).data(uri).build()
            val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching null
            drawable.intrinsicWidth to drawable.intrinsicHeight
        }.getOrNull()
    }

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
    resolveArtwork: suspend (program: ChannelProgram, channelArtWidth: Int?, channelArtHeight: Int?) -> ResolvedArtwork,
    artworkSource: ArtworkSource,
    metadataRefreshToken: Int,
    activeApp: TvApp?,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    // See HeroBanner's identical parameter for why this isn't just
    // `TopShelfTrayHeight` anymore — the tray no longer sits a fixed
    // distance above this carousel's own bottom edge.
    trayClearance: Dp = TopShelfTrayHeight,
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
    // above (its own effect, not folded into the one above) so a slow
    // provider lookup never delays the poster hold itself. `remember` keys
    // on `artworkSource` — a setting change invalidates every
    // already-resolved decision (they were made under the *old* policy) —
    // and on `metadataRefreshToken`, so Content Sources > "Refresh Metadata"
    // (`LauncherViewModel.refreshMetadata`) invalidates the same cache on
    // demand even when nothing else about the policy changed, lazily
    // re-resolved as each index comes back into view rather than all at
    // once. Every program attempts this now, not just portrait/square
    // ones — `LauncherViewModel.resolveArtwork`'s own policy decides what to
    // do with landscape channel art now, this composable no longer gates
    // the attempt itself.
    //
    // Prefetch window, not just the current index — confirmed on-device
    // (real TVDB/TMDB calls, ~400ms-1.1s each) that resolving purely
    // on-demand showed the channel's own art for a beat on *every single*
    // poster before the online swap-in landed, once the portrait-only gate
    // above stopped limiting how many programs even attempted this. The
    // `PosterHoldMillis` hold (8s) comfortably covers resolving a few
    // programs ahead sequentially at this network's real observed latency,
    // so by the time the carousel naturally advances to one, its own
    // resolution has usually already landed. The current index always
    // resolves first (highest priority) before any prefetching starts.
    // Sequential, not concurrent — kinder to TMDB/TVDB rate limits than
    // firing several requests at once, and still finishes well inside the
    // hold window. Self-healing across advances: if the user advances
    // before a prefetch finishes, this whole `LaunchedEffect` (including
    // any in-flight prefetch) is cancelled and restarts keyed on the new
    // `index`, which immediately re-prioritizes whatever's now on screen —
    // whatever prefetch results already landed before cancellation stay in
    // `resolvedBackdrops`, nothing already resolved is wasted.
    val context = LocalContext.current
    val resolvedBackdrops = remember(channel, artworkSource, metadataRefreshToken) { mutableStateMapOf<Int, ResolvedArtwork>() }

    suspend fun ensureResolved(targetIndex: Int, reason: String) {
        if (resolvedBackdrops.containsKey(targetIndex)) {
            Log.d(TAG, "[$reason] index=$targetIndex already cached, skipping")
            return
        }
        val targetProgram = channel.programs.getOrNull(targetIndex) ?: return
        val startMs = System.currentTimeMillis()
        val posterUri = targetProgram.posterArtUri
        val dimensions = if (posterUri != null) decodeImageDimensions(context, posterUri) else null
        Log.d(TAG, "[$reason] index=$targetIndex '${targetProgram.title}' resolving... channelPosterUri=$posterUri channelAspectRatio=${targetProgram.posterAspectRatio} decodedDimensions=$dimensions")
        val result = resolveArtwork(targetProgram, dimensions?.first, dimensions?.second)
        resolvedBackdrops[targetIndex] = result
        Log.d(TAG, "[$reason] index=$targetIndex '${targetProgram.title}' resolved in ${System.currentTimeMillis() - startMs}ms -> $result")
    }

    LaunchedEffect(index) {
        ensureResolved(index, "current")
        for (ahead in 1..PrefetchWindowSize) {
            ensureResolved((index + ahead).mod(channel.programs.size), "prefetch+$ahead")
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
            // Same containment fix as HeroBanner.kt's outer Box — Compose
            // doesn't clip a child's drawing to a parent's layout bounds by
            // default, so kenBurns()'s scale transform (KenBurns.kt) still
            // paints past this Box's own bounds without it, bleeding into
            // the dock stacked below.
            .clip(RectangleShape)
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
                CarouselPhase.Poster -> PosterBackdrop(crossfadeProgram, resolvedBackdrops[crossfadeIndex], activeApp)
                CarouselPhase.Trailer -> {
                    TrailerPlayer(
                        uri = crossfadeProgram.previewVideoUri.orEmpty(),
                        onEnded = { advance(1) },
                        onError = { advance(1) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Shared bottom/left vignette tuning (Vignette.kt) — same feathered,
        // bounded, theme-aware fade HeroBanner.kt's Tier 1/2 hero uses, not
        // a separately-tuned one. This carousel had its own plain 2-stop,
        // unbounded fade (fully transparent at the very top down to fully
        // opaque at the very bottom; similarly unbounded left-to-right)
        // until user-directed unification — same hard-edge and
        // fades-through-black artifacts featheredEdgeStops' own doc
        // describes, just never caught here independently since this
        // carousel is usually covered in real photographic art busy enough
        // to mask it, unlike Tier 2's flat sampled-color fill.
        //
        // Fades to `MaterialTheme.ambientPanelTint()`, not flat
        // `backgroundColor` — same fix as HeroBanner.kt's identical vignette,
        // see that file's own doc: fading to a flat color created a new hard
        // seam against `ambientBackground`'s glow in the chrome behind this
        // carousel once that existed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = featheredEdgeStops(
                            color = MaterialTheme.ambientPanelTint(),
                            start = 1f - VignetteBottomFraction,
                            end = 1f,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = featheredEdgeStops(
                            color = MaterialTheme.ambientPanelTint(),
                            start = 0f,
                            end = VignetteLeftFraction,
                            reversed = true,
                        ),
                    ),
                ),
        )

        // Dedicated text-legibility scrim (§5 #14) — a second, taller/
        // stronger, fixed-dark fade layered on top of the ambient vignette
        // above; see TopShelfTextScrimFraction's own doc (Vignette.kt) for
        // why these are two separately-tuned gradients, not one reused for
        // both jobs.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = featheredEdgeStops(
                            color = MaterialTheme.colorScheme.scrim,
                            start = 1f - TopShelfTextScrimFraction,
                            end = 1f,
                            maxAlpha = TopShelfTextScrimMaxAlpha,
                        ),
                    ),
                ),
        )

        ProgramMetadata(
            program = program,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = ScreenSafeAreaHorizontal,
                    end = ScreenSafeAreaHorizontal,
                    bottom = trayClearance + 16.dp,
                ),
        )
    }
}

/**
 * Poster quality (confirmed on real published channel data — see
 * [LandscapeAspectRatioThreshold]'s doc): landscape art (or a
 * [ResolvedArtwork.OnlineMatch] swap-in, which is always a landscape
 * backdrop) fills full-bleed. Portrait/square art with no swap-in available
 * gets [PortraitPosterBackdrop] instead of being force-cropped into
 * unrecognizability. [ResolvedArtwork.UseTier2Icon] (the `ArtworkSource`
 * policy found nothing usable — `LauncherViewModel.resolveArtwork`'s own
 * doc) renders [activeApp]'s own icon+color card ([Tier2IconFill]) instead
 * of a broken or absent poster. `null` [resolvedArtwork] (the async lookup
 * hasn't landed yet) is treated the same as [ResolvedArtwork.UseChannelArt]
 * — show the channel's own art immediately, swap to the resolved outcome
 * once it lands, rather than a loading flash.
 *
 * The dock/pill/hero blur is a live `RenderEffect` capture of this
 * composable's own output now (`BackdropBlur.kt`), so there's no backdrop
 * artwork to report upward — the sharp art drawn here is simply what those
 * panels blur.
 */
@Composable
private fun PosterBackdrop(
    program: ChannelProgram,
    resolvedArtwork: ResolvedArtwork?,
    activeApp: TvApp?,
) {
    val posterUri = program.posterArtUri
    // Ambient Ken Burns motion (ui/motion/KenBurns.kt) on both real-art
    // branches — restarts fresh per program automatically, since this
    // whole composable is already recomposed per AnimatedContent target
    // state (this file's own carousel Box).
    when (resolvedArtwork) {
        is ResolvedArtwork.OnlineMatch -> {
            // Provider ranking prefers a landscape candidate (see each
            // repository's own `searchBackdrop` doc) but doesn't require
            // one — confirmed on-device that force-cropping a
            // portrait-shaped match full-bleed looked badly zoomed-in, not
            // actually landscape despite technically filling the frame. A
            // non-landscape match gets the exact same inset/uncropped
            // treatment channel-provided portrait art already uses, real
            // dimensions respected either way.
            val isLandscape = resolvedArtwork.width > 0 && resolvedArtwork.height > 0 &&
                resolvedArtwork.width.toFloat() / resolvedArtwork.height >= LandscapeAspectRatioThreshold
            if (isLandscape) {
                val progress = rememberKenBurnsProgress()
                AsyncImage(
                    model = resolvedArtwork.url,
                    contentDescription = program.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .kenBurnsTransform(progress.value),
                )
            } else {
                PortraitPosterBackdrop(
                    resolvedArtwork.url,
                    program.title,
                    resolvedArtwork.width.toFloat() / resolvedArtwork.height.coerceAtLeast(1),
                )
            }
        }
        ResolvedArtwork.UseTier2Icon -> {
            Tier2IconFill(
                icon = activeApp?.icon,
                iconPrimaryColorArgb = activeApp?.iconPrimaryColorArgb,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ResolvedArtwork.UseChannelArt, null -> when {
            posterUri == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            program.posterAspectRatio >= LandscapeAspectRatioThreshold -> {
                val progress = rememberKenBurnsProgress()
                AsyncImage(
                    model = posterUri,
                    contentDescription = program.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .kenBurnsTransform(progress.value),
                )
            }
            else -> {
                PortraitPosterBackdrop(posterUri, program.title, program.posterAspectRatio)
            }
        }
    }
}

/**
 * tvOS's own real treatment for portrait-shaped art in a landscape hero: the
 * actual poster, uncropped, over an ambient tinted wash — not the same image
 * force-cropped full-bleed, which (confirmed on real Plex 204×306 art) zooms
 * in and crops away most of the poster horizontally. Also reused (not just
 * for channel-provided portrait art) for a [ResolvedArtwork.OnlineMatch]
 * that turned out not to be landscape-shaped — same reasoning applies to a
 * portrait provider match as to portrait channel art.
 *
 * Ambient Ken Burns motion, same [kenBurnsTransform] the full-bleed
 * branches use — applied to the *inset* image itself, not a full-bleed
 * layer, so the pan/zoom stays within the poster's own real proportions
 * rather than assuming a landscape frame to move around in.
 *
 * A real `RenderEffect` blur of the poster's own art fills the frame behind
 * the sharp inset (`Modifier.blur`, available on the API-34 floor), under a
 * translucent scrim for legibility — the frosted-backdrop treatment tvOS
 * gives portrait art in a landscape hero.
 */
@Composable
private fun PortraitPosterBackdrop(posterUri: String, title: String, aspectRatio: Float) {
    val progress = rememberKenBurnsProgress()
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = posterUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(DockBlurRadius),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                .kenBurnsTransform(progress.value),
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
 *
 * §5 #14 — text color is fixed near-white
 * (`MaterialTheme.colorScheme.inverseOnSurface` — see Theme.kt's own doc for
 * why that role is fixed regardless of theme), not the theme-flipped
 * `MaterialTheme.colorScheme.onBackground` every other piece of chrome in
 * this app uses. Paired with the fixed-dark text scrim
 * above it (this composable's caller), matching real tvOS's own consistent
 * white-on-dark treatment for Top Shelf content specifically — see
 * `TopShelfTextScrimFraction`'s doc (Vignette.kt) for why. A subtle shadow
 * on the title reinforces contrast further without an opaque card behind
 * it. Weight: SemiBold for the title (primary), the rest stay at their
 * style's own default (Normal/Medium) as secondary text.
 */
@Composable
private fun ProgramMetadata(
    program: ChannelProgram,
    modifier: Modifier = Modifier,
) {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 2f),
        blurRadius = 6f,
    )
    Column(modifier = modifier) {
        val episodeBadge = buildEpisodeBadge(program)
        if (episodeBadge != null) {
            Text(
                text = episodeBadge,
                style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        Text(
            text = program.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = textShadow,
            ),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val description = program.shortDescription
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
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
                style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(top = 8.dp),
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
