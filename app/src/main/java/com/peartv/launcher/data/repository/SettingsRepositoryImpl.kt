package com.peartv.launcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.peartv.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private object Keys {
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    val CHANNELS_PROMPT_DISMISSED = booleanPreferencesKey("channels_prompt_dismissed")
}

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override val isDarkTheme: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.DARK_THEME] ?: true }

    override val tmdbApiKey: Flow<String?> = context.settingsDataStore.data
        .map { it[Keys.TMDB_API_KEY] }

    override val hasDismissedChannelsPrompt: Flow<Boolean> = context.settingsDataStore.data
        .map { it[Keys.CHANNELS_PROMPT_DISMISSED] ?: false }

    override suspend fun setDarkTheme(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    override suspend fun setTmdbApiKey(key: String?) {
        context.settingsDataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(Keys.TMDB_API_KEY) else prefs[Keys.TMDB_API_KEY] = key
        }
    }

    override suspend fun setChannelsPromptDismissed() {
        context.settingsDataStore.edit { it[Keys.CHANNELS_PROMPT_DISMISSED] = true }
    }
}
