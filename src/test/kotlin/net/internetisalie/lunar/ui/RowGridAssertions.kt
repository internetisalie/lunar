package net.internetisalie.lunar.ui

import com.intellij.ui.dsl.gridLayout.Grid
import com.intellij.ui.dsl.gridLayout.GridLayout
import javax.swing.JComponent

/**
 * Helpers for asserting the Kotlin UI DSL put a set of cells in **one** grid (BUG-448 #2/#3).
 *
 * A label-less `row { cell(a); cell(b) }` defaults to [com.intellij.ui.dsl.builder.RowLayout.INDEPENDENT],
 * which makes `PanelBuilder` allocate a fresh sub-grid per row; every such row then sizes its columns
 * alone and they drift apart. `.layout(RowLayout.PARENT_GRID)` puts the cells straight into the panel's
 * root grid instead. The observable difference is exactly this: which [Grid] each cell is registered in.
 *
 * Pixel alignment itself is not assertable headlessly — the grid identity is the structural cause of it.
 */
internal object RowGridAssertions {
    /** The grid a component's cell was registered in, or null when it is not in a DSL grid layout. */
    fun gridOf(component: JComponent): Grid? {
        val host = component.parent as? JComponent ?: return null
        val layout = host.layout as? GridLayout ?: return null
        return layout.getConstraints(component)?.grid
    }

    /** The cell's column index within its grid, or null when it is not in a DSL grid layout. */
    fun columnOf(component: JComponent): Int? {
        val host = component.parent as? JComponent ?: return null
        val layout = host.layout as? GridLayout ?: return null
        return layout.getConstraints(component)?.x
    }

    /** True when every component shares one grid and one column — the shape PARENT_GRID produces. */
    fun shareOneColumn(components: List<JComponent>): Boolean {
        if (components.size < 2) return false
        val grids = components.map { gridOf(it) ?: return false }
        val columns = components.map { columnOf(it) ?: return false }
        return grids.distinctBy { System.identityHashCode(it) }.size == 1 && columns.distinct().size == 1
    }
}
