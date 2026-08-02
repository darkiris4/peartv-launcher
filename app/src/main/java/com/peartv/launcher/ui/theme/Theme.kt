package com.peartv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * Every role this app actually consumes from `androidx.tv.material3.ColorScheme`
 * — everything under `ui/` reads colors through `MaterialTheme.colorScheme.*`,
 * never a `PearTv*Dark`/`PearTv*Light` constant directly (those exist only to
 * define the two schemes below, see [PearTvLauncherTheme]'s own doc), so every
 * role a component might reference has to be set explicitly here or it
 * silently falls back to Material3's own (non-tvOS, non-brand) default
 * tokens — confirmed against the real `tv-material3` 1.0.0 bytecode that this
 * was already happening for `error` (`OptionsMenu`'s "Delete App") and
 * `border` (`StatusBar`'s settings-gear `OutlinedIconButton` ring), both
 * pulling Material3's stock purple-ish defaults before this pass.
 *
 * `tertiary`/`onTertiary` are repurposed here as the third, dimmest tier of
 * the tvOS-style text hierarchy (primary/secondary/tertiary) rather than a
 * third accent hue — nothing in this app or in `tv-material3`'s own
 * `Button`/`IconButton`/`Switch` defaults reads `tertiary` for anything else,
 * so there's no real role to conflict with.
 *
 * `scrim`/`inverseSurface`/`inverseOnSurface` are pinned to the *same* fixed
 * dark/white pair in both the dark and light scheme below (not
 * theme-inverted) — this is §5 #14's fixed-dark hero/carousel
 * text-legibility scrim (`HeroBanner`/`ContentCarousel`), expressed as real
 * theme roles instead of the raw `PearTvBackgroundDark`/`PearTvOnBackgroundDark`
 * constants those files used to import directly. The artwork behind that
 * scrim is arbitrary photographic content, not a themable surface, so it
 * deliberately does not flip with the light/dark toggle — see those files'
 * own docs. `scrim` already carries that "fixed overlay, doesn't flip with
 * theme" meaning in Material's own convention; `inverseSurface`/
 * `inverseOnSurface` have no other reachable call site in this app, so
 * pinning them to the same fixed pair reads as "content guaranteed to pop
 * against a fixed-dark treatment" rather than colliding with their own
 * Material intent.
 *
 * Roles left undefined below (`secondary`, `primaryContainer`,
 * `tertiaryContainer`, `inversePrimary`, `surfaceTint`, etc.) aren't
 * oversights — nothing in this app's components or custom UI reads them
 * (confirmed the same way as `error`/`border` above), so there's no live
 * dependency on their Material3 defaults to remove yet. Add one here the day
 * something actually consumes it, rather than inventing a color with no call
 * site.
 */
private val PearTvDarkColorScheme = darkColorScheme(
    background = PearTvBackgroundDark,
    surface = PearTvSurfaceDark,
    onBackground = PearTvOnBackgroundDark,
    onSurface = PearTvOnBackgroundDark,
    surfaceVariant = PearTvTileFallbackDark,
    onSurfaceVariant = PearTvOnSurfaceVariantDark,
    tertiary = PearTvTertiaryDark,
    onTertiary = PearTvOnBackgroundDark,
    primary = PearTvAccentDark,
    onPrimary = PearTvOnAccent,
    border = PearTvBorderDark,
    borderVariant = PearTvBorderVariantDark,
    error = PearTvErrorDark,
    onError = PearTvOnError,
    scrim = PearTvBackgroundDark,
    inverseSurface = PearTvBackgroundDark,
    inverseOnSurface = PearTvOnBackgroundDark,
)

private val PearTvLightColorScheme = lightColorScheme(
    background = PearTvBackgroundLight,
    surface = PearTvSurfaceLight,
    onBackground = PearTvOnBackgroundLight,
    onSurface = PearTvOnBackgroundLight,
    surfaceVariant = PearTvTileFallbackLight,
    onSurfaceVariant = PearTvOnSurfaceVariantLight,
    tertiary = PearTvTertiaryLight,
    onTertiary = PearTvOnBackgroundLight,
    primary = PearTvAccentLight,
    onPrimary = PearTvOnAccent,
    border = PearTvBorderLight,
    borderVariant = PearTvBorderVariantLight,
    error = PearTvErrorLight,
    onError = PearTvOnError,
    // Fixed regardless of theme — see the fun-doc above.
    scrim = PearTvBackgroundDark,
    inverseSurface = PearTvBackgroundDark,
    inverseOnSurface = PearTvOnBackgroundDark,
)

/**
 * PRODUCT_SPEC.md Decisions Log: "Theme" — explicit toggle between exactly
 * these two fixed schemes, not system-follow and not open-ended theming.
 * [darkTheme] is caller-supplied (from `SettingsViewModel`'s persisted
 * choice), not derived from `isSystemInDarkTheme()`.
 *
 * Everything under `ui/` should read colors via `MaterialTheme.colorScheme`,
 * never the `PearTv*Dark`/`PearTv*Light` constants directly — those exist only
 * to define the two schemes here. A call site that hardcodes one of them
 * bypasses this toggle entirely, which happened by accident before this
 * function grew the light variant (see the color-audit fix applied
 * alongside this change).
 */
@Composable
fun PearTvLauncherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PearTvDarkColorScheme else PearTvLightColorScheme,
        typography = PearTvTypography,
        content = content,
    )
}
