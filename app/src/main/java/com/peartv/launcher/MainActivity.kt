package com.peartv.launcher

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peartv.launcher.domain.repository.ThemeMode
import com.peartv.launcher.domain.usecase.GetInstalledAppsUseCase
import com.peartv.launcher.domain.usecase.LaunchAppUseCase
import com.peartv.launcher.domain.usecase.LaunchContentUseCase
import com.peartv.launcher.domain.usecase.RequestUninstallUseCase
import com.peartv.launcher.ui.launcher.LauncherScreen
import com.peartv.launcher.ui.launcher.LauncherViewModel
import com.peartv.launcher.ui.launcher.LauncherViewModelFactory
import com.peartv.launcher.ui.launcher.StatusBar
import com.peartv.launcher.ui.launcher.rememberGraphicsLayer
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
    // Read once at startup (LocalReduceMotion's own doc) — every focus
    // animation in ui/focus/TvFocusable.kt consults this to skip tilt
    // entirely and snap (not spring) scale/elevation when Android's
    // animator-duration-scale accessibility setting is off.
    val context = LocalContext.current
    val reduceMotion = remember { context.isReduceMotionEnabled() }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
    PearTvLauncherTheme(darkTheme = isDarkTheme) {
        when (screen) {
            Screen.Launcher -> {
                // Shared between `LauncherScreen`'s hero and `StatusBar`
                // (a sibling here, not a descendant of the hero) so the
                // pill can blur a crop of the same recorded backdrop the
                // dock does — see `BackdropBlur.kt`. Hoisted to this shared
                // ancestor rather than owned by either child.
                val heroBackdropLayer = rememberGraphicsLayer()
                var heroWindowPosition by remember { mutableStateOf(Offset.Zero) }
                // User-directed: Up from the dock (via `ContentCarousel`, or
                // directly for apps with no carousel — see `LauncherScreen`)
                // should reach the settings gear. Hoisted here for the same
                // reason `heroBackdropLayer` is — `StatusBar` and the launcher
                // content are siblings, not parent/child.
                val settingsFocusRequester = remember { FocusRequester() }
                Box(modifier = Modifier.fillMaxSize()) {
                    LauncherRoute(
                        viewModel = launcherViewModel,
                        heroBackdropLayer = heroBackdropLayer,
                        onHeroPositioned = { heroWindowPosition = it },
                        settingsFocusRequester = settingsFocusRequester,
                    )
                    StatusBar(
                        onSettingsClick = { screen = Screen.Settings },
                        blurSource = heroBackdropLayer,
                        blurSourceWindowPosition = { heroWindowPosition },
                        settingsFocusRequester = settingsFocusRequester,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp),
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

@Composable
private fun LauncherRoute(
    viewModel: LauncherViewModel,
    heroBackdropLayer: GraphicsLayer,
    onHeroPositioned: (Offset) -> Unit,
    settingsFocusRequester: FocusRequester,
) {
    LauncherScreen(
        viewModel = viewModel,
        heroBackdropLayer = heroBackdropLayer,
        onHeroPositioned = onHeroPositioned,
        settingsFocusRequester = settingsFocusRequester,
    )
}
