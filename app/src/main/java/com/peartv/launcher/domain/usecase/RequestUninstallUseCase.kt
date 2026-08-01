package com.peartv.launcher.domain.usecase

import com.peartv.launcher.domain.repository.AppLauncher

/** Grid Reordering & Folders' context menu "Delete App" (§4 Mechanism B). */
class RequestUninstallUseCase(
    private val appLauncher: AppLauncher,
) {
    operator fun invoke(packageName: String) = appLauncher.requestUninstall(packageName)
}
