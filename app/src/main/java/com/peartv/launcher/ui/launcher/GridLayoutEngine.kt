package com.peartv.launcher.ui.launcher

import com.peartv.launcher.domain.model.GridNode
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
}
