package net.internetisalie.lunar.run.test

import com.intellij.execution.Executor
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.ui.ConsoleView

class LuaTestConsoleProperties(
    val configuration: LuaTestRunConfiguration,
    executor: Executor
) : SMTRunnerConsoleProperties(configuration, "LuaTest", executor), SMCustomMessagesParsing {

    init {
        setIdBasedTestTree(true)
        setPrintTestingStartedTime(true)
    }

    override fun getTestLocator(): SMTestLocator {
        return LuaTestLocator
    }

    override fun createTestEventsConverter(
        testFrameworkName: String,
        consoleProperties: TestConsoleProperties
    ): OutputToGeneralTestEventsConverter {
        return LuaTestOutputToEventsConverter(testFrameworkName, consoleProperties)
    }

    override fun createRerunFailedTestsAction(consoleView: ConsoleView): AbstractRerunFailedTestsAction {
        return LuaRerunFailedTestsAction(consoleView, this)
    }
}
