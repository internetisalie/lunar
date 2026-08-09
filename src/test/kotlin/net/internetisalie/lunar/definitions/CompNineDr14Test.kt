package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * THROWAWAY — COMP-09 DR-14, remediating Step 9 blockers B2 and B8.
 *
 * **B2.** Design §4.5 said `membersIn` "mirrors `doResolveGlobal`… take the members of the first
 * declaring file only, `typeOfGlobalIn`'s `.firstNotNullOfOrNull`". Review checked the *head* of that
 * chain, which I had not: `typeOfGlobalIn` draws candidate files from **`LuaGlobalAssignmentIndex`**
 * — bare top-level globals — not from the receiver-member index. Different candidate set, plus a
 * `.filter { it != exclude }` the proposed signature could not express, plus `globalTypeIn` skipping
 * files that declare the name with no useful type, plus "first" being undefined over an unordered
 * collection. Four divergences in one sentence, all from reading the tail of a call chain.
 *
 * `membersOfGlobal` is the corrected candidate. It reuses `LuaGlobalAssignmentIndex` for selection
 * and honours `exclude`. It deliberately **cannot** reproduce `globalTypeIn`'s skip, which needs the
 * graph build the feature exists to avoid — so the question is what that costs, measured.
 *
 * **B8.** The DR-09b golden took `resolveGlobal(r) ?: resolveType(r)`, which design §1.4 explicitly
 * forbids ("The first DR-01 attempt took `viaGlobal ?: viaType` and would have done exactly that").
 * So §4.4a's claim of exact `@class`-door parity on four receivers was never measured — for `wx` and
 * `Shapes` the golden was the *global* door. Printed here per door, separately.
 */
class CompNineDr14Test : LibraryRootTestCase() {
    private fun fixture() =
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
        )

    /** B8: the golden, per door, never collapsed with `?:`. */
    fun testDr14GoldenPerDoor() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            listOf("wx", "wxFrame", "AllColon", "Shapes").forEach { receiver ->
                val viaGlobal =
                    manager.resolveGlobal(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    }
                val viaType =
                    manager.resolveType(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    }
                println("DR-14 B8 $receiver  GLOBAL door = ${viaGlobal ?: "unresolvable"}")
                println("DR-14 B8 $receiver  CLASS  door = ${viaType ?: "unresolvable"}")
                if (viaGlobal != null && viaType != null && viaGlobal != viaType) {
                    println(
                        "DR-14 B8 $receiver  DOORS DISAGREE: global-only=${viaGlobal - viaType.toSet()} class-only=${viaType - viaGlobal.toSet()}",
                    )
                }
            }
        }
    }

    /** B2: does the corrected rule reproduce the door COMP-09-07 says it preserves? */
    fun testDr14CorrectedScopeRule() {
        registerLibraryRoot(fixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            val all = GlobalSearchScope.allScope(project)
            listOf("wx", "wxFrame", "AllColon", "Shapes").forEach { receiver ->
                val classDoor =
                    manager.resolveType(receiver, context)?.let {
                        LuaGraphType
                            .materialize(it, context)
                            .getMembers()
                            .keys
                            .sorted()
                    } ?: emptyList()
                val union = LuaReceiverMemberIndex.membersIn(receiver, project, all).map { it.name }.sorted()
                val corrected =
                    LuaReceiverMemberIndex
                        .membersOfGlobal(receiver, project, context)
                        .map { it.name }
                        .sorted()
                println("DR-14 B2 $receiver  classDoor=$classDoor")
                println("DR-14 B2 $receiver  union    =$union")
                println("DR-14 B2 $receiver  corrected=$corrected")
                println(
                    "DR-14 B2 $receiver  corrected vs classDoor: missing=${classDoor - corrected.toSet()} extra=${corrected - classDoor.toSet()}",
                )
            }
        }
    }

    /**
     * B2's ordering hazard, isolated. `unrelated.lua` declares a file-**local** `wx`, so it is absent
     * from `LuaGlobalAssignmentIndex` but present in the receiver-member index. If selection came
     * from the receiver index, "first file" could pick it and return a *disjoint* set, not a superset.
     */
    fun testDr14LocalReceiverIsNotSelectable() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    wx = {}

                    ---@return boolean
                    function wx.real() end

                    return wx
                    """.trimIndent(),
                "unrelated.lua" to
                    """
                    local wx = {}

                    function wx.privateToThisFile() end

                    return wx
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val all = GlobalSearchScope.allScope(project)
            val union = LuaReceiverMemberIndex.membersIn("wx", project, all).map { it.name }.sorted()
            val corrected =
                LuaReceiverMemberIndex
                    .membersOfGlobal("wx", project, myFixture.file)
                    .map { it.name }
                    .sorted()
            val golden =
                LuaTypeManager
                    .getInstance(project)
                    .resolveGlobal("wx", myFixture.file)
                    ?.let {
                        LuaGraphType
                            .materialize(it, myFixture.file)
                            .getMembers()
                            .keys
                            .sorted()
                    }
                    ?: emptyList()
            println("DR-14 B2b union     = $union")
            println("DR-14 B2b corrected = $corrected")
            println("DR-14 B2b golden    = $golden")
            println(
                "DR-14 B2b VERDICT: corrected ${if (corrected == golden) "MATCHES" else "DIFFERS FROM"} the global door",
            )
        }
    }

    /**
     * B5: COMP-09-03 (`Must`) scopes in "`@class` fields **and inherited members**", and the design
     * has no supertype walk anywhere. `LuaGraphType.kt:179` does
     * `for (superType in superTypes.reversed()) result.putAll(superType.getMembers())`, so today's
     * graph path inherits. Whether the *completion* door does is a different question, and it decides
     * whether a flat index list is a regression or a non-issue.
     */
    fun testDr14InheritedMembersOnTheGlobalDoor() {
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
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            val globalDoor =
                manager.resolveGlobal("Derived", context)?.let {
                    LuaGraphType
                        .materialize(it, context)
                        .getMembers()
                        .keys
                        .sorted()
                }
            val classDoor =
                manager.resolveType("Derived", context)?.let {
                    LuaGraphType
                        .materialize(it, context)
                        .getMembers()
                        .keys
                        .sorted()
                }
            val corrected =
                LuaReceiverMemberIndex
                    .membersOfGlobal("Derived", project, context)
                    .map { it.name }
                    .sorted()
            println("DR-14 B5 Derived GLOBAL door = $globalDoor")
            println("DR-14 B5 Derived CLASS  door = $classDoor")
            println("DR-14 B5 Derived corrected   = $corrected")
            println("DR-14 B5 inherited on GLOBAL door today = ${globalDoor?.contains("inheritedFn")}")
            println("DR-14 B5 inherited in index rule        = ${corrected.contains("inheritedFn")}")
        }
        val offered = completionsFor("Derived.<caret>\n")
        println("DR-14 B5 completion Derived. offers $offered")
    }
}
