package com.github.erjotek.stickyprojectfolder.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*

private val LOG = Logger.getInstance(StickyScrollManager::class.java)

class StickyScrollManager(private val project: Project) : Disposable {

    private var tree: JTree? = null
    private var scrollPane: JScrollPane? = null
    private var stickyComponent: StickyHeaderComponent? = null
    private var autoCollapseManager: AutoCollapseManager? = null
    private var retryCount = 0
    private var checkTimer: Timer? = null

    // Listeners
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

        checkTimer = Timer(1000) {
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) tryInstall()
            }, project.disposed)
        }
        checkTimer?.start()
    }

    private fun scheduleTryInstall() {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) tryInstall()
        }, project.disposed)
    }

    private fun tryInstall() {
        val projectView = ProjectView.getInstance(project)
        val pane = projectView.currentProjectViewPane as? AbstractProjectViewPane

        if (pane == null) {
            if (retryCount < 10) {
                retryCount++
                scheduleTryInstall()
            }
            return
        }

        val currentTree = pane.tree ?: return
        val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, currentTree) as? JScrollPane ?: return

        // Always detach and reinstall listeners when tree changes
        if (this.tree !== currentTree || this.scrollPane !== sp) {
            detach()
            this.tree = null
            this.scrollPane = null
            adjustmentListener = null
        }

        this.tree = currentTree
        this.scrollPane = sp

        // Create or reuse sticky component
        if (stickyComponent == null || stickyComponent!!.getTree() !== currentTree) {
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
        }
    }

    private fun handleAutoscrollIfNeeded(tree: JTree, sp: JScrollPane) {
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

        val targetY = if (scrollingDown || rowBounds.y < effectiveVisibleTop) {
            rowBounds.y + rowBounds.height / 2 - effectiveVisibleHeight / 2
        } else {
            rowBounds.y + rowBounds.height / 2 - effectiveVisibleHeight / 2
        }

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

        val x = viewportLocationOnScreen.x - layeredPaneLocationOnScreen.x
        val y = viewportLocationOnScreen.y - layeredPaneLocationOnScreen.y

        sticky.setBounds(x, y, viewportBounds.width, viewportBounds.height)
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
        }

        stickyComponent?.let { it.parent?.remove(it) }
        stickyComponent = null

        autoCollapseManager?.let { com.intellij.openapi.util.Disposer.dispose(it) }
        autoCollapseManager = null

        adjustmentListener = null
        componentListener = null
        viewportChangeListener = null
        treeModelListener = null
        treeExpansionListener = null
    }

    override fun dispose() {
        checkTimer?.stop()
        detach()
    }
}
