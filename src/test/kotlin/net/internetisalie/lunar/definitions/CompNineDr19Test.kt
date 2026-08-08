package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * THROWAWAY — COMP-09 DR-19, round five's BLOCKER 2, and a correction to DR-15's own remedy.
 *
 * DR-15 found the index blind to `Config = { host, port }` and proposed "fall back to `resolveGlobal`
 * when the index result is empty". Round five showed that rule fails on the **mixed** receiver — the
 * canonical Lua module idiom:
 *
 *     M = { VERSION = "1.0" }
 *     function M.f() end
 *
 * Source 1 emits `M -> [f]`, so the index is **non-empty**, the fallback never fires, and `VERSION`
 * is lost. DR-15's fixture had only pure shapes, so it could not see this — the same "the golden's
 * fixtures all shared one shape" failure it had just diagnosed, one level down.
 *
 * Worse, DR-15's supporting measurement was `withFallback = if (indexed.isNotEmpty()) … else today`,
 * so for the two receivers it was cited for it asserted `today == today`. A test that cannot fail.
 *
 * This measures the real remedy instead: **index the table-literal fields** (source 4), so the index
 * is authoritative for both halves of a mixed receiver and no emptiness heuristic is needed.
 */
class CompNineDr19Test : LibraryRootTestCase() {
    private fun fixture() =
        mapOf(
            // The shape round five named: literal fields AND a syntactic member.
            "mixed.lua" to
                """
                ---@meta

                M = { VERSION = "1.0", DEBUG = false }

                ---@return boolean
                function M.f() end

                return M
                """.trimIndent(),
            "config.lua" to
                """
                ---@meta

                Config = {
                    host = "localhost",
                    port = 6379,
                }

                return Config
                """.trimIndent(),
            "luassert.lua" to
                """
                ---@meta
                ---@class luassert
                local luassert = {}
                function luassert.unregister(n) end
                return luassert
                """.trimIndent(),
            "busted.lua" to "---@meta\nassert = require(\"luassert\")\n",
            // The case that survives DR-19's first remedy: opaquely bound AND syntactically extended.
            "opaquemix.lua" to
                """
                ---@meta

                OM = require("luassert")

                ---@return boolean
                function OM.extra() end

                return OM
                """.trimIndent(),
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

    fun testDr19MixedReceiver() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            listOf("M", "Config", "assert", "OM", "wx").forEach { receiver ->
                val today =
                    manager.resolveGlobal(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    } ?: emptyList()
                val membership = LuaReceiverMemberIndex.globalMembership(receiver, project, context)
                val indexed = membership.members.map { it.name }.sorted()
                // The rule under test: trust the index only when it says it is authoritative.
                val effective = if (membership.authoritative) indexed else today
                val missing = today - effective.toSet()
                val extra = effective - today.toSet()
                println("DR-19 $receiver  today=$today")
                println(
                    "DR-19 $receiver  index=$indexed authoritative=${membership.authoritative} found=${membership.found}",
                )
                println(
                    "DR-19 $receiver  effective=$effective missing=$missing extra=$extra ${if (missing.isEmpty() && extra.isEmpty()) "MATCH" else "DIVERGES"}",
                )
                // Round five blocked DR-15 for a harness that could not fail. `effective` is `today`
                // by construction on the non-authoritative rows, so assert the part that is NOT
                // tautological: which receivers the index claims authority over.
                assertEquals(
                    "authority for $receiver",
                    receiver in setOf("M", "Config", "wx"),
                    membership.authoritative,
                )
                assertEquals("membership for $receiver", today, effective)
            }
        }
    }

    /** The user-visible half, so the verdict is not about an API. */
    fun testDr19CompletionToday() {
        registerLibraryRoot(fixture())
        listOf("M", "Config", "assert", "OM", "wx").forEach { receiver ->
            println("DR-19 completion $receiver. -> ${completionsFor("$receiver.<caret>\n")}")
        }
    }
}
