package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFileSystemItem
import java.lang.reflect.Method
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.WeakHashMap
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
    private val virtualFileMethodCacheLock = Any()
    private val virtualFileMethodCache = WeakHashMap<Class<*>, Method?>()

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

        val basePath = project.basePath ?: return
        val pathsConfig = settings.state.autoCollapsePaths
        val pathsToCollapse = mutableListOf<String>()
        if (pathsConfig.isNotBlank()) {
            pathsToCollapse += pathsConfig.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        if (settings.state.autoCollapseIncludeExcluded) {
            pathsToCollapse += getExcludedPaths(basePath)
        }

        val normalizedPathsToCollapse = pathsToCollapse
            .map { it.trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (normalizedPathsToCollapse.isEmpty()) return

        val selectedPath = tree.selectionPath
        val selectedVirtualFile = getVirtualFileFromPath(selectedPath)
        val selectedFilePath = selectedVirtualFile?.path

        for (relativePath in normalizedPathsToCollapse) {
            val absolutePath = "$basePath/$relativePath"
            
            val isSelectedInsideThisPath = selectedFilePath != null && 
                (selectedFilePath.startsWith("$absolutePath/") || selectedFilePath == absolutePath)

            if (!isSelectedInsideThisPath) {
                collapsePathInTree(relativePath, absolutePath)
            }
        }
    }

    private fun getExcludedPaths(basePath: String): List<String> {
        val excludedRoots = ModuleManager.getInstance(project).modules
            .flatMap { module ->
                ModuleRootManager.getInstance(module).contentEntries
                    .flatMap { entry -> entry.excludeFolderFiles.toList() }
            }
        return excludedRoots
            .mapNotNull { root ->
                val path = root.path
                if (!path.startsWith(basePath)) {
                    null
                } else {
                    path.removePrefix(basePath).removePrefix("/")
                }
            }
            .filter { it.isNotBlank() }
    }

    private fun collapsePathInTree(relativePath: String, absolutePath: String) {
        val root = tree.model.root ?: return
        val rootPath = TreePath(root)
        val treePath = findExpandedTreePathForDirectory(absolutePath, rootPath)

        if (treePath != null && tree.isExpanded(treePath)) {
            LOG.info("Collapsing: $relativePath")
            tree.collapsePath(treePath)
        }
    }

    private fun findExpandedTreePathForDirectory(targetPath: String, rootPath: TreePath): TreePath? {
        getVirtualFileFromPath(rootPath)?.let { vf ->
            if (vf.path == targetPath) return rootPath
        }

        val expanded = tree.getExpandedDescendants(rootPath) ?: return null
        while (expanded.hasMoreElements()) {
            val path = expanded.nextElement()
            val vf = getVirtualFileFromPath(path) ?: continue
            if (vf.path == targetPath) return path
        }

        return null
    }

    private fun getVirtualFileFromPath(path: TreePath?): VirtualFile? {
        if (path == null) return null
        return getVirtualFileFromNode(path.lastPathComponent)
    }

    private fun getVirtualFileFromNode(node: Any?): VirtualFile? {
        if (node == null) return null

        val candidate: Any? = when (node) {
            is DefaultMutableTreeNode -> node.userObject
            else -> node
        }

        if (candidate is ProjectViewNode<*>) {
            return candidate.virtualFile
        }

        if (candidate is AbstractTreeNode<*>) {
            val direct = when (val value = candidate.value) {
                is PsiDirectory -> value.virtualFile
                is PsiDirectoryContainer -> value.directories.firstOrNull()?.virtualFile
                is PsiFileSystemItem -> value.virtualFile
                is VirtualFile -> value
                else -> null
            }
            if (direct != null) return direct

            resolveVirtualFileByReflection(candidate.value)?.let { return it }
            resolveVirtualFileByReflection(candidate)?.let { return it }
            return null
        }

        resolveVirtualFileByReflection(candidate)?.let { return it }
        return null
    }

    private fun resolveVirtualFileByReflection(target: Any?): VirtualFile? {
        if (target == null) return null
        val clazz = target.javaClass

        val method = synchronized(virtualFileMethodCacheLock) {
            if (virtualFileMethodCache.containsKey(clazz)) {
                virtualFileMethodCache[clazz]
            } else {
                val resolved = clazz.methods.firstOrNull { m ->
                    (m.name == "getVirtualFile" || m.name == "virtualFile") &&
                        m.parameterCount == 0 &&
                        VirtualFile::class.java.isAssignableFrom(m.returnType)
                }
                virtualFileMethodCache[clazz] = resolved
                resolved
            }
        }

        return runCatching { method?.invoke(target) as? VirtualFile }.getOrNull()
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
