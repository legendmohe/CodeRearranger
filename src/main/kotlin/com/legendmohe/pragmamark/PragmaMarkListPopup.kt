package com.legendmohe.pragmamark

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.util.*
import javax.swing.ListSelectionModel

/*
* Copyright 2000-2017 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/ /**
 * @author Rustam Vishnyakov
 */
class PragmaMarkListPopup internal constructor(descriptors: Collection<PragmaMarkData>,
                                               private val myEditor: Editor,
                                               project: Project) {
    private val myPopup: JBPopup
    fun show() {
        myPopup.showInBestPositionFor(myEditor)
    }

    companion object {
        private fun navigateTo(editor: Editor, element: PragmaMarkData) {
            val lineNum = element.lineNum
            val offset = editor.document.getLineStartOffset(lineNum)
            if (offset >= 0 && offset < editor.document.textLength) {
                editor.caretModel.removeSecondaryCarets()
                editor.caretModel.moveToOffset(offset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                editor.selectionModel.removeSelection()
            }
        }
    }

    init {
        val popupBuilder = JBPopupFactory.getInstance().createPopupChooserBuilder(ArrayList(descriptors))
        myPopup = popupBuilder
                .setTitle("Goto Pragma Marks")
                .setResizable(false)
                .setMovable(false)
                .setNamerForFiltering { pragmaMarkData: PragmaMarkData -> pragmaMarkData.title }
                .setCloseOnEnter(true)
                .setAutoSelectIfEmpty(false)
                .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                .setItemChosenCallback { pragmaMarkData: PragmaMarkData? ->
                    if (pragmaMarkData != null) {
                        navigateTo(myEditor, pragmaMarkData)
                        IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation()
                    }
                }
                .createPopup()
    }
}