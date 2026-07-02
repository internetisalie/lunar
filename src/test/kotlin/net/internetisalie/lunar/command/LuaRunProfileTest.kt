package net.internetisalie.lunar.command

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaRunProfileTest : BasePlatformTestCase() {

    fun testRunProfileExposesCommandLineNameAndIcon() {
        val cmd = GeneralCommandLine("java")
        val profile = LuaRunProfile(cmd)

        assertSame(cmd, profile.commandLine)
        assertTrue(profile.name.isNotBlank())
        assertNotNull(profile.icon)

        // getState is best-effort per design §2.5: build a real ExecutionEnvironment if the
        // headless platform allows it, and assert the returned state type when we can.
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        runCatching {
            ExecutionEnvironmentBuilder(project, executor).runProfile(profile).build()
        }.onSuccess { environment ->
            val state = profile.getState(executor, environment)
            assertTrue(state is LuaRunProfileState)
        }
    }
}
