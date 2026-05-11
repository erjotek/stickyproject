package com.github.erjotek.stickyprojectfolder.pinned

import com.github.erjotek.stickyprojectfolder.ui.PinnedFooterComponent
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiManager
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.event.ChangeListener

class PinnedFoldersManager(private val project: Project) : Disposable {
    private val pendingScrollTimers = mutableListOf<Timer>()

    private var tree: JTree? = null
    private var scrollPane: JScrollPane? = null
    private var footerComponent: PinnedFooterComponent? = null
    private var checkTimer: Timer? = null

    // Listeners
    private var adjustmentListener: AdjustmentListener? = null
    private var componentListener: ComponentAdapter? = null
    private var viewportChangeListener: ChangeListener? = null

    init {
        install()
    }

    private fun install() {
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
        val pane = projectView.currentProjectViewPane

        val context = resolveTreeContext(pane)
        if (context == null) return

        val currentTree = context.tree
        val sp = context.scrollPane

        if (this.tree !== currentTree || this.scrollPane !== sp) {
            detach()
            this.tree = null
            this.scrollPane = null
        }

        this.tree = currentTree
        this.scrollPane = sp

        if (footerComponent == null || footerComponent?.parent == null) {
            footerComponent = PinnedFooterComponent(
                project,
                currentTree,
                onHeightChanged = { updateBounds() },
                onPinClick = { file -> handlePinClick(file) }
            )
        }

        val rootPane = SwingUtilities.getRootPane(currentTree) ?: return
        val layeredPane = rootPane.layeredPane

        if (footerComponent!!.parent !== layeredPane) {
            footerComponent!!.parent?.remove(footerComponent)
            layeredPane.add(footerComponent!!)
            layeredPane.setLayer(footerComponent!!, JLayeredPane.POPUP_LAYER)
        }
        
        // Initial update
        footerComponent?.update()
        updateBounds()

        installListeners(sp)
    }
    
    // ... Copy-paste resolveTreeContext helpers from StickyScrollManager ...
    // To avoid duplication I should have shared them, but for speed I'll replicate relevant parts roughly.
    private data class TreeContext(val tree: JTree, val scrollPane: JScrollPane)
    
    private fun resolveTreeContext(pane: AbstractProjectViewPane?): TreeContext? {
        fun toContext(tree: JTree): TreeContext? {
            val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane ?: return null
            return TreeContext(tree, sp)
        }

        resolveFocusedProjectViewTree()?.let { focusedTree ->
            toContext(focusedTree)?.let { return it }
        }

        val treeFromPane = pane?.tree
        if (treeFromPane != null && treeFromPane.isShowing) {
            toContext(treeFromPane)?.let { return it }
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return null
        val treeFromToolWindow = findTree(toolWindow.component) ?: return null
        return toContext(treeFromToolWindow)
    }

    private fun resolveFocusedProjectViewTree(): JTree? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return null
        val toolWindowComponent = toolWindow.component

        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return null
        if (!SwingUtilities.isDescendingFrom(focusOwner, toolWindowComponent)) return null

        return when (focusOwner) {
            is JTree -> focusOwner
            else -> SwingUtilities.getAncestorOfClass(JTree::class.java, focusOwner) as? JTree
        }
    }

    private fun findTree(component: Component?): JTree? {
        if (component == null) return null
        if (component is JTree && component.isShowing) return component
        if (component is Container) {
            for (child in component.components) {
                val tree = findTree(child)
                if (tree != null) return tree
            }
        }
        return null
    }

