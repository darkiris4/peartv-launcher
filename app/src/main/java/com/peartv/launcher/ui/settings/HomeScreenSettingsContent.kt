package com.peartv.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import com.peartv.launcher.ui.launcher.openAppInfoSettings

/**
 * `SettingsScreen`'s `SettingsRoute.HomeScreen` content pane — new category
 * grouping the home screen's own display tuning. "Grant TV Listings Access"
 * is the one functional row here: it's the same real Channels-permission CTA
 * `ContentSourcesSettingsContent` used to host (moved, not duplicated — that
 * file's own original doc already framed it as "Home Screen Channels
 * content," so this is where it actually belongs). Top Shelf Style /
 * Recommendations / Recently Used Content are visually complete placeholders
 * — none of the three exist as a real mechanism in this app yet (a single
 * fixed top-shelf style, no recommendation engine, no "recently used"
 * concept), so their state is local-only and resets on re-entry (including
 * [TopShelfStyleSettingsContent]'s own picker, one level deeper — a
 * placeholder picker losing its pick when you navigate away and back is an
 * accepted trade for not building real backing storage for it yet).
 */
@Composable
fun HomeScreenSettingsContent(
    onOpenTopShelfStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstRowFocusRequester = remember { FocusRequester() }
    var recommendations by remember { mutableStateOf(true) }
    var recentlyUsedContent by remember { mutableStateOf(true) }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsCategoryRow(
            text = "Top Shelf Style",
            value = "Featured",
            onClick = onOpenTopShelfStyle,
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
            description = "Choose how the row of pinned apps at the top of your Home Screen looks",
        )
        SettingsActionRow(
            text = "Grant TV Listings Access",
            onClick = { openAppInfoSettings(context) },
            description = "Allow apps to show live TV schedules and channel guides on your Home Screen",
        )
        SettingsToggleRow(
            text = "Recommendations",
            checked = recommendations,
            onCheckedChange = { recommendations = it },
            description = "Show suggested content based on what you watch",
        )
        SettingsToggleRow(
            text = "Recently Used Content",
            checked = recentlyUsedContent,
            onCheckedChange = { recentlyUsedContent = it },
            description = "Show apps and content you've opened recently at the top of your Home Screen",
        )
    }
}

/** `SettingsRoute.TopShelfStyle` — placeholder picker, local-only selection (see this file's own doc). */
@Composable
fun TopShelfStyleSettingsContent(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf("Featured") }
    val firstRowFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsSelectionRow(
            text = "Featured",
            selected = selected == "Featured",
            onClick = { selected = "Featured" },
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
        )
        SettingsSelectionRow(
            text = "Compact",
            selected = selected == "Compact",
            onClick = { selected = "Compact" },
        )
    }
}
