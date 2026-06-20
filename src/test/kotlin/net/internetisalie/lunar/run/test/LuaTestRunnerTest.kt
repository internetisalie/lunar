package net.internetisalie.lunar.run.test

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.TestProxyPrinterProvider
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.platform.LuaInterpreter
import net.internetisalie.lunar.platform.LuaInterpreterFamily
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingTestEventsProcessor(
    project: Project,
    frameworkName: String,
    rootProxy: SMTestProxy.SMRootTestProxy = SMTestProxy.SMRootTestProxy()
) : GeneralTestEventsProcessor(project, frameworkName, rootProxy) {
    val events = mutableListOf<String>()

    override fun onStartTesting() {
        events.add("onStartTesting")
    }

    override fun onTestsCountInSuite(count: Int) {
        events.add("onTestsCountInSuite: $count")
    }

    override fun onTestStarted(testStartedEvent: TestStartedEvent) {
        events.add("onTestStarted: name=${testStartedEvent.name}, id=${testStartedEvent.id}, parentId=${testStartedEvent.parentId}, url=${testStartedEvent.locationUrl}")
    }

    override fun onTestFinished(testFinishedEvent: TestFinishedEvent) {
        events.add("onTestFinished: name=${testFinishedEvent.name}, id=${testFinishedEvent.id}, duration=${testFinishedEvent.duration}")
    }

    override fun onTestFailure(testFailedEvent: TestFailedEvent) {
        events.add("onTestFailure: name=${testFailedEvent.name}, id=${testFailedEvent.id}, msg=${testFailedEvent.localizedFailureMessage}, trace=${testFailedEvent.stacktrace}, isError=${testFailedEvent.isTestError}, duration=${testFailedEvent.durationMillis}")
    }

    override fun onTestIgnored(testIgnoredEvent: TestIgnoredEvent) {
        events.add("onTestIgnored: name=${testIgnoredEvent.name}, id=${testIgnoredEvent.id}, comment=${testIgnoredEvent.ignoreComment}")
    }

    override fun onTestOutput(testOutputEvent: TestOutputEvent) {
        events.add("onTestOutput: text=${testOutputEvent.text}")
    }

    override fun onSuiteStarted(suiteStartedEvent: TestSuiteStartedEvent) {
        events.add("onSuiteStarted: name=${suiteStartedEvent.name}, id=${suiteStartedEvent.id}, parentId=${suiteStartedEvent.parentId}, url=${suiteStartedEvent.locationUrl}")
    }

    override fun onSuiteFinished(suiteFinishedEvent: TestSuiteFinishedEvent) {
        events.add("onSuiteFinished: name=${suiteFinishedEvent.name}, id=${suiteFinishedEvent.id}")
    }

    override fun onUncapturedOutput(text: String, outputType: Key<*>) {
        events.add("onUncapturedOutput: text=${text.trim()}, type=${outputType}")
    }

    override fun onError(localizedMessage: String, stackTrace: String?, isCritical: Boolean) {
        events.add("onError: msg=$localizedMessage, critical=$isCritical")
    }

    override fun onTestsReporterAttached() {
        events.add("onTestsReporterAttached")
    }

    override fun setPrinterProvider(printerProvider: TestProxyPrinterProvider) {}
}

class LuaTestRunnerTest : BaseDocumentTest() {