    private fun installListeners(sp: JScrollPane) {
        if (adjustmentListener == null) {
            val updateAction = Runnable { 
                updateBounds() 
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
        }
    }
    
    fun updateBounds() {
        val sp = scrollPane ?: return
        val footer = footerComponent ?: return
        val currentTree = tree ?: return
        
        // Simple visibility check
        if (!sp.isShowing || !footer.isVisible) {
             // We need to decide if we hide it or show it. 
             // Logic: Update calls footer.update() which sets preferred size.
             // If size > 0, we show it.
             // But valid bounds calculation requires showing SP.
        }
        
        // Check tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)
        if (toolWindow == null || !toolWindow.isVisible) {
            footer.isVisible = false
            return
        }
        
        // Re-read settings/items to ensure we have content (handled by footer.update() which should be called when settings change?
        // For now, let's call update() here just in case, but it might be expensive.
        // ideally listener.
        footer.update()
        
        if (footer.preferredSize.height <= 0) {
            footer.isVisible = false
            return
        }
        footer.isVisible = true
        
        val viewport = sp.viewport
        val viewportBounds = viewport.bounds
        
        val viewportLoc = try { viewport.locationOnScreen } catch (e: Exception) { return }
        val layeredLoc = try { SwingUtilities.getRootPane(sp)?.layeredPane?.locationOnScreen } catch (e: Exception) { return }
        
        if (layeredLoc == null) return
        
        val x = viewportLoc.x - layeredLoc.x
        val h = footer.preferredSize.height
        val y = viewportLoc.y - layeredLoc.y + viewportBounds.height - h
        
        footer.setBounds(x, y, viewportBounds.width, h)
        footer.repaint()

        val currentInsets = currentTree.border?.getBorderInsets(currentTree) ?: java.awt.Insets(0, 0, 0, 0)
        if (currentInsets.bottom != h) {
            currentTree.border = javax.swing.BorderFactory.createEmptyBorder(
                currentInsets.top, currentInsets.left, h, currentInsets.right
            )
        }
    }
    
    private fun handlePinClick(file: VirtualFile) {
        // Cancel any pending scroll retry timers from previous pin clicks
        cancelPendingScrollTimers()

        // Pane/tree can change when switching Project View tabs (Projects/Project Files/etc.).
        // Ensure we operate on the currently active tree.
        tryInstall()

        val currentTree = tree ?: return
        val psiDir = ApplicationManager.getApplication().runReadAction(Computable {
            PsiManager.getInstance(project).findDirectory(file)
        }) ?: return
        
        val projectView = ProjectView.getInstance(project)
        
        // Step 1: Perform collapse FIRST (synchronously on EDT) to avoid race condition
        // This prevents autocollapse from interfering with our navigation
        com.github.erjotek.stickyprojectfolder.ui.AutoCollapseManager.performCollapse(project, currentTree, ignoreSuppression = true)

        // Prevent debounce auto-collapse triggered by selection/focus changes during navigation.
        com.github.erjotek.stickyprojectfolder.ui.AutoCollapseManager.suppressForNavigation(800)
        
        // Step 2: Select the folder (this will expand parents and scroll)
        // ProjectView.select() internally creates SmartPsiElementPointer which requires read access.
        // requestFocus=true ensures the tree accepts the selection reliably.
        ApplicationManager.getApplication().runReadAction(Runnable {
            projectView.select(psiDir, file, true)
        })

        // Step 3: Wait for tree to stabilize, then scroll to precise position.
        // Selection/expansion can be async, so retry a few times.
        scheduleScrollRetry(projectView, psiDir, currentTree, file, attemptsLeft = 6)
    }

    private fun cancelPendingScrollTimers() {
        for (timer in pendingScrollTimers) {
            timer.stop()
        }
        pendingScrollTimers.clear()
    }

    private fun scheduleScrollRetry(
        projectView: ProjectView,
        psiDir: com.intellij.psi.PsiDirectory,
        tree: JTree,
        file: VirtualFile,
        attemptsLeft: Int
    ) {
        if (attemptsLeft <= 0) return

        val timer = Timer(80) {
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater

                val selectionPath = tree.selectionPath
                val selectedVf = com.github.erjotek.stickyprojectfolder.ui.AutoCollapseManager.getVirtualFileFromPath(selectionPath)

                if (selectedVf != null && selectedVf == file) {
                    // Navigation succeeded – scroll to exact position
                    scrollToPrecisePosition(tree, file)
                    // Schedule a post-navigation collapse after a short delay.
                    // No suppression needed here – the collapse will naturally
                    // skip the newly-selected folder and collapse the old one.
                    schedulePostNavigationCollapse(tree, 200)
                } else {
                    // Re-issue selection in case the first select was ignored during async tree rebuild.
                    ApplicationManager.getApplication().runReadAction(Runnable {
                        projectView.select(psiDir, file, true)
                    })
                    scheduleScrollRetry(projectView, psiDir, tree, file, attemptsLeft = attemptsLeft - 1)
                }
            }, project.disposed)
        }.apply {
            isRepeats = false
            start()
        }
        pendingScrollTimers.add(timer)
    }
    
    private fun scrollToPrecisePosition(tree: JTree, file: VirtualFile) {
        val rows = tree.selectionRows ?: return
        if (rows.isEmpty()) return
        val row = rows[0]
        val path = tree.getPathForRow(row) ?: return
        
        val rowHeight = if (tree.rowHeight > 0) tree.rowHeight else com.intellij.util.ui.JBUI.scale(22)
        
        com.github.erjotek.stickyprojectfolder.util.StickyScrollUtil.scrollToMakeVisibleBelowSticky(tree, path, rowHeight)
    }

    /**
     * After pinned-folder navigation finishes, schedule a collapse so the
     * previously-expanded folder is collapsed promptly.  The collapse naturally
     * skips the newly-selected path, so only "stale" folders get collapsed.
     */
    private fun schedulePostNavigationCollapse(tree: JTree, delayMs: Int) {
        val timer = Timer(delayMs) {
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) {
                    com.github.erjotek.stickyprojectfolder.ui.AutoCollapseManager.performCollapse(
                        project, tree, ignoreSuppression = true
                    )
                }
            }, project.disposed)
        }.apply {
            isRepeats = false
            start()
        }
        pendingScrollTimers.add(timer)
    }

    private fun detach() {
        val sp = scrollPane
        if (sp != null) {
            adjustmentListener?.let { sp.verticalScrollBar.removeAdjustmentListener(it) }
            adjustmentListener?.let { sp.horizontalScrollBar.removeAdjustmentListener(it) }
            componentListener?.let { sp.removeComponentListener(it) }
            viewportChangeListener?.let { sp.viewport.removeChangeListener(it) }
        }
        
        footerComponent?.let { it.parent?.remove(it) }
        footerComponent = null
    }

    override fun dispose() {
        checkTimer?.stop()
        cancelPendingScrollTimers()
        detach()
    }
}
