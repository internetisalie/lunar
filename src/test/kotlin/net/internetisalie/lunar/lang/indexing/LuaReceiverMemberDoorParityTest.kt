package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.definitions.LibraryRootTestCase
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * COMP-09 Phase 1 — TC 7a, and the assertion DR-14 was missing.
 *
 * COMP-09-07 says "behaviour-preserving", and design §4.4a showed that is not well defined while two
 * doors exist for one receiver and one of them is a defect. So **each entry point is measured
 * against the door it actually serves**, and never against `resolveGlobal(r) ?: resolveType(r)` —
 * design §1.4 forbids that collapse, and taking it is how §4.4a's parity claim came to be asserted
 * without ever having been measured:
 *
 * | entry point | door it serves | why |
 * | :-- | :-- | :-- |
 * | [LuaReceiverMemberIndex.membersOfGlobal] | **global** (`resolveGlobal`) | `crossFileGlobalMembers` resolves a bare name through `resolveGlobal` (design §4.5) |
 * | [LuaReceiverMemberIndex.membersIn] | **`@class`** (`resolveType`) | `addMethodsOf` wants every declaring file (§4.6, BUG-399) |
 *
 * `CompNineDr14Test` measured all of this and printed it. Printing is what let §4.4a's wrong claim
 * stand for two review rounds, so the figures are pinned here instead.
 *
 * **Two divergences, both declared in the design, both asserted rather than tolerated.** Anything
 * else moving is a regression:
 * - `Shapes` loses `deep` — the global door hoists `Shapes.nested.deep` onto `Shapes`, which is
 *   **BUG-430**, and COMP-09 deliberately does not reproduce it.
 * - `Derived` gains `ownField` — a `@field` the completion door does not offer today (design §4.5a).
 */
class LuaReceiverMemberDoorParityTest : LibraryRootTestCase() {
    /**
     * The union reproduces the `@class` door exactly on all four receivers — including the two whose
     * `@class` sits on a `local`, which the global door cannot resolve at all.
     */
    fun testTheUnionReproducesTheClassDoor() {
        seed()
        assertEquals(
            mapOf(
                "wx" to listOf("wxFileExists", "wxID_ANY"),
                "wxFrame" to listOf("Show", "staticCount", "title"),
                "AllColon" to listOf("alpha"),
                "Shapes" to listOf("direct", "nested", "plain"),
            ),
            RECEIVERS.associateWith { classDoor(it) },
        )
        assertEquals(
            "materialization preserves the `@class` door (design §4.6); a divergence here is COMP-09-07",
            RECEIVERS.associateWith { classDoor(it) },
            RECEIVERS.associateWith { union(it) },
        )
    }

    /**
     * The completion door against `resolveGlobal`, with its one declared divergence.
     *
     * `wxFrame` and `AllColon` are `unresolvable` through the global door — a `@class` on a `local`
     * is not a global — and `[]` is the *matching* answer, not a regression: today's
     * `crossFileGlobalMembers` returns `emptyMap()` for them. The union would return their full
     * member list, i.e. invent members at a call site that offers none (TC 7b).
     */
    fun testTheCompletionDoorReproducesTheGlobalDoorExceptForBug430() {
        seed()
        assertEquals(
            "today's global door, recorded so the comparison below is against a measured value",
            mapOf(
                "wx" to listOf("wxFileExists", "wxID_ANY"),
                "wxFrame" to emptyList(),
                "AllColon" to emptyList(),
                "Shapes" to listOf("deep", "direct", "nested", "plain"),
            ),
            RECEIVERS.associateWith { globalDoor(it) },
        )
        assertEquals(
            "the only member the index may drop is `deep`, and only because the global door hoists " +
                "it out of `Shapes.nested` — BUG-430, which COMP-09 does not reproduce",
            mapOf(
                "wx" to listOf("wxFileExists", "wxID_ANY"),
                "wxFrame" to emptyList(),
                "AllColon" to emptyList(),
                "Shapes" to listOf("direct", "nested", "plain"),
            ),
            RECEIVERS.associateWith { completionDoor(it) },
        )
    }

    /**
     * Design §4.5a — the superset the reviews did not find, kept as an **expectation** rather than a
     * silent diff. `Derived` declares `---@field ownField`, the index sees it, and today's completion
     * door does not offer it. Inheritance is deliberately *not* added: DR-14 measured that the global
     * door does not inherit either, so a flat list is not a regression (COMP-09-03).
     */
    fun testTheCompletionDoorGainsAnAnnotatedFieldAndStillDoesNotInherit() {
        seedInheritance()
        assertEquals("today's global door does not inherit", listOf("ownFn"), globalDoor("Derived"))
        assertEquals("the class door does inherit — LuaGraphType merges supertypes", 4, classDoor("Derived").size)
        assertEquals(
            "one new member, `ownField`, and no inherited ones — design §4.5a",
            listOf("ownField", "ownFn"),
            completionDoor("Derived"),
        )
    }

    private fun classDoor(receiver: String): List<String> =
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            membersThrough(manager.resolveType(receiver, myFixture.file))
        }

    private fun globalDoor(receiver: String): List<String> =
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            membersThrough(manager.resolveGlobal(receiver, myFixture.file))
        }

    /** Never `resolveGlobal(r) ?: resolveType(r)` — design §1.4. Each caller names its own door. */
    private fun membersThrough(resolved: LuaType?): List<String> =
        resolved
            ?.let {
                LuaGraphType
                    .materialize(it, myFixture.file)
                    .getMembers()
                    .keys
                    .sorted()
            }
            ?: emptyList()

    private fun union(receiver: String): List<String> =
        runReadAction {
            LuaReceiverMemberIndex
                .membersIn(receiver, project, GlobalSearchScope.allScope(project))
                .map { it.name }
                .sorted()
        }

    private fun completionDoor(receiver: String): List<String> =
        runReadAction {
            LuaReceiverMemberIndex
                .membersOfGlobal(receiver, project, myFixture.file)
                .map { it.name }
                .sorted()
        }

    /** DR-14's fixture verbatim, so the figures asserted above are the ones DR-14 measured. */
    private fun seed() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@param filename string
                    ---@return boolean
                    function wx.wxFileExists(filename) end

                    return wx
                    """.trimIndent(),
                "wxframe.lua" to
                    """
                    ---@meta

                    ---@class wxFrame
                    ---@field title string
                    local wxFrame = {}

                    ---@return boolean
                    function wxFrame:Show() end

                    ---@return number
                    function wxFrame.staticCount() end
                    """.trimIndent(),
                "allcolon.lua" to
                    """
                    ---@meta

                    ---@class AllColon
                    local AllColon = {}

                    ---@return string
                    function AllColon:alpha() end
                    """.trimIndent(),
                "shapes.lua" to
                    """
                    ---@meta

                    ---@class Shapes
                    Shapes = {}

                    Shapes.nested = {}
                    Shapes.nested.deep = 1
                    Shapes.direct = 2

                    function Shapes.plain() end
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private fun seedInheritance() {
        registerLibraryRoot(
            mapOf(
                "base.lua" to
                    """
                    ---@meta

                    ---@class Base
                    ---@field inheritedField string
                    Base = {}

                    ---@return boolean
                    function Base.inheritedFn() end

                    return Base
                    """.trimIndent(),
                "derived.lua" to
                    """
                    ---@meta

                    ---@class Derived : Base
                    ---@field ownField number
                    Derived = {}

                    ---@return boolean
                    function Derived.ownFn() end

                    return Derived
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private companion object {
        val RECEIVERS = listOf("wx", "wxFrame", "AllColon", "Shapes")
    }
}
