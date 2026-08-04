package com.peartv.launcher

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peartv.launcher.domain.repository.ThemeMode
import com.peartv.launcher.domain.usecase.GetInstalledAppsUseCase
import com.peartv.launcher.domain.usecase.LaunchAppUseCase
import com.peartv.launcher.domain.usecase.LaunchContentUseCase
import com.peartv.launcher.domain.usecase.RequestUninstallUseCase
import com.peartv.launcher.ui.launcher.BlurredArtwork
import com.peartv.launcher.ui.launcher.DockBackdrop
import com.peartv.launcher.ui.launcher.HeroExpansionMillis
import com.peartv.launcher.ui.launcher.LauncherScreen
import com.peartv.launcher.ui.launcher.LauncherViewModel
import com.peartv.launcher.ui.launcher.LauncherViewModelFactory
import com.peartv.launcher.ui.launcher.StatusBar
import com.peartv.launcher.ui.launcher.reblurred
import com.peartv.launcher.ui.motion.LocalReduceMotion
import com.peartv.launcher.ui.motion.isReduceMotionEnabled
import com.peartv.launcher.ui.settings.SettingsRoute
import com.peartv.launcher.ui.settings.SettingsScreen
import com.peartv.launcher.ui.settings.SettingsViewModel
import com.peartv.launcher.ui.settings.SettingsViewModelFactory
import com.peartv.launcher.ui.theme.PearTvLauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Hosts the launcher and the settings screen (originally §4's narrowly-scoped
 * surface, since expanded per an explicit new ask into a real tvOS-style
 * hierarchy — see `ui/settings/SettingsScreen.kt`'s own doc) as two states of
 * a single Compose surface — still no `Navigation-Compose` dependency for
 * just two top-level screens; a plain enum + `mutableStateOf` for [Screen]
 * itself remains simpler than a nav-graph. Settings' own internal drill-down
 * needs a real back stack now that it's 3 levels deep in places (see the
 * `settingsBackStack` below) — that's a small `mutableStateListOf`, not
 * `Navigation-Compose` either.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as PearTvLauncherApplication
        // Read once, synchronously, before super.onCreate() — DataStore's
        // own Flow is async, but the pre-Compose window theme (below) and
        // this Activity's first Compose frame both need the real persisted
        // value immediately, not several frames later. Confirmed on-device:
        // without this, a light-theme user's cold launch painted the dark
        // windowBackground (themes.xml's default), then rendered the whole
        // Compose UI fully dark-themed for a frame or more, then popped to
        // light once the async read finally landed — a jarring whole-UI
        // color flash on every launch that dark-theme users never hit,
        // since their default already matched. A local DataStore read is a
        // small, already-open file — blocking main thread for it briefly at
        // startup is the same trade every "wait for real prefs before first
        // frame" splash pattern makes.
        val initialThemeMode = runBlocking { app.settingsRepository.themeMode.first() }
        // `Automatic` has no async Flow to read here — the system's own
        // light/dark appearance is a synchronous `Configuration` read, same
        // as `themeMode` itself needing to be resolved before the very
        // first frame (this method's own doc, above).
        val initialDarkTheme = when (initialThemeMode) {
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
            ThemeMode.Automatic ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        setTheme(if (initialDarkTheme) R.style.Theme_PearTvLauncher else R.style.Theme_PearTvLauncher_Light)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val launcherViewModel: LauncherViewModel = viewModel(
                factory = LauncherViewModelFactory(
                    getInstalledApps = GetInstalledAppsUseCase(app.launcherAppRepository),
                    launchApp = LaunchAppUseCase(app.appLauncher),
                    launchContent = LaunchContentUseCase(app.appLauncher),
                    requestUninstall = RequestUninstallUseCase(app.appLauncher),
                    settingsRepository = app.settingsRepository,
                    tmdbRepository = app.tmdbRepository,
                    channelsRepository = app.channelsRepository,
                    layoutRepository = app.layoutRepository,
                ),
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(app.settingsRepository, initialThemeMode),
            )
            PearTvLauncherApp(launcherViewModel, settingsViewModel)
        }
    }
}

