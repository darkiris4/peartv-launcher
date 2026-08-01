package com.peartv.launcher.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** Minute-precision display doesn't need second-by-second polling. */
private const val ClockUpdateIntervalMillis = 30_000L

/** Gap between the clock text and the settings gear inside the pill — previously the two sat edge-to-edge, with only the gear's own `OutlinedIconButton` inset providing any breathing room. */
private val StatusBarContentSpacing = 12.dp

/**
 * PRODUCT_SPEC.md §3.1.1's clock pill (top-right corner, always visible) —
 * extracted from `peartv` as a reference detail, now an actual Phase 3 build
 * item (Decisions Log: "Status bar"). Translucent `colorScheme.surface` pill
 * over a blurred crop of whatever's behind it (always somewhere within
 * `HeroBanner`'s own backdrop — see [blurSource]'s doc) — the earlier plain
 * opaque treatment (Decisions Log: "§3.1.1 'liquid glass' tray/pill styling
 * — removed") was reopened at user request (Decisions Log, "Dock/pill
 * backdrop blur"); see `TopShelfRow`'s matching doc for why this is a
 * downscale/upscale blur rather than real `RenderEffect`.
 *
 * Also hosts the settings entry point (§4's narrowly-scoped settings
 * screen) — folded in here rather than left as the bare, unstyled
 * `IconButton` `MainActivity` used before this existed.
 *
 * [settingsFocusRequester] makes the gear a real D-pad destination, not just
 * a click target — user-directed: pressing Up from the dock (through
 * `ContentCarousel`, or directly for apps with no carousel) should reach
 * Settings. Attached to the gear's own `OutlinedIconButton`, whose built-in
 * TV-material click handling already fires [onSettingsClick] on Center/Enter
 * once it's actually focusable — no extra key handling needed here.
 */
@Composable
fun StatusBar(
    onSettingsClick: () -> Unit,
    blurSource: GraphicsLayer,
    blurSourceWindowPosition: () -> Offset,
    settingsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(TimeFormatter)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ClockUpdateIntervalMillis)
            currentTime = LocalTime.now().format(TimeFormatter)
        }
    }

    // This pill lives outside the hero's own composable subtree entirely
    // (a sibling overlay in `MainActivity`, not a child of the `Box`
    // `HeroBanner`/`TopShelfRow` share) — so unlike `TopShelfRow`'s
    // `positionInParent()`, the offset into [blurSource] needs real window
    // coordinates on both sides, same pattern this codebase already uses for
    // the Options popover's own tile-anchored positioning.
    var windowPosition by remember { mutableStateOf(Offset.Zero) }
    val pillBlurLayer = rememberGraphicsLayer()

    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { windowPosition = it.positionInWindow() }
            .blurredBackdrop(
                source = blurSource,
                lowResLayer = pillBlurLayer,
                sourceOffset = { windowPosition - blurSourceWindowPosition() },
            )
            .background(MaterialTheme.colorScheme.surface.copy(alpha = TranslucentPanelAlpha))
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(StatusBarContentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = currentTime,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // OutlinedIconButton, not IconButton — user-directed: a ring/outline
        // selector around the gear, not a solid filled circle.
        OutlinedIconButton(
            onClick = onSettingsClick,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
