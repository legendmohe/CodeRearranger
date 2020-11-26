package com.legendmohe.coderearranger.resolver

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.structureView.impl.java.ClassInitializerTreeElement
import com.intellij.ide.structureView.impl.java.JavaClassTreeElement
import com.intellij.ide.structureView.impl.java.PsiFieldTreeElement
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.collectDescendantsOfType
import com.legendmohe.coderearranger.CodeRearrangerPanel
import org.jetbrains.kotlin.lexer.KtTokens
import java.util.*
import kotlin.collections.ArrayList

class JavaResolver : ILanguageResolver {
    private var curParent: PsiElement? = null

    override fun collectCodeInfo(project: Project, parentEle: PsiElement?, findChild: Boolean): List<ICodeInfo> {
        println("start sync code $curParent")

        var targetEle = parentEle
        if (targetEle == null && findChild) {
            targetEle = getCurDocument(project)?.let {
                return@let PsiDocumentManager.getInstance(project).getPsiFile(it)?.children?.first { child ->
                    child is PsiClass
                }
            }
        }
        // 再收集
        if (targetEle != null) {
            curParent = targetEle
        }
        val result = ArrayList<ICodeInfo>()
        curParent?.apply {
            var childEle = firstChild
            do {
                if (childEle == null) {
                    continue
                }
                when (childEle) {
                    is PsiField -> {
                        result.add(JavaCodeInfo(
                                getCurDocument(project),
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.FIELD,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiMethod -> {
                        result.add(JavaCodeInfo(
                                getCurDocument(project),
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.METHOD,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiClass -> {
                        result.add(JavaCodeInfo(
                                getCurDocument(project),
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.CLASS,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiClassInitializer -> {
                        result.add(JavaCodeInfo(
                                getCurDocument(project),
                                createPsiTreeElementFromPsiMember(childEle),
                                CodeType.STATIC_INITIALIZER,
                                childEle.getTextRange()
                        ))
                    }
                    is PsiComment -> {
                        result.add(JavaCodeInfo(
                                getCurDocument(project),
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
        return result
    }

    override fun findParentClass(parentEle: PsiElement?): PsiElement? {
        if (parentEle == null) {
            return null
        }
        return if (parentEle is PsiClass) {
            parentEle
        } else findParentClass(parentEle.parent)
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

    private fun getCurDocument(project: Project): Document? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        return editor.document
    }

    override fun getCommentFromText(project: Project, text: String): PsiElement {
        val parserFacade = PsiParserFacade.SERVICE.getInstance(project)
        return parserFacade.createLineCommentFromText(JavaLanguage.INSTANCE, CodeRearrangerPanel.SECTION_PLACEHOLDER)
    }
}


private class JavaCodeInfo(
        curDocument: Document?,
        element: StructureViewTreeElement,
        type: CodeType,
        textRange: TextRange
) : BaseCodeInfo(curDocument, element, type, textRange) {

    override fun getCommentTokenType(): IElementType {
        return JavaTokenType.END_OF_LINE_COMMENT
    }
}
