package net.internetisalie.lunar.performance

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.TimeoutUtil
import net.internetisalie.lunar.IndexedDocumentTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DR-02: Performance benchmarking for Phase 2 (Project-wide Globals) completion.
 *
 * **⚠️ IMPORTANT**: This benchmark measures the **combined performance of Phase 1 + Phase 2**
 * (local/imported symbols + project-wide globals). It does NOT isolate Phase 2 overhead.
 * To measure Phase 2 in isolation after implementation, these tests should be extended
 * to:
 * 1. Measure Phase 1 baseline with global index queries disabled
 * 2. Measure Phase 1+Phase 2 combined with global index queries enabled
 * 3. Calculate Phase 2 overhead = Combined - Phase 1 baseline
 *
 * **Current Purpose**: Establish baseline completion performance and identify any
 * severe degradation at scale (5000-10000 symbols).
 *
 * **Targets** (for Phase 1+2 combined after Phase 2.1 implementation):
 * - Combined <250ms at 1000 symbols (Phase 1 baseline + Phase 2 <100ms overhead)
 * - Combined <400ms at 5000 symbols (with graceful degradation acceptable)
 *
 * **Approach**:
 * 1. Generate test projects with 500, 1000, 5000, 10000 global symbols
 * 2. Force stub index rebuild to ensure all globals are indexed
 * 3. Measure completion time at each scale
 * 4. Identify performance cliffs or bottlenecks
 * 5. Report feasibility and provide fallback guidance
 */
class GlobalSymbolCompletionPerformanceTest : IndexedDocumentTest() {

    /**
     * Generate a Lua module with N global functions.
     *
     * Example output (N=5):
     * ```lua
     * function global_func_0() end
     * function global_func_1() end
     * ... (3 more)
     * ```
     */
    private fun generateGlobalSymbolModule(count: Int): String {
        return (0 until count).joinToString("\n") { i ->
            "function global_func_$i() end"
        }
    }

    /**
     * Generate a @class declaration module.
     *
     * Generates 10% of symbol count (up to 100) to simulate typical codebase ratios
     * where @class declarations are less common than global functions.
     *
     * Example output (N=3):
     * ```lua
     * ---@class MyClass0
     * local MyClass0 = {}
     *
     * ---@class MyClass1
     * local MyClass1 = {}
     * ...
     * ```
     */
    private fun generateClassSymbolModule(count: Int): String {
        return (0 until count).joinToString("\n\n") { i ->
            "---@class MyClass_$i\nlocal MyClass_$i = {}"
        }
    }

    /**
     * Generate a consumer module that requires other modules.
     * This simulates the scenario where phase 1 + phase 2 complete.
     */
    private fun generateConsumerModule(requiredModules: Int): String {
        val requires = (0 until requiredModules).joinToString("\n") { i ->
            "local mod_$i = require('module_$i')"
        }
        return """
            $requires
            
            local local_var = 10
            function consumer_func() end
            
            -- Completion point
            <>
        """.trimIndent()
    }

    /**
     * Benchmark completion at a specific scale.
     *
     * @param symbolCount Total number of global symbols to generate
     * @return BenchmarkResult with timing information
     */
    private fun benchmarkCompletionAtScale(symbolCount: Int): BenchmarkResult {
        val result = BenchmarkResult(symbolCount)

        // Create test files
        val globalModuleCode = generateGlobalSymbolModule(symbolCount)
        val classModuleCode = generateClassSymbolModule(min(100, symbolCount / 10))
        val consumerCode = generateConsumerModule(1)

        // Configure fixture with all modules
        myFixture.configureByText("module_0.lua", globalModuleCode)
        myFixture.configureByText("classes.lua", classModuleCode)
        val consumerFile = myFixture.configureByText("consumer.lua", consumerCode)

        // Force index rebuild to ensure all globals are indexed
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            WriteAction.run<RuntimeException> {
                StubIndex.getInstance().forceRebuild(Throwable("Benchmark setup: forcing stub index rebuild"))
            }
        }
        
