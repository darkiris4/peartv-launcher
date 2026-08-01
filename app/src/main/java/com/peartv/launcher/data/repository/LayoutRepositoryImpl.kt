package com.peartv.launcher.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.peartv.launcher.domain.model.GridNode
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.model.stableId
import com.peartv.launcher.domain.model.withDock
import com.peartv.launcher.domain.model.withPosition
import com.peartv.launcher.domain.repository.LayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.layoutDataStore by preferencesDataStore(name = "layout")

private object LayoutKeys {
    val LAYOUT_JSON = stringPreferencesKey("layout_json")
}

/**
 * User-directed hard cap — the dock holds at most this many tiles, full
 * stop. Applied both at initial seeding (Decision #7's alphabetical-dock-of-6
 * fallback) *and* on every [reconcile] pass, since seeding alone doesn't
 * bound it: `app_enrichment.json`'s `pinnedToTopShelf` apps were previously
 * used unconditionally with no cap, so a curation file pinning more than 6
 * apps (or a stale persisted layout from before this cap existed) could
 * silently grow the dock past its intended size — confirmed as the actual
 * cause of a dock that scrolled well past 6 tiles on-device.
 */
private const val MaxDockSize = 6

private const val TypeApp = "app"
private const val TypeFolder = "folder"

/**
 * Grid Reordering & Folders — plain DataStore + hand-rolled JSON (matching
 * [SettingsRepositoryImpl]/[AppEnrichmentRepositoryImpl]'s existing
 * convention, not Room): the persisted shape is a single small ordered list,
 * not a relational structure, so a database's query/migration ceremony
 * doesn't buy anything here.
 *
 * `null` from [layout] means "never seeded" — distinct from an empty list,
 * which would mean "the user deleted every dock/grid entry," a real (if
 * inert) end state that must not re-trigger seeding.
 */
class LayoutRepositoryImpl(
    private val context: Context,
) : LayoutRepository {

    override val layout: Flow<List<GridNode>?> = context.layoutDataStore.data
        .map { prefs -> prefs[LayoutKeys.LAYOUT_JSON]?.let(::decodeLayout) }

    override suspend fun setLayout(nodes: List<GridNode>) {
        context.layoutDataStore.edit { it[LayoutKeys.LAYOUT_JSON] = encodeLayout(nodes) }
    }

    override suspend fun reconcile(installedApps: List<TvApp>) {
        val installedPackages = installedApps.map { it.packageName }.toSet()
        val current = layout.first()
        val base = current ?: seedLayout(installedApps)
        val reconciled = enforceDockCap(reconcileNodes(base, installedPackages))
        if (reconciled != current) setLayout(reconciled)
    }

    private fun seedLayout(installedApps: List<TvApp>): List<GridNode> {
        val alphabetical = installedApps.sortedBy { it.label.lowercase() }
        val pinned = installedApps.filter { it.pinnedToTopShelf }
        val dockApps = pinned.ifEmpty { alphabetical.take(MaxDockSize) }.take(MaxDockSize)
        val dockPackages = dockApps.map { it.packageName }.toSet()
        val gridApps = alphabetical.filterNot { it.packageName in dockPackages }
        val dockNodes = dockApps.mapIndexed { index, app ->
            GridNode.App(app.packageName, position = index, isDock = true)
        }
        val gridNodes = gridApps.mapIndexed { index, app ->
            GridNode.App(app.packageName, position = index, isDock = false)
        }
        return dockNodes + gridNodes
    }

    /** Drops uninstalled apps (cascading a folder dissolve per §6 when that leaves it with one app), then appends any installed package not yet represented anywhere (Decision #8) to the end of the grid. */
    private fun reconcileNodes(nodes: List<GridNode>, installedPackages: Set<String>): List<GridNode> {
        val referenced = mutableSetOf<String>()
        val survivors = nodes.mapNotNull { node ->
            when (node) {
                is GridNode.App -> node.takeIf { it.packageName in installedPackages }?.also { referenced += it.packageName }
                is GridNode.Folder -> {
                    val remaining = node.appPackages.filter { it in installedPackages }
                    referenced += remaining
                    when {
                        remaining.isEmpty() -> null
                        remaining.size == 1 -> GridNode.App(remaining[0], node.position, node.isDock)
                        else -> node.copy(appPackages = remaining)
                    }
                }
            }
        }
        val newPackages = installedPackages.filterNot { it in referenced }
        if (newPackages.isEmpty()) return normalizePositions(survivors)
        // installedPackages is a Set — walk installedApps' own order instead so appended apps land in a stable, deterministic order rather than hash-set iteration order.
        val orderedNew = installedPackages.filter { it in newPackages }
        val gridCount = survivors.count { !it.isDock }
        val appended = orderedNew.mapIndexed { i, pkg -> GridNode.App(pkg, position = gridCount + i, isDock = false) }
        return normalizePositions(survivors + appended)
    }

    private fun normalizePositions(nodes: List<GridNode>): List<GridNode> {
        val dock = nodes.filter { it.isDock }.sortedBy { it.position }.mapIndexed { i, n -> n.withPosition(i) }
        val grid = nodes.filterNot { it.isDock }.sortedBy { it.position }.mapIndexed { i, n -> n.withPosition(i) }
        return dock + grid
    }

    /**
     * Moves any dock node past [MaxDockSize] back into the grid (appended
     * after whatever's already there, in the same relative order) — run on
     * every [reconcile] pass, not just at seed time, so a persisted layout
     * from before this cap existed (or a curation file pinning too many
     * apps) self-corrects rather than staying stuck oversized forever.
     */
    private fun enforceDockCap(nodes: List<GridNode>): List<GridNode> {
        val dockNodes = nodes.filter { it.isDock }.sortedBy { it.position }
        if (dockNodes.size <= MaxDockSize) return nodes
        val overflowIds = dockNodes.drop(MaxDockSize).map { it.stableId() }.toSet()
        val corrected = nodes.map { if (it.stableId() in overflowIds) it.withDock(false) else it }
        return normalizePositions(corrected)
    }
}

private fun encodeLayout(nodes: List<GridNode>): String {
    val array = JSONArray()
    for (node in nodes) {
        val obj = JSONObject()
        obj.put("position", node.position)
        obj.put("isDock", node.isDock)
        when (node) {
            is GridNode.App -> {
                obj.put("type", TypeApp)
                obj.put("packageName", node.packageName)
            }
            is GridNode.Folder -> {
                obj.put("type", TypeFolder)
                obj.put("id", node.id)
                obj.put("name", node.name)
                obj.put("appPackages", JSONArray(node.appPackages))
            }
        }
        array.put(obj)
    }
    return array.toString()
}

private fun decodeLayout(json: String): List<GridNode> {
    val array = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
    return buildList {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val position = obj.getInt("position")
            val isDock = obj.getBoolean("isDock")
            when (obj.getString("type")) {
                TypeApp -> add(GridNode.App(obj.getString("packageName"), position, isDock))
                TypeFolder -> {
                    val packages = obj.getJSONArray("appPackages")
                    val appPackages = List(packages.length()) { packages.getString(it) }
                    add(GridNode.Folder(obj.getString("id"), obj.getString("name"), position, isDock, appPackages))
                }
            }
        }
    }
}
