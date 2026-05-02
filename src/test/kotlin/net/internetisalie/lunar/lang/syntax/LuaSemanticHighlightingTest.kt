package net.internetisalie.lunar.lang.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LuaSemanticHighlightingTest : BaseDocumentTest() {

    private fun assertHighlighted(text: String, expectedKey: TextAttributesKey) {
        val infos = myFixture.doHighlighting()
        val found = infos.any {
            it.forcedTextAttributesKey == expectedKey && it.text == text
        }
        if (!found) {
            val matchingText = infos.filter { it.text == text }.map { "${it.text}=${it.forcedTextAttributesKey?.externalName}" }
            val allText = infos.map { "${it.text}=${it.forcedTextAttributesKey?.externalName}" }.toSet()
            fail<Unit>("Expected text '${text}' to be highlighted with ${expectedKey.externalName}. Found attributes for this text: $matchingText. All highlights: $allText")
        }
    }

    @Test
    fun testLocalVariable() {
        myFixture.configureByText("test.lua", """
            local myLocal = 1
            local x = myLocal
        """.trimIndent())

        assertHighlighted("myLocal", LuaHighlight.VAR_LOCAL)
    }

    @Test
    fun testGlobalVariable() {
        myFixture.configureByText("test.lua", """
            myGlobal = 1
            local x = myGlobal
        """.trimIndent())

        assertHighlighted("myGlobal", LuaHighlight.VAR_GLOBAL)
    }

    @Test
    fun testParameter() {
        myFixture.configureByText("test.lua", """
            function foo(myParam)
                return myParam
            end
        """.trimIndent())

        assertHighlighted("myParam", LuaHighlight.PARAMETER)
    }

    @Test
    fun testUpvalue() {
        myFixture.configureByText("test.lua", """
            local myUpvalue = 1
            function foo()
                return myUpvalue
            end
        """.trimIndent())

        assertHighlighted("myUpvalue", LuaHighlight.VAR_UP_VALUE)
    }

    @Test
    fun testShadowedVariable() {
        myFixture.configureByText("test.lua", """
            local shadowedVar = 1
            do
                local shadowedVar = 2
                return shadowedVar
            end
        """.trimIndent())

        assertHighlighted("shadowedVar", LuaHighlight.VAR_SHADOWED)
    }}
