package com.legendmohe.coderearranger

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.Factory
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.legendmohe.coderearranger.TableRowTransferHandler.Reorderable
import com.legendmohe.coderearranger.resolver.ICodeInfo
import com.legendmohe.coderearranger.resolver.ILanguageResolver
import com.legendmohe.coderearranger.resolver.JavaResolver
import com.legendmohe.coderearranger.resolver.KotlinResolver
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Component
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel


class CodeRearrangerPanel(private val project: Project, private val toolWindow: ToolWindow) {

    companion object {
        const val SECTION_PLACEHOLDER = "//////////////////////////////////###////////////////////////////////////"
    }

    var mainPanel: JPanel? = null
    var refreshBtn: JButton? = null
    var codeTable: JTable? = null
    var upBtn: JButton? = null
    var downBtn: JButton? = null
    var addSection: JButton? = null

    private var selectedRows: IntArray? = null
    private var lastEditFile: Long = 0
    private var resolver: ILanguageResolver? = null

    init {
        refreshBtn?.addActionListener { syncCurrentFile(null, true) }
        upBtn?.addActionListener { moveEleUp() }
        downBtn?.addActionListener { moveEleDown() }
        addSection?.addActionListener { handleAddSection() }
        toolWindow.activate({ syncCurrentFile(null, true) }, true)
        initTable()
        initData()
    }

    ///////////////////////////////////ui///////////////////////////////////

