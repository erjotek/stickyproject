package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder

class StickyProjectConfigurable(
    private val project: Project
) : Configurable {

    private var mySettingsComponent: JPanel? = null
    private var maxStickyLimitSpinner: JBIntSpinner? = null
    private var autoCollapseEnabledCheckbox: JBCheckBox? = null
    private var autoCollapseIncludeExcludedCheckbox: JBCheckBox? = null
    private var pathsListModel: DefaultListModel<String>? = null
    private var pathsList: JBList<String>? = null
    private var excludedPathsListModel: DefaultListModel<String>? = null
    private var excludedPathsList: JBList<String>? = null
    private var excludedListPanel: JPanel? = null
    private var excludedReadOnlyLabel: JBLabel? = null

    override fun getDisplayName(): String = "Sticky Project"

    override fun getPreferredFocusedComponent(): JComponent? = maxStickyLimitSpinner

    override fun createComponent(): JComponent {
        maxStickyLimitSpinner = JBIntSpinner(10, 1, 100)
        autoCollapseEnabledCheckbox = JBCheckBox("Enable auto-collapse directories (global settings)")
        autoCollapseIncludeExcludedCheckbox = JBCheckBox("Auto-collapse excluded folders")

        pathsListModel = DefaultListModel<String>()
        pathsList = JBList<String>(pathsListModel!!).apply {
            cellRenderer = PathListCellRenderer(pathsListModel!!)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }

        val toolbarDecorator = ToolbarDecorator.createDecorator(pathsList!!)
            .setAddAction { addPath() }
            .setRemoveAction { removePath() }
            .disableUpDownActions()

        val listPanel = toolbarDecorator.createPanel().apply {
            preferredSize = Dimension(0, JBUI.scale(150))
        }

        val legendPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            val grayDot = JLabel("\u25CF").apply {
                foreground = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
                font = font.deriveFont(font.size2D * 0.85f)
            }
            val grayLabel = JLabel("Ignored – does not exist in this project").apply {
                font = font.deriveFont(font.size2D * 0.85f)
            }
            val orangeDot = JLabel("\u25CF").apply {
                foreground = JBColor.namedColor("ColorPalette.YELLOW", JBColor(0x8A6D00, 0xFFD24D))
                font = font.deriveFont(font.size2D * 0.85f)
            }
            val orangeLabel = JLabel("Ignored – covered by another path").apply {
                font = font.deriveFont(font.size2D * 0.85f)
            }
            add(grayDot)
            add(grayLabel)
            add(Box.createHorizontalStrut(JBUI.scale(12)))
            add(orangeDot)
            add(orangeLabel)
        }

        excludedPathsListModel = DefaultListModel<String>()
        excludedPathsList = JBList<String>(excludedPathsListModel!!).apply {
            cellRenderer = PathListCellRenderer(excludedPathsListModel!!)
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }
        excludedListPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JScrollPane(excludedPathsList).apply {
                preferredSize = Dimension(0, JBUI.scale(120))
            })
            add(Box.createVerticalStrut(JBUI.scale(6)))
        }
        updateExcludedPathsModel()

        autoCollapseIncludeExcludedCheckbox?.addActionListener {
            updateExcludedPanelState()
        }

        val separatorPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(JBUI.scale(10), 0, JBUI.scale(4), 0)
            add(JSeparator(), BorderLayout.CENTER)
        }

        val autoCollapseInnerPanel = FormBuilder.createFormBuilder()
            .addComponent(autoCollapseEnabledCheckbox!!, JBUI.scale(4))
            .addLabeledComponent(JBLabel("Auto-collapse paths (relative to project root):"), listPanel, 1, true)
            .addComponent(legendPanel, JBUI.scale(2))
            .addComponent(separatorPanel, JBUI.scale(2))
            .addComponent(autoCollapseIncludeExcludedCheckbox!!, JBUI.scale(2))
            .addLabeledComponent(JBLabel("Excluded paths (read-only list from project settings):"), excludedListPanel!!, 1, true)
            .panel

        val autoCollapseFieldset = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Auto-collapse settings",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION
            )
            add(autoCollapseInnerPanel, BorderLayout.CENTER)
        }

        val stickyInnerPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Max sticky directories (1-100):"), maxStickyLimitSpinner!!, 1, false)
            .panel

        val stickyFieldset = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Sticky settings",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION
            )
            add(stickyInnerPanel, BorderLayout.CENTER)
        }

        mySettingsComponent = FormBuilder.createFormBuilder()
            .addComponent(stickyFieldset, JBUI.scale(4))
            .addComponent(autoCollapseFieldset, JBUI.scale(8))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        updateExcludedPanelState()

        return mySettingsComponent!!
    }

    private fun addPath() {
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
                    sortPathsListModel()
                }
            }
        }
    }

    private fun isNestedPath(value: String, model: ListModel<String>): Boolean {
        val normalizedValue = value.trimEnd('/')
        if (normalizedValue.isEmpty()) return false
        for (i in 0 until model.size) {
            val other = model.getElementAt(i)
            val normalizedOther = other.trimEnd('/')
            if (normalizedOther.isEmpty() || normalizedOther == normalizedValue) continue
            if (normalizedValue.startsWith("$normalizedOther/")) {
                return true
            }
        }
        return false
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
        sortPathsListModel()
    }

    private fun updateExcludedPanelState() {
        val enabled = autoCollapseIncludeExcludedCheckbox?.isSelected == true
        setPanelEnabled(excludedListPanel, enabled)
        excludedReadOnlyLabel?.isEnabled = enabled
    }

    private fun setPanelEnabled(component: JComponent?, enabled: Boolean) {
        component?.isEnabled = enabled
        component?.components?.forEach { child ->
            if (child is JComponent) {
                setPanelEnabled(child, enabled)
            }
        }
    }

    private fun sortPathsListModel() {
        val model = pathsListModel ?: return
        val items = (0 until model.size).mapNotNull { index -> model.get(index) }.sorted()
        model.clear()
        items.forEach { model.addElement(it) }
    }

    private fun updateExcludedPathsModel() {
        val model = excludedPathsListModel ?: return
        model.clear()
        getExcludedPaths().forEach { model.addElement(it) }
    }

    private fun getExcludedPaths(): List<String> {
        val basePath = project.basePath ?: return emptyList()
        val excludedRoots = ModuleManager.getInstance(project).modules
            .flatMap { module ->
                ModuleRootManager.getInstance(module).contentEntries
                    .flatMap { entry -> entry.excludeFolderFiles.toList() }
            }
        return excludedRoots
            .mapNotNull { root ->
                val path = root.path
                if (!path.startsWith(basePath)) {
                    null
                } else {
                    var relativePath = path.removePrefix(basePath).removePrefix("/")
                    if (relativePath.isNotEmpty() && !relativePath.endsWith("/")) {
                        relativePath += "/"
                    }
                    relativePath.takeIf { it.isNotEmpty() }
                }
            }
            .distinct()
            .sorted()
    }

    override fun isModified(): Boolean {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        return maxStickyLimitSpinner?.number != settings.state.maxStickyLimit ||
            autoCollapseEnabledCheckbox?.isSelected != settings.state.autoCollapseEnabled ||
            autoCollapseIncludeExcludedCheckbox?.isSelected != projectSettings.state.autoCollapseIncludeExcluded ||
            getPathsFromModel() != settings.state.autoCollapsePaths
    }

    override fun apply() {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        settings.state.maxStickyLimit = maxStickyLimitSpinner?.number ?: 10
        settings.state.autoCollapseEnabled = autoCollapseEnabledCheckbox?.isSelected ?: true
        projectSettings.state.autoCollapseIncludeExcluded = autoCollapseIncludeExcludedCheckbox?.isSelected ?: false
        settings.state.autoCollapsePaths = getPathsFromModel()
    }

    override fun reset() {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        maxStickyLimitSpinner?.number = settings.state.maxStickyLimit
        autoCollapseEnabledCheckbox?.isSelected = settings.state.autoCollapseEnabled
        autoCollapseIncludeExcludedCheckbox?.isSelected = projectSettings.state.autoCollapseIncludeExcluded
        setPathsToModel(settings.state.autoCollapsePaths)
        updateExcludedPathsModel()
        updateExcludedPanelState()
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
        maxStickyLimitSpinner = null
        autoCollapseEnabledCheckbox = null
        autoCollapseIncludeExcludedCheckbox = null
        pathsListModel = null
        pathsList = null
        excludedPathsListModel = null
        excludedPathsList = null
        excludedListPanel = null
        excludedReadOnlyLabel = null
    }

    private inner class PathListCellRenderer(
        private val model: ListModel<String>
    ) : ListCellRenderer<String> {
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

            val basePath = project.basePath
            val pathExists = if (basePath != null && value != null) {
                val fullPath = "$basePath/${value.trimEnd('/')}"
                File(fullPath).exists()
            } else {
                false
            }

            val isNested = value?.let { isNestedPath(it, model) } == true

            if (isSelected) {
                label.background = list?.selectionBackground
                label.foreground = list?.selectionForeground
            } else {
                label.background = list?.background
                label.foreground = if (isNested) {
                    JBColor.namedColor("ColorPalette.YELLOW", JBColor(0x8A6D00, 0xFFD24D))
                } else if (pathExists) {
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
