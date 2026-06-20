package net.internetisalie.lunar.run.test

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties

class LuaTestConsoleProperties(
    val configuration: LuaTestRunConfiguration,
    executor: Executor
) : SMTRunnerConsoleProperties(configuration, "LuaTest", executor), SMCustomMessagesParsing {

    init {
        setIdBasedTestTree(true)
        setPrintTestingStartedTime(true)
    }

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties
    ): OutputToGeneralTestEventsConverter {
        return LuaTestOutputToEventsConverter(testFrameworkName, consoleProperties)
    }
}
