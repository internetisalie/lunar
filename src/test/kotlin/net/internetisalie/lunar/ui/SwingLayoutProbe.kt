package net.internetisalie.lunar.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JTable

/**
 * Lays a detached component tree out at a chosen size so a test can read the widths a user would
 * actually see.
 *
 * This exists because `TableColumn.preferredWidth` is a *request*, not a result: `JTable` spreads
 * surplus across columns by their remaining headroom, which is equal for every column whose
 * `maxWidth` is untouched. A preferred-width assertion therefore cannot see the rendered
 * distribution — measured on the matrix table, a 320/220/110/80 model renders as 598/505/385/355.
 */
internal object SwingLayoutProbe {
    /** Lays out [root] and every descendant container at [width] × [height]. */
    fun layoutAt(
        root: Container,
        width: Int,
        height: Int,
    ) {
        root.size = Dimension(width, height)
        root.bounds = Rectangle(0, 0, width, height)
        layoutDeep(root)
    }

    /** The rendered column widths of [table] once it has been laid out at [width]. */
    fun renderedColumnWidths(
        table: JTable,
        width: Int,
    ): List<Int> {
        table.size = Dimension(width, table.rowHeight * MEASURE_ROWS)
        table.doLayout()
        return (0 until table.columnModel.columnCount).map { table.columnModel.getColumn(it).width }
    }

    private fun layoutDeep(container: Container) {
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach { layoutDeep(it) }
    }

    private const val MEASURE_ROWS = 4
}
