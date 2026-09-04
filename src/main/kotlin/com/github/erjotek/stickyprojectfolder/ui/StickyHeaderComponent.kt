package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.MyBundle
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDirectory
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.copy.CopyHandler
import com.intellij.ui.ColorUtil
import com.intellij.ui.FileColorManager
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.*
import java.awt.event.*
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.*
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import com.github.erjotek.stickyprojectfolder.util.StickyScrollUtil
import com.github.erjotek.stickyprojectfolder.util.PathValidator.sanitizeForLog
import javax.swing.tree.TreePath

private val LOG = Logger.getInstance(StickyHeaderComponent::class.java)

class StickyHeaderComponent(
    private val project: Project,
    private val tree: JTree,
    private val scrollPane: JScrollPane,
    private val onBoundsUpdateNeeded: () -> Unit = {},
    private val onStickyHeaderClick: () -> Unit = {}
) : JComponent(), Disposable {

    fun getTree(): JTree = tree

    internal data class StickyRow(val path: TreePath, val indent: Int)

    private var stickyRows: List<StickyRow> = emptyList()
    private var pushOffset: Int = 0
    private var hoverIndex: Int = -1
    internal var cachedRowHeight: Int = JBUI.scale(22)

    private var dragAutoScrollTimer: Timer? = null
    private var dragAutoScrollDirection: Int = 0

    private val virtualFileCacheLock = Any()
    private val virtualFileCache = WeakHashMap<Any, VirtualFile?>()
    private val virtualFileLoading = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val colorLoading = mutableSetOf<String>()

    private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("StickyHeaderExecutor", 2)
    private val repaintAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    // Cache for file colors to avoid slow operations on EDT
    private val colorCache = mutableMapOf<String, Color?>()

    init {
        isOpaque = false

        // Enable drop target for drag & drop on sticky headers
        dropTarget = DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {
            override fun dragOver(e: DropTargetDragEvent) {
                val idx = getIndexAt(e.location.y)
                if (idx != -1 && isWithinTreeBounds(e.location.x)) {
                    e.acceptDrag(DnDConstants.ACTION_MOVE)
                    updateDragAutoScroll(e.location.y)
                    if (hoverIndex != idx) {
                        hoverIndex = idx
                        repaint()
                    }
                } else {
                    stopDragAutoScroll()
                    e.rejectDrag()
                }
            }

            override fun dragExit(dte: DropTargetEvent?) {
                stopDragAutoScroll()
                if (hoverIndex != -1) {
                    hoverIndex = -1
                    repaint()
                }
            }

            override fun drop(e: DropTargetDropEvent) {
                handleDrop(e)
            }
        }, true)

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isWithinTreeBounds(e.x)) return
                val idx = getIndexAt(e.y)
                val path = getPathAt(e.y)
                if (path != null && idx != -1) {
                    onStickyHeaderClick()
                    tree.selectionPath = path
                    val parentStickyCount = idx
                    StickyScrollUtil.scrollToMakeVisibleBelowSticky(tree, path, cachedRowHeight, parentStickyCount)
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
        addMouseWheelListener { e -> handleMouseWheel(e) }
    }

    private fun isWithinTreeBounds(x: Int): Boolean {
        return x >= 0 && x < width
    }

    private fun handleMouseWheel(e: MouseWheelEvent) {
        if (!isWithinTreeBounds(e.x)) return

        val targetBar = if (e.isShiftDown) scrollPane.horizontalScrollBar else scrollPane.verticalScrollBar
        val increment = targetBar.unitIncrement
        val delta = e.unitsToScroll * increment * 6
        val max = targetBar.maximum - targetBar.visibleAmount
        val nextValue = (targetBar.value + delta).coerceIn(0, max.coerceAtLeast(0))

        if (nextValue != targetBar.value) {
            targetBar.value = nextValue
        }
        e.consume()
    }

    override fun contains(x: Int, y: Int): Boolean {
        if (!isVisible || stickyRows.isEmpty()) return false
        if (x < 0 || x >= width) return false
        if (y < 0) return false

        val totalHeight = stickyRows.size * cachedRowHeight - pushOffset
        return y >= 0 && y < totalHeight
    }

    private fun getIndexAt(y: Int): Int {
        val rowHeight = cachedRowHeight
        val totalHeight = stickyRows.size * rowHeight - pushOffset
        if (y >= totalHeight) return -1

        val lastIdx = stickyRows.size - 1
        val lastItemTop = lastIdx * rowHeight - pushOffset
        val lastItemBottom = lastItemTop + rowHeight

        if (y >= lastItemTop && y < lastItemBottom) return lastIdx

        val stableHeight = lastIdx * rowHeight
        if (y < stableHeight) return y / rowHeight

        return -1
    }

    private fun getPathAt(y: Int): TreePath? {
        val idx = getIndexAt(y)
        return if (idx in stickyRows.indices) stickyRows[idx].path else null
    }

    private fun getTargetDirectoryAt(y: Int): PsiDirectory? {
        val idx = getIndexAt(y)
        if (idx !in stickyRows.indices) return null

        val stickyRow = stickyRows[idx]
        val node = stickyRow.path.lastPathComponent
        val value = extractValueFromNode(node)
        if (value is PsiDirectory) return value

        val vf = extractVirtualFileFromNode(node)
        if (vf != null) {
            val element = resolvePsiElement(vf, com.intellij.psi.PsiManager.getInstance(project))
            if (element is PsiDirectory) return element
        }

        return null
    }

    private fun handleDrop(e: DropTargetDropEvent) {
        LOG.info("handleDrop called at ${e.location}")

        stopDragAutoScroll()

        if (!isWithinTreeBounds(e.location.x)) {
            LOG.info("Drop rejected - not within tree bounds")
            e.rejectDrop()
            return
        }

        val targetDir = ApplicationManager.getApplication().runReadAction(Computable {
            getTargetDirectoryAt(e.location.y)
        })
        if (targetDir == null) {
            LOG.info("Drop rejected - no target directory at y=${e.location.y}")
            e.rejectDrop()
            return
        }

        LOG.info("Target directory found")

        val transferable = e.transferable
        LOG.info("Available flavors: ${transferable.transferDataFlavors.map { it.mimeType }}")

        val psiElements = ApplicationManager.getApplication().runReadAction(Computable {
            extractPsiElementsFromTransferable(transferable)
        })

        if (psiElements.isEmpty()) {
            LOG.info("Drop rejected - no supported transferable data")
            e.rejectDrop()
            return
        }

        val action = e.dropAction
        val acceptedAction = when (action) {
            DnDConstants.ACTION_COPY -> DnDConstants.ACTION_COPY
            DnDConstants.ACTION_MOVE -> DnDConstants.ACTION_MOVE
            else -> DnDConstants.ACTION_COPY_OR_MOVE
        }

        e.acceptDrop(acceptedAction)

        ApplicationManager.getApplication().invokeLater {
            try {
                val selectedAction = when (action) {
                    DnDConstants.ACTION_COPY -> DnDConstants.ACTION_COPY
                    DnDConstants.ACTION_MOVE -> DnDConstants.ACTION_MOVE
                    else -> {
                        when (Messages.showYesNoCancelDialog(
                            project,
                            MyBundle.message("drop.dialog.message"),
                            MyBundle.message("drop.dialog.title"),
                            MyBundle.message("drop.dialog.move"),
                            MyBundle.message("drop.dialog.copy"),
                            MyBundle.message("drop.dialog.cancel"),
                            null
                        )) {
                            Messages.YES -> DnDConstants.ACTION_MOVE
                            Messages.NO -> DnDConstants.ACTION_COPY
                            else -> null
                        }
                    }
                }

                when (selectedAction) {
                    DnDConstants.ACTION_COPY -> CopyHandler.doCopy(psiElements, targetDir)
                    DnDConstants.ACTION_MOVE -> com.intellij.refactoring.move.MoveHandler.doMove(
                        project,
                        psiElements,
                        targetDir,
                        null,
                        null
                    )
                    else -> Unit
                }
            } catch (ex: Exception) {
                LOG.warn("Move failed", ex)
            }
        }

        e.dropComplete(true)
        hoverIndex = -1
        repaint()
    }

    private fun extractPsiElementsFromTransferable(transferable: Transferable): Array<PsiElement> {
        val elements = mutableListOf<PsiElement>()
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            var data: Any? = null
            try {
                data = transferable.getTransferData(DataFlavor.javaFileListFlavor)
            } catch (e: Exception) {
                if (e is java.awt.datatransfer.UnsupportedFlavorException || e is java.io.IOException) {
                    // ignore
                } else {
                    LOG.error("Failed to get transfer data for javaFileListFlavor", e)
                }
            }
            if (data is List<*>) {
                val lfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                data.asSequence()
                    .filterIsInstance<java.io.File>()
                    .mapNotNull { lfs.findFileByIoFile(it) }
                    .mapNotNull { resolvePsiElement(it, psiManager) }
                    .forEach { elements.add(it) }
            }
        }

        if (elements.isNotEmpty()) return elements.toTypedArray()

        for (flavor in transferable.transferDataFlavors) {
            var data: Any? = null
            try {
                data = transferable.getTransferData(flavor)
            } catch (e: Exception) {
                if (e is java.awt.datatransfer.UnsupportedFlavorException || e is java.io.IOException) {
                    continue
                } else {
                    LOG.error("Failed to get transfer data for flavor ${sanitizeForLog(flavor.mimeType)}", e)
                    continue
                }
            }

            if (data == null) continue

            when (data) {
                is Array<*> -> {
                    data.asSequence()
                        .filterIsInstance<VirtualFile>()
                        .mapNotNull { resolvePsiElement(it, psiManager) }
                        .forEach { elements.add(it) }

                    data.filterIsInstance<PsiElement>().forEach { elements.add(it) }
                }
                is Collection<*> -> {
                    data.asSequence()
                        .filterIsInstance<VirtualFile>()
                        .mapNotNull { resolvePsiElement(it, psiManager) }
                        .forEach { elements.add(it) }

                    data.filterIsInstance<PsiElement>().forEach { elements.add(it) }
                }
            }
        }

        return elements.distinct().toTypedArray()
    }

    private fun resolvePsiElement(vf: VirtualFile, psiManager: com.intellij.psi.PsiManager): PsiElement? {
        return if (vf.isDirectory) {
            psiManager.findDirectory(vf)
        } else {
            psiManager.findFile(vf)
        }
    }

    private fun updateDragAutoScroll(y: Int) {
        if (!isShowing) {
            stopDragAutoScroll()
            return
        }

        val stickyHeight = getStickyHeight()
        if (stickyHeight <= 0) {
            stopDragAutoScroll()
            return
        }

        val zone = (JBUI.scale(24)).coerceAtMost(stickyHeight / 2)
        if (zone <= 0) {
            stopDragAutoScroll()
            return
        }
        val direction = if (y < zone) -1 else 0

        if (direction == 0) {
            stopDragAutoScroll()
            return
        }

        dragAutoScrollDirection = direction

        if (dragAutoScrollTimer == null) {
            dragAutoScrollTimer = Timer(25) {
                performDragAutoScrollStep()
            }.also { timer ->
                timer.initialDelay = 0
                timer.start()
            }
        }
    }

    private fun performDragAutoScrollStep() {
        val sp = scrollPane
        if (!sp.isShowing) {
            stopDragAutoScroll()
            return
        }

        val bar = sp.verticalScrollBar
        val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
        if (max == 0) return

        val step = (bar.unitIncrement * 6).coerceAtLeast(JBUI.scale(20))
        val nextValue = (bar.value + (step * dragAutoScrollDirection)).coerceIn(0, max)
        if (nextValue != bar.value) {
            bar.value = nextValue
        }
    }

    private fun resolveVirtualFileForPainting(node: Any?): VirtualFile? {
        if (node == null) return null

        val cached = synchronized(virtualFileCacheLock) {
            if (virtualFileCache.containsKey(node)) {
                return@synchronized virtualFileCache[node]
            }

            if (!virtualFileLoading.contains(node)) {
                virtualFileLoading.add(node)
                backgroundExecutor.execute {
                    val vf = ApplicationManager.getApplication().runReadAction(Computable {
                        extractVirtualFileFromNode(node)
                    })

                    synchronized(virtualFileCacheLock) {
                        virtualFileCache[node] = vf
                        virtualFileLoading.remove(node)
                    }

                    repaintAlarm.cancelAllRequests()
                    repaintAlarm.addRequest({ repaint() }, 50)
                }
            }

            return@synchronized null
        }

        return cached
    }

    private fun stopDragAutoScroll() {
        dragAutoScrollTimer?.stop()
        dragAutoScrollTimer = null
        dragAutoScrollDirection = 0
    }

    private fun moveFilesToDirectory(files: List<java.io.File>, targetDir: PsiDirectory) {
        LOG.info("moveFilesToDirectory called: ${files.size} files")

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val lfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val psiElements = files.mapNotNull { file ->
            ApplicationManager.getApplication().runReadAction(Computable {
                val vf = lfs.findFileByIoFile(file)
                if (vf != null) resolvePsiElement(vf, psiManager) else null
            })
        }.toTypedArray()

        LOG.info("Found ${psiElements.size} PSI elements to move")

        if (psiElements.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    com.intellij.refactoring.move.MoveHandler.doMove(
                        project,
                        psiElements,
                        targetDir,
                        null,
                        null
                    )
                    LOG.info("MoveHandler.doMove completed")
                } catch (ex: Exception) {
                    LOG.warn("Move failed", ex)
                }
            }
        }
    }

    internal fun calculateRowHeight(): Int? {
        val rowHeight = tree.rowHeight
        if (rowHeight > 0) return rowHeight

        if (tree.rowCount > 0) {
            val visibleRow = tree.getClosestRowForLocation(0, tree.visibleRect.y)
            val visibleBounds = if (visibleRow >= 0) tree.getRowBounds(visibleRow) else null
            if (visibleBounds != null && visibleBounds.height > 0) return visibleBounds.height

            val firstBounds = tree.getRowBounds(0)
            if (firstBounds != null && firstBounds.height > 0) return firstBounds.height
        }

        return null
    }

    fun getStickyHeight(): Int {
        return (stickyRows.size * cachedRowHeight - pushOffset).coerceAtLeast(0)
    }

    fun update(forceRepaint: Boolean = false) {
        if (tree.rowCount == 0) {
            if (stickyRows.isNotEmpty()) clearSticky()
            return
        }

        val visibleRect = tree.visibleRect
        if (visibleRect.height <= 0 || visibleRect.width <= 0) {
            return
        }

        if (visibleRect.y == 0) {
            if (stickyRows.isNotEmpty()) {
                clearSticky()
                onBoundsUpdateNeeded()
            }
            return
        }

        val calculatedHeight = calculateRowHeight()
        if (calculatedHeight != null) {
            cachedRowHeight = calculatedHeight
        }
        val rowHeight = cachedRowHeight

        val settings = com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings.instance
        val maxStickyLimit = settings.state.maxStickyLimit

        val newStickyRows = mutableListOf<StickyRow>()
        
        // Probe at the bottom of where sticky stack would be to find what's visible there
        // This ensures we catch folders as soon as they touch the sticky bottom
        var currentProbeY = visibleRect.y
        
        while (newStickyRows.size < maxStickyLimit) {
            // Find the row at the current probe position (bottom of sticky stack)
            val probeRow = tree.getClosestRowForLocation(0, currentProbeY + 1)
            if (probeRow == -1) break
            
            val probePath = tree.getPathForRow(probeRow) ?: break
            tree.getRowBounds(probeRow) ?: break
            
            // Build list of parent containers for this row (from root to current)
            val candidates = ArrayDeque<TreePath>()
            var ptr: TreePath? = probePath
            while (ptr != null) {
                val node = ptr.lastPathComponent
                if (isContainerNode(node)) {
                    candidates.addFirst(ptr) // Add at beginning to get root-first order
                }
                ptr = ptr.parentPath
            }
            
            // Find the next candidate that should become sticky
            var addedAny = false
            for (candidatePath in candidates) {
                if (newStickyRows.size >= maxStickyLimit) break
                if (newStickyRows.any { it.path == candidatePath }) continue
                
                // Verify this is a child of the last sticky (if any)
                if (newStickyRows.isNotEmpty()) {
                    val lastSticky = newStickyRows.last().path
                    if (!lastSticky.isDescendant(candidatePath)) continue
                }
                
                val candidateRow = tree.getRowForPath(candidatePath)
                if (candidateRow == -1) continue
                
                val rowBounds = tree.getRowBounds(candidateRow) ?: continue
                
                // Calculate where the sticky stack bottom currently is
                val currentStickyBottom = visibleRect.y + (newStickyRows.size * rowHeight)
                
                // A folder becomes sticky when its top edge touches or passes the sticky bottom
                if (rowBounds.y <= currentStickyBottom) {
                    val indent = rowBounds.x
                    newStickyRows.add(StickyRow(candidatePath, indent))
                    currentProbeY = visibleRect.y + (newStickyRows.size * rowHeight)
                    addedAny = true
                    break // Re-probe at new position
                }
            }
            
            if (!addedAny) break
        }

        // Push Logic
        var calculatedPushOffset = 0

        if (newStickyRows.isNotEmpty()) {
            val lastSticky = newStickyRows.last().path
            val nextSibling = findNextSiblingOrCousin(lastSticky)

            if (nextSibling != null) {
                val nextRow = tree.getRowForPath(nextSibling)
                if (nextRow != -1) {
                    val bounds = tree.getRowBounds(nextRow)
                    if (bounds != null) {
                        val stackHeight = newStickyRows.size * rowHeight
                        val visualStackBottomY = visibleRect.y + stackHeight

                        if (bounds.y < visualStackBottomY) {
                            calculatedPushOffset = (visualStackBottomY - bounds.y).coerceIn(0, rowHeight)
                        }
                    }
                }
            }
        }

        // Commit changes
        var stateChanged = false

        val areSemanticallyEqual = if (stickyRows.size == newStickyRows.size) {
            stickyRows.zip(newStickyRows).all { (old, new) ->
                old.indent == new.indent &&
                    old.path.lastPathComponent.toString() == new.path.lastPathComponent.toString()
            }
        } else false

        if (!areSemanticallyEqual || pushOffset != calculatedPushOffset) {
            stickyRows = newStickyRows
            pushOffset = calculatedPushOffset
            onBoundsUpdateNeeded()
            stateChanged = true
        } else if (stickyRows != newStickyRows) {
            stickyRows = newStickyRows
        }

        if (stateChanged || forceRepaint) {
            repaint()
        }
    }

    override fun dispose() {
        repaintAlarm.cancelAllRequests()
        backgroundExecutor.shutdownNow()
    }

    fun isAffectedBy(e: TreeModelEvent): Boolean {
        if (stickyRows.isEmpty()) return false

        val parentPath = e.treePath
        val indices = e.childIndices
        if (indices == null || indices.isEmpty()) {
            return stickyRows.any { it.path == parentPath }
        }

        val children = e.children ?: return false
        for (child in children) {
            val childPath = parentPath.pathByAddingChild(child)
            if (stickyRows.any { it.path == childPath }) return true
        }
        return false
    }

    private fun findNextSiblingOrCousin(path: TreePath): TreePath? {
        val parent = path.parentPath ?: return null
        val node = path.lastPathComponent

        val parentNode = parent.lastPathComponent
        val model = tree.model
        val childCount = model.getChildCount(parentNode)

        for (i in 0 until childCount) {
            val child = model.getChild(parentNode, i)
            if (child === node && i + 1 < childCount) {
                return parent.pathByAddingChild(model.getChild(parentNode, i + 1))
            }
        }

        return findNextSiblingOrCousin(parent)
    }

    private fun isContainerNode(node: Any?): Boolean {
        if (node == null) return false
        return !tree.model.isLeaf(node)
    }

    private fun extractValueFromNode(node: Any?): Any? {
        val candidate = when (node) {
            is DefaultMutableTreeNode -> node.userObject
            else -> node
        }

        return if (candidate is AbstractTreeNode<*>) {
            candidate.value
        } else {
            candidate
        }
    }

    private fun extractVirtualFileFromNode(node: Any?): VirtualFile? {
        return VirtualFileExtractor.extractVirtualFileFromNode(node)
    }

    private fun clearSticky() {
        if (stickyRows.isNotEmpty()) {
            stickyRows = emptyList()
            pushOffset = 0
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        if (stickyRows.isEmpty()) return

        val g2 = g.create() as Graphics2D
        val rowHeight = cachedRowHeight

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            for (i in stickyRows.indices.reversed()) {
                val stickyRow = stickyRows[i]
                var yPos = i * rowHeight

                if (i == stickyRows.size - 1) {
                    yPos -= pushOffset
                }

                drawRow(g2, stickyRow, yPos, rowHeight)
            }

            val stackBottom = stickyRows.size * rowHeight - pushOffset
            g2.color = JBColor.border()
            g2.drawLine(0, stackBottom - 1, width, stackBottom - 1)

        } finally {
            g2.dispose()
        }
    }

    private fun drawRow(g2: Graphics2D, stickyRow: StickyRow, yPos: Int, rowHeight: Int) {
        val renderer = tree.cellRenderer
        val path = stickyRow.path
        val node = path.lastPathComponent
        val row = tree.getRowForPath(path)
        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, true, false, row, false
        ) as JComponent

        val virtualFile = resolveVirtualFileForPainting(node)

        val defaultBg = stickyFillColor(tree)

        val bgColor = if (virtualFile != null) {
            val cacheKey = virtualFile.path
            synchronized(virtualFileCacheLock) {
                if (colorCache.containsKey(cacheKey)) {
                    stickyFillColor(tree, colorCache[cacheKey])
                } else {
                    if (!colorLoading.contains(cacheKey)) {
                        colorLoading.add(cacheKey)
                        backgroundExecutor.execute {
                            val color = ApplicationManager.getApplication().runReadAction(Computable {
                                try {
                                    val colorManager = FileColorManager.getInstance(project)
                                    if (colorManager.isEnabled && colorManager.isEnabledForProjectView) {
                                        colorManager.getFileColor(virtualFile)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            })
                            synchronized(virtualFileCacheLock) {
                                colorCache[cacheKey] = color
                                colorLoading.remove(cacheKey)
                            }
                            repaintAlarm.cancelAllRequests()
                            repaintAlarm.addRequest({ repaint() }, 50)
                        }
                    }
                    defaultBg
                }
            }
        } else {
            defaultBg
        }

        g2.color = bgColor
        g2.fillRect(0, yPos, width, rowHeight)

        if (stickyRows.indexOf(stickyRow) == hoverIndex) {
            g2.color = ColorUtil.withAlpha(JBColor.blue, 0.1)
            g2.fillRect(0, yPos, width, rowHeight)
        }

        val indent = stickyRow.indent

        val oldClip = g2.clip
        g2.clipRect(0, yPos, width, rowHeight)
        g2.translate(indent, yPos)

        component.isOpaque = false
        component.background = null

        component.foreground = UIUtil.getTreeForeground()
        component.bounds = Rectangle(0, 0, width - indent, rowHeight)
        component.validate()

        component.paint(g2)

        g2.translate(-indent, -yPos)
        g2.clip = oldClip
    }
}
