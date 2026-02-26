package com.github.erjotek.stickyprojectfolder.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

class PathValidatorTest {

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
}
