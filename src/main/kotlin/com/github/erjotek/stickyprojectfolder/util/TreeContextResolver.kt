package com.github.erjotek.stickyprojectfolder.util

import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingUtilities

data class TreeContext(val tree: JTree, val scrollPane: JScrollPane)

object TreeContextResolver {

    fun resolve(project: Project, pane: AbstractProjectViewPane?): TreeContext? {
        fun toContext(tree: JTree): TreeContext? {
            val sp = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane ?: return null
            return TreeContext(tree, sp)
        }

        resolveFocusedProjectViewTree(project)?.let { focusedTree ->
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

    private fun resolveFocusedProjectViewTree(project: Project): JTree? {
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
}
