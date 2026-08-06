package net.internetisalie.lunar.luacats

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer

/**
 * BUG-406. LDoc's `@param <name> <description>` has no type slot, so `luacats.bnf:143` consumes the
 * first word of the prose as the parameter's type and the doc surfaces render `array: Lua`.
 *
 * These assert on the **rendered documentation**, which is where the damage actually shows. The type
 * engine was probed and is unaffected — `LuaTypeGraphBridge` already discards a `@param` type that
 * resolves to nothing — so a test asserting on inferred types would pass with the bug present.
 */
class LdocUntypedParamTest : BasePlatformTestCase() {

    /**
     * Rendered documentation with markup stripped. Asserting on the raw HTML is what made the first
     * draft of these tests vacuous: `renderTypeText` wraps every type in `<font color=…>`, so
     * `contains("(Lua)")` was false whether or not the bug was present.
     */
    private fun renderDoc(source: String): String {
        myFixture.configureByText("doc.lua", source)
        val decl = PsiTreeUtil.findChildOfType(myFixture.file, LuaLocalFuncDecl::class.java)!!
        val html = LuaCatsDocumentationRenderer.renderDoc(decl) ?: error("no documentation rendered")
        return html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** The reported case: prose words must not be presented as types. */
    fun testLdocProseIsNotRenderedAsAType() {
        val doc = renderDoc(
            """
            --- Search an array.
            --- @param array Lua table of values to search
            --- @param e a value
            local function findValue(array, e)
                return array[1] == e
            end
            """.trimIndent(),
        )
        assertFalse("'Lua' is a description word, not a type:\n$doc", doc.contains("array ( Lua )"))
        assertFalse("'a' is a description word, not a type:\n$doc", doc.contains("e ( a )"))
        assertTrue("an untyped parameter must still be listed:\n$doc", doc.contains("array - Lua table of values"))
    }

    /** Nothing may be lost: the words demoted from the type slot stay in the description. */
    fun testDemotedWordsSurviveInTheDescription() {
        val doc = renderDoc(
            """
            --- Search an array.
            --- @param array Lua table of values to search
            local function findValue(array)
                return array[1]
            end
            """.trimIndent(),
        )
        assertTrue("the full description must survive:\n$doc", doc.contains("Lua table of values to search"))
    }

    /** A genuine LuaCATS primitive type must still render as a type. */
    fun testRealPrimitiveTypeStillRenders() {
        val doc = renderDoc(
            """
            --- Greet someone.
            --- @param name string the person's name
            local function greet(name)
                return name
            end
            """.trimIndent(),
        )
        assertTrue("a real type must still render:\n$doc", doc.contains("name ( string )"))
    }

    /** A `@class`-declared name is resolvable, so it must still render as a type. */
    fun testDeclaredClassTypeStillRenders() {
        val doc = renderDoc(
            """
            --- @class Builder
            local Builder = {}

            --- Build it.
            --- @param b Builder the builder to use
            local function build(b)
                return b
            end
            """.trimIndent(),
        )
        assertTrue("a declared class must still render:\n$doc", doc.contains("b ( Builder )"))
    }

    /**
     * The boundary the first attempt got wrong. An unresolvable name with **no prose behind it** is
     * still a type — a `@class` declared in a file the index has not seen looks exactly like a prose
     * word, and demoting it would drop a type the author did write. Demotion needs both signals:
     * unresolvable *and* followed by a description.
     */
    fun testUnresolvableNameWithNoDescriptionStaysAType() {
        val doc = renderDoc(
            """
            --- Move it.
            --- @param a Player
            local function move(a)
                return a
            end
            """.trimIndent(),
        )
        assertTrue("an undeclared type with no description must survive:\n$doc", doc.contains("a ( Player )"))
    }

    /** Structural type syntax is unambiguous and must never be demoted. */
    fun testStructuralTypesStillRender() {
        val doc = renderDoc(
            """
            --- Join things.
            --- @param parts string[] the parts
            --- @param sep string|nil the separator
            local function join(parts, sep)
                return parts, sep
            end
            """.trimIndent(),
        )
        assertTrue("an array type must still render:\n$doc", doc.contains("parts ( string[] )"))
        assertTrue("a union type must still render:\n$doc", doc.contains("sep ( string|nil )"))
    }
}
