package com.peartv.launcher.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.OutlinedIconButtonDefaults
import androidx.tv.material3.Text
import com.peartv.launcher.ui.focus.FocusGainMillis
import com.peartv.launcher.ui.focus.FocusLossMillis
import com.peartv.launcher.ui.motion.kenBurnsTransform
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
 * item (Decisions Log: "Status bar"). Same Liquid-Glass-style treatment as
 * `TopShelfRow`'s own dock now — a crop of [dockBackdrop]'s blurred artwork
 * (when there is one), a content-aware [liquidGlassTint], and
 * [LiquidGlassTopHighlight] — user-directed extension of that same panel
 * language to this pill rather than leaving it as the odd one out. Falls
 * back to plain static translucency when [dockBackdrop] is `null` (Tier 1/2,
 * or Tier 3 between poster loads) — see [DockBackdrop]'s own doc
 * (`BlurredArtwork.kt`).
 *
 * [heroWindowRect] (`LauncherScreen`'s own real window rect for the hero/
 * carousel the sharp poster is `ContentScale.Crop`'d across — reported up
 * from there via `onHeroPositioned` since this pill is a sibling of that
 * whole screen, not a descendant) plus this pill's own real window rect
 * (captured here via `onGloballyPositioned`) feed
 * [positionAwareBackdropCrop] (`GlassPanel.kt`) — without it this pill
 * showed an arbitrary, unpositioned centered slice of the *whole* poster
 * (confirmed user-reported, most obvious here given how small/off-center
 * this pill is) rather than the region actually behind it.
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
    dockBackdrop: DockBackdrop?,
    heroWindowRect: Rect,
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

    var panelRect by remember { mutableStateOf(Rect.Zero) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { panelRect = Rect(it.positionInWindow(), it.size.toSize()) },
    ) {
        val artwork = dockBackdrop?.artwork?.value
        if (artwork != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .positionAwareBackdropCrop(
                        bitmap = artwork.bitmap,
                        heroRect = { heroWindowRect },
                        panelRect = { panelRect },
                    )
                    .kenBurnsTransform(dockBackdrop.kenBurnsProgress.value),
            )
        }
        Row(
            modifier = Modifier
                .background(liquidGlassTint(artwork))
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(StatusBarContentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            var isGearFocused by remember { mutableStateOf(false) }
            val gearRotation by animateFloatAsState(
                targetValue = if (isGearFocused) 180f else 0f,
                animationSpec = tween(if (isGearFocused) FocusGainMillis else FocusLossMillis),
                label = "settingsGearRotation",
            )
            // OutlinedIconButton, not IconButton — user-directed: a ring/outline
            // selector around the gear, not a solid filled circle. The button's
            // own default focused state fills the container solid (same color as
            // its own focused border, so the ring became invisible) — forced
            // transparent here so only the border ring shows.
            OutlinedIconButton(
                onClick = onSettingsClick,
                colors = OutlinedIconButtonDefaults.colors(focusedContainerColor = Color.Transparent),
                modifier = Modifier
                    .focusRequester(settingsFocusRequester)
                    .onFocusChanged { isGearFocused = it.isFocused },
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { rotationZ = gearRotation },
                )
            }
        }
        LiquidGlassTopHighlight()
    }
}
