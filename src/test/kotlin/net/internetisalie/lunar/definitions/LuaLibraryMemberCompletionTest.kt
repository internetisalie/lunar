package net.internetisalie.lunar.definitions

/**
 * BUG-431: what a **library**-declared namespace member is actually offered and inserted as.
 *
 * The bug report read a completion of `wx.wxID_<caret>` as returning the qualified `wx.wxID_ANY` as
 * its lookup string, which would insert `wx.wx.wxID_ANY` in a running IDE. Measurement says
 * otherwise on every count — the qualified string was a test harness reading a whole document line
 * where a lookup string was expected (fixed in [LibraryRootTestCase.completionsFor]), and the
 * behaviour underneath it was correct all along.
 *
 * That leaves the claim untested, which is why this exists: the spike whose assertion raised it is a
 * throwaway ([TargetTenDrSpikeTest]) and takes its coverage with it when it goes. These are the
 * three properties the report doubted, pinned against the source it doubted them for — a registered
 * library root, where resolution reaches members only through `allScope` and so takes a different
 * path from a project file.
 */
class LuaLibraryMemberCompletionTest : LibraryRootTestCase() {
    private val namespace =
        """
        ---@meta

        ---@class wx
        wx = {}

        ---@type number
        wx.wxID_ANY = nil

        ---@type number
        wx.wxID_OK = nil

        ---@param parent any
        function wx.wxFrame(parent) end

        return wx
        """.trimIndent()

    /**
     * The whole offered set at a bare receiver caret, asserted exactly rather than by membership.
     *
     * Exact is the point: `contains` would pass just as well against a qualified extra or a
     * duplicate, and a duplicate is not cosmetic here — a second element suppresses the
     * single-perfect-match auto-insert, which is what the insertion tests below turn on.
     */
    private fun assertOffersItsMembersBareAndOnce() {
        val found = completionsFor("wx.<caret>\n")
        assertEquals(
            "a namespace must offer its members bare and once each. Found: $found",
            listOf("wxFrame", "wxID_ANY", "wxID_OK"),
            found.sorted(),
        )
    }

    /** The narrowed caret of the bug report: one match survives the prefix, so it auto-inserts. */
    private fun assertASinglePerfectMatchInsertsTheMemberOnce() {
        myFixture.configureByText("consumer.lua", "wx.wxID_A<caret>\n")
        myFixture.completeBasic()
        assertEquals(
            "accepting a namespace member must not re-insert the receiver",
            "wx.wxID_ANY\n",
            myFixture.editor.document.text,
        )
    }

    fun testLibraryMembersAreOfferedUnqualifiedAndOnce() {
        registerLibraryRoot(mapOf("wx.lua" to namespace))
        assertOffersItsMembersBareAndOnce()
    }

    fun testAcceptingALibraryMemberInsertsItOnceAfterTheReceiver() {
        registerLibraryRoot(mapOf("wx.lua" to namespace))
        assertASinglePerfectMatchInsertsTheMemberOnce()
    }

    /**
     * The report's third possibility — that the answer differs by source — measured rather than
     * argued, by putting the identical fixture through the identical carets from a project file.
     * The two sources cannot be compared within one test: they would declare the same global, and
     * `resolveGlobal` searches `projectScope` before `allScope`, so the project copy would answer
     * both halves. Separate cases against one expectation is the comparison.
     */
    fun testAProjectDeclarationOffersAndInsertsTheSame() {
        myFixture.addFileToProject("wx.lua", namespace)
        assertOffersItsMembersBareAndOnce()
        assertASinglePerfectMatchInsertsTheMemberOnce()
    }
}
