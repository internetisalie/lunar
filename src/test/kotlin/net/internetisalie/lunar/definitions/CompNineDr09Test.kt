package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.lang.indexing.LuaReceiverMember
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.system.measureTimeMillis

/**
 * THROWAWAY — COMP-09 DR-09. Design §4 was written twice from reading the code and failed a Step 9
 * review both times (§4.9 D1–D3). This measures the prototype so §4 can be rewritten from what it
 * **does**, not from what its call shape suggests.
 *
 * Four questions, in the order §4.9 raises them:
 *   A  externalizer round-trip — §4.2 specified no wire format at all
 *   B  membership vs today's golden — the only gate that would catch D2
 *   D2 does `membersOf` over `allScope` union a superset? (Risk 1.1's predicted failure)
 *   D1 what does today's projectScope-then-allScope precedence do that a flat union does not?
 *   C  `membersOf` timing against the 9 568 ms `resolveGlobal` baseline, medians of 5 (DR-08)
 */
class CompNineDr09Test : LibraryRootTestCase() {
    // ------------------------------------------------------------------ A: externalizer

    fun testDr09aExternalizerRoundTrip() {
        val original =
            listOf(
                LuaReceiverMember("Show", LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.COLON),
                LuaReceiverMember("wxID_ANY", LuaReceiverMember.Kind.FIELD, LuaReceiverMember.Separator.DOT),
                LuaReceiverMember("wxFileExists", LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.DOT),
                LuaReceiverMember("ünïcødé", LuaReceiverMember.Kind.FIELD, LuaReceiverMember.Separator.DOT),
            )
        val externalizer = LuaReceiverMemberIndex().valueExternalizer
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { externalizer.save(it, original) }
        val restored = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { externalizer.read(it) }
        println("DR-09a wrote ${bytes.size()} bytes for ${original.size} members")
        println("DR-09a round-trip ${if (restored == original) "EXACT" else "LOSSY: $restored"}")
        assertEquals(original, restored)

        val empty = ByteArrayOutputStream()
        DataOutputStream(empty).use { externalizer.save(it, emptyList()) }
        val emptyBack = DataInputStream(ByteArrayInputStream(empty.toByteArray())).use { externalizer.read(it) }
        println("DR-09a empty list -> ${empty.size()} bytes, restored ${emptyBack.size} members")
    }

    // ------------------------------------------------------------------ B: membership vs golden

    /** The same fixture DR-01's golden was taken from, so the two are directly comparable. */
    private fun goldenFixture() =
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
                ---@field title string
                ---@field onClose fun(self: wxFrame): nil
                local wxFrame = {}

                ---@param show boolean
                ---@return boolean
                function wxFrame:Show(show) end

                ---@return string
                function wxFrame:GetTitle() end

                ---@return number
                function wxFrame.staticCount() end
                """.trimIndent(),
            // Every member colon-declared — the shape DR-06 showed has NO receiver key today.
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
            // §4.4's nested-qualifier rule and D3's assigned-function case.
            "shapes.lua" to
                """
                ---@meta

                ---@class Shapes
                Shapes = {}

                Shapes.nested = {}
                Shapes.nested.deep = 1

                Shapes.assignedFn = function() end

                local helper = function() end
                Shapes.aliasedFn = helper

                Shapes[1] = "keyed"

                function Shapes.plain() end
                """.trimIndent(),
        )

    fun testDr09bMembershipVersusGolden() {
        registerLibraryRoot(goldenFixture())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val manager = LuaTypeManager.getInstance(project)
            val context = myFixture.file
            val scope = GlobalSearchScope.allScope(project)
            listOf("wx", "wxFrame", "AllColon", "Shapes").forEach { receiver ->
                val resolved =
                    manager.resolveGlobal(receiver, context) ?: manager.resolveType(receiver, context)
                val golden =
                    resolved
                        ?.let {
                            LuaGraphType
                                .materialize(it, context)
                                .getMembers()
                                .keys
                                .sorted()
                        }
                        ?: emptyList()
                val indexed =
                    LuaReceiverMemberIndex
                        .membersOf(receiver, project, scope)
                        .map { it.name }
                        .sorted()
                val missing = golden - indexed.toSet()
                val extra = indexed - golden.toSet()
                println("DR-09b $receiver  golden=${golden.size} indexed=${indexed.size}")
                println("DR-09b   golden : $golden")
                println("DR-09b   indexed: $indexed")
                println("DR-09b   MISSING from index (regression if adopted): $missing")
                println("DR-09b   EXTRA in index (superset if adopted)     : $extra")
                val kinds =
                    LuaReceiverMemberIndex
                        .membersOf(receiver, project, scope)
                        .joinToString { "${it.name}=${it.kind}/${it.separator}" }
                println("DR-09b   kinds  : $kinds")
            }
        }
    }

    // ------------------------------------------------------------------ D2: the superset

    /**
     * §4.9 D2. `typeOfGlobalIn` takes the FIRST declaring file; `membersOf(r, allScope)` unions every
     * file that mentions `wx` as a receiver — including one where `wx` is a **local**, i.e. a
     * different symbol entirely. If the extra member shows up here, §4.5 is a membership superset.
     */
    fun testDr09d2UnionVersusFirstDeclaringFile() {
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
                // A DIFFERENT `wx` — file-local, unrelated to the global above.
                "unrelated.lua" to
                    """
                    local wx = {}