        // Wait for smart mode (index completion) before measuring
        // This ensures index queries are safe per engineering contract
        DumbService.getInstance(myFixture.project).waitForSmartMode()

        // Measure Phase 1 baseline (keyword/local completion)
        result.phase1Time = measureCompletionTime {
            invokeCompletion(consumerFile)
        }

        // Record results (totalSymbols already set in constructor)
        result.classSymbols = min(100, symbolCount / 10)
        result.globalFunctions = symbolCount

        return result
    }

    /**
     * Invoke code completion and measure time.
     * Returns the time in milliseconds.
     */
    private fun measureCompletionTime(block: () -> Unit): Long {
        val start = System.nanoTime()
        try {
            block()
        } catch (e: Exception) {
            // Log exception for debugging; don't silently ignore
            System.err.println("⚠️ Exception during completion: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
        return (System.nanoTime() - start) / 1_000_000  // Convert to milliseconds
    }

    /**
     * Invoke completion on the current fixture.
     * Validates that expected symbols appear in results.
     */
    private fun invokeCompletion(file: PsiFile) {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            myFixture.editor.caretModel.moveToOffset(
                file.text.indexOf("<>")
            )
            myFixture.completeBasic()
            val strings = myFixture.lookupElementStrings ?: emptyList()
            assertNotNull(strings, "Completion should return results")
            
            // Sanity check: at least some generated symbols should appear
            // (This validates that indexing is working and completion is functional)
            assertTrue(
                strings.any { it.startsWith("global_func_") || it.startsWith("MyClass_") },
                "Completion should include generated global functions and classes. Found: ${strings.take(10)}"
            )
        }
    }

    // ==================== Tests ====================

    @Test
    fun `baseline 500 symbols`() {
        val result = benchmarkCompletionAtScale(500)
        println("🔍 BASELINE: 500 symbols → Combined: ${result.phase1Time}ms")
        // Informational: capture baseline data
        assertTrue(result.phase1Time > 0, "Benchmark should record a time > 0")
    }

    @Test
    fun `target 1000 symbols`() {
        val result = benchmarkCompletionAtScale(1000)
        println("🎯 TARGET: 1000 symbols → Combined: ${result.phase1Time}ms")
        // Informational: capture target scale data
        // DR-02 target: Combined <250ms (Phase 1 baseline + Phase 2 <100ms overhead)
        assertTrue(result.phase1Time > 0, "Benchmark should record a time > 0")
    }

    @Test
    fun `scale 5000 symbols`() {
        val result = benchmarkCompletionAtScale(5000)
        println("📈 SCALE: 5000 symbols → Combined: ${result.phase1Time}ms")
        // Informational: document graceful degradation at scale
        assertTrue(result.phase1Time > 0, "Benchmark should record a time > 0")
    }

    @Test
    fun `stress 10000 symbols`() {
        val result = benchmarkCompletionAtScale(10000)
        println("🔥 STRESS: 10000 symbols → Combined: ${result.phase1Time}ms")
        // Informational: stress test to identify breaking point
        assertTrue(result.phase1Time > 0, "Benchmark should record a time > 0")
    }

    /**
     * Summary of all benchmark runs.
     * Reports combined Phase 1 + Phase 2 performance and baseline expectations.
     *
     * ⚠️ NOTE: This measures **combined** completion performance (Phase 1 + Phase 2 global index lookup).
     * It does NOT isolate Phase 2 overhead. After Phase 2.1 implementation, this test should be
     * extended to measure Phase 2 separately by toggling global index lookups on/off.
     */
    @Test
    fun `summary report`() {
        val outputLines = mutableListOf<String>()
        
        fun logLine(msg: String) {
            println(msg)
            outputLines.add(msg)
        }
        
        logLine("\n" + "=".repeat(70))
        logLine("DR-02 PERFORMANCE BENCHMARK SUMMARY (Phase 1 + Phase 2 Combined)")
        logLine("=".repeat(70))

        val scales = listOf(500, 1000, 5000, 10000)
        val results = scales.map { benchmarkCompletionAtScale(it) }

        logLine("\n📊 Results by Scale:")
        logLine("┌─────────┬──────────────┬──────────────┬───────────────┐")
        logLine("│ Symbols │ Combined(ms) │ Target       │ Status        │")
        logLine("├─────────┼──────────────┼──────────────┼───────────────┤")

        for (result in results) {
            val target = when (result.totalSymbols) {
                500 -> "<200"
                1000 -> "<250"
                5000 -> "<400"
                10000 -> "<600"
                else -> "unknown"
            }
            val status = when {
                result.totalSymbols == 1000 && result.phase1Time < 250 -> "✅ PASS"
                result.totalSymbols == 5000 && result.phase1Time < 400 -> "✅ PASS"
                result.totalSymbols == 10000 && result.phase1Time < 600 -> "✅ PASS"
                else -> "ℹ️  INFO"
            }
            logLine(
                String.format(
                    "│ %7d │ %12d │ %-12s │ %-13s │",
                    result.totalSymbols, result.phase1Time, target, status
                )
            )
        }
        logLine("└─────────┴──────────────┴──────────────┴───────────────┘")

        logLine("\n✅ Feasibility Assessment:")
        logLine("1. Combined performance at 1000 symbols: ${results[1].phase1Time}ms")
        logLine("   → Target: <250ms (Phase 1 baseline + Phase 2 <100ms overhead)")
        logLine("   → Status: ${if (results[1].phase1Time < 250) "✅ PASS" else "❌ FAIL - May need optimization"}")

        logLine("\n2. Graceful degradation at 5000 symbols: ${results[2].phase1Time}ms")
        logLine("   → Target: <400ms (with degradation acceptable)")
        logLine("   → Status: ${if (results[2].phase1Time < 400) "✅ PASS" else "⚠️  DEGRADE - Monitor"}")

        logLine("\n3. Stress test at 10000 symbols: ${results[3].phase1Time}ms")
        logLine("   → Target: <600ms (severe degradation acceptable)")
        logLine("   → Status: ${if (results[3].phase1Time < 600) "✅ PASS" else "❌ FAIL - Critical bottleneck"}")

        logLine("\n📋 Next Steps After Phase 2.1:")
        logLine("   1. Extend these benchmarks to measure Phase 2 overhead in isolation")
        logLine("   2. Re-run with Phase 2 implementation to validate <100ms overhead at 1000 symbols")
        logLine("   3. If combined > 250ms: optimize GlobalSymbolRankingService or fallback to functions-only")

        logLine("=".repeat(70) + "\n")
        
        // Write results to file for capture
        try {
            val resultsFile = File("/tmp/dr02-benchmark-results.txt")
            resultsFile.writeText(outputLines.joinToString("\n"))
            logLine("✅ Benchmark results saved to: ${resultsFile.absolutePath}")
        } catch (e: Exception) {
            System.err.println("⚠️ Could not write results file: ${e.message}")
        }

        // Gate: If combined at 1000 exceeds 250ms significantly, warn
        if (results[1].phase1Time >= 250) {
            System.err.println("⚠️  WARNING: Combined completion at 1000 symbols exceeded 250ms. Performance optimization may be needed.")
        }
    }

    /**
     * Data class to hold benchmark results.
     */
    private data class BenchmarkResult(
        val totalSymbols: Int,
        var phase1Time: Long = 0,
        var phase2Time: Long = 0,
        var globalFunctions: Int = 0,
        var classSymbols: Int = 0,
    ) {
        val combinedTime: Long
            get() = phase1Time + phase2Time

        override fun toString(): String =
            "Scale($totalSymbols): Phase1=${phase1Time}ms Phase2=${phase2Time}ms Combined=${combinedTime}ms"
    }
}
