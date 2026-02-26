package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.application.ApplicationManager

@State(
    name = "StickyProjectSettings",
    storages = [Storage("StickyProjectSettings.xml")]
)
class StickyProjectSettings : PersistentStateComponent<StickyProjectSettings.State> {

    data class State(
        var maxStickyLimit: Int = 10,
        var autoCollapseEnabled: Boolean = true,
        @Deprecated("Use autoCollapsePathsList instead")
        var autoCollapsePaths: String? = null,
        @com.intellij.util.xmlb.annotations.OptionTag("autoCollapsePathsList")
        var autoCollapsePathsList: MutableList<String> = mutableListOf("app/node_modules/", "app/vendor/", "node_modules/", "vendor/", "build/", "dist/"),
        var avoidTransparentScrollbarOverlap: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // Migration from old String format to List format
        if (myState.autoCollapsePaths != null) {
            val oldPaths = myState.autoCollapsePaths!!
            // Always clear the default list if we have a legacy setting (even if empty)
            myState.autoCollapsePathsList.clear()

            if (oldPaths.isNotBlank()) {
                myState.autoCollapsePathsList.addAll(
                    oldPaths.split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                )
            }
            // Clear the old field so migration doesn't run again and it gets removed from XML eventually
            myState.autoCollapsePaths = null
        }
    }

    companion object {
        val instance: StickyProjectSettings
            get() = ApplicationManager.getApplication().getService(StickyProjectSettings::class.java)
    }
}
