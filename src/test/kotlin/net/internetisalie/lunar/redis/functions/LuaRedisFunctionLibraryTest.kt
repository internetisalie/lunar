package net.internetisalie.lunar.redis.functions

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.syntax.LuaHighlight
import net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REDIS-05 Phase 1 — shebang parse/detect tests (AC-1 / AC-2).
 *
 * Covers TC-SHB-1, TC-SHB-2, TC-SCAN-1 from requirements.md.
 */
@RunWith(JUnit4::class)
class LuaRedisFunctionLibraryTest : IndexedBasePlatformTestCase() {

    // -------------------------------------------------------------------------
    // TC-SHB-1: Shebang parses cleanly; tokens and highlighting are correct.
    // -------------------------------------------------------------------------
    @Test
    fun testShebangParsesCleanly_TC_SHB_1() {
        val file = myFixture.configureByText(
            "lib.lua",
            "#!lua name=mylib\nredis.register_function('f', function(keys, args) return 1 end)",
        )
        // No parse errors
        val errorElement = PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java)
        assertNull("Shebang file must parse without PsiErrorElement", errorElement)

        // Deepest-first leaf is SHEBANG (#!)
        val firstLeaf = PsiTreeUtil.getDeepestFirst(file)
        assertEquals(
            "First leaf must be SHEBANG",
            LuaElementTypes.SHEBANG,
            firstLeaf.elementType,
        )
        assertEquals("SHEBANG leaf text must be '#!'", "#!", firstLeaf.text)

        // Next non-whitespace leaf is a SHORTCOMMENT with the shebang remainder
        var next = PsiTreeUtil.nextLeaf(firstLeaf)
        while (next != null && next.text.isBlank() && next.elementType != LuaElementTypes.SHORTCOMMENT) {
            next = PsiTreeUtil.nextLeaf(next)
        }
        assertNotNull("A SHORTCOMMENT leaf must follow the SHEBANG", next)
        assertEquals(
            "SHORTCOMMENT text must contain 'lua name=mylib'",
            LuaElementTypes.SHORTCOMMENT,
            next!!.elementType,
        )
        assertEquals("lua name=mylib", next.text)

        // SHEBANG token maps to LuaHighlight.COMMENT
        val highlighter = LuaSyntaxHighlighter()
        val highlights = highlighter.getTokenHighlights(LuaElementTypes.SHEBANG)
        assertTrue(
            "SHEBANG must highlight as COMMENT, got: ${highlights.toList()}",
            highlights.contains(LuaHighlight.COMMENT),
        )
    }

    // -------------------------------------------------------------------------
    // TC-SHB-2: detect() matrix — whitespace variants and non-library files.
    // -------------------------------------------------------------------------
    @Test
    fun testDetectLibraryName_TC_SHB_2() {
        assertDetect("#!lua name=lib1\n-- code", "lib1")
        assertDetect("#!lua  name=lib_2\n-- code", "lib_2")
        assertDetect("#! lua name=x\n-- code", "x")
        assertDetect("-- not a shebang\n", null)
        assertDetect("local x = 1\n", null)
    }

    // -------------------------------------------------------------------------
    // TC-SCAN-1: registeredNames() collects literal names and sets hasDynamic.
    // -------------------------------------------------------------------------
    @Test
    fun testRegisteredNames_TC_SCAN_1() {
        val file = myFixture.configureByText(
            "lib.lua",
            """
            #!lua name=mylib
            redis.register_function('a', function(keys, args) return 1 end)
            redis.register_function(nameVar, function(keys, args) return 2 end)
            """.trimIndent(),
        )
        val result = LuaRedisFunctionLibrary.registeredNames(file)
        assertEquals("Literal name 'a' must be collected", setOf("a"), result.names)
        assertTrue("Dynamic registration must set hasDynamic", result.hasDynamic)
    }

    @Test
    fun testRegisteredNamesTableForm() {
        val file = myFixture.configureByText(
            "lib.lua",
            """
            #!lua name=mylib
            redis.register_function{ function_name='b', callback=function(keys, args) end, flags={'no-writes'} }
            """.trimIndent(),
        )
        val result = LuaRedisFunctionLibrary.registeredNames(file)
        assertEquals("Table-form name 'b' must be collected", setOf("b"), result.names)
        assertFalse("No dynamic registrations", result.hasDynamic)

        val flags = LuaRedisFunctionLibrary.registeredFlags(file, "b")
        assertEquals("no-writes flag must be captured", setOf("no-writes"), flags)
    }

    @Test
    fun testNonLibraryFileDetectsNull() {
        val file = myFixture.configureByText("script.lua", "local x = redis.call('GET', 'key')")
        assertNull("Plain script must not detect as library", LuaRedisFunctionLibrary.detect(file))
        assertFalse("isLibrary must return false for plain script", LuaRedisFunctionLibrary.isLibrary(file))
    }

    // -------------------------------------------------------------------------
    // Helper: configures a text file and asserts detect() result.
    // -------------------------------------------------------------------------
    private fun assertDetect(content: String, expected: String?) {
        val file = myFixture.configureByText("test.lua", content)
        val actual = LuaRedisFunctionLibrary.detect(file)
        assertEquals("detect('${content.take(30)}') expected=$expected", expected, actual)
    }
}
