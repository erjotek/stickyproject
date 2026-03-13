package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages

import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import com.github.erjotek.stickyprojectfolder.util.PathValidator

import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.github.erjotek.stickyprojectfolder.util.PathValidator as SecurePathValidator
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

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

    private var pinnedTableModel: ListTableModel<PinnedFolderItem>? = null
    private var pinnedTable: JBTable? = null
    private var pinnedPanel: JPanel? = null
    private var avoidTransparentScrollbarOverlapCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "Sticky Project"

    override fun getPreferredFocusedComponent(): JComponent? = maxStickyLimitSpinner

    override fun createComponent(): JComponent {
        maxStickyLimitSpinner = JBIntSpinner(10, 1, 100)
        autoCollapseEnabledCheckbox = JBCheckBox("Enable auto-collapse directories (global settings)")
        autoCollapseIncludeExcludedCheckbox = JBCheckBox("Auto-collapse excluded folders")
        avoidTransparentScrollbarOverlapCheckbox = JBCheckBox("Adjust sticky width for transparent scrollbar")

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
            .addComponent(avoidTransparentScrollbarOverlapCheckbox!!, JBUI.scale(2))
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

        // --- Pinned Folders fieldset ---
        val pathColumnInfo = object : ColumnInfo<PinnedFolderItem, String>("Path") {
            override fun valueOf(item: PinnedFolderItem): String = item.path
            override fun isCellEditable(item: PinnedFolderItem): Boolean = false
        }
        val descColumnInfo = object : ColumnInfo<PinnedFolderItem, String>("Description") {
            override fun valueOf(item: PinnedFolderItem): String = item.description
            override fun isCellEditable(item: PinnedFolderItem): Boolean = true
            override fun setValue(item: PinnedFolderItem, value: String) {
                item.description = value
            }
        }

        val pinnedSettings = PinnedFoldersSettings.getInstance(project)
        pinnedTableModel = ListTableModel<PinnedFolderItem>(
            arrayOf(pathColumnInfo, descColumnInfo),
            pinnedSettings.state.pinnedFolders.map { it.copy() }.toMutableList()
        )
        pinnedTable = JBTable(pinnedTableModel!!).apply {
            setShowGrid(false)
            tableHeader.reorderingAllowed = false
            columnModel.getColumn(0).preferredWidth = JBUI.scale(250)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(150)
        }

        // Custom renderer: gray out non-existent paths (both path and description columns)
        pinnedTable!!.setDefaultRenderer(Any::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: javax.swing.JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

                // Prevent HTML injection in JTable cells
                if (comp is JComponent) {
                    comp.putClientProperty("html.disable", true)
                }

                if (!isSelected) {
                    val item = pinnedTableModel?.items?.getOrNull(row)
                    if (item != null) {
                        val basePath = project.basePath
                        val pathExists = if (basePath != null) {
                            PathValidator.validatePath(basePath, item.path) != null &&
                                File("$basePath/${item.path.trimEnd('/')}").exists()
                        } else false
                        foreground = if (pathExists) {
                            JBColor.namedColor("Label.foreground", JBColor.foreground())
                        } else {
                            JBColor.namedColor("Label.disabledForeground", JBColor.GRAY)
                        }
                    }
                }
                return comp
            }
        })

        val pinnedToolbar = ToolbarDecorator.createDecorator(pinnedTable!!)
            .setAddAction { addPinnedFolder() }
            .setRemoveAction { removePinnedFolder() }
            .setMoveUpAction { movePinnedUp() }
            .setMoveDownAction { movePinnedDown() }

        pinnedPanel = pinnedToolbar.createPanel().apply {
            preferredSize = Dimension(0, JBUI.scale(150))
        }

        val pinnedInnerPanel = FormBuilder.createFormBuilder()
            .addComponent(pinnedPanel!!, 1)
            .panel

        val pinnedFieldset = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Pinned folders",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION
            )
            add(pinnedInnerPanel, BorderLayout.CENTER)
        }

        mySettingsComponent = FormBuilder.createFormBuilder()
            .addComponent(stickyFieldset, JBUI.scale(4))
            .addComponent(autoCollapseFieldset, JBUI.scale(8))
            .addComponent(pinnedFieldset, JBUI.scale(8))
            .addComponentFillVertically(JPanel(), 0)
            .panel

        updateExcludedPanelState()

        return mySettingsComponent!!
    }

