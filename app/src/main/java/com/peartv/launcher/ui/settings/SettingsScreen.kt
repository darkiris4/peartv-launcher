package com.peartv.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.peartv.launcher.R
import com.peartv.launcher.domain.repository.ThemeMode

/**
 * PRODUCT_SPEC.md §4's settings surface — expanded (an explicit new ask, not
 * scope creep riding in on the original narrow one; see Decisions Log
 * "Settings screen reworked as a category list with drill-down sub-pages")
 * into a real tvOS-style hierarchy: 5 root categories, most with their own
 * sub-pages, one 3 levels deep (Appearance → Theme → Automatic/Light/Dark).
 *
 * Still one screen, not N independent ones — the icon panel is composed
 * exactly once per settings session and never re-renders as [route] changes
 * (same reasoning as the original single-level rework: Compose has no notion
 * that two separately-composed `Image`s "are" the same persistent element).
 * Only the content pane (this file's own `when` branch) and the title swap.
 * Single pane at every depth — no two-pane/sidebar-persists layout (that was
 * tried and reverted earlier: two full-width panes didn't fit this app's
 * actual screen width on-device).
 *
 * [SettingsRoute]'s flat "root or not" back-handling doesn't extend to real
 * depth, so navigation is a caller-owned back *stack* now (`MainActivity`'s
 * `settingsBackStack`) rather than a single enum var — [onNavigate] pushes,
 * [onBack] pops. Everything else about this screen's own shape — the
 * persistent icon panel, single content pane, pill-row visual language — is
 * unchanged from the original rework.
 */
enum class SettingsRoute(val title: String) {
    Root("Settings"),
    Appearance("Appearance"),
    Theme("Theme"),
    HomeScreen("Home Screen"),
    TopShelfStyle("Top Shelf Style"),
    ContentSources("Content Sources"),
    MetadataProviders("Metadata Providers"),
    TvdbConfiguration("TVDB Configuration"),
    Screensaver("Screensaver"),
    System("System"),
    About("About PearTV"),
    Licenses("Open Source Licenses"),
}

@Composable
fun SettingsScreen(
    route: SettingsRoute,
    themeMode: ThemeMode,
    tmdbApiKey: String?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onTmdbApiKeySave: (String) -> Unit,
    onResetSettings: () -> Unit,
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(title = route.title, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SettingsIconListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconPanel()

            Box(modifier = Modifier.width(SettingsCategoryListWidth)) {
                when (route) {
                    SettingsRoute.Root -> SettingsRootContent(onNavigate = onNavigate)

                    SettingsRoute.Appearance -> AppearanceSettingsContent(
                        themeMode = themeMode,
                        onOpenTheme = { onNavigate(SettingsRoute.Theme) },
                    )
                    SettingsRoute.Theme -> ThemeSettingsContent(
                        themeMode = themeMode,
                        onThemeModeChange = {
                            onThemeModeChange(it)
                            onBack()
                        },
                    )

                    SettingsRoute.HomeScreen -> HomeScreenSettingsContent(
                        onOpenTopShelfStyle = { onNavigate(SettingsRoute.TopShelfStyle) },
                    )
                    SettingsRoute.TopShelfStyle -> TopShelfStyleSettingsContent()

                    SettingsRoute.ContentSources -> ContentSourcesSettingsContent(
                        onOpenMetadataProviders = { onNavigate(SettingsRoute.MetadataProviders) },
                        onOpenTvdbConfiguration = { onNavigate(SettingsRoute.TvdbConfiguration) },
                    )
                    SettingsRoute.MetadataProviders -> MetadataProvidersSettingsContent(
                        tmdbApiKey = tmdbApiKey,
                        onTmdbApiKeySave = onTmdbApiKeySave,
                    )
                    SettingsRoute.TvdbConfiguration -> TvdbConfigurationSettingsContent()

                    SettingsRoute.Screensaver -> ScreensaverSettingsContent()

                    SettingsRoute.System -> SystemSettingsContent(
                        onOpenAbout = { onNavigate(SettingsRoute.About) },
                        onOpenLicenses = { onNavigate(SettingsRoute.Licenses) },
                        onResetSettings = onResetSettings,
                    )
                    SettingsRoute.About -> AboutSettingsContent()
                    SettingsRoute.Licenses -> LicensesSettingsContent()
                }
            }
        }
    }
}

/** The persistent left panel — this app's real icon art, grayscale to match the reference. Composed once by [SettingsScreen] regardless of [SettingsRoute], never per-page. */
@Composable
private fun SettingsIconPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(SettingsIconPanelSize)
            .clip(RoundedCornerShape(SettingsIconPanelCornerRadius)),
    ) {
        // `R.mipmap.ic_launcher` itself is an `<adaptive-icon>` XML
        // (foreground + background composited by the OS's own launcher icon
        // renderer) — `painterResource` doesn't support that format at all
        // ("Only VectorDrawables and rasterized asset types are supported"),
        // confirmed on-device as a startup crash the moment this screen
        // composed. Layering the same two real layers manually — the
        // background is a plain VectorDrawable, which `painterResource`
        // *does* support — reproduces the identical icon art without needing
        // the OS's own adaptive-icon machinery.
        //
        // Grayscale (user-directed, against the reference's own muted gray
        // Apple TV mark) — a saturation-0 ColorMatrix rather than a
        // manually-drawn gray asset, so this stays the app's real icon art,
        // just desaturated, not a separate asset to keep in sync with it.
        val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            colorFilter = grayscale,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = grayscale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** [SettingsRoute.Root] — the 5 top-level categories. */
@Composable
private fun SettingsRootContent(
    onNavigate: (SettingsRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsCategoryRow(
            text = "Appearance",
            onClick = { onNavigate(SettingsRoute.Appearance) },
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
        )
        SettingsCategoryRow(text = "Home Screen", onClick = { onNavigate(SettingsRoute.HomeScreen) })
        SettingsCategoryRow(text = "Content Sources", onClick = { onNavigate(SettingsRoute.ContentSources) })
        SettingsCategoryRow(text = "Screensaver", onClick = { onNavigate(SettingsRoute.Screensaver) })
        SettingsCategoryRow(text = "System", onClick = { onNavigate(SettingsRoute.System) })
    }
}

private val SettingsIconPanelSize = 280.dp
private val SettingsIconPanelCornerRadius = 32.dp
private val SettingsIconListSpacing = 48.dp

/** Shared with every page's own content composable so they all line up under the same width the root category list uses, not an independently-guessed value per page. */
val SettingsCategoryListWidth = 480.dp
