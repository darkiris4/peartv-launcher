package com.peartv.launcher.domain.usecase

import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppLauncher

/** PRODUCT_SPEC.md §3.1.2 Template 1/3 — launches a Tier 3 program's own deep link (`COLUMN_INTENT_URI`), falling back to just launching [fallbackApp] when there's no usable intent URI. */
class LaunchContentUseCase(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(intentUri: String?, fallbackApp: TvApp) = appLauncher.launchContent(intentUri, fallbackApp)
}
