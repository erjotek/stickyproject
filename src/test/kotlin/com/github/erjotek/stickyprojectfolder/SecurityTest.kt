package com.github.erjotek.stickyprojectfolder

import com.github.erjotek.stickyprojectfolder.actions.AddToAutoCollapseAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SecurityTest : BasePlatformTestCase() {

    fun testActionEnabledForPathWithSemicolon() {
        val dirName = "foo;bar"
        // Ensure we use the project base directory
        val baseDir = myFixture.project.basePath?.let {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it)
        } ?: myFixture.tempDirFixture.getFile("")!!

        var dir: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
             dir = baseDir.createChildDirectory(this, dirName)
        }

        assertNotNull("Directory with semicolon created", dir)

        val action = AddToAutoCollapseAction()
        val event = createEvent(action, dir!!)

        action.update(event)

        // After fix: it should be ENABLED because semicolons are now safe in paths (not delimiters)
        assertTrue("Action should be ENABLED for path with semicolon", event.presentation.isEnabledAndVisible)
    }

    private fun createEvent(action: AnAction, file: VirtualFile): AnActionEvent {
        val dataContext = MapDataContext()
        dataContext.put(CommonDataKeys.PROJECT, project)
        dataContext.put(CommonDataKeys.VIRTUAL_FILE, file)

        return TestActionEvent.createTestEvent(action, dataContext)
    }
}
