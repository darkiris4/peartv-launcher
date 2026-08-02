package com.peartv.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.peartv.launcher.domain.repository.SettingsRepository
import com.peartv.launcher.domain.repository.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    initialThemeMode: ThemeMode,
) : ViewModel() {

    // Seeded with the same synchronously-read value MainActivity used to
    // pick its pre-Compose window theme (see MainActivity's
    // `initialThemeMode`), not a hardcoded default — otherwise the first
    // Compose frame could render a different theme than the pre-Compose
    // window did, for however many frames DataStore's real (async) value
    // took to arrive, even after the windowBackground itself was fixed to
    // match.
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialThemeMode)

    val tmdbApiKey: StateFlow<String?> = settingsRepository.tmdbApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTmdbApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setTmdbApiKey(key) }
    }

    /** System > Reset Settings, after the user confirms — see `SystemSettingsContent`'s own confirm prompt. */
    fun resetSettings() {
        viewModelScope.launch { settingsRepository.resetAll() }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val initialThemeMode: ThemeMode,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(settingsRepository, initialThemeMode) as T
    }
}
