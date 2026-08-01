package com.peartv.launcher.domain.usecase

import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppLauncher

class LaunchAppUseCase(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(app: TvApp) = appLauncher.launch(app)
}
