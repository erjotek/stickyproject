package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "StickyProjectProjectSettings",
    storages = [Storage("StickyProjectProjectSettings.xml")]
)
class StickyProjectProjectSettings : PersistentStateComponent<StickyProjectProjectSettings.State> {

    data class State(
        var autoCollapseIncludeExcluded: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): StickyProjectProjectSettings =
            project.getService(StickyProjectProjectSettings::class.java)
    }
}