    private fun createProperties(framework: LuaTestFramework): LuaTestConsoleProperties {
        val targetProject = myFixture.project
        val type = LuaTestRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        val config = factory.createTemplateConfiguration(targetProject) as LuaTestRunConfiguration
        config.testFramework = framework
        config.interpreter = LuaInterpreter("/bin/sh", LuaInterpreterFamily.UNKNOWN_PRODUCT)
        config.testTarget = "my_test.lua"
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, "/bin/sh")
        return LuaTestConsoleProperties(config, DefaultRunExecutor.getRunExecutorInstance())
    }

    @Test
    fun testLunityJsonLineParsing() {
        val properties = createProperties(LuaTestFramework.LUNITY)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        converter.process("{\"event\":\"suite_start\",\"name\":\"DB tests\"}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"suite_start\",\"name\":\"connection\"}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"test_start\",\"name\":\"test_connect\",\"file\":\"db_test.lua\",\"line\":10}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"test_pass\",\"name\":\"test_connect\",\"duration\":12}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"suite_end\",\"name\":\"connection\"}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"suite_end\",\"name\":\"DB tests\"}\n", ProcessOutputTypes.STDOUT)

        val expected = listOf(
            "onSuiteStarted: name=DB tests, id=suite_1, parentId=null, url=null",
            "onSuiteStarted: name=connection, id=suite_2, parentId=suite_1, url=null",
            "onTestStarted: name=test_connect, id=test_3, parentId=suite_2, url=lua://db_test.lua:10",
            "onTestFinished: name=test_connect, id=test_3, duration=12",
            "onSuiteFinished: name=connection, id=suite_2",
            "onSuiteFinished: name=DB tests, id=suite_1"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testLunityFailedAndIgnoredTests() {
        val properties = createProperties(LuaTestFramework.LUNITY)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        converter.process("{\"event\":\"test_start\",\"name\":\"test_fail\",\"file\":\"t.lua\",\"line\":5}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"test_fail\",\"name\":\"test_fail\",\"message\":\"expected 4 but got 5\",\"trace\":\"traceback...\",\"duration\":5}\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"event\":\"test_ignore\",\"name\":\"test_pending\",\"message\":\"not done yet\"}\n", ProcessOutputTypes.STDOUT)

        val expected = listOf(
            "onTestStarted: name=test_fail, id=test_1, parentId=null, url=lua://t.lua:5",
            "onTestFailure: name=test_fail, id=test_1, msg=expected 4 but got 5, trace=traceback..., isError=false, duration=5",
            "onTestFinished: name=test_fail, id=test_1, duration=5",
            "onTestStarted: name=test_pending, id=test_2, parentId=null, url=null",
            "onTestIgnored: name=test_pending, id=test_2, comment=not done yet"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testBustedJsonOutputParsing() {
        val properties = createProperties(LuaTestFramework.BUSTED)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        val json = """
            {
              "successes": [
                {
                  "name": "Calculator → addition → adds two positives",
                  "trace": { "source": "@/project/calc_spec.lua", "currentline": 12 },
                  "duration": 0.005
                }
              ],
              "failures": [
                {
                  "name": "Calculator → division → fails by zero",
                  "message": "division by zero",
                  "trace": { "source": "@/project/calc_spec.lua", "currentline": 20 },
                  "duration": 0.010
                }
              ],
              "errors": [],
              "pendings": []
            }
        """.trimIndent()

        converter.process(json, ProcessOutputTypes.STDOUT)
        converter.flushBufferOnProcessTermination(0)

        val expected = listOf(
            "onSuiteStarted: name=Calculator, id=suite_1, parentId=null, url=null",
            "onSuiteStarted: name=addition, id=suite_2, parentId=suite_1, url=null",
            "onTestStarted: name=adds two positives, id=test_3, parentId=suite_2, url=lua:///project/calc_spec.lua:12",
            "onTestFinished: name=adds two positives, id=test_3, duration=5",
            "onSuiteStarted: name=division, id=suite_4, parentId=suite_1, url=null",
            "onTestStarted: name=fails by zero, id=test_5, parentId=suite_4, url=lua:///project/calc_spec.lua:20",
            "onTestFailure: name=fails by zero, id=test_5, msg=division by zero, trace=, isError=false, duration=10",
            "onTestFinished: name=fails by zero, id=test_5, duration=10",
            "onSuiteFinished: name=division, id=suite_4",
            "onSuiteFinished: name=addition, id=suite_2",
            "onSuiteFinished: name=Calculator, id=suite_1"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testBustedMixedStreamRouting() {
        val properties = createProperties(LuaTestFramework.BUSTED)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        converter.process("DEBUG: loading test modules...\n", ProcessOutputTypes.STDOUT)
        converter.process("{\"successes\":[{\"name\":\"tests → foo\",\"trace\":{\"source\":\"@t.lua\",\"currentline\":1},\"duration\":0}]}\n", ProcessOutputTypes.STDOUT)
        converter.process("Done.\n", ProcessOutputTypes.STDOUT)
        converter.flushBufferOnProcessTermination(0)

        val expected = listOf(
            "onUncapturedOutput: text=DEBUG: loading test modules..., type=stdout",
            "onSuiteStarted: name=tests, id=suite_1, parentId=null, url=null",
            "onTestStarted: name=foo, id=test_2, parentId=suite_1, url=lua://t.lua:1",
            "onTestFinished: name=foo, id=test_2, duration=0",
            "onSuiteFinished: name=tests, id=suite_1",
            "onUncapturedOutput: text=Done., type=stdout"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testBustedInvalidJsonFallback() {
        val properties = createProperties(LuaTestFramework.BUSTED)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        val rawText = "lua: segfault at 0x00\nstack traceback:\n\t[C]: ?\n"
        converter.process(rawText, ProcessOutputTypes.STDOUT)
        converter.flushBufferOnProcessTermination(1)

        val expected = listOf(
            "onUncapturedOutput: text=lua: segfault at 0x00\nstack traceback:\n\t[C]: ?, type=stdout"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testCommandLineStateBusted() {
        // Register mock busted tool
        val tool = LuaTool(
            type = LuaToolType.BUSTED,
            name = "Busted",
            path = "/bin/sh", // exists and is executable on Unix
            version = "2.1.0",
            isValid = true
        )
        LuaApplicationSettings.instance.state.toolInventory.clear()
        LuaApplicationSettings.instance.state.toolInventory.add(tool)
        LuaToolManager.getInstance().setGlobalBinding(LuaToolType.BUSTED, tool.id)

        try {
            val properties = createProperties(LuaTestFramework.BUSTED)
            val config = properties.configuration
            config.testTarget = "/project/spec/calc_spec.lua"
            config.testTargetType = "FILE"
            config.workingDirectory = "/project"
            config.extraTestArguments = "--verbose"

            val env = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build()
            val state = LuaTestCommandLineState(config, env)
            val commandLine = state.buildCommandLine()

            assertEquals("/bin/sh", commandLine.exePath)
            assertEquals("/project", commandLine.workDirectory.absolutePath)
            assertEquals(
                listOf("--output=json", "/project/spec/calc_spec.lua", "--verbose"),
                commandLine.parametersList.list
            )
        } finally {
            LuaApplicationSettings.instance.state.toolInventory.clear()
            LuaApplicationSettings.instance.state.globalToolBindings.clear()
        }
    }

    @Test
    fun testCommandLineStateLunity() {
        val properties = createProperties(LuaTestFramework.LUNITY)
        val config = properties.configuration
        config.testTarget = "/project/tests/lunity_runner.lua"
        config.testTargetType = "FILE"
        config.workingDirectory = "/project"
        config.extraTestArguments = "--verbose"

        val env = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build()
        val state = LuaTestCommandLineState(config, env)
        val commandLine = state.buildCommandLine()

        assertEquals("/bin/sh", commandLine.exePath)
        assertEquals("/project", commandLine.workDirectory.absolutePath)
        assertEquals(
            listOf("/project/tests/lunity_runner.lua", "--verbose"),
            commandLine.parametersList.list
        )
    }
}
