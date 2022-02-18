package com.legendmohe.coderearranger.resolver

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType

abstract class BaseCodeInfo(
    var project: Project,
    var element: StructureViewTreeElement,
    private var type: CodeType
) : ICodeInfo {

    private val lineNumber: Pair<Int, Int>
        get() {
            val curDocument = getCurDocument(project) ?: return Pair(0, 0)
            val textRange = (element.value as PsiElement?)?.textRange ?: return Pair(0, 0)
            if (!(textRange.startOffset in (0..curDocument.textLength) &&
                        textRange.endOffset in (0..curDocument.textLength))
            ) {
                return Pair(0, 0)
            }
            return Pair(
                curDocument.getLineNumber(textRange.startOffset),
                curDocument.getLineNumber(textRange.endOffset)
            )
        }

    override fun printLineRange(): String {
        val pair = lineNumber
        return if (pair.first == pair.second) {
            pair.first.toString()
        } else pair.first.inc().toString() + "~" + pair.second.inc()
    }

    override fun printTypeName(): Any {
//        return if (element is KotlinStructureViewElement) {
//            when (val icon = (element as KotlinStructureViewElement).getIcon(false)) {
//                is IconWrapperWithToolTip -> icon.retrieveIcon()
//                is IconLoader.CachedImageIcon -> icon.realIcon
//                else -> type.toString()
//            }
//        } else {
//            type.toString()
//        }
        return type.toString()
    }

    override fun printTitle(): String {
        if (type == CodeType.FIELD) {
            return "<html>&lt; ${element.presentation.presentableText} &gt;</html>"
        } else if (type == CodeType.SECTION) {
            return "<html><b>${element.presentation.presentableText}</b></html>"
        } else {
            return element.presentation.presentableText ?: ""
        }
        // 不用额外收集了！
//        // 额外收集line comment
//        val title = (element.value as PsiElement?)?.collectDescendantsOfType<PsiComment> {
//            it.tokenType == KtTokens.EOL_COMMENT && it.text.startsWith("////////")
//        }?.filter {
//            element.value !is PsiComment
//        }?.joinToString("<br>") {
//            it.text
//        }
//        return "<html>${if (!title.isNullOrEmpty()) "$title<br>" else ""}${element.presentation.presentableText}</html>"
    }

    override fun getViewTreeElement(): StructureViewTreeElement {
        return element
    }

    private fun getCurDocument(project: Project): Document? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        return editor.document
    }

    abstract fun getCommentTokenType(): IElementType
}


fun isSessionComment(ele: PsiComment) = ele.text.startsWith("////////")