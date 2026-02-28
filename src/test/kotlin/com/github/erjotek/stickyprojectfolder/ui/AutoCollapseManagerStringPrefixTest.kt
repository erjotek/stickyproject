package com.github.erjotek.stickyprojectfolder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class AutoCollapseManagerStringPrefixTest {
    @Test
    fun testStringPrefix() {
        val basePath = "/home/user/project"
        val path = "/home/user/project-secret"

        val result1 = if (!path.startsWith(basePath)) null else path.removePrefix(basePath).removePrefix("/")
        assertEquals("-secret", result1) // vulnerable

        val result2 = com.github.erjotek.stickyprojectfolder.util.PathValidator.getValidatedRelativePath(basePath, path)
        assertEquals(null, result2) // secure
    }
}