    private fun initTable() {
        codeTable?.autoResizeMode = JTable.AUTO_RESIZE_OFF
        codeTable?.model = object : AbstractTableModel() {
            private val columnNames = arrayOf(
                    "line", "name", "desc"
            )

            override fun getRowCount(): Int {
                return codeInfos.size
            }

            override fun getColumnCount(): Int {
                return columnNames.size
            }

            override fun getColumnName(column: Int): String {
                return columnNames[column]
            }

            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
                val codeInfo = codeInfos[rowIndex]
                if (columnIndex == 0) {
                    return codeInfo.printLineRange()
                }
                return if (columnIndex == 1) {
                    codeInfo.printTypeName()
                } else codeInfo.printTitle()
            }
        }
        codeTable?.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val target = e.source as JTable
                    val row = target.selectedRow // select a row
                    if (row >= codeInfos.size) {
                        return
                    }
                    val codeInfo = codeInfos[row]
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
                    editor.caretModel.moveToOffset(
                            (codeInfo.getViewTreeElement().value as PsiElement).textOffset
                    )
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                }
            }
        })
        codeTable?.dragEnabled = true
        codeTable?.dropMode = DropMode.INSERT_ROWS
        codeTable?.transferHandler = TableRowTransferHandler(codeTable, object : Reorderable {
            override fun reorder(isMulti: Boolean, fromIndex: Int, toIndex: Int) {
                handleDragAndDrop(isMulti, fromIndex, toIndex)
            }

            override fun finished(isMulti: Boolean) {
                handleDragAndDropFinished(isMulti)
            }
        })
        codeTable?.addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
            override fun ancestorResized(e: HierarchyEvent) {
                super.ancestorResized(e)
                codeTable?.let {
                    resizeColumnWidth(it)
                }
            }
        })
    }

    fun resizeColumnWidth(table: JTable) {
        var cumulativeActual = 0
        val padding = 15
        for (columnIndex in 0 until table.columnCount) {
            var width = 50 // Min width
            val column = table.columnModel.getColumn(columnIndex)
            for (row in 0 until table.rowCount) {
                val renderer = table.getCellRenderer(row, columnIndex)
                val comp = table.prepareRenderer(renderer, row, columnIndex)
                width = (comp.preferredSize.width + padding).coerceAtLeast(width)
            }
            if (columnIndex < table.columnCount - 1) {
                column.preferredWidth = width
                cumulativeActual += column.width
            } else { //LAST COLUMN
                //Use the parent's (viewPort) width and subtract the previous columbs actual widths.
                column.preferredWidth = table.parent.size.getWidth().toInt() - cumulativeActual
            }
        }
        updateRowHeights()
    }

    private fun updateRowHeights() {
        codeTable?.apply {
            for (row in 0 until rowCount) {
                var rowHeight: Int = rowHeight
                for (column in 0 until columnCount) {
                    val comp: Component = prepareRenderer(getCellRenderer(row, column), row, column)
                    rowHeight = rowHeight.coerceAtLeast(comp.preferredSize.height)
                }
                setRowHeight(row, rowHeight)
            }
        }
    }

    private fun updateDataAndSyncSelection() {
        codeTable?.let {
            (it.model as AbstractTableModel).fireTableDataChanged()
            resizeColumnWidth(it)
            it.clearSelection()
            selectedRows?.filter { row -> row < it.rowCount }?.forEach { row ->
                it.addRowSelectionInterval(row, row)
            }
        }
    }

    private fun moveEleDown() {
        if (checkDumping(true)) {
            return
        }
        selectedRows = codeTable?.selectedRows
        selectedRows?.let {
            if (it.isEmpty() || it.last() >= codeInfos.size - 1) {
                return@moveEleDown
            }
        }
        WriteCommandAction.runWriteCommandAction(project) {
            selectedRows?.reversed()?.forEachIndexed { index, i ->
                val nextInfo = codeInfos[i + 1]
                val curInfo = codeInfos[i]
                exchangeElement(nextInfo, curInfo)

                // TODO - 优化掉这个全局刷新吧
                selectedRows?.set(index, i + 1) // 下移一行
                syncCurrentFile(null, false)
            }
        }
    }

    private fun moveEleUp() {
        if (checkDumping(true)) {
            return
        }
        selectedRows = codeTable?.selectedRows
        selectedRows?.let {
            if (it.isEmpty() || it.first() <= 0) {
                return@moveEleUp
            }
        }
        WriteCommandAction.runWriteCommandAction(project) {
            selectedRows?.forEachIndexed { index, i ->
                val preInfo = codeInfos[i - 1]
                val curInfo = codeInfos[i]
                exchangeElement(preInfo, curInfo)

                // TODO - 优化掉这个全局刷新吧
                selectedRows?.set(index, i - 1)  // 上移一行
                syncCurrentFile(null, false)
            }
        }
    }

    private fun handleAddSection() {
        if (checkDumping(true)) {
            return
        }
        selectedRows = codeTable?.selectedRows
        val selectedRow = selectedRows?.let {
            if (it.isEmpty()) {
                return@handleAddSection
            }
            it.first()
        } ?: 0
        val selectedInfo = codeInfos[selectedRow]
        val psiComment: PsiElement = resolver?.getCommentFromText(project, SECTION_PLACEHOLDER)
                ?: return
        val selectedEle = selectedInfo.getViewTreeElement().value as PsiElement
        val whiteSpace: PsiElement? = nl(psiComment)
        val whiteSpace2: PsiElement? = nl(psiComment)
        if (selectedEle is PsiComment) {
            // 注释不能再加注释
            return
        }
        selectedEle.parent?.let {
            WriteCommandAction.runWriteCommandAction(project) {
                it.addBefore(psiComment, selectedEle)
                // 加两行才能空一行出来，妈的
                whiteSpace?.let { w -> it.addBefore(w, selectedEle) }
                whiteSpace2?.let { w -> it.addBefore(w, selectedEle) }
                syncCurrentFile(null, false)
            }
        }
    }

    /**
    * 妈的，要这样搞
    */
    private fun nl(context: PsiElement): PsiWhiteSpace? {
        var tmp: PsiElement = context
            while (tmp !is ASTNode) {
                tmp = tmp.firstChild
                if (tmp == null) return null
            }
            val charTable = SharedImplUtil.findCharTableByTree(tmp as ASTNode?)
        return Factory.createSingleLeafElement(TokenType.WHITE_SPACE, "\n\n",
                    charTable, PsiManager.getInstance(project)) as PsiWhiteSpace
    }

    private fun exchangeElement(targetInfo: ICodeInfo, curInfo: ICodeInfo) {
        val targetEle = targetInfo.getViewTreeElement().value as PsiElement
        val curEle = curInfo.getViewTreeElement().value as PsiElement
        val copy = targetEle.copy()
        targetEle.replace(curEle)
        curEle.replace(copy)
    }

    private fun handleDragAndDrop(isMulti: Boolean, fromIndex: Int, toIndex: Int) {
        if (checkDumping(true)) {
            return
        }
        if (fromIndex == toIndex) {
            return
        }
        if (!isMulti) {
            selectedRows = intArrayOf(toIndex)
        }
        WriteCommandAction.runWriteCommandAction(project) {
            val fromCode = codeInfos[fromIndex]
            val toCode = codeInfos[toIndex]
            val fromEle = fromCode.getViewTreeElement().value as PsiElement
            val toEle = toCode.getViewTreeElement().value as PsiElement
            val copy = fromEle.copy()
            fromEle.delete()
            val parent = toEle.parent
            if (parent != null) {
                if (toIndex > fromIndex) {
                    parent.addAfter(copy, toEle)
                } else {
                    parent.addBefore(copy, toEle)
                }
            }
            // 每移动一个就刷一次
            collectCodeInfo(null, false, project)
        }
    }

    private fun handleDragAndDropFinished(isMulti: Boolean) {
        updateDataAndSyncSelection()
    }

    //////////////////////////////////functions////////////////////////////////////

    private var debouncer: Debouncer? = null

    private val codeInfos: MutableList<ICodeInfo> = ArrayList()

    private fun initData() {
        debouncer = Debouncer(500)
    }

    fun syncCurrentFile(virtualFile: VirtualFile?, syncCaretTarget: Boolean) {
        if (!syncCaretTarget) {
            ApplicationManager.getApplication().invokeLater { syncCurrentFileInternal(virtualFile, false) }
        } else {
            debouncer?.call(CodeRearrangerPanel::class.java) { ApplicationManager.getApplication().invokeLater { syncCurrentFileInternal(virtualFile, syncCaretTarget) } }
        }
    }

    private fun syncCurrentFileInternal(virtualFile: VirtualFile?, syncCaretTarget: Boolean) {
        if (!toolWindow.isVisible) {
            return
        }
        if (project.isDisposed) {
            return
        }
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val curFile = FileDocumentManager.getInstance().getFile(editor.document)
        if (virtualFile != null) {
            if (curFile == null || curFile != virtualFile) {
                return
            }
            // 修改过期
            if (lastEditFile <= 0 || lastEditFile != virtualFile.modificationStamp) {
                syncCurrentFile(null, true)
                return
            }
        }
        lastEditFile = curFile?.modificationStamp ?: 0

        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        initResolverFromFile(file)

        var targetEle: PsiElement? = null
        // 是否使用光标下的类
        if (syncCaretTarget) {
            val caretOffset = editor.caretModel.offset
            val elementAt = file.findElementAt(caretOffset)
            targetEle = resolver?.findParentClass(elementAt)
        }
        collectCodeInfo(targetEle, true, project)
        updateDataAndSyncSelection()
    }

    // TODO - 这里看看怎么重构一下
    private fun initResolverFromFile(file: PsiFile) {
        if (file is KtFile) {
            resolver = KotlinResolver()
        } else if (file is PsiJavaFile) {
            resolver = JavaResolver()
        }
    }

    private fun collectCodeInfo(targetEle: PsiElement?, findChild: Boolean, project: Project) {
        // 先清空
        codeInfos.clear()
        resolver?.collectCodeInfo(project, targetEle, findChild)?.let {
            codeInfos.addAll(it)
        }
    }

    private fun checkDumping(showHint: Boolean): Boolean {
        if (DumbService.getInstance(project).isDumb) {
            if (showHint) {
                DumbService.getInstance(project).showDumbModeNotification("lnot available until indices are built")
            }
            return true
        }
        return false
    }
}