private enum class Screen { Launcher, Settings }

/**
 * Independently-tuned, and *heavier* than the dock's own `BlurRadius`
 * (`BlurredArtwork.kt`) — user-directed: Settings' backdrop is a still image
 * (no Ken Burns motion, no legibility floor from moving art), so it can (and
 * should) read as more heavily "frosted" than the dock's own lighter blur.
 * The toolkit's own max (`com.google.android.renderscript.Toolkit.blur`'s
 * own doc: valid range 1..25) — see [BlurredArtwork.reblurred]'s own doc for
 * why this is a cheap *second* pass over the dock's already-blurred bitmap,
 * not a fresh decode+blur.
 */
private const val SettingsBackdropBlurRadius = 25

/**
 * User-directed: a plain crossfade (no slide/scale/zoom) — cheap on Shield
 * hardware, matching the same reasoning already applied to the dock's own
 * blur work (extra compositing layers there cost real frame time; a
 * `Screen.Launcher`↔`Screen.Settings` swap is a much bigger subtree than a
 * single dock panel, so the same caution applies more, not less). Kept to
 * this alone unless a plain crossfade genuinely reads as insufficient once
 * seen running on-device — not assumed in advance.
 */
private const val ScreenTransitionMillis = 280

@Composable
private fun PearTvLauncherApp(
    launcherViewModel: LauncherViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    // `Automatic` resolves live against the system's own light/dark
    // appearance (recomposes if it changes while the app is open); `Light`/
    // `Dark` stay the fixed choices they always were. `PearTvLauncherTheme`
    // itself is unchanged — still just a plain `darkTheme: Boolean`.
    val isDarkTheme = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.Automatic -> isSystemInDarkTheme()
    }
    var screen by remember { mutableStateOf(Screen.Launcher) }
    // `SettingsPageScaffold`'s own Liquid-Glass-style backdrop — declared at
    // this level, above the `when (screen)` swap below, specifically so it
    // survives that swap. `dockBackdrop` (inside the `Screen.Launcher`
    // branch) does not: a plain `when` only composes its matching branch,
    // so Compose fully disposes `Screen.Launcher`'s whole subtree — and
    // whatever state lived inside it — the instant `screen` flips to
    // `Settings` (confirmed by investigation before this was added; there's
    // no live hero/carousel composed underneath Settings to read a backdrop
    // from at that point). Mirrored from `dockBackdrop`'s own artwork
    // on every real change, last value wins — see the `Screen.Launcher`
    // branch below for where that mirroring actually happens.
    var cachedSettingsBackdrop by remember { mutableStateOf<BlurredArtwork?>(null) }
    // Read once at startup (LocalReduceMotion's own doc) — every focus
    // animation in ui/focus/TvFocusable.kt consults this to skip tilt
    // entirely and snap (not spring) scale/elevation when Android's
    // animator-duration-scale accessibility setting is off.
    val context = LocalContext.current
    val reduceMotion = remember { context.isReduceMotionEnabled() }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
    PearTvLauncherTheme(darkTheme = isDarkTheme) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                fadeIn(animationSpec = tween(ScreenTransitionMillis)) togetherWith
                    fadeOut(animationSpec = tween(ScreenTransitionMillis))
            },
            label = "screenTransition",
        ) { targetScreen ->
        // Guard against the outgoing branch (still composed here, mid
        // exit-fade, during the crossfade above) staying D-pad-reachable —
        // a common TV bug: press a direction mid-transition and focus lands
        // somewhere in the *disappearing* screen instead of the incoming
        // one, since Compose's own focus-search fallback doesn't know one
        // branch is on its way out. `canFocus` set on this wrapping `Box`
        // is inherited by every descendant focus target inside it (the
        // same `FocusProperties`-ancestor-walk mechanism `settingsInitialFocus`
        // already relies on, just applied at the whole-screen level here
        // instead of a single row).
        //
        // Only the OUTGOING branch gets this modifier at all — confirmed
        // on-device that adding it unconditionally (even with `canFocus =
        // true` for the incoming branch) broke Settings' own 350ms initial-
        // focus grace window (`settingsInitialFocus`/
        // `LocalSettingsInitialFocusGraceActive`): a single gear-icon press
        // navigated Root -> Appearance in one shot. Compose's `FocusProperties`
        // ancestor walk applies the *farthest* ancestor's block last, so an
        // explicit `canFocus = true` here — even though `true` looks like a
        // no-op — was overriding the row-level `canFocus = false` set deeper
        // in the tree during the grace window. Not adding a `focusProperties`
        // node at all for the incoming branch leaves that deeper, more
        // specific gating as the only thing in play, exactly as it worked
        // before this whole-screen wrapper existed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (targetScreen != screen) {
                        Modifier.focusProperties { canFocus = false }
                    } else {
                        Modifier
                    },
                ),
        ) {
        when (targetScreen) {
            Screen.Launcher -> {
                // Shared between `LauncherScreen`'s carousel and `StatusBar`
                // (a sibling here, not a descendant of the launcher content)
                // so the clock/settings pill can draw the same Liquid-Glass-
                // style crop of the currently-shown poster the dock does —
                // see `DockBackdrop`'s own doc (`BlurredArtwork.kt`). Hoisted
                // to this shared ancestor rather than owned by either child.
                var dockBackdrop by remember { mutableStateOf<DockBackdrop?>(null) }
                // Mirrors every real artwork change into `cachedSettingsBackdrop`
                // (hoisted above the `when (screen)` swap, this function's own
                // doc on it) — last value wins, never cleared back to `null`
                // here even while `dockBackdrop` itself currently has no
                // artwork (Tier 1/2, or Tier 3 between poster loads):
                // Settings should keep showing whatever the most recent real
                // artwork was, not go back to a flat fill just because the
                // dock momentarily has nothing of its own to show. Re-blurred
                // a second time at [SettingsBackdropBlurRadius] — user-directed:
                // Settings' own static backdrop should read as more heavily
                // "frosted" than the dock's own lighter, motion-tuned blur —
                // rather than caching the dock's own bitmap as-is (see
                // `BlurredArtwork.reblurred`'s own doc for why re-blurring the
                // already-small cached copy, not a fresh decode, is cheap
                // enough to do here on every artwork change).
                val currentDockArtwork = dockBackdrop?.artwork?.value
                LaunchedEffect(currentDockArtwork) {
                    currentDockArtwork?.let {
                        cachedSettingsBackdrop = it.reblurred(SettingsBackdropBlurRadius)
                    }
                }
                // The hero/carousel's own real window rect — `StatusBar`
                // needs this as the reference frame [dockBackdrop]'s artwork
                // was `ContentScale.Crop`'d across, to map its own on-screen
                // position back into that artwork's pixel coordinates
                // (`positionAwareBackdropCrop`'s own doc, `GlassPanel.kt`).
                // Hoisted for the same reason `dockBackdrop` is.
                var heroWindowRect by remember { mutableStateOf(Rect.Zero) }
                // User-directed: Up from the dock (via `ContentCarousel`, or
                // directly for apps with no carousel — see `LauncherScreen`)
                // should reach the settings gear. Hoisted here for the same
                // reason `dockBackdrop` is — `StatusBar` and the launcher
                // content are siblings, not parent/child.
                val settingsFocusRequester = remember { FocusRequester() }
                // User-directed: the status pill (clock + settings gear)
                // should collapse away with the hero too, not sit as a
                // permanently-static overlay regardless of hero state.
                // Re-derives the same `isTopShelfFocused`/`expansionProgress`
                // signal `LauncherScreen` computes for the hero itself
                // (`focusedItemId` in `dockItems`) rather than threading a
                // callback out of it — `StatusBar` and the launcher content
                // are siblings here, same reason `settingsFocusRequester`
                // above is already hoisted to this shared ancestor instead
                // of owned by either child.
                val focusedItemId by launcherViewModel.focusedItemId.collectAsStateWithLifecycle()
                val dockItems by launcherViewModel.dockItems.collectAsStateWithLifecycle()
                val dockPackageNames = remember(dockItems) { dockItems.map { it.id }.toSet() }
                val isTopShelfFocused = focusedItemId == null || focusedItemId in dockPackageNames
                val statusBarAlpha by animateFloatAsState(
                    targetValue = if (isTopShelfFocused) 1f else 0f,
                    animationSpec = tween(HeroExpansionMillis),
                    label = "statusBarCollapse",
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    LauncherRoute(
                        viewModel = launcherViewModel,
                        dockBackdrop = dockBackdrop,
                        onDockBackdropChanged = { dockBackdrop = it },
                        onHeroPositioned = { heroWindowRect = it },
                        settingsFocusRequester = settingsFocusRequester,
                    )
                    StatusBar(
                        onSettingsClick = { screen = Screen.Settings },
                        dockBackdrop = dockBackdrop,
                        heroWindowRect = heroWindowRect,
                        settingsFocusRequester = settingsFocusRequester,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .alpha(statusBarAlpha),
                    )
                }
            }
            Screen.Settings -> {
                // A real stack, not a single enum var — the hierarchy now
                // runs 3 levels deep (Appearance → Theme →
                // Automatic/Light/Dark), which a flat "root or not" var
                // can't represent. `onNavigate` pushes, `onBack`/`BackHandler`
                // pop one entry at a time — same "one level at a time"
                // behavior the original single-level version had (a
                // sub-page always pops toward the root first; only Back
                // from the root itself returns to the launcher), same
                // layered-BackHandler convention `LauncherScreen`'s own root
                // key handling already uses for its own overlay stack.
                // Without a BackHandler at all here, the hardware/remote
                // BACK key falls through to the Activity's default behavior
                // (finish/move-to-back) — confirmed on-device, before the
                // original version of this handler existed, that it exited
                // the entire launcher back to whatever was foregrounded
                // before it.
                val settingsBackStack = remember { mutableStateListOf(SettingsRoute.Root) }
                BackHandler {
                    if (settingsBackStack.size > 1) {
                        settingsBackStack.removeAt(settingsBackStack.lastIndex)
                    } else {
                        screen = Screen.Launcher
                    }
                }
                val tmdbApiKey by settingsViewModel.tmdbApiKey.collectAsStateWithLifecycle()
                SettingsScreen(
                    route = settingsBackStack.last(),
                    themeMode = themeMode,
                    tmdbApiKey = tmdbApiKey,
                    cachedBackdrop = cachedSettingsBackdrop,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onTmdbApiKeySave = settingsViewModel::setTmdbApiKey,
                    onResetSettings = settingsViewModel::resetSettings,
                    onNavigate = { settingsBackStack.add(it) },
                    onBack = { settingsBackStack.removeAt(settingsBackStack.lastIndex) },
                )
            }
        }
        }
        }
    }
    }
}

@Composable
private fun LauncherRoute(
    viewModel: LauncherViewModel,
    dockBackdrop: DockBackdrop?,
    onDockBackdropChanged: (DockBackdrop?) -> Unit,
    onHeroPositioned: (Rect) -> Unit,
    settingsFocusRequester: FocusRequester,
) {
    LauncherScreen(
        viewModel = viewModel,
        dockBackdrop = dockBackdrop,
        onDockBackdropChanged = onDockBackdropChanged,
        onHeroPositioned = onHeroPositioned,
        settingsFocusRequester = settingsFocusRequester,
    )
}
