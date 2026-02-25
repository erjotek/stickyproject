package com.github.erjotek.stickyprojectfolder.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert

class StickyProjectSettingsTest : BasePlatformTestCase() {

    fun testMigrationFromOldFormat() {
        val oldPaths = "foo/bar/;baz/"

        // Simulate loading state where autoCollapsePaths is set (as if from old XML)
        val loadedState = StickyProjectSettings.State()
        loadedState.autoCollapsePaths = oldPaths

        val settings = StickyProjectSettings()
        settings.loadState(loadedState)

        val expectedList = listOf("foo/bar/", "baz/")
        val actualList = settings.state.autoCollapsePathsList

        Assert.assertEquals("Should migrate old paths to list", expectedList, actualList)
        Assert.assertNull("Should clear old paths field after migration", settings.state.autoCollapsePaths)
    }

    fun testMigrationFromEmptyOldFormat() {
        val loadedState = StickyProjectSettings.State()
        loadedState.autoCollapsePaths = ""

        val settings = StickyProjectSettings()
        settings.loadState(loadedState)

        Assert.assertTrue("Should migrate empty string to empty list (clearing defaults)", settings.state.autoCollapsePathsList.isEmpty())
        Assert.assertNull("Should clear old paths field", settings.state.autoCollapsePaths)
    }

    fun testNoMigrationIfOldPathsNull() {
        val loadedState = StickyProjectSettings.State()
        loadedState.autoCollapsePaths = null

        val settings = StickyProjectSettings()
        settings.loadState(loadedState)

        // Should keep default list
        Assert.assertTrue("Should keep default list if no old paths", settings.state.autoCollapsePathsList.contains("node_modules/"))
        Assert.assertNull("Old paths field should remain null", settings.state.autoCollapsePaths)
    }
}
