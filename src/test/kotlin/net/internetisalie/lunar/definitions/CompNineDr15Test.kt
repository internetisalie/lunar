package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * THROWAWAY — COMP-09 DR-15, the fourth Step 9 review's blocker BL-2, and the deepest finding of the
 * whole planning effort.
 *
 * The index knows a member only if it is written **syntactically against the receiver name**:
 * `function R.m()`, `R.f = v`, or `---@field` on `---@class R` (design §4.3). A global whose members
 * come from somewhere else has **no entries at all**:
 *
 *   assert = require("luassert")     <- members belong to the module, not to `assert`
 *   Config = { host = "x", port = 1 }  <- members are in a table literal, not assignments
 *
 * `dottedTarget` needs `varSuffixList.singleOrNull()`, and a bare `assert` target has an empty
 * suffix list, so nothing is emitted. `membersOfGlobal` would therefore return `[]` where today's
 * `resolveGlobal → globalTypeIn` returns the module's members — breaking two existing named tests
 * (`LuaGlobalMemberCompletionTest.globalBoundToARequiredModuleCompletesThatModulesMembers`, whose
 * own KDoc calls this "the case that has to work for a definition library to be useful", and
 * `aMemberCaretOffersNoCrossFileGlobals`).
 *
 * Every DR-14 receiver used `R = {}` with members assigned separately — the one fixture shape that
 * hides this. Measured here rather than reasoned about, and the fallback measured with it.
 */
class CompNineDr15Test : LibraryRootTestCase() {
    private fun fixture() =
        mapOf(
            "luassert.lua" to
                """
                ---@meta
                ---@class luassert
                local luassert = {}
                ---@param namespace string
                function luassert.unregister(namespace) end
                function luassert.register(namespace) end
                return luassert
                """.trimIndent(),
            "busted.lua" to "---@meta\nassert = require(\"luassert\")\n",
            "config.lua" to
                """
                ---@meta

                Config = {
                    host = "localhost",
                    port = 6379,
                }

                return Config
                """.trimIndent(),
            // The control: the shape every DR-14 receiver used.
            "wx.lua" to
                """
                ---@meta

                ---@class wx
                wx = {}

                ---@return boolean
                function wx.works() end

                return wx
                """.trimIndent(),
        )

    fun testDr15IndexIsBlindToModuleAndLiteralGlobals() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            val all = GlobalSearchScope.allScope(project)
            listOf("assert", "Config", "wx").forEach { receiver ->
                val today =
                    manager.resolveGlobal(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    } ?: emptyList()
                val viaIndex =
                    LuaReceiverMemberIndex
                        .membersOfGlobal(receiver, project, context)
                        .map { it.name }
                        .sorted()
                val union = LuaReceiverMemberIndex.membersIn(receiver, project, all).map { it.name }.sorted()
                println("DR-15 $receiver  today=$today")
                println("DR-15 $receiver  index=$viaIndex  union=$union")
                println(
                    "DR-15 $receiver  VERDICT: ${if (viaIndex == today) "matches" else "DIVERGES — lost ${today - viaIndex.toSet()}"}",
                )
            }
        }
    }

    /**
     * The candidate remedy: when the index has no entry for the receiver, fall back to today's path.
     * Measures whether that restores membership, and what the fallback costs on a receiver that
     * takes it.
     */
    fun testDr15FallbackRestoresMembership() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            listOf("assert", "Config", "wx").forEach { receiver ->
                val today =
                    manager.resolveGlobal(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    } ?: emptyList()
                val indexed = LuaReceiverMemberIndex.membersOfGlobal(receiver, project, context)
                val withFallback =
                    if (indexed.isNotEmpty()) {
                        indexed.map { it.name }.sorted()
                    } else {
                        today
                    }
                println(
                    "DR-15 fallback $receiver  indexHit=${indexed.isNotEmpty()}  result=$withFallback  matchesToday=${withFallback == today}",
                )
            }
        }
    }

    /** Real completion, so the claim is about what a user sees rather than about an API. */
    fun testDr15CompletionToday() {
        registerLibraryRoot(fixture())
        listOf("assert", "Config", "wx").forEach { receiver ->
            println("DR-15 completion $receiver. -> ${completionsFor("$receiver.<caret>\n")}")
        }
    }
}
