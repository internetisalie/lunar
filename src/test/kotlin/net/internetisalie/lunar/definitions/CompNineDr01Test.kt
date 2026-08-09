package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.indexing.LuaMemberFieldIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09 design §3.1: can a symbol's type, or at least its member *names*, be answered
 * without building the declaring file's whole type graph?
 *
 * DR-01's golden itself is no longer here. It is checked in and door-labelled —
 * [MemberEnumerationGoldenTest] — because a dump that prints is not a gate, and because the version
 * that lived here resolved through one door per receiver without saying which.
 *
 * Figures are medians of five wherever the quantity can be repeated. `forFile` on a cold file cannot
 * be: the snapshot is memoized, so the second call measures the cache.
 */
class CompNineDr01Test : LibraryRootTestCase() {
    private fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        return root.toString()
    }

    /** §3.1(b): is `LuaTypesSnapshot.forFile` on the declaring file the whole cost? */
    fun testSection31ForFileIsTheCost() {
        val root = bigLibrary()
        val libRoot = registerLibraryRoot(mapOf("wx.lua" to root))
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val libraryFile = libRoot.findChild("wx.lua") ?: error("library file not visible")
            val libraryPsi = PsiManager.getInstance(project).findFile(libraryFile) ?: error("no PSI for the library")
            val forFileMs = measureTimeMillis { LuaTypesSnapshot.forFile(libraryPsi) }
            println(
                "§3.1(b) LuaTypesSnapshot.forFile(library) = ${forFileMs}ms (root ${root.length / 1024} KiB) " +
                    "(single — unrepeatable by construction: the snapshot is memoized)",
            )
            val warmRuns =
                (1..5).map {
                    measureTimeMillis { LuaTypeManager.getInstance(project).resolveGlobal("wx", myFixture.file) }
                }
            Medians.report("§3.1(b) resolveGlobal AFTER forFile is warm", warmRuns)
            println(
                "§3.1(b) => if forFile carries the cost and resolveGlobal is then ~0, the graph build IS the whole path",
            )
        }
    }

    /**
     * §3.1(c) — can member NAMES be had from the existing indexes, without any type graph?
     * Completion needs names; only the checker needs types.
     *
     * ⚠ Both figures below are **withdrawn as evidence** (design §1.5, §2): they compare two
     * different index subsystems — `StubIndex` versus `FileBasedIndex` — and the second prints a
     * filtered match count rather than a key total, polluted by cross-test index accumulation. Kept
     * as the shape of the result only; the load-bearing figure is design §4.0's `membersIn` at 2 ms
     * for 3 600 members.
     */
    fun testSection31IndexOnlyNameEnumeration() {
        registerLibraryRoot(mapOf("wx.lua" to bigLibrary()))
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            var funcNames: List<String> = emptyList()
            val funcRuns =
                (1..5).map {
                    measureTimeMillis {
                        funcNames =
                            StubIndex
                                .getElements<String, LuaFuncDecl>(
                                    LuaGlobalDeclarationIndex.KEY,
                                    "wx",
                                    project,
                                    scope,
                                    LuaFuncDecl::class.java,
                                ).map { it.funcName.text }
                    }
                }
            Medians.report("§3.1(c)(i) getElements(KEY,\"wx\") -> ${funcNames.size} names", funcRuns)

            var assignNames: List<String> = emptyList()
            val assignRuns =
                (1..5).map {
                    measureTimeMillis {
                        val allKeys = FileBasedIndex.getInstance().getAllKeys(LuaMemberFieldIndex.KEY, project)
                        assignNames = allKeys.filter { it.startsWith("wx.") }
                    }
                }
            Medians.report("§3.1(c)(ii) LuaMemberFieldIndex full key scan -> ${assignNames.size} names", assignRuns)
            println("§3.1(c) => compare with resolveGlobal's four-figure milliseconds on an equivalent tree")
        }
    }
}
