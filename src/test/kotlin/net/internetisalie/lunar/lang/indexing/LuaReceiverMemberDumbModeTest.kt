package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.DumbModeTestUtils
import net.internetisalie.lunar.definitions.LibraryRootTestCase

/**
 * COMP-09 Phase 1 — design §4.9, measured by DR-10 and now enforced.
 *
 * Both entry points touch `FileBasedIndex`, which throws `IndexNotReadyException` while the IDE is
 * indexing. DR-10 measured what each door does today:
 *
 * | door, while dumb | today |
 * | :-- | :-- |
 * | `resolveGlobal` | null — guarded at `LuaTypeManagerImpl:129` |
 * | completion, `wx.<caret>` | **`[]`** — empty, no throw, no error |
 * | the DR-09 prototype's union entry point | **threw `IndexNotReadyException`** |
 * | `resolveType` | logged an IDE internal error and rethrew — **BUG-432**, since fixed |
 *
 * So the index matches `resolveGlobal`'s behaviour and deliberately **not** `resolveType`'s: an
 * `IndexNotReadyException` is control flow, and logging it shows the user a crash report for the
 * crime of opening the IDE before indexing has finished.
 *
 * **Why these can fail rather than merely pass**: the platform's `TestLogger` turns `Logger.error`
 * into a `TestLoggerAssertionError`, so a door that logged instead of degrading fails here before
 * reaching an assertion — that is how BUG-432 was found. Nothing else in the suite queries this
 * index while dumb.
 */
class LuaReceiverMemberDumbModeTest : LibraryRootTestCase() {
    /**
     * `authoritative = true` while dumb is deliberate, and is the assertion most likely to be
     * "corrected" by someone who has not read §4.9: it stops the caller taking §4.5c's graph
     * fallback, which is itself dumb-guarded and would return null anyway. A `false` here would send
     * every dumb-mode completion through the 746 ms graph build to reach the same `[]`.
     */
    fun testGlobalMembershipDegradesToAnAuthoritativeEmptyWhileDumb() {
        seed()
        val smart = membershipHere()
        assertTrue("the fixture must give the smart door something to lose", smart.found)
        assertTrue("the fixture must give the smart door something to lose", smart.members.isNotEmpty())

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val dumb = membershipHere()
            assertEquals(
                "the user sees [], which is what completion offers today",
                emptyList<String>(),
                dumb.members.map { it.name },
            )
            assertFalse("nothing was looked up, so no declaring file was found", dumb.found)
            assertTrue(
                "authoritative must be TRUE while dumb — design §4.9: it keeps the caller off the " +
                    "graph fallback, which is dumb-guarded and would return null anyway",
                dumb.authoritative,
            )
        }
    }

    /** The union door has no authority flag to carry, so the whole obligation is "empty, not thrown". */
    fun testTheUnionDoorDegradesToEmptyWhileDumb() {
        seed()
        assertTrue("the fixture must give the smart door something to lose", unionMembers().isNotEmpty())

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertEquals(
                "the prototype threw IndexNotReadyException here; materialization must see an empty list",
                emptyList<String>(),
                unionMembers().map { it.name },
            )
        }
    }

    /** The counter must not report the previous enumeration's work as this one's. */
    fun testTheWorkCounterIsClearedByADumbEnumeration() {
        seed()
        unionMembers()
        assertTrue("the smart enumeration must visit a file for this to mean anything", LuaReceiverMemberWork.files > 0)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val visited =
                runReadAction {
                    LuaReceiverMemberIndex.membersIn(RECEIVER, project, GlobalSearchScope.allScope(project))
                    LuaReceiverMemberWork.files
                }
            assertEquals("a dumb enumeration traverses nothing and must say so", 0, visited)
        }
    }

    private fun membershipHere(): LuaReceiverMemberIndex.Membership =
        runReadAction { LuaReceiverMemberIndex.globalMembership(RECEIVER, project, myFixture.file) }

    private fun unionMembers(): List<LuaReceiverMember> =
        runReadAction {
            LuaReceiverMemberIndex.membersIn(RECEIVER, project, GlobalSearchScope.allScope(project))
        }

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

                    ---@return boolean
                    function wx.wxFileExists() end

                    return wx
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private companion object {
        const val RECEIVER = "wx"
    }
}
