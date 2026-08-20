package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-430: `a.b.c = v` made `c` a member of **`a`** and left `a.b` empty.
 *
 * Both halves are user-visible and they are complements: every nested member was offered at exactly
 * the one path where it does not exist, and withheld from the one where it does. `Config.db.host = …`
 * is ordinary Lua.
 *
 * Measured before the fix (COMP-09 DR-09b / DR-12):
 * ```
 * Foo.      offers [bar, baz, direct]     <- `baz` is not a member of Foo
 * Foo.bar.  offers []                     <- and it IS a member of Foo.bar
 * ```
 *
 * The two enumeration doors disagreed, which is why this was invisible for so long: `resolveType`
 * (the `---@class` door) returns the correct set, `resolveGlobal` (the type-graph door) returns the
 * flattened one, and which one answers depends on whether the receiver happens to carry a `---@class`.
 * These tests drive **completion**, so they assert what a user sees rather than which door answered.
 */
@RunWith(JUnit4::class)
class LuaNestedMemberAssignmentTest : BasePlatformTestCase() {
    private fun completionsFor(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        return myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
    }

    private fun seedNested() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            Foo = {}
            Foo.bar = {}
            Foo.bar.baz = 1
            Foo.bar.qux = "s"
            Foo.direct = 2
            """.trimIndent(),
        )
    }

    /** Defect 1: the grandchild must not be hoisted onto the root. */
    @Test
    fun testGrandchildIsNotAMemberOfTheRoot() {
        seedNested()
        val offered = completionsFor("Foo.<caret>\n")
        assertFalse("`baz` is a member of Foo.bar, not of Foo — offered: $offered", offered.contains("baz"))
        assertFalse("`qux` is a member of Foo.bar, not of Foo — offered: $offered", offered.contains("qux"))
    }

    /** The same call must keep the members that really are the root's. */
    @Test
    fun testRootKeepsItsOwnMembers() {
        seedNested()
        val offered = completionsFor("Foo.<caret>\n")
        assertTrue("`bar` is a member of Foo — offered: $offered", offered.contains("bar"))
        assertTrue("`direct` is a member of Foo — offered: $offered", offered.contains("direct"))
    }

    /**
     * Defect 2, and the half that must not be fixed alone: correcting the hoist without populating
     * the intermediate takes `baz` from the wrong place to nowhere.
     */
    @Test
    fun testIntermediateTableCarriesItsMembers() {
        seedNested()
        val offered = completionsFor("Foo.bar.<caret>\n")
        assertTrue("`baz` is a member of Foo.bar — offered: $offered", offered.contains("baz"))
        assertTrue("`qux` is a member of Foo.bar — offered: $offered", offered.contains("qux"))
    }

    /**
     * The control, and the reason the other tests mean anything.
     *
     * A fix that stopped populating tables altogether satisfies every assertion above — `baz` would
     * not be on `Foo`, which is all two of them ask. This is the one that goes red if the members
     * are removed rather than relocated.
     */
    @Test
    fun testAFlatTableKeepsItsMembers() {
        myFixture.addFileToProject(
            "flat.lua",
            """
            Flat = {}
            Flat.only = 1
            Flat.other = "s"
            """.trimIndent(),
        )
        val offered = completionsFor("Flat.<caret>\n")
        assertTrue("`only` is a member of Flat — offered: $offered", offered.contains("only"))
        assertTrue("`other` is a member of Flat — offered: $offered", offered.contains("other"))
    }

    /** The shape with no `---@class` anywhere, so only the flattening door can answer. */
    @Test
    fun testPlainTableChainWithNoClassAnnotation() {
        myFixture.addFileToProject(
            "plain.lua",
            """
            Plain = {}
            Plain.mid = {}
            Plain.mid.leaf = 1
            """.trimIndent(),
        )
        val root = completionsFor("Plain.<caret>\n")
        assertFalse("`leaf` belongs to Plain.mid — offered: $root", root.contains("leaf"))
        assertTrue("`mid` belongs to Plain — offered: $root", root.contains("mid"))
        assertTrue("`leaf` belongs to Plain.mid — offered: ${completionsFor("Plain.mid.<caret>\n")}", completionsFor("Plain.mid.<caret>\n").contains("leaf"))
    }
}
