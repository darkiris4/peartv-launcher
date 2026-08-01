package com.peartv.launcher.domain.repository

import com.peartv.launcher.domain.model.GridNode
import com.peartv.launcher.domain.model.TvApp
import kotlinx.coroutines.flow.Flow

/**
 * Grid Reordering & Folders model — the sole source of truth for user-driven
 * layout (dock membership, grid order, folder structure) once it exists.
 *
 * Decisions Log "Seed precedence": `app_enrichment.json`'s `pinnedToTopShelf`
 * only ever seeds the *first* layout ever written for this device (see
 * [reconcile]) — every write after that is layout-repository-owned, and
 * enrichment-JSON changes on app updates never again touch a user's
 * customized arrangement.
 */
interface LayoutRepository {
    /** Null until the very first [reconcile] call seeds it; non-null (possibly empty) forever after. */
    val layout: Flow<List<GridNode>?>

    /** Persists a full replacement layout — the result of any reorder/folder-edit gesture. */
    suspend fun setLayout(nodes: List<GridNode>)

    /**
     * Seeds the layout on first-ever call (Decisions Log "Seed precedence"),
     * then on every call reconciles it against [installedApps]: appends any
     * package not yet represented by a [GridNode.App] anywhere (including
     * inside a folder) to the end of the grid (Decisions Log "New app
     * placement"), and drops any [GridNode.App] whose package is no longer
     * installed — cascading a folder dissolve if that leaves it with exactly
     * one app left (§6).
     */
    suspend fun reconcile(installedApps: List<TvApp>)
}
