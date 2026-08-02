package com.peartv.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.ui.launcher.openAppInfoSettings

/**
 * `SettingsScreen`'s `SettingsRoute.ContentSources` content pane — the two
 * settings that feed the hero's own content tiers (§2.4/§3.1.1): the TMDB
 * API key (Tier 1 backdrop art) and the Home Screen Channels permission CTA
 * (Tier 3). Grouped together as "where the app's richer content comes
 * from," same reasoning both had for living on one flat page before this
 * rework.
 */
@Composable
fun ContentSourcesSettingsContent(
    tmdbApiKey: String?,
    onTmdbApiKeySave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyFieldFocusRequester = remember { FocusRequester() }
    var keyInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey.orEmpty()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "TMDB API Key", color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            ),
            modifier = Modifier
                .settingsInitialFocus(keyFieldFocusRequester)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(16.dp)
                // Same DPAD_UP/DOWN trap fix the original flat page already
                // had — a single-line field has nowhere for cursor-vertical
                // movement to go, so redirect to real focus movement.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                        Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                        else -> false
                    }
                },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { onTmdbApiKeySave(keyInput) }) {
            Text("Save TMDB key")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Home Screen Channels content (§2.4) requires TV listings access, granted from this app's system settings page.",
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { openAppInfoSettings(context) }) {
            Text("Open app settings")
        }
    }
}
