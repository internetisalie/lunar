package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DumbModeTestUtils
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * THROWAWAY — COMP-09 DR-10.
 *
 * Design §4.9 asserts that `membersIn` "must degrade to empty rather than throw" during indexing,
 * and that today's path "has its own dumb-mode behaviour" which Phase 1 must match. Neither half was
 * measured. Reading finds exactly one guard in the whole type layer —
 * `LuaTypeManagerImpl:129`, `resolveGlobal` returns null when dumb — and none on `resolveType`,
 * which is the other door (design §1.4). That asymmetry is a hypothesis from a grep, which is the
 * kind this feature has been wrong about three times.
 *
 * So: what does each door actually do when the project is dumb, and what does the user see?
 */
class CompNineDr10Test : LibraryRootTestCase() {
    private fun fixture() =
        mapOf(
            "wx.lua" to
                """
                ---@meta

                ---@class wx
                wx = {}

                ---@type number
                wx.wxID_ANY = nil

                ---@return boolean
                function wx.wxFileExists() end

                return wx
                """.trimIndent(),
            "frame.lua" to
                """
                ---@meta

                ---@class wxFrame
                local wxFrame = {}

                ---@return boolean
                function wxFrame:Show() end
                """.trimIndent(),
        )

    /** Each call, in dumb mode: a value, null, or an exception? Record it, do not assume it. */
    private fun probe(
        label: String,
        call: () -> Any?,
    ) {
        val outcome =
            try {
                val v = call()
                "returned ${v ?: "null"}"
            } catch (e: Throwable) {
                "THREW ${e::class.simpleName}: ${e.message?.take(80)}"
            }
        println("DR-10 $label -> $outcome")
    }

    fun testDr10WhatEachDoorDoesWhenDumb() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val manager = LuaTypeManager.getInstance(project)
        val context = myFixture.file

        println("DR-10 --- SMART (baseline) ---")
        runReadAction {
            probe("resolveGlobal(wx)") { manager.resolveGlobal("wx", context)?.let { "type" } }
            probe("resolveType(wxFrame)") { manager.resolveType("wxFrame", context)?.let { "type" } }
            probe("membersIn(wx)") {
                LuaReceiverMemberIndex
                    .membersIn("wx", project, GlobalSearchScope.allScope(project))
                    .map { it.name }
            }
            probe("materialize(resolveGlobal(wx))") {
                manager.resolveGlobal("wx", context)?.let {
                    LuaGraphType
                        .materialize(it, context)
                        .getMembers()
                        .keys
                        .sorted()
                }
            }
        }

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            println("DR-10 --- DUMB (isDumb=${DumbService.isDumb(project)}) ---")
            runReadAction {
                probe("resolveGlobal(wx)") { manager.resolveGlobal("wx", context)?.let { "type" } }
                probe("resolveType(wxFrame)") { manager.resolveType("wxFrame", context)?.let { "type" } }
                probe("membersIn(wx)") {
                    LuaReceiverMemberIndex
                        .membersIn("wx", project, GlobalSearchScope.allScope(project))
                        .map { it.name }
                }
                probe("materialize(resolveGlobal(wx))") {
                    manager.resolveGlobal("wx", context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    }
                }
            }
        }
        println("DR-10 => the DUMB row for each door is what Phase 1's index path must reproduce")
    }

    /**
     * The user-visible half. Contributors that are not `DumbAware` are skipped entirely while dumb,
     * so the offered set may be empty for a reason that has nothing to do with the type layer's
     * guards — which would make matching those guards irrelevant.
     */
    fun testDr10WhatCompletionOffersWhenDumb() {
        registerLibraryRoot(fixture())
        val smart = completionsFor("wx.<caret>\n")
        println("DR-10 completion SMART wx. -> $smart")

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val dumb =
                try {
                    completionsFor("wx.<caret>\n").toString()
                } catch (e: Throwable) {
                    "THREW ${e::class.simpleName}: ${e.message?.take(120)}"
                }
            println("DR-10 completion DUMB  wx. -> $dumb")
        }
    }
}
