package com.github.erjotek.stickyprojectfolder.util

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFileSystemItem
import javax.swing.tree.DefaultMutableTreeNode

object VirtualFileExtractor {
    fun extractVirtualFileFromNode(node: Any?): VirtualFile? {
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
        }

        return null
    }
}
