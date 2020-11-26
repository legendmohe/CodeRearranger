package com.legendmohe.coderearranger.resolver

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.tree.IElementType
import com.legendmohe.coderearranger.CodeRearrangerPanel
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.structureView.KotlinStructureViewElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import kotlin.reflect.KClass

class KotlinResolver : ILanguageResolver {
    private var curParent: PsiElement? = null

    override fun collectCodeInfo(project: Project, parentEle: PsiElement?, findChild: Boolean): List<ICodeInfo> {
        println("start sync code $curParent")

        var targetEle = parentEle
        if (targetEle == null && findChild) {
            targetEle = getCurDocument(project)?.let {
                return@let PsiDocumentManager.getInstance(project).getPsiFile(it)
            }
        }
        // 再收集
        if (targetEle != null) {
            curParent = if (targetEle !is KtFile) {
                findClassBody(targetEle)
            } else {
                targetEle
            }
        }
        val result = ArrayList<KotlinCodeInfo>()
        curParent?.apply {
            var childEle = firstChild
            do {
                if (childEle == null) {
                    continue
                }
                when (childEle) {
                    is KtProperty -> {
                        createPsiTreeElementFromPsiMember(childEle)?.let {
                            result.add(KotlinCodeInfo(
                                    project,
                                    it,
                                    CodeType.FIELD
                            ))
                        }
                    }
                    is KtNamedFunction -> {
                        createPsiTreeElementFromPsiMember(childEle)?.let {
                            result.add(KotlinCodeInfo(
                                    project,
                                    it,
                                    CodeType.METHOD
                            ))
                        }
                    }
                    is KtClass -> {
                        createPsiTreeElementFromPsiMember(childEle)?.let {
                            result.add(KotlinCodeInfo(
                                    project,
                                    it,
                                    CodeType.CLASS
                            ))
                        }
                    }
                    is PsiComment -> {
                        createPsiTreeElementFromPsiMember(childEle)?.let {
                            result.add(KotlinCodeInfo(
                                    project,
                                    it,
                                    CodeType.SECTION
                            ))
                        }
                    }
                }

                println(">>  $childEle")
                childEle = childEle.nextSibling
            } while (childEle != null)
        }
        return result
    }

    override fun findParentClass(parentEle: PsiElement?): PsiElement? {
        if (parentEle == null) {
            return null
        }
        return if (parentEle is KtClassOrObject || parentEle is KtFile) {
            parentEle
        } else findParentClass(parentEle.parent)
    }

    private fun findClassBody(parentEle: PsiElement?): KtClassBody? {
        return matchPsiElement(parentEle, KtClassBody::class)
    }

    private fun <T : PsiElement> matchPsiElement(parentEle: PsiElement?, clz: KClass<T>): T? {
        if (parentEle == null) {
            return null
        }
        return if (clz.isInstance(parentEle)) {
            parentEle as T
        } else {
            parentEle.children.mapNotNull {
                matchPsiElement(it, clz)
            }.firstOrNull() // 这里会不会全部计算?
        }
    }

    /**
     * 支持更多语言
     *
     * @param ele
     * @return
     */
    private fun createPsiTreeElementFromPsiMember(ele: PsiElement): StructureViewTreeElement? {
        if (ele is KtProperty) {
            return KotlinStructureViewElement(ele, false)
        }
        if (ele is KtNamedFunction) {
            return KotlinStructureViewElement(ele, false)
        }
        if (ele is KtClass) {
            return KotlinStructureViewElement(ele, false)
        }
        if (ele is PsiComment) {
            return object : PsiTreeElementBase<PsiComment?>(ele) {
                override fun getPresentableText(): String? {
                    return ele.getText()
                }

                override fun getChildrenBase(): Collection<StructureViewTreeElement> {
                    return emptyList()
                }
            }
        }
        return null
    }

    private fun getCurDocument(project: Project): Document? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        return editor.document
    }

    override fun getCommentFromText(project: Project, text: String): PsiElement {
        val parserFacade = PsiParserFacade.SERVICE.getInstance(project)
        return parserFacade.createLineCommentFromText(KotlinLanguage.INSTANCE, CodeRearrangerPanel.SECTION_PLACEHOLDER)
    }
}


private class KotlinCodeInfo(
        project: Project,
        element: StructureViewTreeElement,
        type: CodeType
) : BaseCodeInfo(project, element, type) {

    override fun getCommentTokenType(): IElementType {
        return KtTokens.EOL_COMMENT
    }

}

