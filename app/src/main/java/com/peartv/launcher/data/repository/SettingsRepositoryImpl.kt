package com.peartv.launcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val CHANNELS_PROMPT_DISMISSED = booleanPreferencesKey("channels_prompt_dismissed")
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

    override val tmdbApiKey: Flow<String?> = context.settingsDataStore.data
        .map { it[Keys.TMDB_API_KEY] }

    override val hasDismissedChannelsPrompt: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.CHANNELS_PROMPT_DISMISSED] ?: false }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setTmdbApiKey(key: String?) {
        context.settingsDataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(Keys.TMDB_API_KEY) else prefs[Keys.TMDB_API_KEY] = key
        }
    }

    override suspend fun setChannelsPromptDismissed() {
        context.settingsDataStore.edit { it[Keys.CHANNELS_PROMPT_DISMISSED] = true }
    }

    override suspend fun resetAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
