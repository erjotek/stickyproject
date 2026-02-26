package com.github.erjotek.stickyprojectfolder.util

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Paths

object PathValidator {
    private val LOG = Logger.getInstance(PathValidator::class.java)

    /**
     * Validates that the relative path, when resolved against the base path,
     * stays within the base path directory.
     *
     * @param basePath The absolute path of the project base directory.
     * @param relativePath The relative path to append.
     * @return The normalized absolute path as String if valid, or null if it traverses outside or is invalid.
     */
    fun validatePath(basePath: String, relativePath: String): String? {
        try {
            if (basePath.isBlank() || relativePath.isBlank()) return null

            val base = Paths.get(basePath).toAbsolutePath().normalize()
            val resolved = base.resolve(relativePath).toAbsolutePath().normalize()

            if (resolved.startsWith(base)) {
                return resolved.toString().replace('\\', '/')
            } else {
                LOG.warn("Path traversal detected: '$relativePath' attempts to escape '$basePath'")
            }
        } catch (e: Exception) {
            LOG.warn("Invalid path: '$relativePath' relative to '$basePath'", e)
        }
        return null
    }
}
