package com.github.erjotek.stickyprojectfolder.settings

import java.io.File
import java.nio.file.InvalidPathException

object PathValidator {
    /**
     * Checks if the relative path resolves to a file inside the base path and exists.
     *
     * @param basePath The base directory path (e.g. project root).
     * @param relativePath The path relative to the base directory.
     * @return true if the path is valid and exists, false otherwise.
     */
    fun isPathValid(basePath: String, relativePath: String): Boolean {
        try {
            val baseFile = File(basePath).canonicalFile.toPath()
            // We concatenate manually to force relative interpretation,
            // preventing absolute paths in relativePath from being treated as absolute.
            val fullPathString = "$basePath/${relativePath.trimEnd('/', '\\')}"
            val targetFile = File(fullPathString).canonicalFile.toPath()

            // Check if targetFile starts with baseFile
            if (!targetFile.startsWith(baseFile)) {
                return false
            }

            // Check if file exists
            return targetFile.toFile().exists()
        } catch (e: Exception) {
            // Handle IO exceptions or InvalidPathException
            return false
        }
    }
}
