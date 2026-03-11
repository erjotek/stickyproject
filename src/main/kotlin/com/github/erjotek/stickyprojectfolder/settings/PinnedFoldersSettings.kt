package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.github.erjotek.stickyprojectfolder.util.PathValidator.sanitizeForLog

@State(
    name = "PinnedFoldersSettings",
    storages = [Storage("PinnedFoldersSettings.xml")]
)
class PinnedFoldersSettings : PersistentStateComponent<PinnedFoldersSettings.State> {

    data class State(
        @get:XCollection(style = XCollection.Style.v2)
        var pinnedFolders: MutableList<PinnedFolderItem> = mutableListOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        val sanitized = state.pinnedFolders.filter { item ->
            isPathSafe(item.path)
        }.toMutableList()
        state.pinnedFolders = sanitized
        myState = state
    }

    companion object {
        private val LOG = Logger.getInstance(PinnedFoldersSettings::class.java)

        /** Characters that must not appear in pinned folder paths. */
        private val FORBIDDEN_CHARS = charArrayOf(';', '\u0000', '\n', '\r')

        fun getInstance(project: Project): PinnedFoldersSettings = project.service()

        /**
         * Validates that a path is safe to use:
         * - Not blank
         * - No path traversal sequences (..)
         * - No forbidden characters (semicolons, null bytes, newlines)
         */
        internal fun isPathSafe(path: String): Boolean {
            if (path.isBlank()) return false

            // Pinned folder paths must be relative to the project root, never absolute
            if (path.startsWith("/") || path.startsWith("\\") || (path.length >= 2 && path[1] == ':')) {
                LOG.warn("Rejecting absolute pinned folder path: '${sanitizeForLog(path)}'")
                return false
            }

            // Check for path traversal
            val normalized = path.replace('\\', '/')
            val segments = normalized.split('/')
            if (segments.any { it == ".." }) {
                LOG.warn("Rejecting pinned folder path with traversal: '${sanitizeForLog(path)}'")
                return false
            }

            // Check for forbidden characters
            if (FORBIDDEN_CHARS.any { c -> path.contains(c) }) {
                LOG.warn("Rejecting pinned folder path with forbidden characters: '${sanitizeForLog(path)}'")
                return false
            }

            return true
        }
    }
}

@Tag("pinned-folder")
data class PinnedFolderItem(
    var path: String = "",
    var description: String = ""
)
