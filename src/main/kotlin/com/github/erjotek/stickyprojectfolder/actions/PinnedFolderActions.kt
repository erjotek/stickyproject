package com.github.erjotek.stickyprojectfolder.actions

import com.github.erjotek.stickyprojectfolder.settings.PinnedFolderItem
import com.github.erjotek.stickyprojectfolder.settings.PinnedFoldersSettings
import com.github.erjotek.stickyprojectfolder.util.PathValidator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

private fun getPinnedRelativePath(event: AnActionEvent): String? {
    val project = event.project ?: return null
    val basePath = project.basePath ?: return null
    val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
    if (!virtualFile.isDirectory) return null
    val filePath = virtualFile.path
    if (!filePath.startsWith(basePath)) return null
    var relative = filePath.removePrefix(basePath).removePrefix("/")
    if (relative.isNotEmpty() && !relative.endsWith("/")) relative += "/"
    return relative.takeIf { it.isNotEmpty() }
}

class PinFolderAction : AnAction("Set as Pinned"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val relative = getPinnedRelativePath(e)
        if (relative == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val project = e.project ?: return
        val settings = PinnedFoldersSettings.getInstance(project)
        val alreadyIn = settings.state.pinnedFolders.any { it.path.trimEnd('/') == relative.trimEnd('/') }
        e.presentation.isEnabledAndVisible = !alreadyIn
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val relative = getPinnedRelativePath(e) ?: return
        // Use validator only for safety check (path traversal, forbidden chars, etc.)
        // but store the relative path, not the absolute one returned by validatePath()
        PathValidator.validatePath(basePath, relative) ?: return
        val settings = PinnedFoldersSettings.getInstance(project)
        
        if (settings.state.pinnedFolders.none { it.path.trimEnd('/') == relative.trimEnd('/') }) {
            val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
            val description = file?.name ?: "Folder"
            settings.state.pinnedFolders.add(0, PinnedFolderItem(relative, description))
            com.intellij.ide.projectView.ProjectView.getInstance(project).refresh()
        }
    }
}

class UnpinFolderAction : AnAction("Remove from Pinned"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val relative = getPinnedRelativePath(e)
        if (relative == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val project = e.project ?: return
        val settings = PinnedFoldersSettings.getInstance(project)
        val alreadyIn = settings.state.pinnedFolders.any { it.path.trimEnd('/') == relative.trimEnd('/') }
        e.presentation.isEnabledAndVisible = alreadyIn
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val relative = getPinnedRelativePath(e) ?: return
        val settings = PinnedFoldersSettings.getInstance(project)
        val item = settings.state.pinnedFolders.find { it.path.trimEnd('/') == relative.trimEnd('/') }
        if (item != null) {
            settings.state.pinnedFolders.remove(item)
            com.intellij.ide.projectView.ProjectView.getInstance(project).refresh()
        }
    }
}