                    function wx.privateToThisFile() end

                    local function helper()
                        wx.alsoPrivate = 1
                    end

                    return wx
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            val byFile = LuaReceiverMemberIndex.membersByFile("wx", project, scope)
            byFile.forEach { (file, members) ->
                println("DR-09d2 ${file.name} -> ${members.map { it.name }}")
            }
            val unioned = LuaReceiverMemberIndex.membersOf("wx", project, scope).map { it.name }.sorted()
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
            println("DR-09d2 union  = $unioned")
            println("DR-09d2 golden = $golden")
            println(
                "DR-09d2 VERDICT: " +
                    if (unioned.toSet() == golden.toSet()) {
                        "no superset — the union matches today"
                    } else {
                        "SUPERSET CONFIRMED, extra=${unioned - golden.toSet()} missing=${golden - unioned.toSet()}"
                    },
            )
        }
    }

    /**
     * §4.9 D1. BUG-427 makes `doResolveGlobal` projectScope-then-allScope so a project's own
     * declaration beats a bundled stub's. Does that precedence change *membership*, or only which
     * declaration a member resolves to?
     */
    fun testDr09d1ScopePrecedence() {
        registerLibraryRoot(
            mapOf(
                "assert.lua" to
                    """
                    ---@meta

                    ---@class assertlib
                    assert = {}

                    ---@return boolean
                    function assert.fromLibrary() end
                    """.trimIndent(),
            ),
        )
        myFixture.addFileToProject(
            "project_assert.lua",
            """
            assert = {}

            function assert.fromProject() end
            """.trimIndent(),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        runReadAction {
            val project = myFixture.project
            val projectScope = GlobalSearchScope.projectScope(project)
            val projectOnly = LuaReceiverMemberIndex.membersOf("assert", project, projectScope)
            val all = LuaReceiverMemberIndex.membersOf("assert", project, GlobalSearchScope.allScope(project))
            val golden =
                LuaTypeManager
                    .getInstance(project)
                    .resolveGlobal("assert", myFixture.file)
                    ?.let {
                        LuaGraphType
                            .materialize(it, myFixture.file)
                            .getMembers()
                            .keys
                            .sorted()
                    }
                    ?: emptyList()
            println("DR-09d1 membersOf(projectScope) = ${projectOnly.map { it.name }.sorted()}")
            println("DR-09d1 membersOf(allScope)     = ${all.map { it.name }.sorted()}")
            println("DR-09d1 golden (today)          = $golden")
            println(
                "DR-09d1 => a two-phase membersOf is " +
                    if (projectOnly.isNotEmpty() && projectOnly.size != all.size) {
                        "DISTINGUISHABLE from a flat one here"
                    } else {
                        "indistinguishable on this fixture"
                    },
            )
        }
    }

    // ------------------------------------------------------------------ C: timing

    /** The BUG-429 shape: one library file, 3 400 constants + 200 functions + 300 classes. */
    private fun bigLibrary(): String {
        val root = StringBuilder("---@meta\n\n---@class wx\nwx = {}\n\n")
        repeat(3400) { i -> root.append("---@type number\nwx.wxC_$i = nil\n\n") }
        repeat(200) { i -> root.append("---@return boolean\nfunction wx.f$i() end\n\n") }
        repeat(300) { i ->
            root.append("---@class wxG$i\nlocal wxG$i = {}\n\n")
            repeat(8) { m -> root.append("---@return boolean\nfunction wxG$i:M$m() end\n\n") }
        }
        root.append("return wx\n")
        return root.toString()
    }

    fun testDr09cMembersOfTiming() {
        val text = bigLibrary()
        registerLibraryRoot(mapOf("wx.lua" to text))
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        println("DR-09c fixture ${text.length / 1024} KiB")

        runReadAction {
            val scope = GlobalSearchScope.allScope(project)
            var count = 0
            val samples =
                (1..5).map {
                    measureTimeMillis {
                        count = LuaReceiverMemberIndex.membersOf("wx", project, scope).size
                    }
                }
            val median = samples.sorted()[samples.size / 2]
            println("DR-09c membersOf(\"wx\") samples=$samples median=${median}ms members=$count")

            val narrowSamples =
                (1..5).map {
                    measureTimeMillis { LuaReceiverMemberIndex.membersOf("wxG7", project, scope).size }
                }
            val narrowMedian = narrowSamples.sorted()[2]
            println("DR-09c membersOf(\"wxG7\") (8 members) samples=$narrowSamples median=${narrowMedian}ms")

            // The baseline this has to beat, measured on the same fixture in the same run.
            val resolveMs =
                measureTimeMillis {
                    LuaTypeManager.getInstance(project).resolveGlobal("wx", myFixture.file)
                }
            println("DR-09c resolveGlobal(\"wx\") on the SAME fixture, cold = ${resolveMs}ms")
            println("DR-09c => index ${median}ms vs graph ${resolveMs}ms")
        }
    }
}
