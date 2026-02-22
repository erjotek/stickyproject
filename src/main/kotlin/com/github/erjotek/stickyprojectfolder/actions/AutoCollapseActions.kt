package com.github.erjotek.stickyprojectfolder.actions

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

private fun getRelativePath(event: AnActionEvent): String? {
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

private fun getCurrentPaths(): List<String> {
    val raw = StickyProjectSettings.instance.state.autoCollapsePaths
    return raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun savePaths(paths: List<String>) {
    StickyProjectSettings.instance.state.autoCollapsePaths = paths.joinToString(";")
}

class AddToAutoCollapseAction : AnAction("Add to Auto-Collapse"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val relative = getRelativePath(e)
        // Sentinel: Prevent adding paths containing semicolons as they corrupt the settings format
        if (relative == null || relative.contains(";")) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val alreadyIn = getCurrentPaths().any { it.trimEnd('/') == relative.trimEnd('/') }
        e.presentation.isEnabledAndVisible = !alreadyIn
    }

    override fun actionPerformed(e: AnActionEvent) {
        val relative = getRelativePath(e) ?: return
        val paths = getCurrentPaths().toMutableList()
        val normalized = if (relative.endsWith("/")) relative else "$relative/"
        if (paths.none { it.trimEnd('/') == normalized.trimEnd('/') }) {
            paths.add(normalized)
            savePaths(paths.sorted())
        }
    }
}

class RemoveFromAutoCollapseAction : AnAction("Remove from Auto-Collapse"), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val relative = getRelativePath(e)
        if (relative == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val alreadyIn = getCurrentPaths().any { it.trimEnd('/') == relative.trimEnd('/') }
        e.presentation.isEnabledAndVisible = alreadyIn
    }

    override fun actionPerformed(e: AnActionEvent) {
        val relative = getRelativePath(e) ?: return
        val paths = getCurrentPaths().toMutableList()
        paths.removeAll { it.trimEnd('/') == relative.trimEnd('/') }
        savePaths(paths.sorted())
    }
}
