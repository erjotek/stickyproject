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
        var autoCollapsePaths: String? = null,
        @com.intellij.util.xmlb.annotations.OptionTag("autoCollapsePathsList")
        var autoCollapsePathsList: MutableList<String> = mutableListOf("app/node_modules/", "app/vendor/", "node_modules/", "vendor/", "build/", "dist/"),
        var avoidTransparentScrollbarOverlap: Boolean = false,
        var stickyControlBlocks: Boolean = true,
        var stickyArrayScopes: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        if (myState.autoCollapsePaths != null) {
            val legacyPaths = myState.autoCollapsePaths!!.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            myState.autoCollapsePathsList.clear()
            myState.autoCollapsePathsList.addAll(legacyPaths)
            myState.autoCollapsePaths = null
        }
    }

    companion object {
        val instance: StickyProjectSettings
            get() = ApplicationManager.getApplication().getService(StickyProjectSettings::class.java)
    }
}
