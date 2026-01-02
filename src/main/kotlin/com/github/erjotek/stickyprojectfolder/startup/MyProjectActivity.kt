package com.github.erjotek.stickyprojectfolder.startup

import com.github.erjotek.stickyprojectfolder.ui.StickyScrollManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("Installing StickyProjectFolder manager")
        val manager = StickyScrollManager(project)
        com.intellij.openapi.util.Disposer.register(project, manager)
        manager.install()
    }
}