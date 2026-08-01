package com.peartv.launcher.domain.usecase

import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.LauncherAppRepository
import kotlinx.coroutines.flow.StateFlow

class GetInstalledAppsUseCase(
    private val repository: LauncherAppRepository,
) {
    operator fun invoke(): StateFlow<List<TvApp>> = repository.apps
}
