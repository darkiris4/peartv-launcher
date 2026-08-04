package com.peartv.launcher.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.R
import com.peartv.launcher.domain.repository.ThemeMode
import com.peartv.launcher.ui.focus.FocusGainMillis
import com.peartv.launcher.ui.focus.FocusLossMillis

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
    // User-directed: a tip/description line for the focused row appears
    // below the icon panel — hoisted here (not per-content-file state) since
    // every row on every page needs to report into the same place; see
    // [LocalFocusedSettingsDescription]'s own doc.
    //
    // Keyed on [route] — a brand-new `MutableState` (starting at `null`) as
    // part of the very same composition pass that first processes a new
    // route, so there's no window where a just-navigated-to page could read
    // a stale value left over from the page before it.
    //
    // The *write* side has its own, separate guard against staleness —
    // see the `AnimatedContent` call site below, and
    // [LocalFocusedSettingsDescription]'s own doc for why one isn't enough
    // without the other (confirmed user-reported: this alone didn't stop a
    // "flash" of the previous page's description right after navigating).
    val focusedDescription = remember(route) { mutableStateOf<String?>(null) }

    // True, unconstrained screen width — captured above
    // `SettingsPageScaffold`'s own safe-area padding, so the pill column's
    // fixed width/position (below) is an honest percentage of the real
    // screen, not the already-inset content area.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth

        SettingsPageScaffold(title = route.title, modifier = modifier) {
            // `weight(1f)` (ColumnScope, from SettingsPageScaffold's own
            // Column) — this Row must actually fill the remaining page
            // height so the icon column below has real vertical room to
            // work with, rather than just wrapping its own content height.
            //
            // No `horizontalArrangement` gap here anymore — the pill
            // column's own `start` padding (below) now computes its exact
            // position directly from [screenWidth], replacing the fixed
            // 48dp gap this used to rely on.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                // User-directed bug fix, two reported symptoms with one root
                // cause: (1) entering a sub-page briefly jumped the icon up
                // and immediately back down, (2) focusing a row with a
                // description permanently shifted the icon up while it
                // showed. Both traced to the icon and description sharing
                // one `Column` that was centered *as a group* — adding the
                // description's height (even for one stale transient frame
                // before the route-keyed reset above cleared it) shifted the
                // whole group's own center, which dragged the icon along
                // with it. The icon must never move, full stop — so it's now
                // positioned independently (`Alignment.TopCenter` + a fixed
                // offset), and the description gets its own
                // separately-positioned slot below it. Neither can affect
                // the other's position anymore.
                //
                // Anchored near the top (`SettingsIconTopOffset`) rather
                // than centered in the full row height — user-directed:
                // moved up deliberately to reserve real screen estate below
                // for descriptions (some run to multiple lines) without
                // ever needing to encroach on the icon's own space.
                //
                // Fixed `width` (separate bug fix, still needed): this Box
                // used to just wrap its own content, which meant its width
                // depended on whichever row's description happened to be
                // showing (a wide description line vs. none at all). A
                // fixed width means both the icon and the description center
                // within the exact same span every time, regardless of
                // route or whatever description is currently shown.
                Box(
                    modifier = Modifier.fillMaxHeight().width(SettingsIconColumnWidth),
                ) {
                    SettingsIconPanel(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = SettingsIconTopOffset),
                    )
                    val description = focusedDescription.value
                    // Always composed (reserves its own space, doesn't
                    // affect the icon — both fixed-offset now, see this
                    // Box's own doc above) and alpha-animated rather than a
                    // hard conditional show/hide — matches this app's
                    // established focus-label fade elsewhere (e.g.
                    // `AppTile`'s own `labelAlpha`, same
                    // FocusGainMillis/FocusLossMillis timing), so both a
                    // legitimate description appearing after a route change
                    // and one disappearing when focus leaves read as a
                    // deliberate fade rather than an abrupt pop. Holds the
                    // *last* non-null description while fading out (a hard
                    // `if (description != null)` would unmount the `Text`
                    // the instant it goes null, skipping straight past
                    // whatever alpha animation was in flight) — harmless
                    // once alpha reaches 0, since invisible content showing
                    // stale text behind it is indistinguishable from no text
                    // at all.
                    var lastDescription by remember { mutableStateOf("") }
                    if (description != null) lastDescription = description
                    val descriptionAlpha by animateFloatAsState(
                        targetValue = if (description != null) 1f else 0f,
                        animationSpec = tween(if (description != null) FocusGainMillis else FocusLossMillis),
                        label = "settingsDescriptionAlpha",
                    )
                    Text(
                        text = lastDescription,
                        // User-directed: was bodyLarge, read as too big next
                        // to the icon.
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = SettingsIconTopOffset + SettingsIconPanelSize + SettingsDescriptionSpacing)
                            .widthIn(max = SettingsDescriptionMaxWidth)
                            .alpha(descriptionAlpha),
                    )
                }

                // User-directed: menu items ground to the top (see the
                // reference's own row-list position, well above the icon's
                // vertical center) — this Box doesn't share the icon's
                // centering, so its default top-start content alignment is
                // exactly what's wanted here, no explicit alignment needed.
                //
                // Fixed width + explicit offset, both computed directly
                // from [screenWidth] — user-directed, reversing the earlier
                // intrinsic-per-label hugging approach after direct
                // comparison against a real tvOS Settings screen: pills
                // should be a fixed, generous width regardless of route
                // (`SettingsPillWidthFraction` of the true screen width),
                // with their right edge sitting close to the screen's own
                // right edge (`SettingsPillRightMarginFraction`), not
                // wherever the longest label happened to end.
                //
                // `Modifier.offset`, not `.padding(start = ...)` — confirmed
                // on-device this actually matters, not just style: this
                // Row's own available width is bounded by
                // `SettingsPageScaffold`'s safe-area padding (screenWidth
                // minus 2x[SettingsHorizontalPadding]), which is narrower
                // than icon-column-width + the padding this needed + the
                // full pill width. `padding()` participates in the
                // constraint system, so the pill silently got clamped
                // ~30dp narrower than requested before it ever rendered.
                // `offset()` repositions *after* this Box is independently
                // measured at its real, unclamped [pillColumnWidth], so
                // sibling/parent space never constrains it — the same
                // reason it can legitimately sit closer to the true screen
                // edge than this page's own shared safe-area padding
                // would otherwise allow.
                val pillColumnWidth = screenWidth * SettingsPillWidthFraction
                val pillRightMargin = screenWidth * SettingsPillRightMarginFraction
                val pillLeftEdge = screenWidth - pillRightMargin - pillColumnWidth
                val pillOffset = pillLeftEdge - SettingsHorizontalPadding - SettingsIconColumnWidth
                Box(
                    modifier = Modifier
                        .width(pillColumnWidth)
                        .offset(x = pillOffset),
                ) {
                    // User-directed: transitions between menus should be
                    // fluid, not an abrupt cut — a short crossfade only (no
                    // slide): this codebase's own focus-management history
                    // (`SettingsPageScaffold`'s `settingsInitialFocus` doc)
                    // already hit a real bug from a stale key event racing
                    // Compose's automatic focus fallback across a page
                    // transition, so this keeps the transition window short
                    // and simple rather than risk a similar race with two
                    // pages' rows briefly composed together.
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            fadeIn(tween(SettingsRouteTransitionMillis))
                                .togetherWith(fadeOut(tween(SettingsRouteTransitionMillis)))
                        },
                        label = "settingsRouteContent",
                    ) { targetRoute ->
                        // Guard against the outgoing page's own rows (still
                        // composed here, mid exit-fade, during the crossfade
                        // above) writing into [focusedDescription] — see
                        // [LocalFocusedSettingsDescription]'s own doc for the
                        // full mechanism this closes off. Every row under
                        // this specific `AnimatedContent` branch only ever
                        // sees this one setter, permanently closed over
                        // *this* branch's own `targetRoute` — once `route`
                        // moves on, this branch's writes silently no-op
                        // instead of landing on the (still shared, currently
                        // displayed) state.
                        CompositionLocalProvider(
                            LocalFocusedSettingsDescription provides { description ->
                                if (targetRoute == route) focusedDescription.value = description
                            },
                        ) {
                            when (targetRoute) {
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
        }
    }
}
/**
 * The persistent left panel — the app's muted badge mark. Composed once by
 * [SettingsScreen] regardless of [SettingsRoute], never per-page.
 *
 * Dark theme uses the grey badge (`design/peartv.png`) — the full-color
 * version is reserved for outside the app (TV banner, launcher icon). Light
 * theme uses its own dedicated light badge (`design/peartv-light.png`), not
 * the same grey asset — the grey mark was tuned to sit on a dark panel and
 * reads muddy on a light background.
 */
