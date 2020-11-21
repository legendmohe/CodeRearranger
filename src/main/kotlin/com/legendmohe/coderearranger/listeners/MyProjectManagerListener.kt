package com.legendmohe.coderearranger.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.legendmohe.coderearranger.services.MyProjectService

internal class MyProjectManagerListener : ProjectManagerListener {

    override fun projectOpened(project: Project) {
        project.service<MyProjectService>().onProjectOpened()
    }

    override fun projectClosed(project: Project) {
        project.service<MyProjectService>().onProjectClosed()
    }
}
