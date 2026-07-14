package com.github.erjotek.stickyprojectfolder.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.github.erjotek.stickyprojectfolder.util.TreeContextResolver
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*

private val LOG = Logger.getInstance(StickyScrollManager::class.java)

class StickyScrollManager(private val project: Project) : Disposable {

    private var tree: JTree? = null
    private var scrollPane: JScrollPane? = null
    private var stickyComponent: StickyHeaderComponent? = null
    private var autoCollapseManager: AutoCollapseManager? = null
    private var searchTimer: Timer? = null

    // Listeners
    private var contentManagerListener: ContentManagerListener? = null
    private var hierarchyListener: HierarchyListener? = null
    private var adjustmentListener: AdjustmentListener? = null
    private var componentListener: ComponentAdapter? = null
    private var viewportChangeListener: ChangeListener? = null
    private var treeModelListener: TreeModelListener? = null
    private var treeExpansionListener: TreeExpansionListener? = null
    private var treeSelectionListener: TreeSelectionListener? = null
    private var lastScrollY = 0

    // Flag to skip autoscroll when selection comes from sticky header click
    private var skipNextAutoscroll = false

    init {
        install()
    }

    fun install() {
        scheduleTryInstall()

        val connection = project.messageBus.connect(this)

        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                scheduleTryInstall()
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) updateBounds()
                }, project.disposed)
            }
        })
    }

    private fun startSearchTimer() {
        if (searchTimer == null) {
            searchTimer = Timer(1000) {
                scheduleTryInstall()
            }
            searchTimer?.isRepeats = true
            searchTimer?.start()
        }
    }

    private fun stopSearchTimer() {
        searchTimer?.stop()
        searchTimer = null
    }

    private fun scheduleTryInstall() {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) tryInstall()
        }, project.disposed)
    }

    private fun tryInstall() {
        val projectView = ProjectView.getInstance(project)
        val pane = projectView.currentProjectViewPane

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (toolWindow != null && contentManagerListener == null) {
            val listener = object : ContentManagerListener {
                override fun selectionChanged(event: ContentManagerEvent) {
                    scheduleTryInstall()
                }
            }
            contentManagerListener = listener
            toolWindow.contentManager.addContentManagerListener(listener)
        }

        val context = TreeContextResolver.resolve(project, pane)
        if (context == null) {
            if (this.tree != null) {
                detach()
            }
            if (toolWindow?.isVisible == true) {
                startSearchTimer()
            } else {
                stopSearchTimer()
            }
            return
        }

        val currentTree = context.tree
        val sp = context.scrollPane

        // Always detach and reinstall listeners when tree changes
        if (this.tree !== currentTree || this.scrollPane !== sp) {
            detach()
            this.tree = null
            this.scrollPane = null
            adjustmentListener = null
        }

        this.tree = currentTree
        this.scrollPane = sp
        stopSearchTimer()

        // Create or reuse sticky component
        if (stickyComponent == null || stickyComponent!!.getTree() !== currentTree) {
            stickyComponent?.let { Disposer.dispose(it) }
            stickyComponent?.parent?.remove(stickyComponent)
            stickyComponent = StickyHeaderComponent(
                project,
                currentTree,
                sp,
                onBoundsUpdateNeeded = { updateBounds() },
                onStickyHeaderClick = { skipNextAutoscroll = true }
            )
        }

        // Add to root pane's layered pane with high z-index to stay on top
        val rootPane = SwingUtilities.getRootPane(currentTree) ?: return
        val layeredPane = rootPane.layeredPane

        if (stickyComponent!!.parent !== layeredPane) {
            stickyComponent!!.parent?.remove(stickyComponent)
            layeredPane.add(stickyComponent!!)
            layeredPane.setLayer(stickyComponent!!, JLayeredPane.POPUP_LAYER)
        }

        if (stickyComponent?.isVisible != true) stickyComponent?.isVisible = true

        updateBounds()
        stickyComponent?.update(forceRepaint = false)

        installListeners(sp, currentTree)

        if (autoCollapseManager == null) {
            autoCollapseManager = AutoCollapseManager(project, currentTree)
            com.intellij.openapi.util.Disposer.register(this, autoCollapseManager!!)
            autoCollapseManager?.install()
        }
    }

    private fun installListeners(sp: JScrollPane, currentTree: JTree) {
        if (adjustmentListener == null) {
            val updateAction = Runnable {
                updateBounds()
                stickyComponent?.update(forceRepaint = false)
                stickyComponent?.repaint()
            }

            val updateActionForced = Runnable {
                updateBounds()
                stickyComponent?.update(forceRepaint = true)
            }

            val adjListener = AdjustmentListener { updateAction.run() }
            adjustmentListener = adjListener
            sp.verticalScrollBar.addAdjustmentListener(adjListener)
            sp.horizontalScrollBar.addAdjustmentListener(adjListener)

            val compListener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) { updateAction.run() }
                override fun componentMoved(e: ComponentEvent?) { updateAction.run() }
                override fun componentShown(e: ComponentEvent?) { updateAction.run() }
            }
            componentListener = compListener
            sp.addComponentListener(compListener)

            val viewListener = ChangeListener { updateAction.run() }
            viewportChangeListener = viewListener
            sp.viewport.addChangeListener(viewListener)

            val tmListener = object : TreeModelListener {
                override fun treeNodesChanged(e: TreeModelEvent?) {
                    if (e != null && stickyComponent?.isAffectedBy(e) == true) {
                        updateActionForced.run()
                    }
                }
                override fun treeNodesInserted(e: TreeModelEvent?) { updateAction.run() }
                override fun treeNodesRemoved(e: TreeModelEvent?) { updateAction.run() }
                override fun treeStructureChanged(e: TreeModelEvent?) { updateAction.run() }
            }
            treeModelListener = tmListener
            currentTree.model?.addTreeModelListener(tmListener)

            val teListener = object : TreeExpansionListener {
                override fun treeExpanded(e: TreeExpansionEvent?) { updateAction.run() }
                override fun treeCollapsed(e: TreeExpansionEvent?) { updateAction.run() }
            }
            treeExpansionListener = teListener
            currentTree.addTreeExpansionListener(teListener)

            val tsListener = TreeSelectionListener {
                handleAutoscrollIfNeeded(currentTree, sp)
            }
            treeSelectionListener = tsListener
            currentTree.addTreeSelectionListener(tsListener)

            val hListener = HierarchyListener { e ->
                if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L ||
                    (e.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong()) != 0L) {
                    scheduleTryInstall()
                }
            }
            hierarchyListener = hListener
            currentTree.addHierarchyListener(hListener)
        }
    }

    private fun handleAutoscrollIfNeeded(tree: JTree, sp: JScrollPane) {
        if (AutoCollapseManager.isNavigationSuppressed()) {
            return
        }

        if (skipNextAutoscroll) {
            skipNextAutoscroll = false
            return
        }

        val path = tree.selectionPath ?: return
        val row = tree.getRowForPath(path)
        if (row == -1) return

        val rowBounds = tree.getRowBounds(row) ?: return
        val visibleRect = tree.visibleRect
        val stickyHeight = stickyComponent?.getStickyHeight() ?: 0

        val currentScrollY = sp.verticalScrollBar.value
        val scrollingDown = currentScrollY > lastScrollY
        lastScrollY = currentScrollY

        val effectiveVisibleTop = visibleRect.y + stickyHeight
        val effectiveVisibleHeight = visibleRect.height - stickyHeight

        if (rowBounds.y >= effectiveVisibleTop && rowBounds.y + rowBounds.height <= visibleRect.y + visibleRect.height) {
            return
        }

        val targetY = rowBounds.y + rowBounds.height / 2 - effectiveVisibleHeight / 2

        val maxScroll = tree.height - visibleRect.height
        val clampedY = targetY.coerceIn(0, maxScroll.coerceAtLeast(0))

        SwingUtilities.invokeLater {
            sp.verticalScrollBar.value = clampedY
        }
    }

    private fun updateBounds() {
        val sp = scrollPane ?: return
        val sticky = stickyComponent ?: return
        val currentTree = tree ?: return

        // Check if tree and scroll pane are actually visible
        if (!sp.isShowing || !sp.viewport.isShowing || !currentTree.isShowing) {
            sticky.isVisible = false
            return
        }

        // Check if the tool window is visible
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (toolWindow == null || !toolWindow.isVisible) {
            sticky.isVisible = false
            return
        }

        val stickyHeight = sticky.getStickyHeight()
        if (stickyHeight <= 0) {
            sticky.isVisible = false
            return
        }

        sticky.isVisible = true

        val viewport = sp.viewport
        val viewportBounds = viewport.bounds
        val viewportLocationOnScreen = try {
            viewport.locationOnScreen
        } catch (e: Exception) {
            sticky.isVisible = false
            return
        }

        val rootPane = SwingUtilities.getRootPane(sp) ?: return
        val layeredPane = rootPane.layeredPane
        val layeredPaneLocationOnScreen = try {
            layeredPane.locationOnScreen
        } catch (e: Exception) {
            sticky.isVisible = false
            return
        }

        var stickyX = viewportLocationOnScreen.x - layeredPaneLocationOnScreen.x
        val y = viewportLocationOnScreen.y - layeredPaneLocationOnScreen.y

        var stickyWidth = viewportBounds.width
        val vScrollBar = sp.verticalScrollBar
        val settings = com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings.instance
        if (settings.state.avoidTransparentScrollbarOverlap && vScrollBar != null && vScrollBar.isVisible && !vScrollBar.isOpaque) {
            stickyWidth -= vScrollBar.width
            if (!sp.componentOrientation.isLeftToRight) {
                stickyX += vScrollBar.width
            }
        }

        sticky.setBounds(stickyX, y, stickyWidth, stickyHeight)
    }

    private fun detach() {
        val sp = scrollPane
        val t = tree

        if (sp != null) {
            adjustmentListener?.let { sp.verticalScrollBar.removeAdjustmentListener(it) }
            adjustmentListener?.let { sp.horizontalScrollBar.removeAdjustmentListener(it) }
            componentListener?.let { sp.removeComponentListener(it) }
            viewportChangeListener?.let { sp.viewport.removeChangeListener(it) }
        }

        if (t != null) {
            treeSelectionListener?.let { t.removeTreeSelectionListener(it) }
            t.model?.let { model ->
                treeModelListener?.let { model.removeTreeModelListener(it) }
            }
            treeExpansionListener?.let { t.removeTreeExpansionListener(it) }
            hierarchyListener?.let { t.removeHierarchyListener(it) }
        }

        if (contentManagerListener != null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
            toolWindow?.contentManager?.removeContentManagerListener(contentManagerListener!!)
            contentManagerListener = null
        }

        stickyComponent?.let {
            it.parent?.remove(it)
            Disposer.dispose(it)
        }
        stickyComponent = null

        autoCollapseManager?.let { com.intellij.openapi.util.Disposer.dispose(it) }
        autoCollapseManager = null

        adjustmentListener = null
        componentListener = null
        viewportChangeListener = null
        treeModelListener = null
        treeExpansionListener = null
        hierarchyListener = null
    }

    override fun dispose() {
        stopSearchTimer()
        detach()
    }
}
