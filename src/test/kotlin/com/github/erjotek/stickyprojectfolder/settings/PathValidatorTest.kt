package com.github.erjotek.stickyprojectfolder.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidRelativePathInsideProject() {
        val baseDir = tempFolder.newFolder("project")
        val subDir = File(baseDir, "subdir").apply { mkdirs() }

        assertTrue(PathValidator.isPathValid(baseDir.absolutePath, "subdir"))
    }

    @Test
    fun testPathTraversalOutsideProject() {
        val baseDir = tempFolder.newFolder("project")
        val outsideFile = tempFolder.newFile("secret.txt")

        // ../secret.txt (relative to project)
        val relativePath = "../${outsideFile.name}"
        assertFalse(PathValidator.isPathValid(baseDir.absolutePath, relativePath))
    }

    @Test
    fun testValidPathWithDotsInsideProject() {
        val baseDir = tempFolder.newFolder("project")
        val subDir = File(baseDir, "subdir").apply { mkdirs() }
        val nested = File(subDir, "nested").apply { mkdirs() }

        // subdir/../subdir/nested
        val relativePath = "subdir/../subdir/nested"
        assertTrue(PathValidator.isPathValid(baseDir.absolutePath, relativePath))
    }

    @Test
    fun testAbsolutePathAsRelativeInjection() {
        val baseDir = tempFolder.newFolder("project")
        val subDir = File(baseDir, "etc").apply { mkdirs() }
        val target = File(subDir, "passwd").apply { createNewFile() }

        // Input: /etc/passwd
        // Should resolve to project/etc/passwd and return true because we force relative interpretation
        assertTrue(PathValidator.isPathValid(baseDir.absolutePath, "/etc/passwd"))
    }

    @Test
    fun testAbsolutePathPointingOutside() {
        val baseDir = tempFolder.newFolder("project")
        val outsideDir = tempFolder.newFolder("outside")
        val outsideFile = File(outsideDir, "file.txt").apply { createNewFile() }

        // If we pass absolute path of outsideFile
        // It becomes project/absolute/path/to/outside/file.txt which likely doesn't exist
        assertFalse(PathValidator.isPathValid(baseDir.absolutePath, outsideFile.absolutePath))
    }

    @Test
    fun testNonExistentPath() {
        val baseDir = tempFolder.newFolder("project")
        assertFalse(PathValidator.isPathValid(baseDir.absolutePath, "doesnotexist"))
    }
}
