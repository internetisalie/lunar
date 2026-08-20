package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BUG-437 — the indexer's emitted map, pinned exactly, so merging its traversals cannot change it.
 *
 * This is a **characterization** test, not a red-then-green one: BUG-437 is a chore with no
 * observable behaviour change, and its stated gate is that the emitted map stay *byte-identical*
 * while five sources collapse from seven traversals into one. So it was written **before** the
 * refactor, run green, and must stay green after — the point is that it never goes red.
 *
 * The fixture exercises every source design §4.3 names, plus the two BUG-439 added, because a merged
 * walk that quietly drops one of them is exactly the failure this guards.
 */
class LuaReceiverMemberIndexerWalkTest : BasePlatformTestCase() {
    fun testTheEmittedMapIsUnchangedByHowTheFileIsWalked() {
        myFixture.addFileToProject(
            "sources.lua",
            """
            Api = {}
            function Api.direct() end
            function Api:method() end
            Api.assigned = 1
            Api.fnValue = function() end

            Lit = { inLiteral = 2, alsoInLiteral = "s" }

            Opaque = require("elsewhere")
            function Opaque.extra() end

            ---@class Doc
            ---@field field string
            Doc = {}

            local Loc = {}
            function Loc.private() end
            """.trimIndent(),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")

        assertEquals(
            "dot/colon kind and separator must survive the walk",
            listOf("assigned:FIELD:DOT", "direct:FUNCTION:DOT", "fnValue:FUNCTION:DOT", "method:FUNCTION:COLON"),
            membersOf("Api"),
        )
        assertEquals(listOf("alsoInLiteral:FIELD:DOT", "inLiteral:FIELD:DOT"), membersOf("Lit"))
        assertEquals(
            "an opaquely-bound receiver still records its syntactic members",
            listOf("extra:FUNCTION:DOT"),
            membersOf("Opaque"),
        )
        assertEquals(listOf("field:FIELD:DOT"), membersOf("Doc"))
        assertEquals(
            "a file-local's members are recorded; the sentinel is what excludes it at the other door",
            listOf("private:FUNCTION:DOT"),
            membersOf("Loc"),
        )
    }

    /** The sentinels are index-internal and must never reach a caller, however the walk is arranged. */
    fun testNeitherSentinelLeaksThroughTheUnionDoor() {
        myFixture.addFileToProject(
            "s2.lua",
            "Opq = require(\"x\")\nfunction Opq.m() end\nlocal Lcl = {}\nfunction Lcl.n() end\n",
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val sentinels = setOf(LuaReceiverMember.OPAQUE_BINDING, LuaReceiverMember.LOCAL_BINDING)
        listOf("Opq", "Lcl").forEach { receiver ->
            assertTrue(
                "a sentinel reached the union door for $receiver",
                rawNamesOf(receiver).none { it in sentinels },
            )
        }
    }

    private fun membersOf(receiver: String): List<String> =
        runReadAction {
            LuaReceiverMemberIndex
                .membersIn(receiver, project, GlobalSearchScope.projectScope(project))
                .map { "${it.name}:${it.kind}:${it.separator}" }
                .sorted()
        }

    private fun rawNamesOf(receiver: String): List<String> =
        runReadAction {
            LuaReceiverMemberIndex
                .membersIn(receiver, project, GlobalSearchScope.projectScope(project))
                .map { it.name }
        }
}
