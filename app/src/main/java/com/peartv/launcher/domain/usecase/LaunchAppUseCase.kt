package com.peartv.launcher.domain.usecase

import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppLauncher
import com.peartv.launcher.domain.repository.LaunchOrigin

class LaunchAppUseCase(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(app: TvApp, origin: LaunchOrigin? = null) = appLauncher.launch(app, origin)
}
