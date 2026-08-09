package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.definitions.LibraryRootTestCase

/**
 * COMP-09 Phase 1 — design §4.5c's binding-opacity rule, on **all five** DR-19 shapes.
 *
 * Three de-risking rounds each missed a real defect because every fixture used one binding shape
 * (`R = {}` with members assigned separately), which is the one shape where the index and the type
 * graph agree. The shape is the variable here; the member style is not:
 *
 * | receiver | shape | authoritative | why it is here |
 * | :-- | :-- | :-- | :-- |
 * | `wx` | `{}` + syntactic | true | the shape every earlier fixture used, alone |
 * | `M` | literal + syntactic | true | remedy 1 died here — the index is **non-empty** and still incomplete without source 4 |
 * | `Config` | pure literal | true | remedy 1's original counterexample: no entries at all before source 4 |
 * | `Busted` | `= require(…)` | **false** | opaque, and empty |
 * | `OM` | `= require(…)` + syntactic | **false** | remedy 2 died here — the index has something and is still not to be trusted |
 *
 * `OM` is the row that matters, and it is the reason authority is a sentinel rather than an
 * emptiness test: `membersOfGlobal("OM")` returns `[extra]`, which is neither empty nor complete, so
 * any caller keying off `isNotEmpty()` silently drops `unregister`.
 */
class LuaReceiverMemberBindingShapeTest : LibraryRootTestCase() {
    fun testAuthorityIsDecidedByTheBindingShapeAndNotByEmptiness() {
        seed()
        val authority = SHAPES.associateWith { membershipOf(it).authoritative }
        assertEquals(
            "authority must follow the binding shape: a table literal or a bare table is see-through, " +
                "a `require`/call binding is not — and being non-empty is not authority",
            mapOf("wx" to true, "M" to true, "Config" to true, "Busted" to false, "OM" to false),
            authority,
        )
    }

    /** All five are declared as bare globals, so every one of them is *found*; only authority varies. */
    fun testEveryShapeIsFoundSoAuthorityIsNotStandingInForAMissingReceiver() {
        seed()
        assertEquals(
            "if a receiver were simply absent, `authoritative = false` would be indistinguishable " +
                "from opacity and this class would be asserting the wrong thing",
            SHAPES.associateWith { true },
            SHAPES.associateWith { membershipOf(it).found },
        )
    }

    /**
     * The membership half, which is what makes `OM` diagnostic: an emptiness heuristic would trust
     * `[extra]` and lose `unregister`, and would trust `M`'s `[f]` and lose `VERSION`/`DEBUG`.
     */
    fun testMembershipPerShapeAndTheSentinelIsNeverOffered() {
        seed()
        assertEquals(
            mapOf(
                "wx" to listOf("works"),
                "M" to listOf("DEBUG", "VERSION", "f"),
                "Config" to listOf("host", "port"),
                "Busted" to emptyList(),
                "OM" to listOf("extra"),
            ),
            SHAPES.associateWith { membershipOf(it).members.map { member -> member.name }.sorted() },
        )
    }

    /** The union door filters the marker too — Phase 3 materializes these names directly. */
    fun testTheUnionDoorDoesNotLeakTheOpacitySentinelEither() {
        seed()
        val leaked =
            SHAPES.filter { receiver ->
                runReadAction {
                    LuaReceiverMemberIndex
                        .membersIn(receiver, project, GlobalSearchScope.allScope(project))
                        .any { it.name == LuaReceiverMember.OPAQUE_BINDING }
                }
            }
        assertEquals(
            "the sentinel is an index-internal marker and must never reach a consumer",
            emptyList<String>(),
            leaked,
        )
    }

    private fun membershipOf(receiver: String): LuaReceiverMemberIndex.Membership =
        runReadAction { LuaReceiverMemberIndex.globalMembership(receiver, project, myFixture.file) }

    private fun seed() {
        registerLibraryRoot(
            mapOf(
                "luassert.lua" to
                    """
                    ---@meta
                    ---@class luassert
                    local luassert = {}
                    function luassert.unregister(n) end
                    return luassert
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
                "busted.lua" to "---@meta\nBusted = require(\"luassert\")\n",
                "opaquemix.lua" to
                    """
                    ---@meta

                    OM = require("luassert")

                    ---@return boolean
                    function OM.extra() end

                    return OM
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private companion object {
        val SHAPES = listOf("wx", "M", "Config", "Busted", "OM")
    }
}
