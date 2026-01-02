package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class StickyProjectConfigurable : Configurable {

    private var mySettingsComponent: JPanel? = null
    private var maxStickyLimitSpinner: JBIntSpinner? = null

    override fun getDisplayName(): String = "Sticky Project"

    override fun getPreferredFocusedComponent(): JComponent? = maxStickyLimitSpinner

    override fun createComponent(): JComponent {
        maxStickyLimitSpinner = JBIntSpinner(10, 1, 100)

        mySettingsComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Max sticky directories (1-100):"), maxStickyLimitSpinner!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mySettingsComponent!!
    }

    override fun isModified(): Boolean {
        val settings = StickyProjectSettings.instance
        return maxStickyLimitSpinner?.number != settings.state.maxStickyLimit
    }

    override fun apply() {
        val settings = StickyProjectSettings.instance
        settings.state.maxStickyLimit = maxStickyLimitSpinner?.number ?: 10
    }

    override fun reset() {
        val settings = StickyProjectSettings.instance
        maxStickyLimitSpinner?.number = settings.state.maxStickyLimit
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
        maxStickyLimitSpinner = null
    }
}
