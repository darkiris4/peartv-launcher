package com.peartv.launcher.domain.model

/**
 * PRODUCT_SPEC.md's Grid Reordering & Folders model — the persisted layout
 * unit. [LauncherAppRepository]/`PackageManager` supplies *what apps exist*;
 * `app_enrichment.json` supplies curated defaults; a [GridNode] list (owned
 * by `LayoutRepository`) supplies *where the user has put things* — the only
 * one of the three that's user-editable at runtime.
 *
 * `position` is a dense, per-region (dock vs. grid) sort index, not a slot
 * coordinate — row/column is derived at render time from grid width, exactly
 * like the plain alphabetical grid did before this feature existed.
 */
sealed class GridNode {
    abstract val position: Int
    abstract val isDock: Boolean

    data class App(
        val packageName: String,
        override val position: Int,
        override val isDock: Boolean,
    ) : GridNode()

    /** Folders are grid-only (Decisions Log: "Folders excluded from the dock" — an app's focus state inside the dock drives the hero region, which a folder has no single answer for). */
    data class Folder(
        val id: String,
        val name: String,
        override val position: Int,
        override val isDock: Boolean,
        val appPackages: List<String>,
    ) : GridNode()
}

/** Repositioning helper — reordering/drag logic only ever needs to bump [GridNode.position], never touch the rest of either variant's payload. */
fun GridNode.withPosition(position: Int): GridNode = when (this) {
    is GridNode.App -> copy(position = position)
    is GridNode.Folder -> copy(position = position)
}

/** Dock-membership helper — crossing the dock/grid boundary while dragging (§3 "Top-Shelf Dock Exclusivity") never touches anything else about the node. */
fun GridNode.withDock(isDock: Boolean): GridNode = when (this) {
    is GridNode.App -> copy(isDock = isDock)
    is GridNode.Folder -> copy(isDock = isDock)
}

/** Stable identity across a reorder — a package name for [GridNode.App], a generated id for [GridNode.Folder] (whose own contained apps can change without the folder itself losing identity). */
fun GridNode.stableId(): String = when (this) {
    is GridNode.App -> packageName
    is GridNode.Folder -> id
}

/** Re-derives dense, per-region position indices after any edit that removes/inserts nodes (folder create/merge/eject) — dock and grid are numbered independently, each starting at 0. */
fun List<GridNode>.renumbered(): List<GridNode> {
    val dock = filter { it.isDock }.sortedBy { it.position }.mapIndexed { i, n -> n.withPosition(i) }
    val grid = filterNot { it.isDock }.sortedBy { it.position }.mapIndexed { i, n -> n.withPosition(i) }
    return dock + grid
}
