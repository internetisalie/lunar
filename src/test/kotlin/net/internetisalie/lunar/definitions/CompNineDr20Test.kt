package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * THROWAWAY — COMP-09 DR-20. Phase 2 aborted (ABORT_REPLAN) because the completion site pays
 * `LuaTypesSnapshot.forFile(consumerFile)` **before** the branch COMP-09 set out to replace: 1 462 ms
 * of a 1 661 ms cold completion. Two questions decide which re-plan is right, and both have so far
 * been answered by reading rather than running.
 *
 * **Q1 — where does that cost come from?** The consumer file in these fixtures is two lines. Two
 * lines cannot cost 1 462 ms on their own content, so the cost must be what building the snapshot
 * *drags in* — the hypothesis is `LuaTypesVisitor.seedAmbientGlobals`, which seeds globals from
 * library and stub files during `buildSnapshot`. Measured here as the same consumer file WITHOUT and
 * WITH a large library registered. If the delta is the cost, the fix is to stop seeding ambient
 * globals eagerly. If it is not, the only lever is hoisting the index lookup above the snapshot,
 * which inverts local-shadows-global.
 *
 * **Q2 — is the routing question cheap on its own?** The site builds a whole type graph to answer
 * "does *this* file bind the receiver name?", which is a scope walk. `LuaScope` cannot be reused —
 * it holds `VariableNode`s and is built during inference — so this times a hand-rolled PSI walk. If
 * that is ~free, the re-plan is: answer the routing question directly, keep the graph for in-file
 * receivers, use the index for the rest, and shadowing is preserved by construction.
 */
class CompNineDr20Test : LibraryRootTestCase() {
    /** Distinct text per sample so `forFile`'s per-file-text memoization cannot serve a warm answer. */
    private fun consumer(index: Int): PsiFile =
        myFixture.configureByText(
            "consumer$index.lua",
            "local pad$index = $index\nwx.<caret>\n",
        )

    private fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        return root.toString()
    }

    private fun timeForFileUs(file: PsiFile): Long {
        val startNs = System.nanoTime()
        LuaTypesSnapshot.forFile(file)
        return (System.nanoTime() - startNs) / 1000
    }

    private fun fiveColdSamples(): List<Long> =
        runReadAction { (0 until 5).map { timeForFileUs(consumer(it)) } }.sorted()

    /** Q1 arm A — no library root at all. */
    fun testDr20ForFileWithoutALibrary() {
        val samples = fiveColdSamples()
        println("DR-20 Q1-A forFile(consumer), NO library: samples(us)=$samples median=${samples[2]}us")
    }

    /** Q1 arm B — identical consumer text, one large library registered. */
    fun testDr20ForFileWithALargeLibrary() {
        val text = bigLibrary()
        registerLibraryRoot(mapOf("wx.lua" to text))
        println("DR-20 Q1-B library is ${text.length / 1024} KiB")
        val samples = fiveColdSamples()
        println("DR-20 Q1-B forFile(consumer), WITH library: samples(us)=$samples median=${samples[2]}us")
        println("DR-20 Q1 => compare medians; a large delta means the cost is what the snapshot drags in, not the file")
    }

    /**
     * Q2 — the routing question, answered without the graph.
     *
     * "Does this file bind [name]?" over the file-scope statements plus any enclosing function's
     * parameters. Deliberately syntactic: no inference, no index, no ambient seeding.
     */
    private fun fileBindsName(
        file: PsiFile,
        name: String,
    ): Boolean {
        val declarations = PsiTreeUtil.findChildrenOfType(file, PsiElement::class.java)
        for (declaration in declarations) {
            val bound =
                when (declaration) {
                    is LuaLocalVarDecl -> declaration.attNameList.any { boundName(it) == name }
                    is LuaLocalFuncDecl -> boundName(declaration) == name
                    is LuaAssignmentStatement ->
                        declaration.varList.varList.any {
                            it.varSuffixList.isEmpty() && it.nameRef?.text == name
                        }
                    else -> false
                }
            if (bound) return true
        }
        return false
    }

    private fun boundName(declaration: PsiElement): String? =
        declaration.node
            .findChildByType(LuaElementTypes.NAME_REF)
            ?.psi
            ?.text

    fun testDr20RoutingQuestionWithoutTheGraph() {
        registerLibraryRoot(mapOf("wx.lua" to bigLibrary()))
        val small = myFixture.configureByText("small.lua", "local t = {}\nt.x = 1\nwx.<caret>\n")
        val bulk = StringBuilder()
        repeat(2000) { i -> bulk.append("local v$i = $i\nlocal function f$i() return v$i end\n") }
        bulk.append("wx.field = 1\n")
        val large = myFixture.configureByText("large.lua", bulk.toString())

        runReadAction {
            listOf("small" to small, "large" to large).forEach { (label, file) ->
                val walk =
                    (0 until 5)
                        .map {
                            val startNs = System.nanoTime()
                            fileBindsName(file, "wx")
                            (System.nanoTime() - startNs) / 1000
                        }.sorted()
                val graphUs = timeForFileUs(file)
                println("DR-20 Q2 $label: scope walk samples(us)=$walk median=${walk[2]}us  |  forFile=${graphUs}us")
                println(
                    "DR-20 Q2 $label: fileBindsName(\"wx\")=${fileBindsName(file, "wx")} " +
                        "fileBindsName(\"t\")=${fileBindsName(file, "t")}",
                )
            }
            println("DR-20 Q2 => if the walk is orders below forFile, the routing question does not need the graph")
        }
    }
}
