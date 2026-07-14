package com.github.erjotek.stickyprojectfolder.ui

import com.github.erjotek.stickyprojectfolder.settings.PinnedFoldersSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JTree
import com.intellij.openapi.util.Computable
import com.intellij.ui.FileColorManager
import com.intellij.ui.ColorUtil
import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import java.awt.dnd.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import com.intellij.psi.PsiElement
import com.intellij.refactoring.copy.CopyHandler
import com.intellij.openapi.ui.Messages
import javax.swing.JScrollPane
import javax.swing.Timer
import javax.swing.SwingUtilities


class PinnedFooterComponent(
    private val project: Project,
    private val tree: JTree,
    private val onHeightChanged: () -> Unit,
    private val onPinClick: (VirtualFile) -> Unit
) : JComponent() {

    private var pinnedItems: List<PinnedItemRenderData> = emptyList()
    private val rowHeight = JBUI.scale(22)
    private var hoverIndex: Int = -1
    private var dragAutoScrollTimer: Timer? = null
    private var dragAutoScrollDirection: Int = 0

    data class PinnedItemRenderData(
        val item: com.github.erjotek.stickyprojectfolder.settings.PinnedFolderItem,
        val virtualFile: VirtualFile,
        val cachedColor: Color?
    )

    companion object {
        private val LOG = Logger.getInstance(PinnedFooterComponent::class.java)
    }

    init {
        isOpaque = false
        isVisible = false
        
        // Enable drop target for drag & drop
        dropTarget = DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {
            override fun dragOver(e: DropTargetDragEvent) {
                val idx = getIndexAt(e.location.y)
                if (idx != -1 && idx in pinnedItems.indices) {
                    e.acceptDrag(DnDConstants.ACTION_MOVE)
                    updateDragAutoScroll(e.location.y)
                    if (hoverIndex != idx) {
                        hoverIndex = idx
                        repaint()
                    }
                } else {
                    // Hovering over empty space or non-existent, scroll tree down
                    e.rejectDrag()
                    hoverIndex = -1
                    repaint()
                    updateDragAutoScroll(e.location.y)
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
        
        val mouseAdapter = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = getIndexAt(e.y)
                if (index in pinnedItems.indices) {
                    val data = pinnedItems[index]
                    onPinClick(data.virtualFile)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val index = getIndexAt(e.y)
                if (index != hoverIndex) {
                    hoverIndex = index
                    cursor = if (index != -1 && index in pinnedItems.indices) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
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
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        
        addMouseWheelListener { e ->
            if (e.unitsToScroll > 0) {
                val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane
                if (sp != null) {
                    val targetBar = if (e.isShiftDown) sp.horizontalScrollBar else sp.verticalScrollBar
                    val increment = targetBar.unitIncrement
                    val delta = e.unitsToScroll * increment * 6
                    val max = targetBar.maximum - targetBar.visibleAmount
                    val nextValue = (targetBar.value + delta).coerceIn(0, max.coerceAtLeast(0))

                    if (nextValue != targetBar.value) {
                        targetBar.value = nextValue
                    }
                }
            }
            e.consume()
        }
    }

    fun update() {
        val settings = PinnedFoldersSettings.getInstance(project)
        val basePath = project.basePath ?: return

        pinnedItems = settings.state.pinnedFolders.mapNotNull { item ->
            val fullPath = "$basePath/${item.path.trimEnd('/')}"
            val file = File(fullPath)
            // refresh, which is a slow op banned on the EDT; update() runs on EDT on every scroll.
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
                ?: return@mapNotNull null

            val fileColor = ApplicationManager.getApplication().runReadAction(Computable {
                try {
                    val colorManager = FileColorManager.getInstance(project)
                    if (colorManager.isEnabled && colorManager.isEnabledForProjectView) {
                        colorManager.getFileColor(virtualFile)
                    } else null
                } catch (e: Exception) {
                    LOG.warn("Failed to get file color for ${item.path}", e)
                    null
                }
            })
            PinnedItemRenderData(item, virtualFile, fileColor)
        }

        val separatorHeight = if (pinnedItems.isNotEmpty()) JBUI.scale(1) else 0
        val newHeight = pinnedItems.size * getEffectiveRowHeight() + separatorHeight
        if (preferredSize.height != newHeight) {
            preferredSize = Dimension(tree.width, newHeight)
            onHeightChanged()
        }
        repaint()
    }
    
    private fun getEffectiveRowHeight(): Int {
        return if (tree.rowHeight > 0) tree.rowHeight else rowHeight
    }

    private fun getIndexAt(y: Int): Int {
        val rh = getEffectiveRowHeight()
        if (rh == 0) return -1
        val separatorHeight = if (pinnedItems.isNotEmpty()) JBUI.scale(1) else 0
        val adjustedY = y - separatorHeight
        if (adjustedY < 0) return -1
        return adjustedY / rh
    }

    private fun updateDragAutoScroll(y: Int) {
        if (!isShowing) {
            stopDragAutoScroll()
            return
        }

        val componentHeight = height
        if (componentHeight <= 0) {
            stopDragAutoScroll()
            return
        }

        val zone = (JBUI.scale(24)).coerceAtMost(componentHeight / 2)
        if (zone <= 0) {
            stopDragAutoScroll()
            return
        }
        
        // Scroll down if hovering near the bottom of the component
        val direction = when {
            y > componentHeight - zone -> 1
            else -> 0
        }

        if (direction == 0) {
            stopDragAutoScroll()
            return
        }

        dragAutoScrollDirection = direction

        if (dragAutoScrollTimer == null) {
            dragAutoScrollTimer = Timer(25) {
                val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane
                if (sp != null && sp.isShowing) {
                    val bar = sp.verticalScrollBar
                    val max = (bar.maximum - bar.visibleAmount).coerceAtLeast(0)
                    if (max > 0) {
                        val step = (bar.unitIncrement * 6).coerceAtLeast(JBUI.scale(20))
                        val nextValue = (bar.value + step * dragAutoScrollDirection).coerceIn(0, max)
                        if (nextValue != bar.value) {
                            bar.value = nextValue
                        }
                    }
                }
            }.also { timer ->
                timer.initialDelay = 0
                timer.start()
            }
        }
    }

    private fun stopDragAutoScroll() {
        dragAutoScrollTimer?.stop()
        dragAutoScrollTimer = null
        dragAutoScrollDirection = 0
    }

    private fun handleDrop(e: DropTargetDropEvent) {
        stopDragAutoScroll()
        val idx = getIndexAt(e.location.y)
        if (idx !in pinnedItems.indices) {
            e.rejectDrop()
            return
        }
        val data = pinnedItems[idx]
        val targetDir = ApplicationManager.getApplication().runReadAction(Computable {
            PsiManager.getInstance(project).findDirectory(data.virtualFile)
        })
        if (targetDir == null) {
            e.rejectDrop()
            return
        }

        val transferable = e.transferable
        val psiElements = ApplicationManager.getApplication().runReadAction(Computable {
            extractPsiElements(transferable)
        })

        if (psiElements.isEmpty()) {
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
                            "Do you want to move or copy?",
                            "Drop",
                            "Move",
                            "Copy",
                            "Cancel",
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
                LOG.warn("Failed to perform drop action", ex)
            }
        }
        e.dropComplete(true)
        hoverIndex = -1
        repaint()
    }

    private fun extractPsiElements(transferable: Transferable): Array<PsiElement> {
        val elements = mutableListOf<PsiElement>()
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                val data = transferable.getTransferData(DataFlavor.javaFileListFlavor)
                if (data is List<*>) {
                    val lfs = LocalFileSystem.getInstance()
                    val psiManager = PsiManager.getInstance(project)
                    for (file in data.filterIsInstance<java.io.File>()) {
                        val vf = lfs.findFileByIoFile(file) ?: continue
                        val psi = if (vf.isDirectory) psiManager.findDirectory(vf) else psiManager.findFile(vf)
                        if (psi != null) elements.add(psi)
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Failed to extract PSI elements from transferable", e)
            }
        }
        return elements.toTypedArray()
    }

    override fun paintComponent(g: Graphics) {
        if (pinnedItems.isEmpty()) return

        val g2 = g.create() as Graphics2D
        val rh = getEffectiveRowHeight()

        val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane
        val state = StickyProjectSettings.instance.state
        val adjustWidth = if (state.avoidTransparentScrollbarOverlap && sp != null) {
            val vsb = sp.verticalScrollBar
            if (vsb != null && vsb.isVisible && !vsb.isOpaque) {
                vsb.width
            } else 0
        } else 0
        val effectiveWidth = if (adjustWidth > 0) tree.width - adjustWidth else tree.width

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            // Draw top separator line to visually separate pinned items from the file tree
            val separatorHeight = JBUI.scale(1)
            g2.color = JBColor.border()
            g2.fillRect(0, 0, width, separatorHeight)

            for (i in pinnedItems.indices) {
                val item = pinnedItems[i]
                val y = i * rh + separatorHeight

                // Background
                val isHovered = i == hoverIndex
                val bg = tree.background ?: UIUtil.getTreeBackground()
                val fileColor = item.cachedColor

                g2.color = fileColor ?: bg
                g2.fillRect(0, y, effectiveWidth, rh)

                if (isHovered) {
                    g2.color = ColorUtil.withAlpha(JBColor.blue, 0.1)
                    g2.fillRect(0, y, effectiveWidth, rh)
                }

                // Icon
                val icon = com.intellij.util.IconUtil.getIcon(item.virtualFile, 0, project)
                val iconY = y + (rh - icon.iconHeight) / 2
                icon.paintIcon(this, g2, JBUI.scale(5), iconY)

                // Text
                g2.font = tree.font
                g2.color = UIUtil.getTreeForeground()
                val text = item.item.description
                g2.drawString(text, JBUI.scale(25), y + rh - JBUI.scale(6))
            }
        } finally {
            g2.dispose()
        }
    }
}
