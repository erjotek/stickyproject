package com.github.erjotek.stickyprojectfolder.util

import com.github.erjotek.stickyprojectfolder.settings.StickyProjectSettings
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreePath

object StickyScrollUtil {

    fun scrollToMakeVisibleBelowSticky(tree: JTree, path: TreePath, rowHeight: Int, stickyCount: Int? = null) {
        val row = tree.getRowForPath(path)
        if (row == -1) return

        val rowBounds = tree.getRowBounds(row) ?: return

        val effectiveStickyCount = if (stickyCount != null) {
            stickyCount
        } else {
            val settings = StickyProjectSettings.instance
            val maxStickyLimit = settings.state.maxStickyLimit

            val ancestors = path.pathCount - 1
            ancestors.coerceAtMost(maxStickyLimit)
        }

        val projectedStickyHeight = effectiveStickyCount * rowHeight

        val targetY = (rowBounds.y - projectedStickyHeight - 3).coerceAtLeast(0)

        val visibleRect = tree.visibleRect

        val scrollPane = SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane::class.java, tree) as? javax.swing.JScrollPane
        if (scrollPane != null) {
            scrollPane.verticalScrollBar.value = targetY
        } else {
            tree.scrollRectToVisible(Rectangle(visibleRect.x, targetY, visibleRect.width, visibleRect.height))
        }
    }
}
