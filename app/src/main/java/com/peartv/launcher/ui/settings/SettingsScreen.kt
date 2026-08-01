package com.peartv.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.peartv.launcher.ui.launcher.openAppInfoSettings

/**
 * PRODUCT_SPEC.md §4's narrowly-scoped settings surface — exactly three
 * things (theme toggle, TMDB API key, Home Screen Channels permission CTA),
 * nothing more. Not styled to the same tvOS-grade motion standard as the
 * launcher grid (§1) — this is a utility form, not part of the focus/motion
 * system that spec is about.
 *
 * No `TextField` in `androidx.tv.material3` (checked directly against the
 * 1.0.0 artifact, not assumed) — `BasicTextField` from Compose Foundation
 * is used for the API key input instead, manually styled.
 *
 * The Channels permission CTA deep-links to this app's App Info settings
 * page rather than a specific "grant TV listings access" action — no
 * confirmed direct deep link for that special permission, and App Info is
 * guaranteed to exist and let the user find it manually. §2.4/task #20 owns
 * actually checking/using the grant; this screen only offers the entry point.
 * [openAppInfoSettings] (`ui/launcher/ChannelsPermissionPrompt.kt`) is
 * shared with that file's own identical CTA on the first-launch prompt
 * (Decisions Log) rather than duplicated here.
 */
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    tmdbApiKey: String?,
    onDarkThemeChange: (Boolean) -> Unit,
    onTmdbApiKeySave: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val themeFocusRequester = remember { FocusRequester() }
    var keyInput by remember(tmdbApiKey) { mutableStateOf(tmdbApiKey.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Dark theme", color = MaterialTheme.colorScheme.onBackground)
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onDarkThemeChange,
                modifier = Modifier.focusRequester(themeFocusRequester),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

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
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(16.dp)
                // Confirmed on-device: a focused single-line BasicTextField
                // traps DPAD_UP/DOWN for cursor movement by default — since
                // there's only one line, that movement is meaningless here,
                // but it left focus with nowhere to go, stuck in the field
                // with no way out via remote. Intercept both directions and
                // move focus explicitly instead; DPAD_LEFT/RIGHT still work
                // normally for actual cursor movement within the text.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
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

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack) {
            Text("Back")
        }
    }

    LaunchedEffect(Unit) {
        themeFocusRequester.requestFocus()
    }
}
