package com.peartv.launcher.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.R
import com.peartv.launcher.ui.focus.tvOSFocusable

/**
 * PRODUCT_SPEC.md §4's settings surface — reworked against the real tvOS
 * Settings reference (`design/settings-menu.png`, user-directed): a root
 * category list (this screen) rather than the original single flat form —
 * a big icon panel on the left, a vertical list of rounded "pill" category
 * rows on the right, each drilling into its own sub-screen
 * (`AppearanceSettingsScreen`/`ContentSourcesSettingsScreen`/
 * `ScreensaverSettingsScreen`) with a real back stack (`MainActivity`'s
 * `SettingsRoute`).
 *
 * Only three categories, not the reference's full Apple TV list — this
 * app's actual settings surface is genuinely narrow (a theme toggle, a TMDB
 * key, two deep-link CTAs), so categories exist here to group *that*
 * content sensibly, not to reproduce Apple TV's own unrelated categories
 * (Profiles and Accounts, AirPlay, Remotes and Devices, etc. have no
 * equivalent in this app at all).
 *
 * The left icon panel shows this app's own adaptive launcher icon
 * (`R.mipmap.ic_launcher`) — the reference's own left panel is Apple TV's
 * app icon in the identical role, a brand-identity anchor beside the list,
 * not a per-row contextual image.
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenContentSources: () -> Unit,
    onOpenScreensaver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocusRequester = remember { FocusRequester() }

    SettingsPageScaffold(title = "Settings", modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SettingsIconListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SettingsIconPanelSize)
                    .clip(RoundedCornerShape(SettingsIconPanelCornerRadius)),
            ) {
                // `R.mipmap.ic_launcher` itself is an `<adaptive-icon>` XML
                // (foreground + background composited by the OS's own
                // launcher icon renderer) — `painterResource` doesn't support
                // that format at all ("Only VectorDrawables and rasterized
                // asset types are supported"), confirmed on-device as a
                // startup crash the moment this screen composed. Layering the
                // same two real layers manually — the background is a plain
                // VectorDrawable, which `painterResource` *does* support —
                // reproduces the identical icon art without needing the OS's
                // own adaptive-icon machinery.
                //
                // Grayscale (user-directed, against the reference's own muted
                // gray Apple TV mark) — a saturation-0 ColorMatrix rather than
                // a manually-drawn gray asset, so this stays the app's real
                // icon art, just desaturated, not a separate asset to keep in
                // sync with it.
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

            Column(
                verticalArrangement = Arrangement.spacedBy(SettingsCategoryRowSpacing),
                modifier = Modifier.width(SettingsCategoryListWidth),
            ) {
                SettingsCategoryRow(
                    text = "Appearance",
                    onClick = onOpenAppearance,
                    modifier = Modifier.focusRequester(firstRowFocusRequester),
                )
                SettingsCategoryRow(text = "Content Sources", onClick = onOpenContentSources)
                SettingsCategoryRow(text = "Screensaver", onClick = onOpenScreensaver)
            }
        }
    }

    LaunchedEffect(Unit) {
        firstRowFocusRequester.requestFocus()
    }
}

/**
 * One row of the category list — same focus-swap-background language as
 * `OptionsMenu.kt`'s `OptionRow` (solid `onSurface` fill + inverted text on
 * focus, translucent `surface` fill otherwise), not the reference's literal
 * white-on-black: that pairing only works because Apple TV Settings is
 * always dark, where this app supports both themes, so it needs the same
 * opposite-luminance-in-either-scheme pairing every other focusable list
 * row in this app already relies on. Pill-shaped (a large corner radius
 * relative to its own height) and full-width, unlike `OptionRow`'s smaller
 * popover-scoped row, to match the reference's own proportions.
 */
@Composable
private fun SettingsCategoryRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusedBackground = MaterialTheme.colorScheme.onSurface
    val unfocusedBackground = MaterialTheme.colorScheme.surface
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) focusedBackground else unfocusedBackground,
        label = "settingsCategoryRowBackground",
    )
    val contentColor = if (isFocused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsCategoryRowCornerRadius))
            .background(backgroundColor)
            .tvOSFocusable(
                focusedScale = 1f,
                cornerRadius = SettingsCategoryRowCornerRadius,
                glowColor = MaterialTheme.colorScheme.onBackground,
                onFocusChange = { isFocused = it },
                onClick = onClick,
            )
            .padding(horizontal = SettingsCategoryRowHorizontalPadding, vertical = SettingsCategoryRowVerticalPadding),
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(text = "›", color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
}

private val SettingsIconPanelSize = 280.dp
private val SettingsIconPanelCornerRadius = 32.dp
private val SettingsIconListSpacing = 48.dp

/** Shared with every sub-page (`AppearanceSettingsScreen`/`ContentSourcesSettingsScreen`/`ScreensaverSettingsScreen`) so their own content column lines up under the same width the root category list uses, not an independently-guessed value per page. */
val SettingsCategoryListWidth = 480.dp
private val SettingsCategoryRowSpacing = 6.dp
private val SettingsCategoryRowCornerRadius = 28.dp
private val SettingsCategoryRowHorizontalPadding = 28.dp
private val SettingsCategoryRowVerticalPadding = 18.dp
