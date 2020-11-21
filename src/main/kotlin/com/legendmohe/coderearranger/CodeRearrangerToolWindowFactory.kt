package com.legendmohe.coderearranger

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.legendmohe.coderearranger.services.MyProjectService
import javax.swing.JComponent

class CodeRearrangerToolWindowFactory : ToolWindowFactory {

    private var content: Content? = null
    private var mainUIForm: CodeRearrangerPanel? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.SERVICE.getInstance()
        content?.let {
            toolWindow.contentManager.removeContent(it, true)
        }
        content = contentFactory.createContent(
                createCodeRearrangerUIComponent(project, toolWindow),
                "",
                true)
                .apply {
                    toolWindow.contentManager.addContent(this)
                }
        initComponent(project)
    }

    ///////////////////////////////////data///////////////////////////////////

    private fun initComponent(project: Project) {
        project.service<MyProjectService>().setListener(object : MyProjectService.Listener {
            override fun onCodeUpdate(file: VirtualFile?) {
                ApplicationManager.getApplication().invokeLater {
                    mainUIForm?.syncCurrentFile(file, true)
                }
            }
        })
    }

    //////////////////////////////////////////////////////////////////////

    private fun createCodeRearrangerUIComponent(project: Project, toolWindow: ToolWindow): JComponent? {
        mainUIForm = CodeRearrangerPanel(project, toolWindow)
        return mainUIForm?.mainPanel
    }

    init {
        ApplicationManager.getApplication().apply {
            messageBus.connect(this).subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
                override fun appWillBeClosed(isRestart: Boolean) {}
            })
        }
    }
}