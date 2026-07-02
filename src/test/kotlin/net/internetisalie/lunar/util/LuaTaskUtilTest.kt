package net.internetisalie.lunar.util

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.concurrent.atomic.AtomicInteger

class LuaTaskUtilTest : BasePlatformTestCase() {

    fun testNewProjectBackgroundTaskCarriesFieldsAndRunsAction() {
        val description = "lunar-task"
        val runCount = AtomicInteger(0)
        val indicator = EmptyProgressIndicator()

        val task = newProjectBackgroundTask(description, project) { received ->
            assertSame(indicator, received)
            runCount.incrementAndGet()
        }

        assertEquals(description, task.title)
        assertSame(project, task.project)

        task.run(indicator)

        assertEquals(1, runCount.get())
    }

    fun testNewAppBackgroundTaskRunsAction() {
        val runCount = AtomicInteger(0)

        val task = newAppBackgroundTask("lunar-app-task") { runCount.incrementAndGet() }

        assertTrue(task is Task.Backgroundable)
        task.run(EmptyProgressIndicator())

        assertEquals(1, runCount.get())
    }
}
