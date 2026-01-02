package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.psi.PsiElement
import com.intellij.openapi.vfs.VirtualFile
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

private val LOG = Logger.getInstance("#com.github.erjotek.stickyprojectfolder.ui.StickyScrollManager")

class StickyScrollManager(private val project: Project) : Disposable {
    
    private var tree: JTree? = null
    private var scrollPane: JScrollPane? = null
    private var stickyComponent: StickyHeaderComponent? = null
    private var retryCount = 0
    private var checkTimer: Timer? = null

    // Listeners
    private var adjustmentListener: AdjustmentListener? = null
    private var componentListener: ComponentAdapter? = null
    private var viewportChangeListener: ChangeListener? = null
    private var treeModelListener: TreeModelListener? = null
    private var treeExpansionListener: TreeExpansionListener? = null

    fun install() {
        scheduleTryInstall()
        
        val connection = project.messageBus.connect(this)
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                scheduleTryInstall()
            }
            override fun toolWindowRegistered(id: String) {
                if (id == ToolWindowId.PROJECT_VIEW) scheduleTryInstall()
            }
        })
        
        checkTimer = Timer(1000) { tryInstall() }
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
        
        val currentTree = pane.tree
        if (currentTree == null) return
        
        val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, currentTree) as? JScrollPane
        if (sp == null) return

        if ((this.tree != null && this.tree !== currentTree) || (this.scrollPane != null && this.scrollPane !== sp)) {
            detach()
        }

        val rootPane = SwingUtilities.getRootPane(currentTree) ?: return
        val targetPane = rootPane.layeredPane 

        this.tree = currentTree
        this.scrollPane = sp
        
        if (stickyComponent == null) {
            stickyComponent = StickyHeaderComponent(project, currentTree, sp)
        }
        
        if (stickyComponent!!.parent !== targetPane) {
            stickyComponent!!.parent?.remove(stickyComponent)
            targetPane.add(stickyComponent!!, JLayeredPane.POPUP_LAYER)
        }
        
        if (stickyComponent?.isVisible != true) stickyComponent?.isVisible = true
        
        updateBounds()
        stickyComponent?.update()

        installListeners(sp, currentTree)
    }

    private fun installListeners(sp: JScrollPane, currentTree: JTree) {
        if (adjustmentListener == null) {
            val updateAction = { 
                updateBounds()
                stickyComponent?.update() 
            }
            
            val adjListener = AdjustmentListener { updateAction() }
            adjustmentListener = adjListener
            sp.verticalScrollBar.addAdjustmentListener(adjListener)
            sp.horizontalScrollBar.addAdjustmentListener(adjListener)
            
            val compListener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) { updateAction() }
                override fun componentMoved(e: ComponentEvent?) { updateAction() }
                override fun componentShown(e: ComponentEvent?) { updateAction() }
            }
            componentListener = compListener
            sp.addComponentListener(compListener)
            
            val viewListener = ChangeListener { updateAction() }
            viewportChangeListener = viewListener
            sp.viewport.addChangeListener(viewListener)
            
            val tmListener = object : TreeModelListener {
                override fun treeNodesChanged(e: TreeModelEvent?) { updateAction() }
                override fun treeNodesInserted(e: TreeModelEvent?) { updateAction() }
                override fun treeNodesRemoved(e: TreeModelEvent?) { updateAction() }
                override fun treeStructureChanged(e: TreeModelEvent?) { updateAction() }
            }
            treeModelListener = tmListener
            currentTree.model.addTreeModelListener(tmListener)
    
            val teListener = object : TreeExpansionListener {
                override fun treeExpanded(event: TreeExpansionEvent?) { updateAction() }
                override fun treeCollapsed(event: TreeExpansionEvent?) { updateAction() }
            }
            treeExpansionListener = teListener
            currentTree.addTreeExpansionListener(teListener)
        }
    }

    private fun updateBounds() {
        val sp = scrollPane ?: return
        val sticky = stickyComponent ?: return
        val viewport = sp.viewport ?: return

        if (!sp.isShowing || !viewport.isShowing) {
            sticky.isVisible = false
            return
        }
        sticky.isVisible = true

        try {
            val pt = SwingUtilities.convertPoint(viewport, 0, 0, sticky.parent)
            sticky.bounds = Rectangle(pt.x, pt.y, viewport.width, viewport.height)
        } catch (e: Exception) {
            // Ignore
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
        detach()
    }
}

