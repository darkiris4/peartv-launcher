package com.peartv.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

private val PearTvDarkColorScheme = darkColorScheme(
    background = PearTvBackgroundDark,
    surface = PearTvSurfaceDark,
    onBackground = PearTvOnBackgroundDark,
    onSurface = PearTvOnBackgroundDark,
    surfaceVariant = PearTvTileFallbackDark,
)

private val PearTvLightColorScheme = lightColorScheme(
    background = PearTvBackgroundLight,
    surface = PearTvSurfaceLight,
    onBackground = PearTvOnBackgroundLight,
    onSurface = PearTvOnBackgroundLight,
    surfaceVariant = PearTvTileFallbackLight,
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
