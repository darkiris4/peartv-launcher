package com.peartv.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader

/**
 * `SettingsScreen`'s `SettingsRoute.ContentSources` content pane — now a
 * category page over two sub-pages ([MetadataProvidersSettingsContent],
 * [TvdbConfigurationSettingsContent]) plus two direct actions. "Clear
 * Artwork Cache" is functional (Coil's own `ImageLoader`, already configured
 * in `PearTvLauncherApplication`'s `ImageLoaderFactory`, exposes a real,
 * safe, reversible cache-clear — not new architecture). "Refresh Metadata"
 * is a placeholder: this app has no existing "force re-fetch enrichment for
 * every app" operation to hang it on, and inventing one wasn't part of this
 * pass's scope.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun ContentSourcesSettingsContent(
    onOpenMetadataProviders: () -> Unit,
    onOpenTvdbConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstRowFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsCategoryRow(
            text = "Metadata Providers",
            onClick = onOpenMetadataProviders,
            modifier = Modifier.settingsInitialFocus(firstRowFocusRequester),
            description = "Connect external services used to fetch artwork and details for your apps",
        )
        SettingsCategoryRow(
            text = "TVDB Configuration",
            onClick = onOpenTvdbConfiguration,
            description = "Connect a TVDB account for additional show and movie metadata",
        )
        SettingsActionRow(
            text = "Refresh Metadata",
            onClick = {},
            description = "Re-fetch the latest artwork and details for your apps",
        )
        SettingsActionRow(
            text = "Clear Artwork Cache",
            onClick = {
                val loader = context.imageLoader
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            },
            description = "Frees up storage by clearing downloaded artwork; images redownload as needed",
        )
    }
}

/**
 * `SettingsRoute.MetadataProviders` — the real TMDB API key field, moved
 * here unchanged from this file's original flat page (TMDB is a metadata
 * provider; its configuration belongs under this category now that one
 * exists). TVDB has no real integration in this codebase at all — shown as
 * a disabled placeholder row rather than omitted, so the two providers read
 * as a real list rather than TMDB being the only thing here.
 */
@Composable
fun MetadataProvidersSettingsContent(
    tmdbApiKey: String?,
    onTmdbApiKeySave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                // A single-line field has nowhere for cursor-vertical
                // movement to go, so redirect DPAD_UP/DOWN to real focus
                // movement instead of it being silently swallowed.
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

        Text(text = "TVDB", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Not yet available", color = MaterialTheme.colorScheme.tertiary)
    }
}

/** `SettingsRoute.TvdbConfiguration` — placeholder, structurally mirroring the real TMDB key field above but not wired to any repository (no TVDB integration exists in this codebase yet). */
@Composable
fun TvdbConfigurationSettingsContent(modifier: Modifier = Modifier) {
    var keyInput by remember { mutableStateOf("") }
    val keyFieldFocusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "TVDB integration isn't available yet — this page previews the eventual layout.",
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "TVDB API Key", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .padding(16.dp),
        )
    }
}
