package com.legendmohe.coderearranger.resolver

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.collectDescendantsOfType
import org.jetbrains.kotlin.lexer.KtTokens

abstract class BaseCodeInfo(var curDocument: Document?,
                            var element: StructureViewTreeElement,
                            private var type: CodeType,
                            private var textRange: TextRange): ICodeInfo{

    private val lineNumber: Pair<Int, Int>
        get() {
            val curDocument = curDocument ?: return Pair(0, 0)
            return Pair(
                    curDocument.getLineNumber(textRange.startOffset),
                    curDocument.getLineNumber(textRange.endOffset)
            )
        }

    override fun printLineRange(): String {
        val pair = lineNumber
        return if (pair.first == pair.second) {
            pair.first.toString()
        } else pair.first.toString() + "~" + pair.second
    }

    override fun printTypeName(): String {
        return type.toString()
    }

    override fun printTitle(): String {
        // 额外收集line comment
        val title = (element.value as PsiElement?)?.collectDescendantsOfType<PsiComment> {
            it.tokenType == KtTokens.EOL_COMMENT && it.text.startsWith("////////")
        }?.filter { element.value !is PsiComment }?.joinToString("<br>") { it.text }
        return "<html>${if (!title.isNullOrEmpty()) "$title<br>" else ""}${element.presentation.presentableText}</html>"
    }

    override fun getViewTreeElement(): StructureViewTreeElement {
        return element
    }

    abstract fun getCommentTokenType(): IElementType
}