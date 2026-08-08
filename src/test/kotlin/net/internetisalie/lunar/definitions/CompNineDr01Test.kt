package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalDeclarationIndex
import net.internetisalie.lunar.lang.indexing.LuaMemberFieldIndex
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09 DR-01 (done properly) and design §3.1.
 *
 * DR-01's first attempt used `resolveGlobal` for everything and got null for the two `@class`
 * receivers, because those are types rather than globals. This covers both entry points, and the
 * fixture carries **colon-declared methods** — without them the golden file certifies their loss as
 * behaviour-preserving (DR-06).
 *
 * §3.1 asks whether a symbol's type can be answered without `LuaTypesSnapshot.forFile`. The three
 * measurements at the bottom answer it.
 */
class CompNineDr01Test : LibraryRootTestCase() {
    private fun fixture() =
        mapOf(
            "wx.lua" to
                """
                ---@meta

                ---@class wx
                wx = {}

                ---@type number
                wx.wxID_ANY = nil

                ---@type number
                wx.wxID_OK = nil

                ---@param filename string
                ---@return boolean
                function wx.wxFileExists(filename) end

                ---@param parent any
                ---@return wxFrame
                function wx.wxFrame(parent) end

                return wx
                """.trimIndent(),
            "wxframe.lua" to
                """
                ---@meta

                ---@class wxFrame
                local wxFrame = {}

                ---@param show boolean
                ---@return boolean
                function wxFrame:Show(show) end

                ---@return string
                function wxFrame:GetTitle() end

                ---@return number
                function wxFrame.staticCount() end
                """.trimIndent(),
            // Every member colon-declared: today this receiver has NO key at all (DR-06).
            "allcolon.lua" to
                """
                ---@meta

                ---@class AllColon
                local AllColon = {}

                ---@return string
                function AllColon:alpha() end

                ---@return string
                function AllColon:beta() end
                """.trimIndent(),
        )

    /** DR-01: the golden enumeration, via BOTH entry points. */
    fun testDr01GoldenAcrossBothEntryPoints() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            listOf("wx", "wxFrame", "AllColon").forEach { name ->
                val viaGlobal = manager.resolveGlobal(name, context)
                val viaType = manager.resolveType(name, context)
                println(
                    "DR-01 $name: resolveGlobal=${viaGlobal?.let {
                        it::class.simpleName
                    } ?: "null"} resolveType=${viaType?.let {
                        it::class
                            .simpleName
                    } ?: "null"}",
                )
                (viaGlobal ?: viaType)?.let { resolved ->
                    val members =
                        LuaGraphType
                            .materialize(resolved, context)
                            .getMembers()
                            .keys
                            .sorted()
                    println("DR-01   GOLDEN $name (${members.size}): $members")
                } ?: println("DR-01   GOLDEN $name: UNRESOLVABLE by either entry point")
            }
        }
    }

    /** §3.1(b): is `LuaTypesSnapshot.forFile` on the declaring file the whole cost? */
    fun testSection31ForFileIsTheCost() {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        val libRoot = registerLibraryRoot(mapOf("wx.lua" to root.toString()))
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val libVf = libRoot.findChild("wx.lua")!!
            val libPsi =
                com.intellij.psi.PsiManager
                    .getInstance(project)
                    .findFile(libVf)!!
            val forFileMs = measureTimeMillis { LuaTypesSnapshot.forFile(libPsi) }
            println("§3.1(b) LuaTypesSnapshot.forFile(library) = ${forFileMs}ms  (root ${root.length / 1024} KiB)")
            val resolveMs =
                measureTimeMillis { LuaTypeManager.getInstance(project).resolveGlobal("wx", myFixture.file) }
            println("§3.1(b) resolveGlobal AFTER forFile is warm = ${resolveMs}ms")
            println(
                "§3.1(b) => if forFile carries the cost and resolveGlobal is then ~0, the graph build IS the whole path",
            )
        }
    }

    /**
     * §3.1(c) — the money measurement. Can member NAMES be had from the existing indexes, without any
     * type graph, and how fast? Completion needs names; only the checker needs types.
     */
    fun testSection31IndexOnlyNameEnumeration() {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        root.append("return wx\n")
        registerLibraryRoot(mapOf("wx.lua" to root.toString()))
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val scope = GlobalSearchScope.allScope(project)

            // (i) dot-form function members: one keyed query, no graph.
            var funcNames: List<String> = emptyList()
            val funcMs =
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
            println("§3.1(c)(i)  getElements(KEY,\"wx\") -> ${funcNames.size} names in ${funcMs}ms")

            // (ii) assignment members: LuaMemberFieldIndex is QUALIFIED-keyed, so this is a full key
            //      scan — exactly what COMP-09-09's work bound forbids. Measured to size the gap.
            var assignNames: List<String> = emptyList()
            val assignMs =
                measureTimeMillis {
                    val all = FileBasedIndex.getInstance().getAllKeys(LuaMemberFieldIndex.KEY, project)
                    assignNames = all.filter { it.startsWith("wx.") }
                }
            println("§3.1(c)(ii) LuaMemberFieldIndex full key scan -> ${assignNames.size} names in ${assignMs}ms")

            val total = funcMs + assignMs
            println(
                "§3.1(c) index-only name enumeration total = ${total}ms for ${funcNames.size + assignNames.size} names",
            )
            println("§3.1(c) => compare with resolveGlobal's 9568ms on an equivalent tree")
        }
    }
}
