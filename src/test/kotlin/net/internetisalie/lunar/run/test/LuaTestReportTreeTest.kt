package net.internetisalie.lunar.run.test

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.sm.runner.GeneralIdBasedToSMTRunnerEventsConvertor
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import net.internetisalie.lunar.BaseDocumentTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * BUG-526: feeds test-framework output through the converter into the platform's id-based tree
 * builder — the same processor the Run window uses — and asserts the resulting test tree. The
 * platform rejects any node whose parent id is undefined, so a converter that emits one fails here
 * exactly as it does in the IDE. The busted reports under `/busted/` were captured from real
 * busted 2.3.0 (`busted --output=json`); see the BUG-526 bug report for the spec sources.
 */
class LuaTestReportTreeTest : BaseDocumentTest() {
    @Test
    fun testFlatDescribeBuildsTreeUnderFileSuite() {
        assertEquals(
            listOf(
                "spec/arith_spec.lua [suite] lua://spec/arith_spec.lua:1",
                "  arith [suite]",
                "    adds PASSED_INDEX lua://spec/arith_spec.lua:2",
                "    subtracts FAILED_INDEX lua://spec/arith_spec.lua:3",
            ),
            treeFor("arith.json"),
        )
    }

    @Test
    fun testNestedDescribesErrorsAndTopLevelTests() {
        assertEquals(
            listOf(
                "spec/nested_spec.lua [suite] lua://spec/nested_spec.lua:1",
                "  outer group [suite]",
                "    top level of outer PASSED_INDEX lua://spec/nested_spec.lua:2",
                "  outer group inner group [suite]",
                "    adds two numbers PASSED_INDEX lua://spec/nested_spec.lua:6",
                "    fails on purpose FAILED_INDEX lua://spec/nested_spec.lua:9",
                "    errors on purpose ERROR_INDEX lua://spec/nested_spec.lua:12",
                "  bare top-level test PASSED_INDEX lua://spec/nested_spec.lua:18",
            ),
            treeFor("nested.json"),
        )
    }

    @Test
    fun testDescribeBodyErrorBecomesErrorNode() {
        assertEquals(
            listOf(
                "spec/broken_spec.lua [suite] lua://spec/broken_spec.lua:1",
                "  broken describe ERROR_INDEX lua://spec/broken_spec.lua:1",
            ),
            treeFor("broken-describe.json"),
        )
    }

    @Test
    fun testFileLoadErrorWithEmptyTraceSitsAtRoot() {
        assertEquals(
            listOf("spec/syntax_spec.lua ERROR_INDEX null"),
            treeFor("syntax-error.json"),
        )
    }

    @Test
    fun testLunityTopLevelSuiteAndTestHangOffTheRoot() {
        val output =
            listOf(
                """{"event":"suite_start","name":"DB tests"}""",
                """{"event":"test_start","name":"test_connect","file":"db_test.lua","line":10}""",
                """{"event":"test_pass","name":"test_connect","duration":12}""",
                """{"event":"suite_end","name":"DB tests"}""",
                """{"event":"test_start","name":"test_loose","file":"db_test.lua","line":20}""",
                """{"event":"test_fail","name":"test_loose","message":"boom","duration":1}""",
            )
        assertEquals(
            listOf(
                "DB tests [suite]",
                "  test_connect PASSED_INDEX lua://db_test.lua:10",
                "test_loose FAILED_INDEX lua://db_test.lua:20",
            ),
            treeOf(LuaTestFramework.LUNITY, output.joinToString("\n", postfix = "\n")),
        )
    }

    private fun treeFor(resourceName: String): List<String> {
        val report = checkNotNull(javaClass.getResourceAsStream("/busted/$resourceName")).bufferedReader().readText()
        return treeOf(LuaTestFramework.BUSTED, report)
    }

    private fun treeOf(
        framework: LuaTestFramework,
        output: String,
    ): List<String> {
        val rootProxy = SMTestProxy.SMRootTestProxy()
        val processor = GeneralIdBasedToSMTRunnerEventsConvertor(myFixture.project, rootProxy, "LuaTest")
        val converter = LuaTestOutputToEventsConverter("LuaTest", consoleProperties(framework))
        converter.setProcessor(processor)
        processor.onStartTesting()
        val lines = output.lineSequence().filter { it.isNotEmpty() }
        lines.forEach { converter.process("$it\n", ProcessOutputTypes.STDOUT) }
        converter.flushBufferOnProcessTermination(1)
        return rootProxy.children.flatMap { describe(it, "") }
    }

    private fun describe(
        proxy: SMTestProxy,
        indent: String,
    ): List<String> {
        if (!proxy.isSuite) return listOf("$indent${proxy.name} ${proxy.magnitudeInfo.name} ${proxy.locationUrl}")
        val header = "$indent${proxy.name} [suite]" + (proxy.locationUrl?.let { " $it" } ?: "")
        return listOf(header) + proxy.children.flatMap { describe(it, "$indent  ") }
    }

    private fun consoleProperties(framework: LuaTestFramework): LuaTestConsoleProperties {
        val config =
            LuaTestRunConfigurationType
                .getInstance()
                .configurationFactories[0]
                .createTemplateConfiguration(myFixture.project) as LuaTestRunConfiguration
        config.testFramework = framework
        config.options.interpreter = "/bin/sh"
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, "/bin/sh")
        return LuaTestConsoleProperties(config, DefaultRunExecutor.getRunExecutorInstance())
    }
}
