package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager

@State(
    name = "StickyProjectSettings",
    storages = [Storage("StickyProjectSettings.xml")]
)
class StickyProjectSettings : PersistentStateComponent<StickyProjectSettings.State> {

    data class State(
        var maxStickyLimit: Int = 10,
        var autoCollapseEnabled: Boolean = true,
        var autoCollapsePaths: String = "app/node_modules/;app/vendor/"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: StickyProjectSettings
            get() = ApplicationManager.getApplication().getService(StickyProjectSettings::class.java)
    }
}
