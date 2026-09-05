package com.peartv.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.ui.launcher.SettingsBlurRadius
import com.peartv.launcher.ui.theme.ambientBackground
import com.peartv.launcher.ui.theme.ambientPanelTint
import com.peartv.launcher.ui.theme.settingsBackground
import kotlinx.coroutines.delay

/**
 * Shared shell for every settings page (the root category list and each
 * sub-page) — user-directed against the real tvOS Settings reference: a
 * centered title over a full-bleed background, everything else specific to
 * that one page. No on-screen "Back" affordance on any page (the reference
 * has none either, relying on the remote's Back button).
 *
 * [cachedBackdrop] is a still snapshot of the launcher content, captured by
 * `MainActivity` while the launcher was on screen (`BackdropBlur.kt`) —
 * Settings has no live hero to blur once it's showing. It's blurred here at
 * [SettingsBlurRadius] (heavier than the dock's own motion-tuned radius,
 * since a still image has no legibility floor from moving art) behind a
 * fixed frosted [settingsPanelTint]. `null` on a true cold start straight
 * into Settings — falls back to the flat `settingsBackground()` fill.
 */
@Composable
fun SettingsPageScaffold(
    title: String,
    cachedBackdrop: ImageBitmap?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .ambientBackground(baseColor = MaterialTheme.settingsBackground()),
    ) {
        if (cachedBackdrop != null) {
            Image(
                bitmap = cachedBackdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(SettingsBlurRadius),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(settingsPanelTint()),
            )
        }
        Column(
            // `Start`, not `CenterHorizontally` — the latter re-centered the
            // icon+content row as a unit every time a route's content changed
            // width. The title keeps its own explicit centering below.
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SettingsHorizontalPadding, vertical = SettingsVerticalPadding),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(SettingsTitleSpacing))
            content()
        }
    }
}

/**
 * Settings' own fixed, theme-aware frosted tint over [cachedBackdrop] —
 * deliberately not the dock's `glassTint` (tuned to read as light *glass*
 * over fast-rotating art). Settings' backdrop is a single still image behind
 * a full page of text, so a heavier fixed alpha per theme reads as a proper
 * frosted *panel*. Light theme sits higher: light-theme foreground content
 * loses legibility faster against a barely-tinted bright backdrop.
 */
@Composable
private fun settingsPanelTint(): Color {
    val baseTint = MaterialTheme.ambientPanelTint()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDarkTheme) SettingsPanelTintAlphaDark else SettingsPanelTintAlphaLight
    return baseTint.copy(alpha = alpha)
}

private const val SettingsPanelTintAlphaDark = 0.62f
private const val SettingsPanelTintAlphaLight = 0.72f

val SettingsHorizontalPadding = 64.dp
val SettingsVerticalPadding = 48.dp
val SettingsTitleSpacing = 40.dp

/**
 * Applied to whatever a settings pane wants as its initial focus target in
 * place of a plain `focusRequester(requester)` + a bare
 * `LaunchedEffect { requester.requestFocus() }`. Keeps the target genuinely
 * unfocusable (`focusProperties { canFocus = false }`) for
 * [SettingsInitialFocusGraceMillis]: navigating into a route disposes the
 * previously-focused row, and Compose's own focus system immediately
 * redirects focus to the next available focusable — the sub-page's control —
 * before any app code runs. The original press's key-up, still in flight at
 * the input-dispatch level, then lands on it and fires it. Compose's
 * focus-search fallback skips an unfocusable node entirely, so it has
 * nothing to land the stale key-up on until this modifier flips `canFocus`
 * back on and claims focus explicitly.
 *
 * [isInitialFocusEnabled], not `canFocus`, names the local state —
 * `focusProperties { canFocus = ... }`'s lambda receiver itself declares a
 * `canFocus` property, and `canFocus = canFocus` inside it resolves both
 * sides to the receiver's own property (a silent no-op).
 */
@Composable
fun Modifier.settingsInitialFocus(requester: FocusRequester): Modifier {
    var isInitialFocusEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SettingsInitialFocusGraceMillis)
        isInitialFocusEnabled = true
        requester.requestFocus()
    }

    return this
        .focusRequester(requester)
        .focusProperties { canFocus = isInitialFocusEnabled }
}

internal const val SettingsInitialFocusGraceMillis = 350L