@Composable
private fun SettingsIconPanel(modifier: Modifier = Modifier) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .size(SettingsIconPanelSize)
            .clip(RoundedCornerShape(SettingsIconPanelCornerRadius)),
    ) {
        Image(
            painter = painterResource(if (isDarkTheme) R.drawable.app_logo_grey else R.drawable.app_logo_light),
            contentDescription = null,
            contentScale = ContentScale.Crop,
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

/**
 * Fixed distance from the top of the icon column's own box to the icon's
 * top edge (user-directed bug fix — see this constant's own call site for
 * the full story: the icon must never move, and is deliberately anchored
 * near the top rather than centered in the full row height, to reserve
 * screen room below it for descriptions without ever touching the icon's
 * own position).
 */
private val SettingsIconTopOffset = 24.dp

/**
 * Pill column width, as a fraction of the true screen width — user-directed,
 * measured directly against a real tvOS Settings screen: a fixed, generous
 * width regardless of route, not sized to whichever page's label happens to
 * be longest (the previous `IntrinsicSize.Max` approach — reversed on
 * direct comparison against native proportions, see this constant's own
 * call site).
 */
private const val SettingsPillWidthFraction = 0.40f

/** Gap from the pill column's own right edge to the true screen's right edge, as a fraction of screen width — see [SettingsPillWidthFraction]'s own doc. */
private const val SettingsPillRightMarginFraction = 0.04f

/** User-directed: was 20dp, then 8dp — nudged closer each round, still read as too far below the icon. */
private val SettingsDescriptionSpacing = 2.dp
private val SettingsDescriptionMaxWidth = 320.dp

/**
 * Fixed width for the icon+description column (user-directed bug fix — see
 * this constant's own call site) — sized to the wider of the two things it
 * holds, [SettingsDescriptionMaxWidth], comfortably covering
 * [SettingsIconPanelSize] (280dp) too so the icon never determines this
 * column's own width.
 */
private val SettingsIconColumnWidth = SettingsDescriptionMaxWidth

/** Route-change crossfade duration — short and simple on purpose, see the transition's own call site doc. */
private const val SettingsRouteTransitionMillis = 200
