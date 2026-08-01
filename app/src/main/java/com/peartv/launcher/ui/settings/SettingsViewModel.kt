package com.peartv.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.peartv.launcher.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    initialDarkTheme: Boolean,
) : ViewModel() {

    // Seeded with the same synchronously-read value MainActivity used to
    // pick its pre-Compose window theme (see MainActivity's
    // `initialDarkTheme`), not a hardcoded `true` — otherwise a light-theme
    // user's first Compose frame still rendered fully dark-themed for the
    // one or more frames DataStore's real (async) value took to arrive,
    // even after the windowBackground itself was fixed to match.
    val isDarkTheme: StateFlow<Boolean> = settingsRepository.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialDarkTheme)

    val tmdbApiKey: StateFlow<String?> = settingsRepository.tmdbApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    fun setTmdbApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setTmdbApiKey(key) }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val initialDarkTheme: Boolean,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(settingsRepository, initialDarkTheme) as T
    }
}
