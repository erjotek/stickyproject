package com.github.erjotek.stickyprojectfolder.ui

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.TimeUnit
import javax.swing.JTree
import kotlin.system.measureNanoTime

class AutoCollapseManagerBenchmarkTest : BasePlatformTestCase() {

    private lateinit var autoCollapseManager: AutoCollapseManager
    private lateinit var tree: JTree

    override fun setUp() {
        super.setUp()
        tree = JTree()
        autoCollapseManager = AutoCollapseManager(project, tree)
    }

    override fun tearDown() {
        autoCollapseManager.dispose()
        super.tearDown()
    }

    fun testBenchmarkGetExcludedPaths() {
        // Setup: add some excluded folders to make the test more realistic
        ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
            val contentEntry = model.contentEntries.firstOrNull() ?: model.addContentEntry(myFixture.tempDirFixture.findOrCreateDir("content"))
            contentEntry.addExcludeFolder(contentEntry.url + "/excluded1")
            contentEntry.addExcludeFolder(contentEntry.url + "/excluded2")
        }

        val method = AutoCollapseManager.Companion::class.java.getDeclaredMethod("getExcludedPaths", Project::class.java, String::class.java)
        method.isAccessible = true

        val basePath = project.basePath ?: ""

        // Warmup
        repeat(1000) {
            method.invoke(AutoCollapseManager.Companion, project, basePath)
        }

        // Benchmark
        val iterations = 5000
        val totalTime = measureNanoTime {
            repeat(iterations) {
                method.invoke(AutoCollapseManager.Companion, project, basePath)
            }
        }

        val averageTime = totalTime / iterations
        println("Average execution time for getExcludedPaths: ${averageTime} ns")
    }

    fun testCacheInvalidation() {
        val method = AutoCollapseManager.Companion::class.java.getDeclaredMethod("getExcludedPaths", Project::class.java, String::class.java)
        method.isAccessible = true

        // Create content root
        val contentRoot = myFixture.tempDirFixture.findOrCreateDir("content")
        val simulatedBasePath = contentRoot.parent.path

        PsiTestUtil.addContentRoot(myFixture.module, contentRoot)

        // Initial call
        val initialResult = method.invoke(AutoCollapseManager.Companion, project, simulatedBasePath) as List<String>
        val initialSize = initialResult.size

        // Add excluded folder
        val excludedDir = myFixture.tempDirFixture.findOrCreateDir("content/excluded")
        PsiTestUtil.addExcludedRoot(myFixture.module, excludedDir)

        // Call again
        val newResult = method.invoke(AutoCollapseManager.Companion, project, simulatedBasePath) as List<String>

        assertEquals("Should have one more excluded path", initialSize + 1, newResult.size)
        assertTrue("Result should contain the new excluded folder", newResult.any { it.endsWith("excluded") })
    }
}
