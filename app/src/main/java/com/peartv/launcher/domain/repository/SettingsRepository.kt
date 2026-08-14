package com.peartv.launcher.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * `Automatic` follows the system's own light/dark appearance
 * (`isSystemInDarkTheme()`); `Light`/`Dark` are the pre-existing fixed
 * choices (`PearTvLauncherTheme`'s own `darkTheme: Boolean` param is
 * resolved from whichever of these three is active at the call site, not
 * changed itself — see `MainActivity`).
 */
enum class ThemeMode { Automatic, Light, Dark }

/**
 * Governs whether Tier 3's per-program artwork (and, as a side effect, Tier
 * 1's trending backdrop — see `LauncherViewModel.heroBackdrop`'s own doc) may
 * be overridden with a match from a configured metadata provider (TMDB/TVDB),
 * Plex-style. `Native` is the default — deliberately *not* a preservation of
 * pre-this-feature behavior: Tier 1 curated apps used to always show their
 * TMDB trending backdrop unconditionally once a key was configured, and now
 * don't unless the user actively picks `Online`/`Automatic`. `Online`/
 * `Automatic` require at least one of [tmdbApiKey]/[tvdbApiKey] configured —
 * the settings UI makes them unselectable otherwise, and clearing the last
 * configured key while one is active reverts this back to `Native` (see
 * `SettingsRepositoryImpl`'s setters).
 */
enum class ArtworkSource { Native, Online, Automatic }

/**
 * PRODUCT_SPEC.md §4's settings surface — expanded (per an explicit new ask,
 * not scope creep riding in on the original narrow one) into a real tvOS-style
 * hierarchy, but this repository itself stays just persisted preferences, not
 * business logic. Plain [Flow], not [kotlinx.coroutines.flow.StateFlow] —
 * callers (currently just `SettingsViewModel`) own converting to hot state
 * with their own lifecycle-scoped `stateIn`, so this repository doesn't need
 * its own long-lived `CoroutineScope`.
 */
interface SettingsRepository {
    /** Defaults to [ThemeMode.Dark] — matches the app's dark-only look before theming existed at all (Decisions Log: "Theme"), and is *not* [ThemeMode.Automatic] specifically so existing installs' behavior doesn't silently change on upgrade. */
    val themeMode: Flow<ThemeMode>

    /** The user's own Settings entry if set, otherwise a baked-in build-time default if one was configured (see impl — `BuildConfig`, sourced from gitignored `local.properties`, never committed), otherwise `null`. Tier 1 (§2.4) treats `null` as "TMDB unavailable," not an error. */
    val tmdbApiKey: Flow<String?>

    /** Same fallback chain as [tmdbApiKey] (user entry, then baked-in default, then `null`) — same "unavailable, not an error" contract. */
    val tvdbApiKey: Flow<String?>

    /** Defaults to [ArtworkSource.Native] — see that enum's own doc for why this is a real behavior change on upgrade, not a preserved default. */
    val artworkSource: Flow<ArtworkSource>

    /** Defaults to `false` — once the user dismisses the first-launch Channels permission prompt ("Not now"), it never shows again, regardless of whether the permission ends up granted later. Decisions Log: "First-launch Channels permission prompt." */
    val hasDismissedChannelsPrompt: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    /** Blank/empty is normalized to `null` — see impl. Also reverts [artworkSource] back to [ArtworkSource.Native] if this leaves both provider keys unset (see impl). */
    suspend fun setTmdbApiKey(key: String?)

    /** Blank/empty is normalized to `null` — see impl. Also reverts [artworkSource] back to [ArtworkSource.Native] if this leaves both provider keys unset (see impl). */
    suspend fun setTvdbApiKey(key: String?)

    suspend fun setArtworkSource(source: ArtworkSource)

    suspend fun setChannelsPromptDismissed()

    /** System > Reset Settings — clears every persisted preference (theme mode, TMDB/TVDB keys, artwork source, the Channels-prompt-dismissed flag) back to their defaults. */
    suspend fun resetAll()
}
