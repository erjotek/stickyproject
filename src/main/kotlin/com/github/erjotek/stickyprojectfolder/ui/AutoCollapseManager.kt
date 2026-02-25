package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectProjectSettings
import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.github.erjotek.stickyprojectfolder.util.PathValidator
import com.github.erjotek.stickyprojectfolder.util.StickyScrollUtil
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.WeakHashMap
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities
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
    private var allExcludedPathsCache: CachedValue<List<String>>? = null

    companion object {
        private const val DEBOUNCE_DELAY_MS = 400
        private val selectionAnchorCacheLock = Any()
        private val selectionAnchors = WeakHashMap<JTree, SelectionAnchor>()

        private data class SelectionAnchor(
            val filePath: String,
            val offsetYInViewport: Int,
            val viewportX: Int
        )

        @Volatile
        private var suppressUntilEpochMs: Long = 0

        fun suppressForNavigation(durationMs: Int) {
            if (durationMs <= 0) return
            val now = System.currentTimeMillis()
            val next = now + durationMs
            if (next > suppressUntilEpochMs) {
                suppressUntilEpochMs = next
            }
        }

        fun isNavigationSuppressed(): Boolean {
            return isSuppressed()
        }

        private fun isSuppressed(): Boolean {
            return System.currentTimeMillis() < suppressUntilEpochMs
        }
    }

    private fun recordSelectionAnchor() {
        val selectionPath = tree.selectionPath ?: return
        val selectedVirtualFile = getVirtualFileFromPath(selectionPath) ?: return

        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane ?: return
        val viewport = scrollPane.viewport ?: return

        val row = tree.getRowForPath(selectionPath)
        if (row == -1) return

        val rowBounds = tree.getRowBounds(row) ?: return
        val visibleRect = tree.visibleRect
        if (!visibleRect.intersects(rowBounds)) return

        val offsetY = rowBounds.y - viewport.viewPosition.y
        synchronized(selectionAnchorCacheLock) {
            selectionAnchors[tree] = SelectionAnchor(selectedVirtualFile.path, offsetY, viewport.viewPosition.x)
        }
    }

    private fun restoreViewportPosition(scrollPane: JScrollPane, requestedPosition: Point) {
        val viewport = scrollPane.viewport ?: return

        val extent = viewport.extentSize
        val viewSize = tree.preferredSize

        val maxX = (viewSize.width - extent.width).coerceAtLeast(0)
        val maxY = (viewSize.height - extent.height).coerceAtLeast(0)

        val clampedX = requestedPosition.x.coerceIn(0, maxX)
        val clampedY = requestedPosition.y.coerceIn(0, maxY)

        viewport.viewPosition = Point(clampedX, clampedY)
    }

    private fun restoreViewportPositionBySelectionAnchor(scrollPane: JScrollPane, anchor: SelectionAnchor) {
        val viewport = scrollPane.viewport ?: return
        val selectionPath = tree.selectionPath ?: return
        val selectedVirtualFile = getVirtualFileFromPath(selectionPath) ?: return
        if (selectedVirtualFile.path != anchor.filePath) return

        val row = tree.getRowForPath(selectionPath)
        if (row == -1) return

        val rowBounds = tree.getRowBounds(row) ?: return
        val requestedY = rowBounds.y - anchor.offsetYInViewport

        val extent = viewport.extentSize
        val viewSize = tree.preferredSize

        val maxX = (viewSize.width - extent.width).coerceAtLeast(0)
        val maxY = (viewSize.height - extent.height).coerceAtLeast(0)

        val clampedX = anchor.viewportX.coerceIn(0, maxX)
        val clampedY = requestedY.coerceIn(0, maxY)

        viewport.viewPosition = Point(clampedX, clampedY)
    }

    private fun ensureSelectionVisible() {
        val selectionPath = tree.selectionPath ?: return
        val row = tree.getRowForPath(selectionPath)
        if (row == -1) return

        val rowBounds = tree.getRowBounds(row) ?: return
        val visibleRect = tree.visibleRect
        if (visibleRect.intersects(rowBounds)) return

        val rowHeight = if (tree.rowHeight > 0) tree.rowHeight else com.intellij.util.ui.JBUI.scale(22)
        StickyScrollUtil.scrollToMakeVisibleBelowSticky(tree, selectionPath, rowHeight)
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
        recordSelectionAnchor()
        scheduleCollapseCheck()
    }

    private fun scheduleCollapseCheck() {
        if (isSuppressed()) return
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
        if (isSuppressed()) return
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
        if (StickyProjectProjectSettings.getInstance(project).state.autoCollapseIncludeExcluded) {
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

        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane
        val viewport = scrollPane?.viewport
        val viewportPositionBeforeCollapse = viewport?.viewPosition?.let { Point(it) }
        val selectionAnchor = synchronized(selectionAnchorCacheLock) { selectionAnchors[tree] }
        val canRestoreByAnchor = selectionAnchor != null && selectedFilePath != null && selectionAnchor.filePath == selectedFilePath
        var didCollapseAny = false

        for (relativePath in normalizedPathsToCollapse) {
            val absolutePath = PathValidator.validatePath(basePath, relativePath) ?: continue
            
            val isSelectedInsideThisPath = selectedFilePath != null && 
                (selectedFilePath.startsWith("$absolutePath/") || selectedFilePath == absolutePath)

            if (!isSelectedInsideThisPath) {
                didCollapseAny = collapsePathInTree(relativePath, absolutePath) || didCollapseAny
            }
        }

        if (!didCollapseAny) return
        if (scrollPane == null || viewportPositionBeforeCollapse == null) return

        tree.revalidate()
        tree.repaint()

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater

            if (canRestoreByAnchor && selectionAnchor != null) {
                restoreViewportPositionBySelectionAnchor(scrollPane, selectionAnchor)
            } else {
                restoreViewportPosition(scrollPane, viewportPositionBeforeCollapse)
            }
            ensureSelectionVisible()
        }, project.disposed)
    }

    private fun getExcludedPaths(basePath: String): List<String> {
        if (allExcludedPathsCache == null) {
            allExcludedPathsCache = CachedValuesManager.getManager(project).createCachedValue {
                val excludedRoots = ModuleManager.getInstance(project).modules
                    .flatMap { module ->
                        ModuleRootManager.getInstance(module).contentEntries
                            .flatMap { entry -> entry.excludeFolderFiles.toList() }
                    }
                val result = excludedRoots.map { it.path }

                CachedValueProvider.Result.create(result, ProjectRootManager.getInstance(project))
            }
        }
        return allExcludedPathsCache!!.value
            .mapNotNull { path ->
                if (!path.startsWith(basePath)) {
                    null
                } else {
                    path.removePrefix(basePath).removePrefix("/")
                }
            }
            .filter { it.isNotBlank() }
    }

    private fun collapsePathInTree(relativePath: String, absolutePath: String): Boolean {
        val root = tree.model.root ?: return false
        val rootPath = TreePath(root)
        val treePath = findExpandedTreePathForDirectory(absolutePath, rootPath)

        if (treePath != null && tree.isExpanded(treePath)) {
            LOG.info("Collapsing: $relativePath")
            tree.collapsePath(treePath)
            return true
        }

        return false
    }

    private fun findExpandedTreePathForDirectory(targetPath: String, currentPath: TreePath): TreePath? {
        val vf = getVirtualFileFromPath(currentPath)
        if (vf != null) {
            val path = vf.path
            if (path == targetPath) return currentPath

            if (!isAncestor(path, targetPath)) {
                return null
            }
        }

        if (!tree.isExpanded(currentPath)) return null

        val model = tree.model
        val node = currentPath.lastPathComponent
        val count = model.getChildCount(node)

        for (i in 0 until count) {
            val child = model.getChild(node, i)
            val childPath = currentPath.pathByAddingChild(child)
            val result = findExpandedTreePathForDirectory(targetPath, childPath)
            if (result != null) return result
        }

        return null
    }

    private fun isAncestor(ancestor: String, descendant: String): Boolean {
        if (descendant == ancestor) return true
        return descendant.startsWith(ancestor) &&
            (ancestor.endsWith("/") || descendant.length > ancestor.length && descendant[ancestor.length] == '/')
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

            return null
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
