package com.legendmohe.coderearranger

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.impl.java.ClassInitializerTreeElement
import com.intellij.ide.structureView.impl.java.JavaClassTreeElement
import com.intellij.ide.structureView.impl.java.PsiFieldTreeElement
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.*
import com.legendmohe.coderearranger.TableRowTransferHandler.Reorderable
import java.awt.Component
import java.awt.event.*
import java.util.*
import javax.swing.DropMode
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

class CodeRearrangerPanel(private val project: Project, private val toolWindow: ToolWindow) {
    var mainPanel: JPanel? = null
    var refreshBtn: JButton? = null
    var codeTable: JTable? = null
    var upBtn: JButton? = null
    var downBtn: JButton? = null
    var addSection: JButton? = null

    private var selectedRows: IntArray? = null
    private var lastEditFile: Long = 0
    private var targetClass: PsiClass? = null

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
                            (codeInfo.element.value as PsiElement).textOffset
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
            selectedRows?.forEach { row ->
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
        val parserFacade = PsiParserFacade.SERVICE.getInstance(project)
        val psiComment = parserFacade.createLineCommentFromText(JavaLanguage.INSTANCE, SECTION_PLACEHOLDER)
        //  PsiComment psiComment = JavaPsiFacade.getElementFactory(project).createCommentFromText(SECTION_PLACEHOLDER, null);
        val selectedEle = selectedInfo.element.value as PsiElement
        if (selectedEle is PsiComment) {
            // 注释不能再加注释
            return
        }
        selectedEle.parent?.let {
            WriteCommandAction.runWriteCommandAction(project) {
                it.addBefore(psiComment, selectedEle)
                syncCurrentFile(null, false)
            }
        }
    }

    private fun exchangeElement(targetInfo: CodeInfo, curInfo: CodeInfo) {
        val targetEle = targetInfo.element.value as PsiElement
        val curEle = curInfo.element.value as PsiElement
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
            val fromEle = fromCode.element.value as PsiElement
            val toEle = toCode.element.value as PsiElement
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
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    ?: return@runWriteCommandAction
            collectCodeInfo(targetClass, editor)
        }
    }

    private fun handleDragAndDropFinished(isMulti: Boolean) {
        updateDataAndSyncSelection()
    }

    //////////////////////////////////functions////////////////////////////////////

    private var debouncer: Debouncer? = null

    private val codeInfos: MutableList<CodeInfo> = ArrayList()

    private fun initData() {
        debouncer = Debouncer(500)
    }

    fun syncCurrentFile(virtualFile: VirtualFile?, syncCaretTarget: Boolean) {
        if (!syncCaretTarget) {
            syncCurrentFileInternal(virtualFile, false)
        } else {
            debouncer?.call(CodeRearrangerPanel::class.java) { ApplicationManager.getApplication().invokeLater { syncCurrentFileInternal(virtualFile, syncCaretTarget) } }
        }
    }

    private fun syncCurrentFileInternal(virtualFile: VirtualFile?, syncCaretTarget: Boolean) {
        if (!toolWindow.isVisible) {
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
        // 是否使用光标下的类
        if (syncCaretTarget) {
            val caretOffset = editor.caretModel.offset
            val elementAt = file.findElementAt(caretOffset)
            targetClass = findParentClass(elementAt)
        }
        if (targetClass == null) {
            for (child in file.children) {
                if (child is PsiClass) {
                    targetClass = child
                    break
                }
            }
        }
        if (targetClass != null) {
            collectCodeInfo(targetClass, editor)
        }
        println("start sync code $targetClass")
        updateDataAndSyncSelection()
    }

    private fun findParentClass(elementAt: PsiElement?): PsiClass? {
        if (elementAt == null) {
            return null
        }
        return if (elementAt is PsiClass) {
            elementAt
        } else findParentClass(elementAt.parent)
    }

    private val curDocument: Document?
        get() {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
            return editor.document
        }

    private fun collectCodeInfo(psiClass: PsiClass?,
                                editor: Editor) {
        // 先清空
        codeInfos.clear()
        // 再收集
        psiClass?.apply {
            var childEle = firstChild
            do {
                if (childEle == null) {
                    continue
                }
                when (childEle) {
                    is PsiField -> {
                        codeInfos.add(CodeInfo(
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.FIELD,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiMethod -> {
                        codeInfos.add(CodeInfo(
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.METHOD,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiClass -> {
                        codeInfos.add(CodeInfo(
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.CLASS,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiClassInitializer -> {
                        codeInfos.add(CodeInfo(
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.STATIC_INITIALIZER,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiComment -> {
                        codeInfos.add(CodeInfo(
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.SECTION,
                                childEle.getTextRange()
                        ))
                    }
                }

//            System.out.println(">>  " + childEle.toString());
                childEle = childEle.nextSibling
            } while (childEle != null)

        }
    }

    /**
     * 支持更多语言
     *
     * @param ele
     * @return
     */
    private fun createPsiTreeElementFromPsiMember(ele: PsiElement): StructureViewTreeElement {
        if (ele is PsiField) {
            return PsiFieldTreeElement(ele, false)
        }
        if (ele is PsiMethod) {
            return PsiMethodTreeElement(ele, false)
        }
        if (ele is PsiClass) {
            return JavaClassTreeElement(ele, false, object : HashSet<PsiClass?>() {
                init {
                    add(ele)
                }
            })
        }
        if (ele is PsiClassInitializer) {
            return ClassInitializerTreeElement(ele)
        }
        return if (ele is PsiComment) {
            object : PsiTreeElementBase<PsiComment?>(ele) {
                override fun getPresentableText(): String? {
                    return ele.getText()
                }

                override fun getChildrenBase(): Collection<StructureViewTreeElement> {
                    return emptyList()
                }
            }
        } else PsiFieldTreeElement(null, false)
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

    ///////////////////////////////////inner class///////////////////////////////////
    private inner class CodeInfo(var element: StructureViewTreeElement, var type: CodeType, var textRange: TextRange) {
        val lineNumber: Pair<Int, Int>
            get() {
                val curDocument = curDocument
                        ?: return Pair(0, 0)
                return Pair(
                        curDocument.getLineNumber(textRange.startOffset),
                        curDocument.getLineNumber(textRange.endOffset)
                )
            }

        fun printLineRange(): String {
            val pair = lineNumber
            return if (pair.first == pair.second) {
                pair.first.toString()
            } else pair.first.toString() + "~" + pair.second
        }

        fun printTypeName(): String {
            return type.toString()
        }

        fun printTitle(): String {
            // 额外收集line comment
            val title = (element.value as PsiElement).children.filter {
                it is PsiComment
                        && it.tokenType == JavaTokenType.END_OF_LINE_COMMENT
                        && it.text.startsWith("////////")
            }.joinToString("<br>") { it.text }
            return "<html>${if (title.isNotEmpty()) "$title<br>" else ""}${element.presentation.presentableText}</html>"
        }
    }

    private enum class CodeType {
        METHOD, FIELD, CLASS, STATIC_INITIALIZER, SECTION
    }

    companion object {
        const val SECTION_PLACEHOLDER = "//////////////////////////////////###////////////////////////////////////"
    }

    init {
        refreshBtn?.addActionListener { e: ActionEvent? -> syncCurrentFile(null, true) }
        upBtn?.addActionListener { e: ActionEvent? -> moveEleUp() }
        downBtn?.addActionListener { e: ActionEvent? -> moveEleDown() }
        addSection?.addActionListener { e: ActionEvent? -> handleAddSection() }
        toolWindow.activate({ syncCurrentFile(null, true) }, true)
        initTable()
        initData()
    }
}