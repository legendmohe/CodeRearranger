package com.legendmohe.coderearranger.resolver

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface ILanguageResolver {
    fun collectCodeInfo(project: Project, parentEle: PsiElement?, findChild: Boolean): List<ICodeInfo>
    fun findParentClass(parentEle: PsiElement?): PsiElement?
    fun getCommentFromText(project: Project, text: String): PsiElement
}

enum class CodeType {
    METHOD, FIELD, CLASS, STATIC_INITIALIZER, SECTION
}

interface ICodeInfo {
    fun printLineRange(): String
    fun printTypeName(): Any
    fun printTitle(): String
    fun getViewTreeElement(): StructureViewTreeElement
}