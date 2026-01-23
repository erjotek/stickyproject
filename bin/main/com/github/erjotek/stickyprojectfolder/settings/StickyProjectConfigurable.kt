package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.io.File
import javax.swing.*

class StickyProjectConfigurable : Configurable {

    private var mySettingsComponent: JPanel? = null
    private var maxStickyLimitSpinner: JBIntSpinner? = null
    private var autoCollapseEnabledCheckbox: JBCheckBox? = null
    private var pathsListModel: DefaultListModel<String>? = null
    private var pathsList: JBList<String>? = null

    override fun getDisplayName(): String = "Sticky Project"

    override fun getPreferredFocusedComponent(): JComponent? = maxStickyLimitSpinner

    override fun createComponent(): JComponent {
        maxStickyLimitSpinner = JBIntSpinner(10, 1, 100)
        autoCollapseEnabledCheckbox = JBCheckBox("Enable auto-collapse directories")
        
        pathsListModel = DefaultListModel<String>()
        pathsList = JBList<String>(pathsListModel!!).apply {
            cellRenderer = PathListCellRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }

        val toolbarDecorator = ToolbarDecorator.createDecorator(pathsList!!)
            .setAddAction { addPath() }
            .setRemoveAction { removePath() }
            .disableUpDownActions()

        val listPanel = toolbarDecorator.createPanel().apply {
            preferredSize = Dimension(0, JBUI.scale(150))
        }

        mySettingsComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Max sticky directories (1-100):"), maxStickyLimitSpinner!!, 1, false)
            .addSeparator()
            .addComponent(autoCollapseEnabledCheckbox!!, JBUI.scale(10))
            .addLabeledComponent(JBLabel("Auto-collapse paths (relative to project root):"), listPanel, 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mySettingsComponent!!
    }

    private fun addPath() {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Directory to Auto-Collapse"
        val projectDir = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
        if (projectDir != null) {
            descriptor.setRoots(projectDir)
        }

        FileChooser.chooseFile(descriptor, project, projectDir) { selectedFile ->
            val basePath = project.basePath ?: return@chooseFile
            val selectedPath = selectedFile.path

            if (selectedPath.startsWith(basePath)) {
                var relativePath = selectedPath.removePrefix(basePath).removePrefix("/")
                if (relativePath.isNotEmpty() && !relativePath.endsWith("/")) {
                    relativePath += "/"
                }

                if (relativePath.isNotEmpty() && !pathsListModel!!.contains(relativePath)) {
                    pathsListModel!!.addElement(relativePath)
                }
            }
        }
    }

    private fun removePath() {
        val selectedIndex = pathsList?.selectedIndex ?: return
        if (selectedIndex >= 0) {
            pathsListModel?.remove(selectedIndex)
        }
    }

    private fun getPathsFromModel(): String {
        val paths = mutableListOf<String>()
        for (i in 0 until (pathsListModel?.size ?: 0)) {
            pathsListModel?.get(i)?.let { paths.add(it) }
        }
        return paths.joinToString(";")
    }

    private fun setPathsToModel(pathsString: String) {
        pathsListModel?.clear()
        pathsString.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { pathsListModel?.addElement(it) }
    }

    override fun isModified(): Boolean {
        val settings = StickyProjectSettings.instance
        return maxStickyLimitSpinner?.number != settings.state.maxStickyLimit ||
            autoCollapseEnabledCheckbox?.isSelected != settings.state.autoCollapseEnabled ||
            getPathsFromModel() != settings.state.autoCollapsePaths
    }

    override fun apply() {
        val settings = StickyProjectSettings.instance
        settings.state.maxStickyLimit = maxStickyLimitSpinner?.number ?: 10
        settings.state.autoCollapseEnabled = autoCollapseEnabledCheckbox?.isSelected ?: true
        settings.state.autoCollapsePaths = getPathsFromModel()
    }

    override fun reset() {
        val settings = StickyProjectSettings.instance
        maxStickyLimitSpinner?.number = settings.state.maxStickyLimit
        autoCollapseEnabledCheckbox?.isSelected = settings.state.autoCollapseEnabled
        setPathsToModel(settings.state.autoCollapsePaths)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
        maxStickyLimitSpinner = null
        autoCollapseEnabledCheckbox = null
        pathsListModel = null
        pathsList = null
    }

    private inner class PathListCellRenderer : ListCellRenderer<String> {
        private val label = JLabel()
        private val folderIcon = UIManager.getIcon("FileView.directoryIcon")

        override fun getListCellRendererComponent(
            list: JList<out String>?,
            value: String?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            label.text = value ?: ""
            label.icon = folderIcon
            label.border = JBUI.Borders.empty(2, 4)

            val project = ProjectManager.getInstance().openProjects.firstOrNull()
            val basePath = project?.basePath
            val pathExists = if (basePath != null && value != null) {
                val fullPath = "$basePath/${value.trimEnd('/')}"
                File(fullPath).exists()
            } else {
                false
            }

            if (isSelected) {
                label.background = list?.selectionBackground
                label.foreground = list?.selectionForeground
            } else {
                label.background = list?.background
                label.foreground = if (pathExists) {
                    JBColor.namedColor("Label.foreground", JBColor.foreground())
                } else {
                    JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
                }
            }

            label.isOpaque = true
            return label
        }
    }
}
