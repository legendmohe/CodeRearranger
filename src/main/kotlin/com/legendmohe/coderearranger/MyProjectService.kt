package com.legendmohe.coderearranger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection

class MyProjectService(project: Project) : BulkFileListener {

    private var connection: MessageBusConnection = project.messageBus.connect()

    private var listener: Listener? = null

    fun onProjectOpened() {
        connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    fun onProjectClosed() {
        connection.disconnect()
    }

    override fun after(events: List<VFileEvent>) {
        println("CodeRearrangerComponent after events=$events")
        for (event in events) {
            if (event is VFileContentChangeEvent) {
                if (event.oldModificationStamp != event.modificationStamp) {
                    listener?.onCodeUpdate(event.file)
                }
            }
        }
    }

    ///////////////////////////////////public///////////////////////////////////

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    //////////////////////////////////////////////////////////////////////

    interface Listener {
        fun onCodeUpdate(file: VirtualFile?)
    }
}
