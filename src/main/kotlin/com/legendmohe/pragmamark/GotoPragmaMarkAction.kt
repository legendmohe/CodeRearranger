package com.legendmohe.pragmamark

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.psi.PsiClass
import java.lang.Boolean
import java.util.*

class GotoPragmaMarkAction : AnAction(), DumbAware, PopupAction {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (Boolean.TRUE == e.getData(PlatformDataKeys.IS_MODAL_CONTEXT)) {
            return
        }
        if (project != null && editor != null) {
            if (DumbService.getInstance(project).isDumb) {
                DumbService.getInstance(project).showDumbModeNotification("line desc navigation is not available until indices are built")
                return
            }
            val processor = CommandProcessor.getInstance()
            processor.executeCommand(
                    project,
                    { process(e, project, editor) },
                    "goto pragma marks",
                    null)
        }
    }

    private fun process(e: AnActionEvent, project: Project, editor: Editor) {
//        val data = e.getData(LangDataKeys.PSI_FILE) ?: return
//        for (child in data.children) {
//            if (child is PsiClass) {
//                printPsiClass(child, editor)
//            }
//            println(child.toString())
//        }
        val pragmaMarks = getCustomFoldingDescriptors(editor, project)
        if (pragmaMarks.isNotEmpty()) {
            val regionsPopup = PragmaMarkListPopup(pragmaMarks, editor, project)
            regionsPopup.show()
        } else {
            notifyCustomRegionsUnavailable(editor, project)
        }
    }
//
//    private fun printPsiClass(psiClass: PsiClass, editor: Editor) {
//        val allMethods = psiClass.methods
//        val allFields = psiClass.fields
//        val allInnerClasses = psiClass.innerClasses
//        println("allMethods=" + Arrays.toString(allMethods))
//        println("allFields=" + Arrays.toString(allFields))
//        println("allInnerClasses=" + Arrays.toString(allInnerClasses))
//        for (allMethod in allMethods) {
//            val textRange = allMethod.textRange
//            val lineContent = editor.document.getText(textRange).trim { it <= ' ' }
//            println(">>  $lineContent")
//        }
//    }

    companion object {
        private const val PRAGMA_MARK_PREFIX = "////////"

        ///////////////////////////////////function///////////////////////////////////
        private fun getCustomFoldingDescriptors(editor: Editor, project: Project): Collection<PragmaMarkData> {
            val descDataList: MutableList<PragmaMarkData> = ArrayList()
            val document = editor.document
            for (curLine in 0 until document.lineCount) {
                val textRange: TextRange = UnfairTextRange(
                        document.getLineStartOffset(curLine),
                        document.getLineEndOffset(curLine)
                )
                val lineContent = document.getText(textRange).trim { it <= ' ' }
                if (lineContent.startsWith(PRAGMA_MARK_PREFIX)) {
                    val data = createPragmaMarkData(lineContent, curLine)
                    if (data != null) {
                        descDataList.add(data)
                    }
                }
            }
            return descDataList
        }

        ///////////////////////////////////public///////////////////////////////////
        private fun createPragmaMarkData(lineContent: String, curLine: Int): PragmaMarkData? {
            // 将/替换成空格再trim即可实现提取中间部分，但要注意中间部分里面的/也会被替换掉
            val title = lineContent.replace('/', ' ').trim { it <= ' ' }
            if (title.isNotEmpty()) {
                val newData = PragmaMarkData()
                newData.title = title
                newData.lineNum = curLine
                return newData
            }
            return null
        }

        private fun notifyCustomRegionsUnavailable(editor: Editor, project: Project) {
            val popupFactory = JBPopupFactory.getInstance()
            val balloon = popupFactory
                    .createHtmlTextBalloonBuilder("There are no line desc in the current file.", MessageType.INFO, null)
                    .setFadeoutTime(2000)
                    .setHideOnClickOutside(true)
                    .setHideOnKeyOutside(true)
                    .createBalloon()
            Disposer.register(project, balloon)
            balloon.show(popupFactory.guessBestPopupLocation(editor), Balloon.Position.below)
        }
    }
}