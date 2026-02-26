package com.github.erjotek.stickyprojectfolder.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Paths

class PathValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidPathInsideProject() {
        // Construct absolute path based on current directory to ensure consistency
        val currentDir = Paths.get(".").toAbsolutePath().normalize()
        val basePath = currentDir.resolve("project/root").toString()
        val relative = "src/main"

        val expected = currentDir.resolve("project/root/src/main").toString()
        val result = PathValidator.validatePath(basePath, relative)

        // Normalize separators for comparison just in case
        assertEquals(expected.replace("\\", "/"), result?.replace("\\", "/"))
    }

    @Test
    fun testPathTraversalOutsideProject() {
        val currentDir = Paths.get(".").toAbsolutePath().normalize()
        val basePath = currentDir.resolve("project/root").toString()

        // Attempts to go up two levels: ../../secret -> /project/secret (outside root)
        val relative = "../../secret"

        val result = PathValidator.validatePath(basePath, relative)
        assertNull("Should return null for path traversal attempt outside base", result)
    }

    @Test
    fun testPathTraversalInsideProjectAllowed() {
        val currentDir = Paths.get(".").toAbsolutePath().normalize()
        val basePath = currentDir.resolve("project/root").toString()

        // Go into src, then up to root, then into build -> /project/root/build
        val relative = "src/../build"

        val expected = currentDir.resolve("project/root/build").toString()
        val result = PathValidator.validatePath(basePath, relative)

        assertEquals(expected.replace("\\", "/"), result?.replace("\\", "/"))
    }

    @Test
    fun testEmptyPath() {
        assertNull(PathValidator.validatePath("", "foo"))
        assertNull(PathValidator.validatePath("/foo", ""))
    }

    @Test
    fun testPathWithDotDotAtEnd() {
        val currentDir = Paths.get(".").toAbsolutePath().normalize()
        val basePath = currentDir.resolve("project/root").toString()

        // src/.. resolves to . inside base -> /project/root
        val relative = "src/.."

        val expected = basePath
        val result = PathValidator.validatePath(basePath, relative)

        assertEquals(expected.replace("\\", "/"), result?.replace("\\", "/"))
    }

    // New tests for isPathValid and isDescendant

    @Test
    fun testIsPathValid_Exists() {
        val root = tempFolder.newFolder("root")
        val child = File(root, "child").apply { mkdir() }

        assertTrue(PathValidator.isPathValid(root.absolutePath, "child"))
    }

    @Test
    fun testIsPathValid_DoesNotExist() {
        val root = tempFolder.newFolder("root")

        assertFalse(PathValidator.isPathValid(root.absolutePath, "nonexistent"))
    }

    @Test
    fun testIsPathValid_TraversalAttempt() {
        val root = tempFolder.newFolder("root")
        val secret = tempFolder.newFile("secret")

        // secret is outside root
        // ../secret
        val relative = "../${secret.name}"
        assertFalse(PathValidator.isPathValid(root.absolutePath, relative))
    }

    @Test
    fun testIsDescendant_True() {
        val root = tempFolder.newFolder("root")
        val child = File(root, "child")

        assertTrue(PathValidator.isDescendant(root.absolutePath, child.absolutePath))
    }

    @Test
    fun testIsDescendant_False() {
        val root = tempFolder.newFolder("root")
        val other = tempFolder.newFolder("other")

        assertFalse(PathValidator.isDescendant(root.absolutePath, other.absolutePath))
    }

    @Test
    fun testIsDescendant_SamePath() {
        val root = tempFolder.newFolder("root")
        assertTrue(PathValidator.isDescendant(root.absolutePath, root.absolutePath))
    }

    @Test
    fun testIsDescendant_NestedDeep() {
        val root = tempFolder.newFolder("root")
        val nested = File(root, "a/b/c")
        assertTrue(PathValidator.isDescendant(root.absolutePath, nested.absolutePath))
    }
}
