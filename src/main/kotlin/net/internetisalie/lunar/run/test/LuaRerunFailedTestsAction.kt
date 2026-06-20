package net.internetisalie.lunar.run.test

import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.ComponentContainer

class LuaRerunFailedTestsAction(
    componentContainer: ComponentContainer,
    consoleProperties: TestConsoleProperties
) : AbstractRerunFailedTestsAction(componentContainer) {

    init {
        init(consoleProperties)
    }

    override fun getRunProfile(environment: ExecutionEnvironment): MyRunProfile? {
        val currentTestModel = model ?: return null
        val targetConfiguration = currentTestModel.properties.configuration as? LuaTestRunConfiguration ?: return null
        return MyTestRunProfile(targetConfiguration)
    }

    private inner class MyTestRunProfile(
        private val targetConfiguration: LuaTestRunConfiguration
    ) : MyRunProfile(targetConfiguration) {

        override fun getModules(): Array<Module> = Module.EMPTY_ARRAY

        override fun getState(executor: Executor, env: ExecutionEnvironment): RunProfileState {
            val targetProject = targetConfiguration.project
            val failedTestProxies = getFailedTests(targetProject)
            val failedTestNamesString = failedTestProxies.filter { it.isLeaf }.map { it.name }.joinToString(",")

            val clonedRunConfiguration = targetConfiguration.clone() as LuaTestRunConfiguration
            clonedRunConfiguration.failedTestNames = failedTestNamesString

            return LuaTestCommandLineState(clonedRunConfiguration, env)
        }
    }
}
