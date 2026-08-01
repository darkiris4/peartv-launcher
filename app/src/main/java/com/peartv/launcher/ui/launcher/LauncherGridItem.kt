package com.peartv.launcher.ui.launcher

import com.peartv.launcher.domain.model.TvApp

/**
 * A [com.peartv.launcher.domain.model.GridNode] resolved against the real
 * installed-app list — what `AppGrid`/`TopShelfRow` actually render. Kept
 * separate from `GridNode` itself so the persistence layer never needs to
 * know about [TvApp] (banners, labels, accent colors) at all — it only ever
 * stores package names and folder membership.
 */
sealed class LauncherGridItem {
    abstract val id: String

    data class AppItem(val app: TvApp) : LauncherGridItem() {
        override val id: String = app.packageName
    }

    data class FolderItem(
        override val id: String,
        val name: String,
        val apps: List<TvApp>,
    ) : LauncherGridItem()
}