class StickyHeaderComponent(
    private val project: Project,
    private val tree: JTree, 
    private val scrollPane: JScrollPane
) : JComponent() {
    
    private var stickyPaths: List<TreePath> = emptyList()
    private var pushOffset: Int = 0
    private var hoverIndex: Int = -1

    init {
        isOpaque = false
        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isWithinTreeBounds(e.x)) return
                val path = getPathAt(e.y)
                if (path != null) {
                    tree.selectionPath = path
                    tree.scrollPathToVisible(path)
                    e.consume()
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                if (!isWithinTreeBounds(e.x)) {
                     if (hoverIndex != -1) {
                         hoverIndex = -1
                         cursor = Cursor.getDefaultCursor()
                         repaint()
                     }
                     return
                }
                
                val idx = getIndexAt(e.y)
                if (hoverIndex != idx) {
                    hoverIndex = idx
                    cursor = if (idx != -1) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
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
    
    private fun isWithinTreeBounds(x: Int): Boolean {
         return x >= 0 && x < width // Constrain to component width (viewport width)
    }

    override fun contains(x: Int, y: Int): Boolean {
        if (!isVisible || stickyPaths.isEmpty()) return false
        if (x < 0 || x >= width) return false
        
        val rowHeight = getRowHeight()
        val totalHeight = stickyPaths.size * rowHeight - pushOffset
        return y < totalHeight
    }
    
    private fun getIndexAt(y: Int): Int {
        val rowHeight = getRowHeight()
        val totalHeight = stickyPaths.size * rowHeight - pushOffset
        if (y >= totalHeight) return -1
        
        val lastIdx = stickyPaths.size - 1
        val lastItemTop = lastIdx * rowHeight - pushOffset
        val lastItemBottom = lastItemTop + rowHeight
        
        if (y >= lastItemTop && y < lastItemBottom) return lastIdx
        
        val stableHeight = lastIdx * rowHeight
        if (y < stableHeight) return y / rowHeight
        
        return -1
    }

    private fun getPathAt(y: Int): TreePath? {
        val idx = getIndexAt(y)
        return if (idx in stickyPaths.indices) stickyPaths[idx] else null
    }

    private fun getRowHeight(): Int = tree.rowHeight.takeIf { it > 0 } ?: JBUI.scale(22)

    fun update() {
        if (tree.rowCount == 0) {
            clearSticky()
            return
        }
        
        val visibleRect = tree.visibleRect
        val rowHeight = getRowHeight()
        val settings = StickyProjectSettings.instance
        val maxStickyLimit = settings.state.maxStickyLimit
        
        val newStickyPaths = mutableListOf<TreePath>()
        var currentStickyBottomRelative = 0 
        
        // Iterative Probing with Strict/Loose checks
        while (newStickyPaths.size < maxStickyLimit) {
            val stickyBottom = visibleRect.y + currentStickyBottomRelative
            // Probe deeper to catch entering rows
            val probeY = stickyBottom + (rowHeight / 2) 
            
            val row = tree.getClosestRowForLocation(0, probeY)
            if (row == -1) break
            
            val path = tree.getPathForRow(row) ?: break
            val rowBounds = tree.getRowBounds(row) ?: break
            
            val candidates = mutableListOf<TreePath>()
            var ptr: TreePath? = path.parentPath
            while (ptr != null) {
                if (tree.isRootVisible || ptr.parentPath != null) {
                    candidates.add(0, ptr)
                }
                ptr = ptr.parentPath
            }
            
            var isContainer = false
            val node = path.lastPathComponent
            if (node is DefaultMutableTreeNode) {
                if (node.allowsChildren && !node.isLeaf) {
                     isContainer = true
                } else {
                    val userObject = node.userObject
                    if (userObject is AbstractTreeNode<*>) {
                        val value = userObject.value
                        if (value is com.intellij.psi.PsiDirectory || 
                            value is com.intellij.psi.PsiDirectoryContainer ||
                            value is com.intellij.openapi.project.Project) {
                            isContainer = true
                        }
                    }
                }
            }
            
            // Fix: Strict vs Loose Comparison
            // Level 0 (Root/First Item): loose `<=` to ensure it sticks immediately at top (0).
            // Level > 0 (Nested): strict `<` to ensure it waits until it physically slides UNDER the parent sticky.
            // This prevents "Double Jump" (cascading stickiness) and hiding of content.
            
            val useStrict = newStickyPaths.isNotEmpty()
            
            if (isContainer) {
                val matches = if (useStrict) {
                     rowBounds.y < stickyBottom // Strict: Must be slightly under
                } else {
                     rowBounds.y <= stickyBottom + 1 // Loose: Catch 0/1px
                }
                
                if (matches) {
                    candidates.add(path)
                }
            }
            
            var addedAny = false
            for (candidate in candidates) {
                if (newStickyPaths.size >= maxStickyLimit) break
                if (newStickyPaths.contains(candidate)) continue
                
                if (newStickyPaths.isNotEmpty()) {
                    val last = newStickyPaths.last()
                    if (!last.isDescendant(candidate)) {
                        break 
                    }
                }
                
                newStickyPaths.add(candidate)
                currentStickyBottomRelative += rowHeight
                addedAny = true
            }
            
            if (!addedAny) break
        }
        
        // Push Logic
        var calculatedPushOffset = 0
        
        if (newStickyPaths.isNotEmpty()) {
            val lastSticky = newStickyPaths.last()
            val nextSibling = findNextSiblingOrCousin(lastSticky)
            
            if (nextSibling != null) {
                val nextRow = tree.getRowForPath(nextSibling)
                if (nextRow != -1) {
                    val bounds = tree.getRowBounds(nextRow)
                    if (bounds != null) {
                        val contentBottomY = bounds.y
                        val stackHeight = newStickyPaths.size * rowHeight
                        val visualStackBottomY = visibleRect.y + stackHeight
                        
                        val diff = visualStackBottomY - contentBottomY
                        if (diff > 0) {
                            calculatedPushOffset = diff
                            if (calculatedPushOffset > rowHeight) calculatedPushOffset = rowHeight
                        }
                    }
                }
            }
        }
        
        if (stickyPaths != newStickyPaths || pushOffset != calculatedPushOffset) {
            stickyPaths = newStickyPaths
            pushOffset = calculatedPushOffset
            repaint()
        }
    }
    
    private fun findNextSiblingOrCousin(path: TreePath): TreePath? {
        val parent = path.parentPath ?: return null
        val node = path.lastPathComponent
        val parentNode = parent.lastPathComponent
        val model = tree.model
        val count = model.getChildCount(parentNode)
        val idx = model.getIndexOfChild(parentNode, node)
        
        if (idx < count - 1) {
            val nextNode = model.getChild(parentNode, idx + 1)
            return parent.pathByAddingChild(nextNode)
        }
        return findNextSiblingOrCousin(parent)
    }

    private fun clearSticky() {
        if (stickyPaths.isNotEmpty()) {
            stickyPaths = emptyList()
            pushOffset = 0
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        if (stickyPaths.isEmpty()) return
        
        val g2 = g.create() as Graphics2D
        val rowHeight = getRowHeight()
        val colorManager = FileColorManager.getInstance(project)
        
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            // Draw REVERSE (Deepest first, Root last) for correct stacking
            for (i in stickyPaths.indices.reversed()) {
                val path = stickyPaths[i]
                var yPos = i * rowHeight
                
                if (i == stickyPaths.size - 1) {
                    yPos -= pushOffset
                }
                
                var bgColor: Color? = null
                val node = path.lastPathComponent
                var virtualFile: VirtualFile? = null
                
                if (node is DefaultMutableTreeNode) {
                     val userObject = node.userObject
                     if (userObject is AbstractTreeNode<*>) {
                         val value = userObject.value
                         if (value is PsiElement) {
                             virtualFile = value.containingFile?.virtualFile ?: (value as? com.intellij.psi.PsiDirectory)?.virtualFile
                         } else if (value is VirtualFile) {
                             virtualFile = value
                         }
                     }
                }
                
                if (virtualFile != null) {
                    try {
                        bgColor = colorManager.getFileColor(virtualFile)
                    } catch (e: Exception) { }
                }

                if (bgColor == null) {
                    bgColor = UIUtil.getTreeBackground()
                }
                
                drawRow(g2, path, yPos, rowHeight, bgColor)
            }
            
            val stackBottom = stickyPaths.size * rowHeight - pushOffset
            g2.color = JBColor.border()
            g2.drawLine(0, stackBottom - 1, width, stackBottom - 1)
            
        } finally {
            g2.dispose()
        }
    }
    
    private fun drawRow(g2: Graphics2D, path: TreePath, yPos: Int, rowHeight: Int, bgColor: Color) {
        val renderer = tree.cellRenderer
        val node = path.lastPathComponent
        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, true, false, tree.getRowForPath(path), false
        ) as JComponent
        
        // 1. Fill background manually with correct Scope Color
        g2.color = bgColor
        g2.fillRect(0, yPos, width, rowHeight)
        
        if (stickyPaths.indexOf(path) == hoverIndex) {
            g2.color = ColorUtil.withAlpha(JBColor.blue, 0.1)
            g2.fillRect(0, yPos, width, rowHeight)
        }
        
        val indent = (path.pathCount - 1) * JBUI.scale(20)
        val oldClip = g2.clip
        g2.clipRect(0, yPos, width, rowHeight)
        g2.translate(indent, yPos)
        
        // 2. Force component transparency so manual background shows through
        component.isOpaque = false 
        component.background = null
        
        component.foreground = UIUtil.getTreeForeground()
        // Ensure strictly sized to avoid painting over others? 
        // Clip rect handles it.
        component.bounds = Rectangle(0, 0, width - indent, rowHeight)
        component.validate()
        
        // Paint content
        component.paint(g2)
        
        g2.translate(-indent, -yPos)
        g2.clip = oldClip
        
        g2.color = JBColor.border()
        g2.drawLine(0, yPos + rowHeight - 1, width, yPos + rowHeight - 1)
    }
}
