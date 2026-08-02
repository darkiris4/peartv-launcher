package com.peartv.launcher.ui.launcher

import com.peartv.launcher.domain.model.GridNode
import com.peartv.launcher.domain.model.renumbered
import com.peartv.launcher.domain.model.stableId
import com.peartv.launcher.domain.model.withDock
import com.peartv.launcher.domain.model.withPosition
import com.peartv.launcher.ui.focus.DpadDirection

/**
 * Grid Reordering & Folders' §3 "Spatial Reordering & Tile Swapping Logic" —
 * a pure function over [GridNode] lists, kept separate from
 * [LauncherViewModel] so the swap/reflow/boundary math is exercised without
 * any Compose or coroutine machinery around it.
 *
 * Reinterpreted for this hardware's actual input (D-pad, not the touch-drag
 * the spec's language ("Direct D-Pad Dragging") already anticipates isn't a
 * literal drag gesture): each direction press swaps the active node with
 * whichever neighbor sits in that direction, then the moved-away neighbor is
 * exactly where the active node used to be — which *is* the spec's own
 * "Reflow Transition," just expressed as a swap instead of a general
 * insert-shift. A swap target that doesn't exist (grid edge, e.g. an
 * incomplete last row) is a no-op rather than a general row-insert — a
 * deliberate simplification, since a real "push everything over" reflow
 * needs exact row-fullness accounting this hardware's simple swap-based
 * model doesn't otherwise need.
 *
 * "Top-Shelf Dock Exclusivity" (§3) is the one case that crosses the dock/
 * grid boundary: moving Up out of the grid's row 0 or Down out of the dock's
 * single row swaps the active node with the dock's last slot / the grid's
 * first slot respectively, flipping [GridNode.isDock] on both — a folder can
 * never end up in the dock this way, since [crossIntoDock] simply declines
 * to move a [GridNode.Folder] (Decisions Log: "Folders excluded from the
 * dock").
 */
object GridLayoutEngine {

    fun move(nodes: List<GridNode>, activeId: String, direction: DpadDirection, columnCount: Int): List<GridNode> {
        if (columnCount <= 0) return nodes
        val dock = nodes.filter { it.isDock }.sortedBy { it.position }.toMutableList()
        val grid = nodes.filterNot { it.isDock }.sortedBy { it.position }.toMutableList()

        val dockIndex = dock.indexOfFirst { it.stableId() == activeId }
        val inDock = dockIndex >= 0
        val gridIndex = if (inDock) -1 else grid.indexOfFirst { it.stableId() == activeId }
        if (!inDock && gridIndex == -1) return nodes

        when (direction) {
            DpadDirection.Left -> swapWithin(if (inDock) dock else grid, if (inDock) dockIndex else gridIndex, (if (inDock) dockIndex else gridIndex) - 1)
            DpadDirection.Right -> swapWithin(if (inDock) dock else grid, if (inDock) dockIndex else gridIndex, (if (inDock) dockIndex else gridIndex) + 1)
            DpadDirection.Up -> if (!inDock) {
                val target = gridIndex - columnCount
                if (target >= 0) swapWithin(grid, gridIndex, target) else crossIntoDock(dock, grid, gridIndex)
            }
            DpadDirection.Down -> if (inDock) {
                crossIntoGrid(dock, grid, dockIndex)
            } else {
                val target = gridIndex + columnCount
                if (target < grid.size) swapWithin(grid, gridIndex, target)
            }
        }

        return renumber(dock) + renumber(grid)
    }

    private fun swapWithin(list: MutableList<GridNode>, i: Int, j: Int) {
        if (i !in list.indices || j !in list.indices) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }

    private fun crossIntoDock(dock: MutableList<GridNode>, grid: MutableList<GridNode>, gridIndex: Int) {
        val moving = grid[gridIndex]
        if (moving is GridNode.Folder) return
        if (dock.isEmpty()) {
            grid.removeAt(gridIndex)
            dock.add(moving.withDock(true))
            return
        }
        val lastIndex = dock.size - 1
        val displaced = dock[lastIndex]
        grid[gridIndex] = displaced.withDock(false)
        dock[lastIndex] = moving.withDock(true)
    }

