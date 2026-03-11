package com.github.erjotek.stickyprojectfolder.util

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Paths

object PathValidator {
    private val LOG = Logger.getInstance(PathValidator::class.java)

    fun sanitizeForLog(input: String): String {
        return input.replace('\n', '_').replace('\r', '_')
    }

    /** Characters that must not appear in paths to prevent injection attacks. */
    private val FORBIDDEN_CHARS = charArrayOf(';', '\u0000', '\n', '\r')

    /**
     * Validates that the relative path, when resolved against the base path,
     * stays within the base path directory and does not contain forbidden characters.
     *
     * @param basePath The absolute path of the project base directory.
     * @param relativePath The relative path to append.
     * @return The normalized absolute path as String if valid, or null if it traverses outside or is invalid.
     */
    fun validatePath(basePath: String, relativePath: String): String? {
        try {
            if (basePath.isBlank() || relativePath.isBlank()) return null

            // Check for forbidden characters
            if (FORBIDDEN_CHARS.any { c -> relativePath.contains(c) }) {
                LOG.warn("Path contains forbidden characters: '${sanitizeForLog(relativePath)}'")
                return null
            }

            val base = Paths.get(basePath).toAbsolutePath().normalize()
            val resolved = base.resolve(relativePath).toAbsolutePath().normalize()

            if (resolved.startsWith(base)) {
                return resolved.toString().replace('\\', '/')
            } else {
                LOG.warn("Path traversal detected: '${sanitizeForLog(relativePath)}' attempts to escape '${sanitizeForLog(basePath)}'")
            }
        } catch (e: Exception) {
            LOG.warn("Invalid path: '${sanitizeForLog(relativePath)}' relative to '${sanitizeForLog(basePath)}'", e)
        }
        return null
    }

    /**
     * Validates that the target path is contained within the base path and returns the relative path.
     * This is useful for validating absolute paths from file choosers or external sources.
     *
     * @param basePath The absolute path of the project base directory.
     * @param targetPath The absolute path to validate.
     * @return The normalized relative path (relative to basePath) if valid, or null if invalid or traverses outside.
     */
    fun getValidatedRelativePath(basePath: String, targetPath: String): String? {
        try {
            if (basePath.isBlank() || targetPath.isBlank()) return null

            val base = Paths.get(basePath).toAbsolutePath().normalize()
            val target = Paths.get(targetPath).toAbsolutePath().normalize()

            if (target.startsWith(base)) {
                val relative = base.relativize(target).toString().replace('\\', '/')
                return relative
            } else {
                LOG.warn("Path traversal detected: '${sanitizeForLog(targetPath)}' is not inside '${sanitizeForLog(basePath)}'")
            }
        } catch (e: Exception) {
            LOG.warn("Invalid path: '${sanitizeForLog(targetPath)}' relative to '${sanitizeForLog(basePath)}'", e)
        }
        return null
    }
}
