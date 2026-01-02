package com.github.erjotek.stickyprojectfolder.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.AdjustmentListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.*
import javax.swing.tree.TreePath

import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

internal val LOG = Logger.getInstance("#com.github.erjotek.stickyprojectfolder.ui.StickyScrollManager")

class StickyScrollManager(private val project: Project) : Disposable {
    
    private var tree: JTree? = null
    private var scrollPane: JScrollPane? = null
    private var stickyComponent: StickyHeaderComponent? = null
    private var retryCount = 0
    private var checkTimer: Timer? = null

    // Listeners references to avoid leaks
    private var adjustmentListener: AdjustmentListener? = null
    private var componentListener: ComponentAdapter? = null
    private var viewportChangeListener: ChangeListener? = null
    private var treeModelListener: TreeModelListener? = null
    private var treeExpansionListener: TreeExpansionListener? = null
    private var contentManagerListener: ContentManagerListener? = null

    fun install() {
        LOG.info("Installing StickyScrollManager for project: " + project.name)
        scheduleTryInstall()
        
        val connection = project.messageBus.connect(this)
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                scheduleTryInstall()
                // Also try to attach to tool window content listener if not already
                attachToToolWindow(toolWindowManager)
            }
            
            override fun toolWindowRegistered(id: String) {
                if (id == ToolWindowId.PROJECT_VIEW) {
                    scheduleTryInstall()
                    attachToToolWindow(ToolWindowManager.getInstance(project))
                }
            }
        })
        
        // Initial attempt to attach listener
        attachToToolWindow(ToolWindowManager.getInstance(project))

        connection.subscribe(LafManagerListener.TOPIC, object : LafManagerListener {
            override fun lookAndFeelChanged(source: LafManager) {
                stickyComponent?.update()
                stickyComponent?.repaint()
            }
        })

        // Poll every 500ms to handle tab switches or other changes in Project View
        // Keeping timer as fallback but relying more on listeners
        checkTimer?.stop()
        checkTimer = Timer(1000) { tryInstall() }
        checkTimer?.start()
    }

    private fun attachToToolWindow(toolWindowManager: ToolWindowManager) {
        if (contentManagerListener != null) return
        
        val toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return
        
        val listener = object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                scheduleTryInstall()
            }
        }
        contentManagerListener = listener
        toolWindow.contentManager.addContentManagerListener(listener)
        Disposer.register(this) { toolWindow.contentManager.removeContentManagerListener(listener) }
    }

    private fun scheduleTryInstall() {
        if (stickyComponent != null && stickyComponent?.parent != null) return
        
        ApplicationManager.getApplication().invokeLater({
            tryInstall()
        }, project.disposed)
    }

    private fun tryInstall() {
        val projectView = ProjectView.getInstance(project)
        val pane = projectView.currentProjectViewPane as? ProjectViewPane
        
        if (pane == null) {
            if (retryCount < 20) {
                retryCount++
                scheduleTryInstall()
            }
            return
        }
        
        val currentTree = pane.tree
        if (currentTree == null) return
        
        val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, currentTree) as? JScrollPane
        if (sp == null) return

        // If tree or scrollpane changed, reset
        if ((this.tree != null && this.tree !== currentTree) || (this.scrollPane != null && this.scrollPane !== sp)) {
            LOG.info("Tree or ScrollPane instance changed, resetting StickyScrollManager")
            detach()
        }

        // Find correct target pane
        // Avoid FrameLayeredPane (internal IntelliJ class with strict layout assertions)
        var targetPane: JLayeredPane? = null
        
        // Strategy: Prefer the RootPane's LayeredPane as it is a safe standard place for overlays
        val rootPane = SwingUtilities.getRootPane(currentTree)
        if (rootPane != null) {
            val candidate = rootPane.layeredPane
            if (candidate != null && !candidate.javaClass.name.contains("FrameLayeredPane")) {
                targetPane = candidate
            } else {
                LOG.info("RootPane's LayeredPane rejected: ${candidate?.javaClass?.name}")
            }
        }

        // Fallback: search up but skip FrameLayeredPane
        if (targetPane == null) {
            var current: Component? = sp
            while (current != null) {
                if (current is JLayeredPane && !current.javaClass.name.contains("FrameLayeredPane")) {
                    targetPane = current
                    break
                }
                current = current.parent
            }
        }

        if (targetPane == null) {
             // Can happen if component is not attached to window yet
             return
        }

        this.tree = currentTree
        this.scrollPane = sp
        
        if (stickyComponent == null) {
            stickyComponent = StickyHeaderComponent(currentTree, sp)
        }
        
        // Check if we are properly installed in the correct hierarchy
        if (stickyComponent!!.parent !== targetPane) {
            LOG.info("Reparenting StickyComponent to: " + targetPane.javaClass.name)
            stickyComponent!!.parent?.remove(stickyComponent)
            
            // Fix: Use setLayer and add without index to avoid "constraint as primitive integer" warning
            // treating the layer as an index.
            targetPane.setLayer(stickyComponent!!, JLayeredPane.DRAG_LAYER)
            targetPane.add(stickyComponent!!)
        }
        
        // Ensure visible and updated
        if (stickyComponent?.isVisible != true) {
            stickyComponent?.isVisible = true
        }
        updateBounds()
        stickyComponent?.update()

        // Install listeners if not already installed
        if (adjustmentListener == null) {
            val adjListener = AdjustmentListener { 
                updateBounds()
                stickyComponent?.update() 
            }
            adjustmentListener = adjListener
            sp.verticalScrollBar.addAdjustmentListener(adjListener)
            sp.horizontalScrollBar.addAdjustmentListener(adjListener)
            
            val compListener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    updateBounds()
                    stickyComponent?.update()
                }
            }
            componentListener = compListener
            sp.addComponentListener(compListener)
            
            // Also listen to viewport for resizing
            val viewListener = ChangeListener {
                // If the target pane is no longer valid (e.g. window moved), trigger a full reinstall check
                if (stickyComponent?.parent !== targetPane || !targetPane.isShowing) {
                     scheduleTryInstall()
                     return@ChangeListener
                }
                updateBounds()
                stickyComponent?.update()
            }
            viewportChangeListener = viewListener
            sp.viewport.addChangeListener(viewListener)
            
            val tmListener = object : TreeModelListener {
                override fun treeNodesChanged(e: TreeModelEvent?) { stickyComponent?.update() }
                override fun treeNodesInserted(e: TreeModelEvent?) { stickyComponent?.update() }
                override fun treeNodesRemoved(e: TreeModelEvent?) { stickyComponent?.update() }
                override fun treeStructureChanged(e: TreeModelEvent?) { stickyComponent?.update() }
            }
            treeModelListener = tmListener
            currentTree.model.addTreeModelListener(tmListener)
    
            val teListener = object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent?) { stickyComponent?.update() }
                override fun treeCollapsed(event: TreeExpansionEvent?) { stickyComponent?.update() }
            }
            treeExpansionListener = teListener
            currentTree.addTreeExpansionListener(teListener)
        }
    }

    private fun logHierarchy(component: Component) {
        var current: Component? = component
        val sb = StringBuilder("Component Hierarchy for ${component.javaClass.name}:\n")
        while (current != null) {
            sb.append(" -> ${current.javaClass.name} (visible=${current.isVisible}, bounds=${current.bounds})\n")
            current = current.parent
        }
        LOG.info(sb.toString())
    }

    private fun updateBounds() {
        // Defer bounds update to avoid modifying component state during layout/painting
        SwingUtilities.invokeLater {
            val sp = scrollPane ?: return@invokeLater
            val sticky = stickyComponent ?: return@invokeLater
            
            if (!sp.isShowing) {
                if (sticky.isVisible) sticky.isVisible = false
                return@invokeLater
            }
            if (!sticky.isVisible) {
                sticky.isVisible = true
            }
            
            val viewport = sp.viewport
            // Check if viewport is valid and attached
            if (viewport.width == 0 || viewport.height == 0 || sticky.parent == null) {
                return@invokeLater
            }

            try {
                val location = SwingUtilities.convertPoint(viewport, 0, 0, sticky.parent)
                val newBounds = Rectangle(location.x, location.y, viewport.width, viewport.height)
                
                if (sticky.bounds != newBounds) {
                    sticky.bounds = newBounds
                }
            } catch (e: Exception) {
                // Handle potential hierarchy issues during conversion
                LOG.debug("Error updating sticky bounds", e)
            }
        }
    }

    private fun detach() {
        scrollPane?.let { sp ->
            adjustmentListener?.let {
                sp.verticalScrollBar.removeAdjustmentListener(it)
                sp.horizontalScrollBar.removeAdjustmentListener(it)
            }
            componentListener?.let { sp.removeComponentListener(it) }
            viewportChangeListener?.let { sp.viewport.removeChangeListener(it) }
        }
        
        tree?.let { t ->
            treeModelListener?.let { t.model.removeTreeModelListener(it) }
            treeExpansionListener?.let { t.removeTreeExpansionListener(it) }
        }

        stickyComponent?.let { it.parent?.remove(it) }
        stickyComponent = null
        
        adjustmentListener = null
        componentListener = null
        viewportChangeListener = null
        treeModelListener = null
        treeExpansionListener = null
    }

    override fun dispose() {
        checkTimer?.stop()
        checkTimer = null
        detach()
        tree = null
        scrollPane = null
    }
}

