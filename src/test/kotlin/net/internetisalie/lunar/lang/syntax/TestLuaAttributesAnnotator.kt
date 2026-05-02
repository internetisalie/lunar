package net.internetisalie.lunar.lang.syntax

import com.intellij.lang.annotation.HighlightSeverity
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.analysis.luacheck.LuaCheckSettings
import net.internetisalie.lunar.lang.LuaFileType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestLuaAttributesAnnotator : BaseDocumentTest() {

    @BeforeEach
    fun setupSettings() {
        LuaCheckSettings.getInstance().executablePath = ""
    }

    @Test
    fun testAttributeHighlighting() {
        myFixture.configureByText(LuaFileType, "local x <const> = 10")

        // We expect 'const' to be highlighted with ATTRIB_NAME
        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)

        val attributeHighlight = highlights.find { it.text == "const" }
        Assertions.assertNotNull(attributeHighlight, "Attribute 'const' should be highlighted")
        Assertions.assertEquals(LuaHighlight.ATTRIB_NAME, attributeHighlight!!.forcedTextAttributesKey)
    }

    @Test
    fun testCloseAttributeHighlighting() {
        myFixture.configureByText(LuaFileType, "local f <close> = io.open('test.txt')")

        val highlights = myFixture.doHighlighting(HighlightSeverity.TEXT_ATTRIBUTES)

        val attributeHighlight = highlights.find { it.text == "close" }
        Assertions.assertNotNull(attributeHighlight, "Attribute 'close' should be highlighted")
        Assertions.assertEquals(LuaHighlight.ATTRIB_NAME, attributeHighlight!!.forcedTextAttributesKey)
    }

    @Test
    fun testConstAssignmentError() {
        myFixture.configureByText(LuaFileType,
            """
                local x <const> = 10
                x = 20
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val constError = highlights.find { it.description?.contains("constant") == true || it.description?.contains("const") == true }

        Assertions.assertNotNull(constError, "Reassigning a constant variable should produce an error")
    }

        @Test
        fun testNoInitializationError() {
        myFixture.configureByText(LuaFileType, "local x <const>")

        val highlights = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val initError = highlights.find { it.description?.contains("initialized") == true || it.description?.contains("initialization") == true }

        Assertions.assertNotNull(initError, "Local variable with attribute must be initialized")
        }
        }

