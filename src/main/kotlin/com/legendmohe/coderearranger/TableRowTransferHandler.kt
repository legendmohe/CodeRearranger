package com.legendmohe.coderearranger

import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DragSource
import javax.activation.DataHandler
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.TransferHandler

class TableRowTransferHandler(
        private var table: JTable?,
        private var reorderable: Reorderable
) : TransferHandler() {

    private val localObjectFlavor = DataFlavor(Int::class.java, "Integer Row Index")

    override fun createTransferable(c: JComponent): Transferable {
        assert(c === table)
        return DataHandler(table?.selectedRow, localObjectFlavor.mimeType)
    }

    override fun canImport(info: TransferSupport): Boolean {
        val b = info.component === table && info.isDrop && info.isDataFlavorSupported(localObjectFlavor)
        table?.cursor = if (b) DragSource.DefaultMoveDrop else DragSource.DefaultMoveNoDrop
        return b
    }

    override fun getSourceActions(c: JComponent): Int {
        return COPY_OR_MOVE
    }

    override fun importData(info: TransferSupport): Boolean {
        val target = info.component as JTable
        val dl = info.dropLocation as JTable.DropLocation
        var index = dl.row
        val max = table!!.model.rowCount
        if (index < 0 || index > max) {
            index = max
        }
        target.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
        try {
            val rowFrom = info.transferable.getTransferData(localObjectFlavor) as Int
            if (rowFrom != -1 && rowFrom != index) {
                val rows = table?.selectedRows?.let { rows ->
                    for ((iter, row) in rows.withIndex()) {
                        if (index > row) {
                            index--
                            reorderable.reorder(rows.size > 1, row - iter, index)
                        } else {
                            reorderable.reorder(rows.size > 1, row, index)
                        }
                        index++
                    }
                    rows
                }
                reorderable.finished(rows?.size ?: 0 > 1)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    override fun exportDone(c: JComponent, t: Transferable, act: Int) {
        if (act == MOVE) {
            table!!.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
        }
    }

    interface Reorderable {
        fun reorder(isMulti: Boolean, fromIndex: Int, toIndex: Int)
        fun finished(isMulti: Boolean)
    }
}