class StickyHeaderComponent(private val tree: JTree, private val scrollPane: JScrollPane) : JComponent() {
    private var stickyPaths: List<TreePath> = emptyList()
    private var pushOffset: Int = 0
    private var stableLinesCount: Int = 0
    private var hoverIndex: Int = -1

    init {
        isOpaque = false
        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val rowHeight = getRowHeight()
                var clickedIndex = -1
                val stableHeight = stableLinesCount * rowHeight
                
                if (e.y < stableHeight) {
                    clickedIndex = e.y / rowHeight
                } else {
                    val pushedY = e.y + pushOffset
                    if (pushedY >= stableHeight) {
                        clickedIndex = pushedY / rowHeight
                    }
                }
                
                if (clickedIndex in stickyPaths.indices) {
                    val path = stickyPaths[clickedIndex]
                    tree.selectionPath = path
                    tree.scrollPathToVisible(path)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val rowHeight = getRowHeight()
                val stableHeight = stableLinesCount * rowHeight
                var newHoverIndex = -1
                
                if (e.y < stableHeight) {
                    newHoverIndex = e.y / rowHeight
                } else {
                    val totalHeight = stickyPaths.size * rowHeight
                    val pushedY = e.y + pushOffset
                    if (pushedY >= stableHeight && pushedY < totalHeight) {
                        newHoverIndex = pushedY / rowHeight
                    }
                }
                
                if (newHoverIndex !in stickyPaths.indices) newHoverIndex = -1

                if (hoverIndex != newHoverIndex) {
                    hoverIndex = newHoverIndex
                    cursor = if (hoverIndex != -1) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                if (hoverIndex != -1) {
                    hoverIndex = -1
                    repaint()
                }
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    override fun contains(x: Int, y: Int): Boolean {
        if (stickyPaths.isEmpty()) return false
        val rowHeight = getRowHeight()
        val stableHeight = stableLinesCount * rowHeight
        val totalHeight = stickyPaths.size * rowHeight
        
        if (y < stableHeight) return true
        
        val visualBottom = totalHeight - pushOffset
        return y < visualBottom
    }

    private fun getRowHeight(): Int = tree.rowHeight.takeIf { it > 0 } ?: JBUI.scale(22)

    fun update() {
        val visibleRect = tree.visibleRect
        val rowHeight = getRowHeight()
        val maxStickyLevels = 5
        val maxStickyHeight = maxStickyLevels * rowHeight
        
        // Find the first row below the maximum possible sticky area
        // This ensures we get the correct ancestors even when sticky area is full
        val firstRow = tree.getClosestRowForLocation(0, visibleRect.y + maxStickyHeight + 1)
        
        if (firstRow == -1) {
            if (stickyPaths.isNotEmpty()) {
                stickyPaths = emptyList()
                pushOffset = 0
                stableLinesCount = 0
                repaint()
            }
            return
        }
        
        val firstPath = tree.getPathForRow(firstRow) ?: return
        
        // Collect all expanded ancestors from root to current
        val allAncestors = mutableListOf<TreePath>()
        var ptr: TreePath? = firstPath
        while (ptr != null) {
            allAncestors.add(ptr)
            ptr = ptr.parentPath
        }
        allAncestors.reverse() // Root -> ... -> Current
        
        // Filter to only expanded, visible ancestors (skip invisible root)
        val expandedAncestors = allAncestors.filter { path ->
            path.pathCount >= 2 && tree.isExpanded(path)
        }
        
        // Determine which ancestors should be sticky
        // A path becomes sticky when its top edge touches or goes above the bottom of the sticky area
        // The sticky area grows as we add more sticky paths
        val newStickyPaths = mutableListOf<TreePath>()
        
        for (path in expandedAncestors) {
            val bounds = tree.getPathBounds(path)
            if (bounds != null) {
                // The bottom of the current sticky area (where the next sticky row would start)
                val stickyAreaBottom = visibleRect.y + (newStickyPaths.size * rowHeight)
                
                // Path becomes sticky when its top edge is at or above the sticky area bottom
                // This means the folder's top has scrolled up to touch the bottom of existing sticky headers
                if (bounds.y <= stickyAreaBottom) {
                    newStickyPaths.add(path)
                }
            }
            
            // Stop if we've reached the maximum
            if (newStickyPaths.size >= maxStickyLevels) break
        }
        
        val limitedSticky = newStickyPaths

        var newPushOffset = 0
        var newStableLines = limitedSticky.size
        
        if (limitedSticky.isNotEmpty()) {
            val lastStickyPath = limitedSticky.last()
            val totalStickyHeight = limitedSticky.size * rowHeight
            
            // Find the first row below the sticky area for push calculation
            val effectiveTop = visibleRect.y + totalStickyHeight
            val startRow = tree.getClosestRowForLocation(0, effectiveTop + 1)
            if (startRow == -1) return
            
            for (i in startRow until (startRow + 20).coerceAtMost(tree.rowCount)) {
                val path = tree.getPathForRow(i) ?: continue
                
                if (path.pathCount <= lastStickyPath.pathCount && path != lastStickyPath && !lastStickyPath.isDescendant(path)) {
                     val bounds = tree.getPathBounds(path) ?: continue
                     val distance = bounds.y - visibleRect.y
                     
                     if (distance < totalStickyHeight) {
                         newPushOffset = totalStickyHeight - distance
                         val maxPush = totalStickyHeight - (stableLinesCount * rowHeight)
                         if (newPushOffset > maxPush) newPushOffset = maxPush
                         
                         var stableCount = 0
                         for (sticky in limitedSticky) {
                             if (sticky.isDescendant(path)) {
                                 stableCount++
                             } else {
                                 break
                             }
                         }
                         
                         newStableLines = stableCount
                     }
                     break
                }
            }
        }
        
        if (stickyPaths != limitedSticky || pushOffset != newPushOffset || stableLinesCount != newStableLines) {
            stickyPaths = limitedSticky
            pushOffset = newPushOffset
            stableLinesCount = newStableLines
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        if (stickyPaths.isEmpty()) return

        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            
            val rowHeight = getRowHeight()
            val stableHeight = stableLinesCount * rowHeight
            val totalHeight = stickyPaths.size * rowHeight
            
            if (stableLinesCount < stickyPaths.size) {
                 val pushedYStart = stableHeight - pushOffset
                 
                 val oldClip = g2.clip
                 g2.clipRect(0, stableHeight, width, height - stableHeight)
                 
                 g2.translate(0, -pushOffset)
                 
                 for (i in stableLinesCount until stickyPaths.size) {
                     val path = stickyPaths[i]
                     val yPos = i * rowHeight
                     drawRow(g2, path, yPos, rowHeight)
                 }
                 
                 g2.color = JBColor.border()
                 val bottomY = totalHeight - 1
                 g2.drawLine(0, bottomY, width, bottomY)
                 
                 g2.translate(0, pushOffset)
                 g2.clip = oldClip
            }
            
            if (stableLinesCount > 0) {
                for (i in 0 until stableLinesCount) {
                    val path = stickyPaths[i]
                    val yPos = i * rowHeight
                    drawRow(g2, path, yPos, rowHeight)
                }
                
                if (stableLinesCount == stickyPaths.size) {
                    g2.color = JBColor.border()
                    val bottomY = totalHeight - 1
                    g2.drawLine(0, bottomY, width, bottomY)
                }
            }
            
        } finally {
            g2.dispose()
        }
    }
    
    private fun drawRow(g2: Graphics2D, path: TreePath, yOffset: Int, rowHeight: Int) {
        val renderer = tree.cellRenderer
        val node = path.lastPathComponent
        val isSelected = false 
        val isExpanded = tree.isExpanded(path)
        val isLeaf = tree.getModel().isLeaf(node)
        val row = tree.getRowForPath(path)
        
        val component = renderer.getTreeCellRendererComponent(
            tree, node, isSelected, isExpanded, isLeaf, row, false
        ) as JComponent
        
        g2.color = tree.background
        g2.fillRect(0, yOffset, width, rowHeight)
        
        if (stickyPaths.indexOf(path) == hoverIndex) {
            g2.color = ColorUtil.withAlpha(JBColor.blue, 0.05)
            g2.fillRect(0, yOffset, width, rowHeight)
        }

        // Get the actual X position from the tree's bounds for this path
        val bounds = tree.getPathBounds(path)
        val indent = bounds?.x ?: ((path.pathCount - 2) * 20)
        
        val oldClip = g2.clip
        g2.clipRect(0, yOffset, width, rowHeight)
        
        g2.translate(indent, yOffset)
        component.bounds = Rectangle(0, 0, width - indent, rowHeight)
        component.isOpaque = false 
        component.validate()
        component.paint(g2)
        g2.translate(-indent, -yOffset)
        g2.clip = oldClip
        
        g2.color = JBColor.border()
        g2.drawLine(0, yOffset + rowHeight - 1, width, yOffset + rowHeight - 1)
    }
}
