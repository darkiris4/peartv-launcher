package com.peartv.launcher.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * PRODUCT_SPEC.md §4's narrowly-scoped settings surface — deliberately just
 * these two things (theme choice, TMDB API key), not a general preferences
 * store. Plain [Flow], not [kotlinx.coroutines.flow.StateFlow] — callers
 * (currently just `SettingsViewModel`) own converting to hot state with
 * their own lifecycle-scoped `stateIn`, so this repository doesn't need its
 * own long-lived `CoroutineScope`.
 */
interface SettingsRepository {
    /** Defaults to `true` (dark) — matches the app's dark-only look before this decision (Decisions Log: "Theme"). */
    val isDarkTheme: Flow<Boolean>

    /** `null` until the user enters one via the settings screen — Tier 1 (§2.4) treats that as "TMDB unavailable," not an error. */
    val tmdbApiKey: Flow<String?>

    /** Defaults to `false` — once the user dismisses the first-launch Channels permission prompt ("Not now"), it never shows again, regardless of whether the permission ends up granted later. Decisions Log: "First-launch Channels permission prompt." */
    val hasDismissedChannelsPrompt: Flow<Boolean>

    suspend fun setDarkTheme(enabled: Boolean)

    /** Blank/empty is normalized to `null` — see impl. */
    suspend fun setTmdbApiKey(key: String?)

    suspend fun setChannelsPromptDismissed()
}
