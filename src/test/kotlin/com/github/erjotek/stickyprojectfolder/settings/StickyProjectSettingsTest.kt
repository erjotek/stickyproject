package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class StickyProjectSettingsTest : BasePlatformTestCase() {

    fun testDefaultState() {
        val settings = StickyProjectSettings()
        // Default values
        assertTrue(settings.state.autoCollapsePathsList.contains("node_modules/"))
        // Legacy string should be null
        assertNull(settings.state.autoCollapsePaths)
    }

    fun testMigrationFromLegacy() {
        val settings = StickyProjectSettings()

        // Create a state object that mimics what XmlSerializer would create from old XML
        // In old XML, autoCollapsePaths is present. autoCollapsePathsList is absent (so default).

        val legacyState = StickyProjectSettings.State()
        // Simulate deserializer setting the legacy string
        legacyState.autoCollapsePaths = "foo/;bar/"

        // Manually invoke loadState
        settings.loadState(legacyState)

        // Verify migration: legacy string should be parsed and added to list
        // And list should NOT contain defaults if legacy was present.

        assertTrue(settings.state.autoCollapsePathsList.contains("foo/"))
        assertTrue(settings.state.autoCollapsePathsList.contains("bar/"))
        assertFalse(settings.state.autoCollapsePathsList.contains("node_modules/")) // Defaults cleared
        assertNull(settings.state.autoCollapsePaths)
    }

    fun testLoadNewFormat() {
        val settings = StickyProjectSettings()

        val newState = StickyProjectSettings.State()
        newState.autoCollapsePaths = null
        newState.autoCollapsePathsList = mutableListOf("baz/", "qux/")

        settings.loadState(newState)

        assertTrue(settings.state.autoCollapsePathsList.contains("baz/"))
        assertTrue(settings.state.autoCollapsePathsList.contains("qux/"))
        assertNull(settings.state.autoCollapsePaths)
    }

    fun testPathsWithSemicolons() {
        val settings = StickyProjectSettings()

        val newState = StickyProjectSettings.State()
        newState.autoCollapsePathsList = mutableListOf("foo;bar/", "baz/")

        settings.loadState(newState)

        assertTrue(settings.state.autoCollapsePathsList.contains("foo;bar/"))
        assertEquals(2, settings.state.autoCollapsePathsList.size)
    }

    fun testMigrationFromEmptyOldFormat() {
        val loadedState = StickyProjectSettings.State()
        loadedState.autoCollapsePaths = ""

        val settings = StickyProjectSettings()
        settings.loadState(loadedState)

        assertTrue("Should migrate empty string to empty list (clearing defaults)", settings.state.autoCollapsePathsList.isEmpty())
        assertNull("Should clear old paths field", settings.state.autoCollapsePaths)
    }

    fun testNoMigrationIfOldPathsNull() {
        val loadedState = StickyProjectSettings.State()
        loadedState.autoCollapsePaths = null

        val settings = StickyProjectSettings()
        settings.loadState(loadedState)

        assertTrue("Should keep default list if no old paths", settings.state.autoCollapsePathsList.contains("node_modules/"))
        assertNull("Old paths field should remain null", settings.state.autoCollapsePaths)
    }
}
