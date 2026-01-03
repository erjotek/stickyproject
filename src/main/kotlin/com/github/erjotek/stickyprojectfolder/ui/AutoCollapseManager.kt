package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFileSystemItem
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JTree
import javax.swing.Timer
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

private val LOG = Logger.getInstance(AutoCollapseManager::class.java)

class AutoCollapseManager(
    private val project: Project,
    private val tree: JTree
) : Disposable {

    private var debounceTimer: Timer? = null
    private var treeSelectionListener: TreeSelectionListener? = null
    private var focusListener: FocusAdapter? = null
    private var toolWindowListener: ToolWindowManagerListener? = null

    companion object {
        private const val DEBOUNCE_DELAY_MS = 400
    }

    fun install() {
        treeSelectionListener = TreeSelectionListener { e ->
            onSelectionChanged(e)
        }
        tree.addTreeSelectionListener(treeSelectionListener)

        focusListener = object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
                scheduleCollapseCheck()
            }
        }
        tree.addFocusListener(focusListener)

        toolWindowListener = object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                val toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)
                if (toolWindow != null && !toolWindow.isActive) {
                    scheduleCollapseCheck()
                }
            }
        }
        project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, toolWindowListener!!)

        LOG.info("AutoCollapseManager installed")
    }

    private fun onSelectionChanged(e: TreeSelectionEvent) {
        scheduleCollapseCheck()
    }

    private fun scheduleCollapseCheck() {
        debounceTimer?.stop()
        debounceTimer = Timer(DEBOUNCE_DELAY_MS) {
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) {
                    performCollapseCheck()
                }
            }, project.disposed)
        }
        debounceTimer?.isRepeats = false
        debounceTimer?.start()
    }

    private fun performCollapseCheck() {
        val settings = StickyProjectSettings.instance
        if (!settings.state.autoCollapseEnabled) return

        val pathsConfig = settings.state.autoCollapsePaths
        if (pathsConfig.isBlank()) return

        val pathsToCollapse = pathsConfig.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.trimEnd('/') }

        if (pathsToCollapse.isEmpty()) return

        val selectedPath = tree.selectionPath
        val selectedVirtualFile = getVirtualFileFromPath(selectedPath)
        val selectedFilePath = selectedVirtualFile?.path
        val basePath = project.basePath ?: return

        for (relativePath in pathsToCollapse) {
            val absolutePath = "$basePath/$relativePath"
            
            val isSelectedInsideThisPath = selectedFilePath != null && 
                (selectedFilePath.startsWith("$absolutePath/") || selectedFilePath == absolutePath)

            if (!isSelectedInsideThisPath) {
                collapsePathInTree(relativePath, absolutePath)
            }
        }
    }

    private fun collapsePathInTree(relativePath: String, absolutePath: String) {
        val root = tree.model.root ?: return
        val treePath = findTreePathForDirectory(root, absolutePath, TreePath(root))
        
        if (treePath != null && tree.isExpanded(treePath)) {
            LOG.info("Collapsing: $relativePath")
            tree.collapsePath(treePath)
        }
    }

    private fun findTreePathForDirectory(node: Any, targetPath: String, currentPath: TreePath): TreePath? {
        val virtualFile = getVirtualFileFromNode(node)
        
        if (virtualFile != null && virtualFile.path == targetPath) {
            return currentPath
        }

        if (node is DefaultMutableTreeNode) {
            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i)
                val childPath = currentPath.pathByAddingChild(child)
                val result = findTreePathForDirectory(child, targetPath, childPath)
                if (result != null) return result
            }
        }

        return null
    }

    private fun getVirtualFileFromPath(path: TreePath?): VirtualFile? {
        if (path == null) return null
        return getVirtualFileFromNode(path.lastPathComponent)
    }

    private fun getVirtualFileFromNode(node: Any?): VirtualFile? {
        if (node == null) return null

        if (node is DefaultMutableTreeNode) {
            val userObject = node.userObject
            if (userObject is AbstractTreeNode<*>) {
                return when (val value = userObject.value) {
                    is PsiDirectory -> value.virtualFile
                    is PsiDirectoryContainer -> value.directories.firstOrNull()?.virtualFile
                    is PsiFileSystemItem -> value.virtualFile
                    is VirtualFile -> value
                    else -> null
                }
            }
        }
        return null
    }

    override fun dispose() {
        debounceTimer?.stop()
        debounceTimer = null

        treeSelectionListener?.let { tree.removeTreeSelectionListener(it) }
        treeSelectionListener = null

        focusListener?.let { tree.removeFocusListener(it) }
        focusListener = null

        LOG.info("AutoCollapseManager disposed")
    }
}
