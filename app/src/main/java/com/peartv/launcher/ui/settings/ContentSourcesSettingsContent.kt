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
import com.peartv.launcher.domain.repository.ArtworkSource

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
    artworkSource: ArtworkSource,
    onOpenMetadataProviders: () -> Unit,
    onOpenTvdbConfiguration: () -> Unit,
    onOpenArtworkSource: () -> Unit,
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
        SettingsCategoryRow(
            text = "Artwork Source",
            value = artworkSource.name,
            onClick = onOpenArtworkSource,
            description = "Choose whether to use each app's own artwork, always fetch a cleaner online match, or let PearTV decide automatically",
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
 * exists). TVDB's own key field lives on its own sub-page
 * ([TvdbConfigurationSettingsContent], now real, no longer a placeholder).
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
    }
}

/** `SettingsRoute.TvdbConfiguration` — real key field, mirroring [MetadataProvidersSettingsContent]'s own TMDB field exactly (same `remember(tvdbApiKey)`-resync-on-external-change pattern, same up/down-redirect on the single-line field, same save-button idiom). */
@Composable
fun TvdbConfigurationSettingsContent(
    tvdbApiKey: String?,
    onTvdbApiKeySave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyFieldFocusRequester = remember { FocusRequester() }
    var keyInput by remember(tvdbApiKey) { mutableStateOf(tvdbApiKey.orEmpty()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "TVDB API Key", color = MaterialTheme.colorScheme.onBackground)
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
        Button(onClick = { onTvdbApiKeySave(keyInput) }) {
            Text("Save TVDB key")
        }
    }
}

/**
 * `SettingsRoute.ArtworkSource` — mirrors [ThemeSettingsContent]'s own
 * 3-way-picker structure exactly (`AppearanceSettingsContent.kt`), same
 * select-and-return behavior, same current-value-gets-initial-focus rule.
 * `Online`/`Automatic` are `enabled = hasAnyProviderKey` — [ArtworkSource]'s
 * own doc explains why: both require at least one of TMDB/TVDB configured,
 * and picking either without one wouldn't do anything.
 */
@Composable
fun ArtworkSourceSettingsContent(
    artworkSource: ArtworkSource,
    onArtworkSourceChange: (ArtworkSource) -> Unit,
    hasAnyProviderKey: Boolean,
    modifier: Modifier = Modifier,
) {
    val nativeFocusRequester = remember { FocusRequester() }
    val onlineFocusRequester = remember { FocusRequester() }
    val automaticFocusRequester = remember { FocusRequester() }

    Column(
        verticalArrangement = Arrangement.spacedBy(SettingsRowSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        SettingsSelectionRow(
            text = "Native",
            selected = artworkSource == ArtworkSource.Native,
            onClick = { onArtworkSourceChange(ArtworkSource.Native) },
            description = "Always use each app's own artwork, never fetch a replacement",
            modifier = if (artworkSource == ArtworkSource.Native) {
                Modifier.settingsInitialFocus(nativeFocusRequester)
            } else {
                Modifier
            },
        )
        SettingsSelectionRow(
            text = "Online",
            selected = artworkSource == ArtworkSource.Online,
            onClick = { onArtworkSourceChange(ArtworkSource.Online) },
            description = "Always replace artwork with the best online match; requires a TMDB or TVDB key below",
            enabled = hasAnyProviderKey,
            modifier = if (artworkSource == ArtworkSource.Online) {
                Modifier.settingsInitialFocus(onlineFocusRequester)
            } else {
                Modifier
            },
        )
        SettingsSelectionRow(
            text = "Automatic",
            selected = artworkSource == ArtworkSource.Automatic,
            onClick = { onArtworkSourceChange(ArtworkSource.Automatic) },
            description = "Replace only low-quality artwork with an online match; requires a TMDB or TVDB key below",
            enabled = hasAnyProviderKey,
            modifier = if (artworkSource == ArtworkSource.Automatic) {
                Modifier.settingsInitialFocus(automaticFocusRequester)
            } else {
                Modifier
            },
        )
    }
}
