package com.peartv.launcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import com.peartv.launcher.BuildConfig
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.peartv.launcher.domain.repository.ArtworkSource
import com.peartv.launcher.domain.repository.SettingsRepository
import com.peartv.launcher.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private object Keys {
    /** Superseded by [THEME_MODE] — kept only as a one-time fallback for installs that persisted this before the tri-state hierarchy existed; never written to anymore. */
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    val TVDB_API_KEY = stringPreferencesKey("tvdb_api_key")
    val ARTWORK_SOURCE = stringPreferencesKey("artwork_source")
    val CHANNELS_PROMPT_DISMISSED = booleanPreferencesKey("channels_prompt_dismissed")
}

/**
 * [SettingsRepositoryImpl.setTmdbApiKey]/[SettingsRepositoryImpl.setTvdbApiKey]'s
 * shared auto-revert — see [ArtworkSource]'s own doc for why a stored
 * `Online`/`Automatic` value with no configured key left behind would be a
 * confusing, inert state to leave around. Takes the *about-to-be-written*
 * prefs (called from inside the same `edit { }` transaction, after this
 * key's own write/removal has already happened) so both provider keys are
 * checked in their final state, not the state before this particular write.
 *
 * Checks the *effective* key (stored value, or [BuildConfig]'s baked-in
 * default otherwise — same fallback [tmdbApiKey]/[tvdbApiKey] themselves
 * use), not just the raw stored preference: with a baked-in default
 * present, clearing the user's own key should never trigger this, since
 * `Online`/`Automatic` still have something real to use.
 */
private fun MutablePreferences.revertArtworkSourceIfNoProviderKeyConfigured() {
    val tmdbBlank = (this[Keys.TMDB_API_KEY] ?: BuildConfig.TMDB_API_KEY_DEFAULT).isBlank()
    val tvdbBlank = (this[Keys.TVDB_API_KEY] ?: BuildConfig.TVDB_API_KEY_DEFAULT).isBlank()
    if (tmdbBlank && tvdbBlank) this[Keys.ARTWORK_SOURCE] = ArtworkSource.Native.name
}

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data
        .map { prefs ->
            val stored = prefs[Keys.THEME_MODE]
            if (stored != null) {
                runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.Dark)
            } else {
                // One-time fallback for installs from before THEME_MODE
                // existed — preserves an existing Light choice instead of
                // silently reverting every upgrading install to the new
                // default. Never written back here; the next explicit
                // setThemeMode() call is what actually migrates the key.
                if (prefs[Keys.DARK_THEME] == false) ThemeMode.Light else ThemeMode.Dark
            }
        }

    // Falls back to a baked-in default (BuildConfig, sourced from
    // gitignored local.properties — see app/build.gradle.kts) only when the
    // user hasn't entered their own key; an explicit Settings entry always
    // wins since it's checked first.
    override val tmdbApiKey: Flow<String?> = context.settingsDataStore.data
        .map { it[Keys.TMDB_API_KEY] ?: BuildConfig.TMDB_API_KEY_DEFAULT.ifBlank { null } }

    override val tvdbApiKey: Flow<String?> = context.settingsDataStore.data
        .map { it[Keys.TVDB_API_KEY] ?: BuildConfig.TVDB_API_KEY_DEFAULT.ifBlank { null } }

    override val artworkSource: Flow<ArtworkSource> = context.settingsDataStore.data
        .map { prefs ->
            val stored = prefs[Keys.ARTWORK_SOURCE]
            if (stored != null) {
                runCatching { ArtworkSource.valueOf(stored) }.getOrDefault(ArtworkSource.Native)
            } else {
                ArtworkSource.Native
            }
        }

    override val hasDismissedChannelsPrompt: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.CHANNELS_PROMPT_DISMISSED] ?: false }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setTmdbApiKey(key: String?) {
        context.settingsDataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(Keys.TMDB_API_KEY) else prefs[Keys.TMDB_API_KEY] = key
            prefs.revertArtworkSourceIfNoProviderKeyConfigured()
        }
    }

    override suspend fun setTvdbApiKey(key: String?) {
        context.settingsDataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(Keys.TVDB_API_KEY) else prefs[Keys.TVDB_API_KEY] = key
            prefs.revertArtworkSourceIfNoProviderKeyConfigured()
        }
    }

    override suspend fun setArtworkSource(source: ArtworkSource) {
        context.settingsDataStore.edit { it[Keys.ARTWORK_SOURCE] = source.name }
    }

    override suspend fun setChannelsPromptDismissed() {
        context.settingsDataStore.edit { it[Keys.CHANNELS_PROMPT_DISMISSED] = true }
    }

    override suspend fun resetAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
