package net.internetisalie.lunar.perfrecords

import com.intellij.lang.PsiBuilderFactory
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import net.internetisalie.lunar.IndexedDocumentTest
import net.internetisalie.lunar.lang.LuaParserDefinition
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.parser.LuaParser
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.RootAccessor
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * MAINT-45-01/-02/-03. Runs every counted workload twice and writes one record per workload to
 * `build/perf-records/`. Excluded from the routine `test` run; see `build.gradle.kts`.
 */
class LuaPerfRecordWorkloadsTest : IndexedDocumentTest() {
    @Test
    fun recordEveryWorkload() {
        val workloads = workloads()
        val warm = workloads.map(::measurePass)
        val recorded = workloads.map(::measurePass)
        val failures =
            workloads.zip(recorded).flatMap { (workload, pass) ->
                violations(workload.checks, pass.rawCounts, pass.outputs).map { "${workload.spec.name}: $it" }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        val repoRoot = File(System.getProperty("user.dir"))
        workloads.indices.forEach { i ->
            PerfRecordFile.write(repoRoot, toRecord(workloads[i].spec, warm[i], recorded[i]))
        }
    }

    private fun workloads(): List<Workload> =
        listOf(500, 2000).map { parseWorkload("parse-flat-$it", flat(it)) } +
            listOf(8, 12).map { parseWorkload("parse-callbacks-$it", callbacks(it)) } +
            parseWorkload("parse-parens-12", parens(12)) +
            listOf(20, 80, 160).map(::snapshotWorkload) +
            listOf(80, 320).map(::resolveWorkload) +
            listOf(200, 800).map(::completionMemberWorkload) +
            listOf(200, 800).map(::completionGlobalWorkload) +
            listOf(200, 800).map(::completionCrossfileWorkload)

    private fun measurePass(workload: Workload): Pass {
        resetWorkspace()
        System.gc()
        StubLookupCounter.drain()
        val startNanos = System.nanoTime()
        val measured = workload.run()
        val elapsedMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI
        return Pass(measured.counts + StubLookupCounter.drain(), measured.outputs, elapsedMs)
    }

    private fun resetWorkspace() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            WriteCommandAction.runWriteCommandAction(myFixture.project) {
                LightPlatformTestCase.getSourceRoot().children.forEach { it.delete(this) }
            }
            WriteAction.run<RuntimeException> {
                StubIndex.getInstance().forceRebuild(Throwable("MAINT-45 workload reset"))
            }
        }
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
        ReadAction.run<RuntimeException> {
            StubIndex.getInstance().getAllKeys(LuaGlobalDeclarationIndex.KEY, myFixture.project)
        }
    }

    private fun parseWorkload(
        name: String,
        text: String,
    ): Workload =
        Workload(
            WorkloadSpec(name, "parse", text),
            WorkloadChecks(setOf("marks", "advances"), emptyMap(), emptySet()),
        ) {
            val definition = LuaParserDefinition()
            val lexer = definition.createLexer(myFixture.project)
            val builder = CountingPsiBuilder(PsiBuilderFactory.getInstance().createBuilder(definition, lexer, text))
            LuaParser().parseLight(definition.fileNodeType, builder)
            Measured(mapOf("marks" to builder.marks, "advances" to builder.advances), emptyMap())
        }

    private fun snapshotWorkload(n: Int): Workload {
        val text = annotatedCalls(n)
        val required = setOf("write", "read", "demand", "stub:lunar.global.decl")
        return Workload(
            WorkloadSpec("snapshot-annotated-$n", "snapshot", text),
            WorkloadChecks(required, emptyMap(), emptySet()),
        ) {
            EdtTestUtil.runInEdtAndGet<Measured, RuntimeException> {
                configureByText(text)
                Measured(rootCounts(LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot), emptyMap())
            }
        }
    }

    private fun resolveWorkload(n: Int): Workload {
        val text = colonCalls(n)
        return Workload(
            WorkloadSpec("resolve-colon-$n", "resolve", text),
            WorkloadChecks(setOf("write", "read", "demand"), mapOf("resolved" to n.toLong()), emptySet()),
        ) {
            EdtTestUtil.runInEdtAndGet<Measured, RuntimeException> {
                configureByText(text)
                val types = LuaTypesSnapshot.forFile(myFixture.file) as LuaTypesSnapshot
                // Resolving adds root resolutions, so the counts must be read after it.
                val resolved = resolvedColonCallSites()
                Measured(rootCounts(types), mapOf("resolved" to resolved))
            }
        }
    }

    private fun completionMemberWorkload(n: Int): Workload {
        val text = members(n)
        return completionWorkload(
            WorkloadSpec("completion-member-$n", "completion", text),
            WorkloadChecks(setOf(STUB_DECL), mapOf("lookupItems" to n.toLong()), emptySet()),
        ) { configureByText(text) }
    }

    private fun completionGlobalWorkload(n: Int): Workload {
        val text = globals(n) + "\nglobal_fu<caret>"
        return completionWorkload(
            WorkloadSpec("completion-global-$n", "completion", text),
            WorkloadChecks(setOf(STUB_DECL), emptyMap(), setOf("lookupItems")),
        ) { configureByText(text) }
    }

    private fun completionCrossfileWorkload(n: Int): Workload {
        val globalsText = globals(n)
        return completionWorkload(
            WorkloadSpec("completion-crossfile-$n", "completion", globalsText + "\n" + USE_LUA),
            WorkloadChecks(setOf(STUB_DECL), emptyMap(), setOf("lookupItems")),
        ) {
            myFixture.configureByText("globals.lua", globalsText)
            myFixture.configureByText("use.lua", USE_LUA)
        }
    }

    private fun completionWorkload(
        spec: WorkloadSpec,
        checks: WorkloadChecks,
        configure: () -> Unit,
    ): Workload =
        Workload(spec, checks) {
            configure()
            myFixture.completeBasic()
            Measured(emptyMap(), mapOf("lookupItems" to (myFixture.lookupElements?.size?.toLong() ?: 0L)))
        }

    private fun rootCounts(types: LuaTypesSnapshot): Map<String, Long> =
        mapOf(
            "write" to types.rootResolutionCount(RootAccessor.WRITE),
            "read" to types.rootResolutionCount(RootAccessor.READ),
            "demand" to types.rootResolutionCount(RootAccessor.DECLARED_DEMAND),
        )

    private fun resolvedColonCallSites(): Long =
        PsiTreeUtil
            .findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .filter { it.parent is LuaMethodExpr }
            .count { it.reference?.resolve() != null }
            .toLong()

    private companion object {
        const val STUB_DECL = "stub:lunar.global.decl"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