private fun addPinnedFolder() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Folder to Pin"
        val projectDir = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
        
        FileChooser.chooseFile(descriptor, project, projectDir) { selectedFile ->
            val basePath = project.basePath ?: return@chooseFile
            val selectedPath = selectedFile.path

            if (selectedPath.startsWith(basePath)) {
                var relativePath = selectedPath.removePrefix(basePath).removePrefix("/")
                if (relativePath.isNotEmpty() && !relativePath.endsWith("/")) {
                    relativePath += "/"
                }

                if (relativePath.isNotEmpty()) {
                    val validPath = PathValidator.validatePath(basePath, relativePath)
                    if (validPath != null) {
                        val description = generateDefaultDescription(relativePath)
                        val newItem = PinnedFolderItem(relativePath, description)
                        pinnedTableModel?.insertRow(0, newItem)
                    }
                }
            }
        }
    }

    private fun generateDefaultDescription(path: String): String {
        val segments = path.trimEnd('/').split("/")
        if (segments.isEmpty()) return "Folder"
        val last = segments.last()
        val existingDescriptions = pinnedTableModel?.items?.map { it.description }?.toSet() ?: emptySet()
        if (!existingDescriptions.contains(last)) return last
        if (segments.size > 1) {
            val secondLast = segments[segments.size - 2]
            return "$secondLast/$last"
        }
        return last
    }

    private fun removePinnedFolder() {
        val index = pinnedTable?.selectedRow ?: return
        if (index >= 0) {
            pinnedTableModel?.removeRow(index)
        }
    }

    private fun movePinnedUp() {
        val index = pinnedTable?.selectedRow ?: return
        if (index > 0) {
            pinnedTableModel?.exchangeRows(index, index - 1)
            pinnedTable?.setRowSelectionInterval(index - 1, index - 1)
        }
    }

    private fun movePinnedDown() {
        val index = pinnedTable?.selectedRow ?: return
        if (index < (pinnedTableModel?.rowCount ?: 0) - 1) {
            pinnedTableModel?.exchangeRows(index, index + 1)
            pinnedTable?.setRowSelectionInterval(index + 1, index + 1)
        }
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

            var relativePath = SecurePathValidator.getValidatedRelativePath(basePath, selectedPath)
            if (relativePath != null) {
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

    private fun removePath() {
        val selectedIndex = pathsList?.selectedIndex ?: return
        if (selectedIndex >= 0) {
            pathsListModel?.remove(selectedIndex)
        }
    }

    private fun getPathsFromModel(): List<String> {
        val paths = mutableListOf<String>()
        for (i in 0 until (pathsListModel?.size ?: 0)) {
            pathsListModel?.get(i)?.let { paths.add(it) }
        }
        return paths
    }

    private fun setPathsToModel(paths: List<String>) {
        pathsListModel?.clear()
        paths.map { it.trim() }
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
                val relativePath = SecurePathValidator.getValidatedRelativePath(basePath, path)
                if (relativePath != null) {
                    var finalPath = relativePath
                    if (finalPath.isNotEmpty() && !finalPath.endsWith("/")) {
                        finalPath += "/"
                    }
                    finalPath.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
            .distinct()
            .sorted()
    }

    override fun isModified(): Boolean {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        val pinnedSettings = PinnedFoldersSettings.getInstance(project)
        val currentItems = pinnedTableModel?.items ?: emptyList()
        val pinnedModified = currentItems.size != pinnedSettings.state.pinnedFolders.size ||
            currentItems.zip(pinnedSettings.state.pinnedFolders).any { (current, saved) ->
                current.path != saved.path || current.description != saved.description
            }

        return maxStickyLimitSpinner?.number != settings.state.maxStickyLimit ||
            autoCollapseEnabledCheckbox?.isSelected != settings.state.autoCollapseEnabled ||
            avoidTransparentScrollbarOverlapCheckbox?.isSelected != settings.state.avoidTransparentScrollbarOverlap ||
            autoCollapseIncludeExcludedCheckbox?.isSelected != projectSettings.state.autoCollapseIncludeExcluded ||
            getPathsFromModel() != settings.state.autoCollapsePathsList ||
            pinnedModified
    }

    override fun apply() {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        settings.state.maxStickyLimit = maxStickyLimitSpinner?.number ?: 10
        settings.state.autoCollapseEnabled = autoCollapseEnabledCheckbox?.isSelected ?: true
        settings.state.avoidTransparentScrollbarOverlap = avoidTransparentScrollbarOverlapCheckbox?.isSelected ?: false
        projectSettings.state.autoCollapseIncludeExcluded = autoCollapseIncludeExcludedCheckbox?.isSelected ?: false
        settings.state.autoCollapsePathsList = getPathsFromModel().toMutableList()

        val pinnedSettings = PinnedFoldersSettings.getInstance(project)
        pinnedSettings.state.pinnedFolders = pinnedTableModel?.items?.map { it.copy() }?.toMutableList() ?: mutableListOf()
    }

    override fun reset() {
        val settings = StickyProjectSettings.instance
        val projectSettings = StickyProjectProjectSettings.getInstance(project)
        maxStickyLimitSpinner?.number = settings.state.maxStickyLimit
        autoCollapseEnabledCheckbox?.isSelected = settings.state.autoCollapseEnabled
        avoidTransparentScrollbarOverlapCheckbox?.isSelected = settings.state.avoidTransparentScrollbarOverlap
        autoCollapseIncludeExcludedCheckbox?.isSelected = projectSettings.state.autoCollapseIncludeExcluded
        setPathsToModel(settings.state.autoCollapsePathsList)
        updateExcludedPathsModel()
        updateExcludedPanelState()

        val pinnedSettings = PinnedFoldersSettings.getInstance(project)
        pinnedTableModel?.items = pinnedSettings.state.pinnedFolders.map { it.copy() }.toMutableList()
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
        maxStickyLimitSpinner = null
        autoCollapseEnabledCheckbox = null
        autoCollapseIncludeExcludedCheckbox = null
        avoidTransparentScrollbarOverlapCheckbox = null
        pathsListModel = null
        pathsList = null
        excludedPathsListModel = null
        excludedPathsList = null
        excludedListPanel = null
        excludedReadOnlyLabel = null
        pinnedTableModel = null
        pinnedTable = null
        pinnedPanel = null
    }

    private inner class PathListCellRenderer(
        private val model: ListModel<String>
    ) : ListCellRenderer<String> {
        private val label = JLabel().apply {
            putClientProperty("html.disable", true)
        }
        private val folderIcon = UIManager.getIcon("FileView.directoryIcon")
        private var nestedPaths: Set<String> = emptySet()

        init {
            model.addListDataListener(object : ListDataListener {
                override fun intervalAdded(e: ListDataEvent) = recalculate()
                override fun intervalRemoved(e: ListDataEvent) = recalculate()
                override fun contentsChanged(e: ListDataEvent) = recalculate()
            })
            recalculate()
        }

        private fun recalculate() {
            val paths = (0 until model.size).map { model.getElementAt(it) }
            nestedPaths = PathUtils.calculateNestedPaths(paths)
        }

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
                SecurePathValidator.validatePath(basePath, value)?.let { File(it).exists() } ?: false
            } else {
                false
            }

            val isNested = value?.let { nestedPaths.contains(it) } == true

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
