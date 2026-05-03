package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestLuaGotoAnnotator : BaseDocumentTest() {

    @Test
    fun testUnresolvedLabel() {
        myFixture.configureByText(LuaFileType, "goto missing")
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val error = highlights.find { it.description?.contains("Unresolved label 'missing'") == true }
        Assertions.assertNotNull(error, "Should flag unresolved label")
    }

    @Test
    fun testDuplicateLabel() {
        myFixture.configureByText(LuaFileType,
            """
                ::dup::
                ::dup::
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val error = highlights.find { it.description?.contains("Duplicate label 'dup'") == true }
        Assertions.assertNotNull(error, "Should flag duplicate label")
    }

    @Test
    fun testInvalidScopeJump() {
        myFixture.configureByText(LuaFileType,
            """
                goto bad
                local x = 1
                ::bad::
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val error = highlights.find { it.description?.contains("jump into the scope of local") == true }
        Assertions.assertNotNull(error, "Should flag jump into local scope")
    }

    @Test
    fun testValidBackwardJump() {
        myFixture.configureByText(LuaFileType,
            """
                ::good::
                local x = 1
                goto good
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        Assertions.assertTrue(highlights.isEmpty(), "Backward jump should be valid")
    }

    @Test
    fun testValidJumpOutOfBlock() {
        myFixture.configureByText(LuaFileType,
            """
                do
                    goto out
                end
                ::out::
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        Assertions.assertTrue(highlights.isEmpty(), "Jump out of block should be valid")
    }

    @Test
    fun testLabelVisibilityInNestedFunction() {
        myFixture.configureByText(LuaFileType,
            """
                ::top::
                local function f()
                    goto top
                end
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val error = highlights.find { it.description?.contains("Unresolved label 'top'") == true }
        Assertions.assertNotNull(error, "Label should not be visible in nested function")
    }

    @Test
    fun testLabelShadowing() {
        myFixture.configureByText(LuaFileType,
            """
                ::a::
                do
                    ::a::
                    goto a
                end
            """.trimIndent()
        )
        // No errors expected
        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        Assertions.assertTrue(highlights.isEmpty(), "Shadowing labels is valid")
    }
}
