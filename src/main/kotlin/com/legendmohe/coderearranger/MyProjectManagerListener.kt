package com.legendmohe.coderearranger

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

internal class MyProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.service<MyProjectService>().onProjectOpened()
    }

    override fun projectClosed(project: Project) {
        project.service<MyProjectService>().onProjectClosed()
    }
}
