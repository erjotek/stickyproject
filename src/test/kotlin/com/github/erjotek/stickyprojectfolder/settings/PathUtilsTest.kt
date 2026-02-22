package com.github.erjotek.stickyprojectfolder.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PathUtilsTest {

    @Test
    fun testEmptyList() {
        val result = PathUtils.calculateNestedPaths(emptyList())
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun testNoNestedPaths() {
        val paths = listOf("a", "b", "c/d")
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun testSimpleNesting() {
        val paths = listOf("a", "a/b")
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(setOf("a/b"), result)
    }

    @Test
    fun testMultiLevelNesting() {
        val paths = listOf("a", "a/b", "a/b/c", "d", "d/e")
        // "a/b" is nested in "a"
        // "a/b/c" is nested in "a" (and "a/b")
        // "d/e" is nested in "d"
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(setOf("a/b", "a/b/c", "d/e"), result)
    }

    @Test
    fun testTrailingSlashes() {
        // The implementation trims trailing slashes for comparison
        val paths = listOf("a/", "a/b/")
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(setOf("a/b/"), result)
    }

    @Test
    fun testMixedSlashes() {
        val paths = listOf("a", "a/b/")
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(setOf("a/b/"), result)
    }

    @Test
    fun testPartialMatchIsNotNested() {
        val paths = listOf("apple", "applepie")
        val result = PathUtils.calculateNestedPaths(paths)
        // "applepie" starts with "apple", but not "apple/"
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun testDuplicatePaths() {
        // Technically set/list usage.
        val paths = listOf("a", "a", "a/b")
        val result = PathUtils.calculateNestedPaths(paths)
        assertEquals(setOf("a/b"), result)
    }
}