    private fun crossIntoGrid(dock: MutableList<GridNode>, grid: MutableList<GridNode>, dockIndex: Int) {
        val moving = dock[dockIndex]
        if (grid.isEmpty()) {
            dock.removeAt(dockIndex)
            grid.add(moving.withDock(false))
            return
        }
        val displaced = grid[0]
        dock[dockIndex] = displaced.withDock(true)
        grid[0] = moving.withDock(false)
    }

    private fun renumber(list: List<GridNode>): List<GridNode> = list.mapIndexed { index, node -> node.withPosition(index) }

    /**
     * Grid-Reordering §8 (drag-to-merge) — the D-pad-native equivalent of
     * real tvOS's "drop one jiggling icon on another." A literal touch-drag
     * hover has no D-pad analog (a direction press is a single discrete
     * step, not a continuous gesture with a hover state to hang a "settle
     * here" trigger on), so [LauncherScreen]'s key handling instead
     * distinguishes a tap from a hold via Android's own key-repeat signal:
     * a tap always calls [move] (swap/reposition, entirely unchanged); only
     * once the OS starts auto-repeating the same held direction does merging
     * come into play. This is deliberately grid-only, never the dock — a
     * freshly-created [GridNode.Folder] could never legally re-enter the
     * dock afterward ("Folders excluded from the dock"), so merging while
     * [activeId] is currently docked would need to immediately eject the new
     * folder somewhere unasked-for; simpler to just not offer a merge target
     * there at all and let a held dock direction press do nothing rather
     * than something surprising.
     *
     * `LauncherViewModel.moveActive` calls this *before* calling [move] on
     * every direction press — not just on a detected hold — specifically so
     * it captures whichever neighbor was actually adjacent at the moment the
     * press started. Confirmed on-device this ordering matters: an earlier
     * version called this only after a hold was detected, by which point
     * [move]'s own first-press swap had already run, so it found whatever
     * was adjacent to the tile's *new* position instead — one cell further
     * along than the neighbor the user was actually aiming at, skipping the
     * intended target entirely.
     */
    fun mergeTarget(nodes: List<GridNode>, activeId: String, direction: DpadDirection, columnCount: Int): GridNode.App? {
        if (columnCount <= 0) return null
        val grid = nodes.filterNot { it.isDock }.sortedBy { it.position }
        val activeIndex = grid.indexOfFirst { it.stableId() == activeId }
        if (activeIndex == -1) return null
        val targetIndex = when (direction) {
            DpadDirection.Left -> activeIndex - 1
            DpadDirection.Right -> activeIndex + 1
            DpadDirection.Up -> activeIndex - columnCount
            DpadDirection.Down -> activeIndex + columnCount
        }
        return grid.getOrNull(targetIndex) as? GridNode.App
    }

    /**
     * Folds [activeId] and [targetId] into one new [GridNode.Folder] —
     * identified by their own stable ids, not re-derived from spatial
     * adjacency the way [mergeTarget] finds a candidate in the first place.
     * That's deliberate: [targetId] is [mergeTarget]'s result *captured
     * before* the same held press's initial [move] swap ran (see that
     * function's own doc), so by the time a confirmed merge actually reaches
     * this function, [activeId] and [targetId] may no longer be spatially
     * adjacent at all — this only needs to find each by identity and doesn't
     * care where either currently sits. [folderId]/[folderName] supplied by
     * the caller (`LauncherViewModel`, matching how "+ New Folder" already
     * generates both) rather than invented here, keeping this object's own
     * job limited to the reflow math its class doc promises. The vacated
     * active slot isn't left as a gap: [renumbered] (same helper every other
     * structural edit in `LauncherViewModel` already reflows through —
     * "+ New Folder," "Move to…," folder ejection) closes it by shifting
     * every later position down one, which is exactly a left-then-up ground
     * for a row-major grid, no separate reflow logic needed.
     */
    fun mergeById(nodes: List<GridNode>, activeId: String, targetId: String, folderId: String, folderName: String): List<GridNode>? {
        val active = nodes.find { it.stableId() == activeId } as? GridNode.App ?: return null
        val target = nodes.find { it.stableId() == targetId } as? GridNode.App ?: return null
        if (active.isDock || target.isDock) return null
        val folder = GridNode.Folder(folderId, folderName, target.position, isDock = false, appPackages = listOf(target.packageName, active.packageName))
        return (nodes.filterNot { it.stableId() == activeId || it.stableId() == targetId } + folder).renumbered()
    }
}
