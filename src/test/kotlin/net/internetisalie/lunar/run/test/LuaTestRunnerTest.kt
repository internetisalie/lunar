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
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings
import net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectState
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import java.io.File
import java.util.UUID
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
        val expActStr = if (testFailedEvent.comparisonFailureExpectedText != null || testFailedEvent.comparisonFailureActualText != null) {
            ", expected=${testFailedEvent.comparisonFailureExpectedText}, actual=${testFailedEvent.comparisonFailureActualText}"
        } else {
            ""
        }
        events.add("onTestFailure: name=${testFailedEvent.name}, id=${testFailedEvent.id}, msg=${testFailedEvent.localizedFailureMessage}, trace=${testFailedEvent.stacktrace}, isError=${testFailedEvent.isTestError}, duration=${testFailedEvent.durationMillis}$expActStr")
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
        config.options.interpreter = "/bin/sh"
        config.testTarget = "my_test.lua"
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, "/bin/sh")
        return LuaTestConsoleProperties(config, DefaultRunExecutor.getRunExecutorInstance())
    }

    private fun bindBusted(path: String): LuaRegisteredTool {
        resetToolchain()
        val tool = LuaRegisteredTool(
            id = UUID.randomUUID().toString(),
            kindId = "busted",
            path = path,
            version = "2.1.0",
            luaVersion = null,
            runtime = null,
            origin = Origin.MANUAL,
            environmentId = null,
            health = LuaToolHealth(fileExists = true, executable = true, probeOk = true, probedAtMtime = 1L, reason = null),
        )
        LuaToolchainRegistry.getInstance().registerProvisioned(tool)
        LuaToolchainRegistry.getInstance().setGlobalBinding("busted", tool.id)
        return tool
    }

    private fun resetToolchain() {
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        LuaToolchainProjectSettings.getInstance(myFixture.project).loadState(LuaToolchainProjectState())
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
    fun testAssertionDiffParsing() {
        val properties = createProperties(LuaTestFramework.BUSTED)
        val converter = LuaTestOutputToEventsConverter("LuaTest", properties)
        val processor = RecordingTestEventsProcessor(myFixture.project, "LuaTest")
        converter.setProcessor(processor)

        val json = """
            {
              "successes": [],
              "failures": [
                {
                  "name": "Calculator → addition → adds two positives",
                  "message": "Expected objects to be equal.\nPassed in:\n4\nExpected:\n5",
                  "trace": { "source": "@/project/calc_spec.lua", "currentline": 12 },
                  "duration": 0.005
                },
                {
                  "name": "Calculator → addition → alternative message style",
                  "message": "Expected: 10\nGot: 9",
                  "trace": { "source": "@/project/calc_spec.lua", "currentline": 15 },
                  "duration": 0.002
                },
                {
                  "name": "Calculator → addition → actual first style",
                  "message": "Actual:\n[1, 2, 3]\nExpected:\n[1, 2]",
                  "trace": { "source": "@/project/calc_spec.lua", "currentline": 18 },
                  "duration": 0.003
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
            "onTestFailure: name=adds two positives, id=test_3, msg=Expected objects to be equal.\nPassed in:\n4\nExpected:\n5, trace=, isError=false, duration=5, expected=5, actual=4",
            "onTestFinished: name=adds two positives, id=test_3, duration=5",
            "onTestStarted: name=alternative message style, id=test_4, parentId=suite_2, url=lua:///project/calc_spec.lua:15",
            "onTestFailure: name=alternative message style, id=test_4, msg=Expected: 10\nGot: 9, trace=, isError=false, duration=2, expected=10, actual=9",
            "onTestFinished: name=alternative message style, id=test_4, duration=2",
            "onTestStarted: name=actual first style, id=test_5, parentId=suite_2, url=lua:///project/calc_spec.lua:18",
            "onTestFailure: name=actual first style, id=test_5, msg=Actual:\n[1, 2, 3]\nExpected:\n[1, 2], trace=, isError=false, duration=3, expected=[1, 2], actual=[1, 2, 3]",
            "onTestFinished: name=actual first style, id=test_5, duration=3",
            "onSuiteFinished: name=addition, id=suite_2",
            "onSuiteFinished: name=Calculator, id=suite_1"
        )
        assertEquals(expected, processor.events)
    }

    @Test
    fun testCommandLineStateBusted() {
        bindBusted("/bin/sh") // exists and is executable on Unix

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
            resetToolchain()
        }
    }

    @Test
    fun testBustedPathPrependedFromEnvBuilder() {
        val toolsDir = com.intellij.openapi.util.io.FileUtil.createTempDirectory("lunar-tools", null)
        val bustedPath = File(toolsDir, "busted").absolutePath
        bindBusted(bustedPath)

        try {
            val properties = createProperties(LuaTestFramework.BUSTED)
            val config = properties.configuration
            config.testTarget = "spec.lua"
            config.testTargetType = "FILE"

            val env = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build()
            val commandLine = LuaTestCommandLineState(config, env).buildCommandLine()

            assertEquals(bustedPath, commandLine.exePath)
            val pathEnv = commandLine.environment["PATH"]!!
            assertTrue(pathEnv.startsWith(toolsDir.absolutePath + File.pathSeparator) || pathEnv == toolsDir.absolutePath)
        } finally {
            resetToolchain()
        }
    }

    @Test
    fun testBustedUnresolvedThrows() {
        resetToolchain()
        val properties = createProperties(LuaTestFramework.BUSTED)
        val config = properties.configuration
        config.testTarget = "spec.lua"

        val env = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build()
        val state = LuaTestCommandLineState(config, env)
        try {
            state.buildCommandLine()
            kotlin.test.fail("Expected ExecutionException for unresolved busted")
        } catch (e: com.intellij.execution.ExecutionException) {
            assertTrue(e.message!!.contains("Busted"))
            assertTrue(e.message!!.contains("Toolchain"))
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

    @Test
    fun testLuaTestLocator() {
        val targetProject = myFixture.project
        val psiFile = myFixture.configureByText("t.lua", "local x = 1\nlocal y = 2\n")
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(targetProject)

        val locations = LuaTestLocator.getLocation("lua", "${psiFile.virtualFile.path}:2", targetProject, scope)
        assertEquals(1, locations.size)
        val location = locations[0]
        val psiElement = location.psiElement
        kotlin.test.assertNotNull(psiElement)
        com.intellij.openapi.application.runReadAction {
            assertEquals(psiFile, psiElement.containingFile)
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(psiFile.virtualFile)!!
            val offset = psiElement.textOffset
            assertEquals(document.getLineStartOffset(1), offset)
        }
    }

    @Test
    fun testRerunFailedTestsAction() {
        val properties = createProperties(LuaTestFramework.BUSTED)
        val config = properties.configuration
        config.testTarget = "/project/spec/calc_spec.lua"
        config.testTargetType = "FILE"
        config.workingDirectory = "/project"

        bindBusted("/bin/sh")

        try {
            val rootProxy = com.intellij.execution.testframework.sm.runner.SMTestProxy.SMRootTestProxy()
            val failedTestProxy = com.intellij.execution.testframework.sm.runner.SMTestProxy("test_fail", false, null)
            failedTestProxy.setTestFailed("assertion failed", "traceback...", false)
            rootProxy.addChild(failedTestProxy)

            val passedTestProxy = com.intellij.execution.testframework.sm.runner.SMTestProxy("test_pass", false, null)
            passedTestProxy.setFinished()
            rootProxy.addChild(passedTestProxy)

            val runningModel = object : com.intellij.execution.testframework.TestFrameworkRunningModel {
                override fun getProperties(): TestConsoleProperties = properties
                override fun getRoot(): com.intellij.execution.testframework.AbstractTestProxy = rootProxy
                override fun setFilter(filter: com.intellij.execution.testframework.Filter<*>) {}
                override fun isRunning(): Boolean = false
                override fun getTreeView(): com.intellij.execution.testframework.TestTreeView? = null
                override fun getTreeBuilder(): com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase<*>? = null
                override fun hasTestSuites(): Boolean = false
                override fun selectAndNotify(testProxy: com.intellij.execution.testframework.AbstractTestProxy?) {}
                override fun dispose() {}
            }

            val mockConsoleView = object : com.intellij.openapi.ui.ComponentContainer {
                val panel = javax.swing.JPanel()
                override fun getComponent(): javax.swing.JComponent = panel
                override fun getPreferredFocusableComponent(): javax.swing.JComponent = panel
                override fun dispose() {}
            }
            val rerunAction = LuaRerunFailedTestsAction(mockConsoleView, properties)
            rerunAction.setModel(runningModel)

            val executionEnvironment = com.intellij.execution.runners.ExecutionEnvironmentBuilder.create(
                com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance(), config
            ).build()
            val runProfile = rerunAction.getRunProfileTestAccessor(executionEnvironment)
            kotlin.test.assertNotNull(runProfile)

            val state = runProfile.getState(com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance(), executionEnvironment)
            kotlin.test.assertNotNull(state)
            assertTrue(state is LuaTestCommandLineState)

            val commandLine = state.buildCommandLine()
            assertEquals("/bin/sh", commandLine.exePath)
            assertEquals("/project", commandLine.workDirectory.absolutePath)
            assertEquals(
                listOf("--output=json", "--filter=\\Qtest_fail\\E", "/project/spec/calc_spec.lua"),
                commandLine.parametersList.list
            )
        } finally {
            resetToolchain()
        }
    }
}
